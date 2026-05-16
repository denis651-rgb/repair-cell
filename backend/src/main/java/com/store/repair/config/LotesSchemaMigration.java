package com.store.repair.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
@Order(100)
public class LotesSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                crearTablaLotes(connection);
                asegurarColumnaMotivoCierre(connection);
                asegurarColumnaPrecioVentaUnitario(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void crearTablaLotes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS lotes_inventario (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        variante_id INTEGER NOT NULL,
                        proveedor_id INTEGER,
                        codigo_lote TEXT NOT NULL UNIQUE,
                        codigo_proveedor TEXT,
                        fecha_ingreso TEXT NOT NULL,
                        cantidad_inicial INTEGER NOT NULL DEFAULT 0,
                        cantidad_disponible INTEGER NOT NULL DEFAULT 0,
                        costo_unitario REAL NOT NULL DEFAULT 0,
                        precio_venta_unitario REAL NOT NULL DEFAULT 0,
                        subtotal_compra REAL NOT NULL DEFAULT 0,
                        estado TEXT NOT NULL,
                        compra_id INTEGER,
                        activo INTEGER NOT NULL DEFAULT 1,
                        visible_en_ventas INTEGER NOT NULL DEFAULT 1,
                        fecha_cierre TEXT,
                        motivo_cierre TEXT,
                        creado_en TEXT NOT NULL,
                        actualizado_en TEXT NOT NULL,
                        FOREIGN KEY (variante_id) REFERENCES productos_variantes(id),
                        FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lotes_variante ON lotes_inventario(variante_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lotes_proveedor ON lotes_inventario(proveedor_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lotes_estado ON lotes_inventario(estado)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lotes_fecha_ingreso ON lotes_inventario(fecha_ingreso)");
        }
    }

    private void asegurarColumnaMotivoCierre(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE lotes_inventario ADD COLUMN motivo_cierre TEXT");
        } catch (SQLException ignored) {
            // SQLite throws when the column already exists; we keep the migration idempotent.
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE lotes_inventario ADD COLUMN proveedor_id INTEGER");
        } catch (SQLException ignored) {
            // SQLite throws when the column already exists; we keep the migration idempotent.
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lotes_proveedor ON lotes_inventario(proveedor_id)");
        }
    }

    private void asegurarColumnaPrecioVentaUnitario(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE lotes_inventario ADD COLUMN precio_venta_unitario REAL NOT NULL DEFAULT 0");
        } catch (SQLException ignored) {
            // SQLite throws when the column already exists; we keep the migration idempotent.
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE lotes_inventario
                    SET precio_venta_unitario = COALESCE(NULLIF((
                        SELECT cd.precio_venta_unitario
                        FROM compras_detalle cd
                        WHERE cd.compra_id = lotes_inventario.compra_id
                          AND cd.variante_id = lotes_inventario.variante_id
                          AND (
                              cd.codigo_lote = lotes_inventario.codigo_lote
                              OR cd.codigo_proveedor = lotes_inventario.codigo_proveedor
                          )
                        ORDER BY cd.id DESC
                        LIMIT 1
                    ), 0), NULLIF((
                        SELECT pv.precio_venta_sugerido
                        FROM productos_variantes pv
                        WHERE pv.id = lotes_inventario.variante_id
                    ), 0), 0)
                    WHERE precio_venta_unitario IS NULL OR precio_venta_unitario = 0
                    """);
        } catch (SQLException ignored) {
            // If catalog/purchase tables are created by another runner later, Hibernate keeps the new column
            // and future lot writes will store their own sale price.
        }
    }
}
