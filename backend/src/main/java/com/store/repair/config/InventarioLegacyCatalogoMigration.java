package com.store.repair.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.inventory.migrate-legacy-on-start", havingValue = "true")
public class InventarioLegacyCatalogoMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void migrarInventarioViejoAlCatalogoNuevo() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!existeTabla(connection, "productos_inventario")) {
                return;
            }

            if (!existeTabla(connection, "productos_base")
                    || !existeTabla(connection, "productos_variantes")
                    || !existeTabla(connection, "lotes_inventario")) {
                log.warn(
                        "No se puede migrar inventario viejo porque aun no existen las tablas nuevas de catalogo/lotes.");
                return;
            }

            connection.setAutoCommit(false);

            try {
                int migrados = migrarProductos(connection);
                connection.commit();

                if (migrados > 0) {
                    log.info("Migracion inventario viejo -> catalogo nuevo completada. Productos procesados: {}",
                            migrados);
                }
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private int migrarProductos(Connection connection) throws SQLException {
        int procesados = 0;

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    id,
                    sku,
                    nombre,
                    descripcion,
                    categoria_id,
                    marca_id,
                    calidad,
                    costo_unitario,
                    precio_venta,
                    cantidad_stock,
                    stock_minimo,
                    activo
                FROM productos_inventario
                ORDER BY id
                """);
                ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                Long productoViejoId = rs.getLong("id");
                String sku = limpiarTexto(rs.getString("sku"));

                if (sku == null || sku.isBlank()) {
                    sku = "LEGACY-" + productoViejoId;
                }

                Long categoriaId = leerLongNullable(rs, "categoria_id");
                Long marcaId = leerLongNullable(rs, "marca_id");

                if (categoriaId == null || categoriaId <= 0) {
                    log.warn("Producto viejo {} omitido: no tiene categoria_id.", productoViejoId);
                    continue;
                }

                if (marcaId == null || marcaId <= 0) {
                    marcaId = asegurarMarcaSinMarca(connection);
                }

                String nombre = limpiarTexto(rs.getString("nombre"));
                if (nombre == null || nombre.isBlank()) {
                    nombre = "Producto migrado " + productoViejoId;
                }

                String descripcion = limpiarTexto(rs.getString("descripcion"));
                String calidad = limpiarTexto(rs.getString("calidad"));
                if (calidad == null || calidad.isBlank()) {
                    calidad = "ESTANDAR";
                }

                Double costoUnitario = leerDoubleNullable(rs, "costo_unitario");
                Double precioVenta = leerDoubleNullable(rs, "precio_venta");
                Integer stockActual = leerIntegerNullable(rs, "cantidad_stock");
                Integer stockMinimo = leerIntegerNullable(rs, "stock_minimo");
                Boolean activo = leerBooleanNullable(rs, "activo");

                costoUnitario = costoUnitario == null ? 0D : Math.max(costoUnitario, 0D);
                precioVenta = precioVenta == null ? 0D : Math.max(precioVenta, 0D);
                stockActual = stockActual == null ? 0 : Math.max(stockActual, 0);
                stockMinimo = stockMinimo == null ? 0 : Math.max(stockMinimo, 0);
                activo = activo == null ? Boolean.TRUE : activo;

                String codigoBase = normalizarCodigo("LEG-" + sku);
                String codigoVariante = normalizarCodigo(sku);

                Long productoBaseId = obtenerOCrearProductoBase(
                        connection,
                        codigoBase,
                        nombre,
                        categoriaId,
                        marcaId,
                        nombre,
                        descripcion,
                        activo);

                Long varianteId = obtenerOCrearVariante(
                        connection,
                        productoBaseId,
                        codigoVariante,
                        calidad,
                        precioVenta,
                        stockMinimo,
                        activo);

                if (stockActual > 0) {
                    crearLoteMigradoSiNoExiste(
                            connection,
                            varianteId,
                            codigoVariante,
                            stockActual,
                            costoUnitario,
                            precioVenta,
                            activo);
                }

                procesados++;
            }
        }

        return procesados;
    }

    private Long obtenerOCrearProductoBase(
            Connection connection,
            String codigoBase,
            String nombreBase,
            Long categoriaId,
            Long marcaId,
            String modelo,
            String descripcion,
            Boolean activo) throws SQLException {
        Long existente = buscarIdPorCodigo(connection, "productos_base", "codigo_base", codigoBase);
        if (existente != null) {
            return existente;
        }

        String ahora = LocalDateTime.now().toString();

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO productos_base (
                    codigo_base,
                    nombre_base,
                    categoria_id,
                    marca_id,
                    modelo,
                    descripcion,
                    activo,
                    creado_en,
                    actualizado_en
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {

            insert.setString(1, codigoBase);
            insert.setString(2, nombreBase);
            insert.setLong(3, categoriaId);
            insert.setLong(4, marcaId);
            insert.setString(5, modelo);
            insert.setString(6, descripcion);
            insert.setInt(7, Boolean.TRUE.equals(activo) ? 1 : 0);
            insert.setString(8, ahora);
            insert.setString(9, ahora);
            insert.executeUpdate();

            return leerGeneratedId(insert);
        }
    }

    private Long obtenerOCrearVariante(
            Connection connection,
            Long productoBaseId,
            String codigoVariante,
            String calidad,
            Double precioVenta,
            Integer stockMinimo,
            Boolean activo) throws SQLException {
        Long existente = buscarIdPorCodigo(connection, "productos_variantes", "codigo_variante", codigoVariante);
        if (existente != null) {
            return existente;
        }

        String ahora = LocalDateTime.now().toString();

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO productos_variantes (
                    producto_base_id,
                    codigo_variante,
                    calidad,
                    tipo_presentacion,
                    color,
                    precio_venta_sugerido,
                    stock_minimo,
                    activo,
                    creado_en,
                    actualizado_en
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {

            insert.setLong(1, productoBaseId);
            insert.setString(2, codigoVariante);
            insert.setString(3, calidad);
            insert.setString(4, "REPUESTO");
            insert.setString(5, "");
            insert.setDouble(6, precioVenta);
            insert.setInt(7, stockMinimo);
            insert.setInt(8, Boolean.TRUE.equals(activo) ? 1 : 0);
            insert.setString(9, ahora);
            insert.setString(10, ahora);
            insert.executeUpdate();

            return leerGeneratedId(insert);
        }
    }

    private void crearLoteMigradoSiNoExiste(
            Connection connection,
            Long varianteId,
            String codigoVariante,
            Integer stockActual,
            Double costoUnitario,
            Double precioVenta,
            Boolean activo) throws SQLException {
        String codigoLote = normalizarCodigo("MIG-" + codigoVariante);

        Long existente = buscarIdPorCodigo(connection, "lotes_inventario", "codigo_lote", codigoLote);
        if (existente != null) {
            return;
        }

        String ahora = LocalDateTime.now().toString();

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO lotes_inventario (
                    variante_id,
                    proveedor_id,
                    codigo_lote,
                    codigo_proveedor,
                    fecha_ingreso,
                    cantidad_inicial,
                    cantidad_disponible,
                    costo_unitario,
                    precio_venta_unitario,
                    subtotal_compra,
                    estado,
                    compra_id,
                    activo,
                    visible_en_ventas,
                    fecha_cierre,
                    motivo_cierre,
                    creado_en,
                    actualizado_en
                )
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, ?, ?, ?)
                """)) {

            insert.setLong(1, varianteId);
            insert.setString(2, codigoLote);
            insert.setString(3, "MIGRACION");
            insert.setString(4, LocalDate.now().toString());
            insert.setInt(5, stockActual);
            insert.setInt(6, stockActual);
            insert.setDouble(7, costoUnitario);
            insert.setDouble(8, precioVenta);
            insert.setDouble(9, costoUnitario * stockActual);
            insert.setString(10, stockActual > 0 ? "ACTIVO" : "AGOTADO");
            insert.setInt(11, Boolean.TRUE.equals(activo) ? 1 : 0);
            insert.setInt(12, stockActual > 0 && Boolean.TRUE.equals(activo) ? 1 : 0);
            insert.setString(13, "Lote generado automaticamente desde productos_inventario.");
            insert.setString(14, ahora);
            insert.setString(15, ahora);
            insert.executeUpdate();
        }
    }

    private Long asegurarMarcaSinMarca(Connection connection) throws SQLException {
        Long existente = buscarIdPorNombre(connection, "marcas_inventario", "Sin marca");
        if (existente != null) {
            return existente;
        }

        String ahora = LocalDateTime.now().toString();

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO marcas_inventario (
                    nombre,
                    descripcion,
                    activa,
                    creado_en,
                    actualizado_en
                )
                VALUES (?, ?, 1, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {

            insert.setString(1, "Sin marca");
            insert.setString(2, "Marca generada automaticamente para migracion.");
            insert.setString(3, ahora);
            insert.setString(4, ahora);
            insert.executeUpdate();

            return leerGeneratedId(insert);
        }
    }

    private Long buscarIdPorCodigo(Connection connection, String tabla, String columna, String codigo)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM " + tabla + " WHERE lower(" + columna + ") = lower(?) LIMIT 1")) {
            statement.setString(1, codigo);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    private Long buscarIdPorNombre(Connection connection, String tabla, String nombre) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM " + tabla + " WHERE lower(nombre) = lower(?) LIMIT 1")) {
            statement.setString(1, nombre);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    private Long leerGeneratedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }

        throw new SQLException("No se pudo obtener el ID generado.");
    }

    private boolean existeTabla(Connection connection, String nombreTabla) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = ?
                """)) {
            statement.setString(1, nombreTabla);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Long leerLongNullable(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    private Integer leerIntegerNullable(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private Double leerDoubleNullable(ResultSet rs, String columna) throws SQLException {
        double valor = rs.getDouble(columna);
        return rs.wasNull() ? null : valor;
    }

    private Boolean leerBooleanNullable(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor == 1;
    }

    private String limpiarTexto(String valor) {
        if (valor == null) {
            return null;
        }

        String limpio = valor.trim();
        return limpio.isBlank() ? null : limpio;
    }

    private String normalizarCodigo(String valor) {
        String base = limpiarTexto(valor);
        if (base == null) {
            base = "SIN-CODIGO";
        }

        return base
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}