package com.store.repair.config;

import com.store.repair.util.SkuUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class InventarioSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                asegurarTablaMarcas(connection);
                migrarProductosConMarca(connection);
                migrarSkusProductos(connection);
                migrarMovimientosStock(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void asegurarTablaMarcas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS marcas_inventario (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        nombre TEXT NOT NULL UNIQUE,
                        descripcion TEXT,
                        activa INTEGER NOT NULL DEFAULT 1,
                        creado_en TEXT NOT NULL,
                        actualizado_en TEXT NOT NULL
                    )
                    """);
        }

        asegurarColumna(connection, "marcas_inventario", "descripcion", "TEXT");
        asegurarColumna(connection, "marcas_inventario", "activa", "INTEGER NOT NULL DEFAULT 1");
    }

    private void migrarProductosConMarca(Connection connection) throws SQLException {
        if (!existeTabla(connection, "productos_inventario")) {
            return;
        }

        asegurarColumna(connection, "productos_inventario", "marca_id", "INTEGER");
        asegurarColumna(connection, "productos_inventario", "calidad", "TEXT");
        asegurarIndice(connection, "idx_productos_marca", "productos_inventario", "marca_id");
        asegurarIndice(connection, "idx_productos_categoria", "productos_inventario", "categoria_id");

        boolean existeMarcaTexto = existeColumna(connection, "productos_inventario", "marca");
        if (existeMarcaTexto) {
            insertarMarcasDesdeTexto(connection);
            asignarMarcaIdDesdeTexto(connection);
        }

        // Mantenemos la migracion tolerante para bases viejas: si algun producto
        // no pudo mapearse, lo vinculamos a una marca neutra para no romper lecturas.
        long marcaPorDefectoId = asegurarMarcaPorDefecto(connection);
        asignarMarcaPorDefecto(connection, marcaPorDefectoId);
    }

    private void migrarMovimientosStock(Connection connection) throws SQLException {
        if (!existeTabla(connection, "movimientos_stock")) {
            return;
        }

        asegurarColumna(connection, "movimientos_stock", "stock_anterior", "INTEGER");
        asegurarColumna(connection, "movimientos_stock", "stock_posterior", "INTEGER");
        asegurarColumna(connection, "movimientos_stock", "costo_unitario", "REAL");
        asegurarColumna(connection, "movimientos_stock", "precio_venta_unitario", "REAL");
        asegurarIndice(connection, "idx_movimientos_stock_fecha", "movimientos_stock", "fecha_movimiento");
    }

    private void migrarSkusProductos(Connection connection) throws SQLException {
        if (!existeTabla(connection, "productos_inventario")) {
            return;
        }

        asegurarColumna(connection, "productos_inventario", "sku", "TEXT");

        Set<String> usados = new HashSet<>();
        List<ProductoSkuMigracion> productos = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.id, p.sku, p.nombre, p.calidad, c.nombre AS categoria_nombre, m.nombre AS marca_nombre
                FROM productos_inventario p
                LEFT JOIN categorias_inventario c ON c.id = p.categoria_id
                LEFT JOIN marcas_inventario m ON m.id = p.marca_id
                ORDER BY p.id
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                productos.add(new ProductoSkuMigracion(
                        resultSet.getLong("id"),
                        resultSet.getString("sku"),
                        resultSet.getString("nombre"),
                        resultSet.getString("calidad"),
                        resultSet.getString("categoria_nombre"),
                        resultSet.getString("marca_nombre")));
            }
        }

        for (ProductoSkuMigracion producto : productos) {
            String sugerido = SkuUtils.normalize(producto.sku());
            if (sugerido == null) {
                sugerido = SkuUtils.suggest(
                        producto.categoriaNombre(),
                        producto.marcaNombre(),
                        producto.nombre(),
                        producto.calidad());
            }

            String skuFinal = SkuUtils.ensureUnique(sugerido, sku -> usados.contains(sku.toUpperCase()));
            usados.add(skuFinal.toUpperCase());

            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE productos_inventario
                    SET sku = ?
                    WHERE id = ?
                    """)) {
                update.setString(1, skuFinal);
                update.setLong(2, producto.id());
                update.executeUpdate();
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_productos_sku_unique ON productos_inventario(sku)");
        }
    }

    private void insertarMarcasDesdeTexto(Connection connection) throws SQLException {
        List<String> marcas = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT DISTINCT TRIM(marca) AS marca
                     FROM productos_inventario
                     WHERE marca IS NOT NULL AND TRIM(marca) <> ''
                     """)) {
            while (resultSet.next()) {
                marcas.add(resultSet.getString("marca"));
            }
        }

        for (String marca : marcas) {
            insertarMarcaSiNoExiste(connection, marca, null);
        }
    }

    private void asignarMarcaIdDesdeTexto(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE productos_inventario
                    SET marca_id = (
                        SELECT id
                        FROM marcas_inventario
                        WHERE lower(nombre) = lower(trim(productos_inventario.marca))
                        LIMIT 1
                    )
                    WHERE (marca_id IS NULL OR marca_id = 0)
                      AND marca IS NOT NULL
                      AND TRIM(marca) <> ''
                    """);
        }
    }

    private long asegurarMarcaPorDefecto(Connection connection) throws SQLException {
        insertarMarcaSiNoExiste(connection, "Sin marca", "Marca generada automaticamente para migracion");

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM marcas_inventario WHERE nombre = ? LIMIT 1")) {
            statement.setString(1, "Sin marca");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("id");
                }
            }
        }

        throw new SQLException("No se pudo obtener la marca por defecto");
    }

    private void asignarMarcaPorDefecto(Connection connection, long marcaId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE productos_inventario
                SET marca_id = ?
                WHERE marca_id IS NULL OR marca_id = 0
                """)) {
            statement.setLong(1, marcaId);
            statement.executeUpdate();
        }
    }

    private void insertarMarcaSiNoExiste(Connection connection, String nombre, String descripcion) throws SQLException {
        String ahora = LocalDateTime.now().toString();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marcas_inventario (nombre, descripcion, activa, creado_en, actualizado_en)
                SELECT ?, ?, 1, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM marcas_inventario WHERE lower(nombre) = lower(?)
                )
                """)) {
            statement.setString(1, nombre);
            statement.setString(2, descripcion);
            statement.setString(3, ahora);
            statement.setString(4, ahora);
            statement.setString(5, nombre);
            statement.executeUpdate();
        }
    }

    private boolean existeTabla(Connection connection, String nombreTabla) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name = ?
                """)) {
            statement.setString(1, nombreTabla);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean existeColumna(Connection connection, String nombreTabla, String nombreColumna) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + nombreTabla + ")")) {
            while (resultSet.next()) {
                if (nombreColumna.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void asegurarColumna(Connection connection, String nombreTabla, String nombreColumna, String definicion) throws SQLException {
        if (existeColumna(connection, nombreTabla, nombreColumna)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + nombreTabla + " ADD COLUMN " + nombreColumna + " " + definicion);
        }
    }

    private void asegurarIndice(Connection connection, String nombreIndice, String tabla, String columna) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + nombreIndice + " ON " + tabla + "(" + columna + ")");
        }
    }

    private record ProductoSkuMigracion(
            long id,
            String sku,
            String nombre,
            String calidad,
            String categoriaNombre,
            String marcaNombre) {
    }
}
