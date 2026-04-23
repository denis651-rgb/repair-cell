package com.store.repair.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class VentasSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        asegurarColumnaCantidadDevuelta();
        asegurarColumnasVentaPorVariante();
        normalizarTablaVentasDetalle();
        asegurarTablaVentaDetalleLote();
        normalizarTablaVentas();
    }

    private void asegurarColumnaCantidadDevuelta() {
        if (!existeColumna("ventas_detalle", "cantidad_devuelta")) {
            jdbcTemplate.execute("ALTER TABLE ventas_detalle ADD COLUMN cantidad_devuelta INTEGER NOT NULL DEFAULT 0");
            log.info("Migracion ventas: columna cantidad_devuelta agregada a ventas_detalle");
            return;
        }

        jdbcTemplate.update("UPDATE ventas_detalle SET cantidad_devuelta = 0 WHERE cantidad_devuelta IS NULL");
    }

    private void asegurarColumnasVentaPorVariante() {
        asegurarColumna("ventas_detalle", "variante_id", "INTEGER");
        asegurarColumna("ventas_detalle", "producto_base_codigo", "TEXT");
        asegurarColumna("ventas_detalle", "tipo_presentacion", "TEXT");
        asegurarColumna("ventas_detalle", "color", "TEXT");
        asegurarColumna("ventas_detalle", "precio_lista_unitario", "REAL NOT NULL DEFAULT 0");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_detalle_variante ON ventas_detalle(variante_id)");
    }

    private void asegurarTablaVentaDetalleLote() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS venta_detalle_lote (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    venta_detalle_id INTEGER NOT NULL,
                    lote_id INTEGER NOT NULL,
                    cantidad INTEGER NOT NULL,
                    cantidad_devuelta INTEGER NOT NULL DEFAULT 0,
                    costo_unitario_aplicado REAL NOT NULL,
                    costo_total REAL NOT NULL,
                    ganancia_bruta REAL NOT NULL,
                    creado_en TEXT NOT NULL,
                    actualizado_en TEXT NOT NULL,
                    FOREIGN KEY (venta_detalle_id) REFERENCES ventas_detalle(id),
                    FOREIGN KEY (lote_id) REFERENCES lotes_inventario(id)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_venta_detalle_lote_detalle ON venta_detalle_lote(venta_detalle_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_venta_detalle_lote_lote ON venta_detalle_lote(lote_id)");
        if (!existeColumna("venta_detalle_lote", "cantidad_devuelta")) {
            jdbcTemplate.execute("ALTER TABLE venta_detalle_lote ADD COLUMN cantidad_devuelta INTEGER NOT NULL DEFAULT 0");
        }
        jdbcTemplate.update("UPDATE venta_detalle_lote SET cantidad_devuelta = 0 WHERE cantidad_devuelta IS NULL");
    }

    private void normalizarTablaVentasDetalle() {
        if (!requiereRecrearTablaVentasDetalle()) {
            return;
        }

        log.info("Migracion ventas: recreando tabla ventas_detalle para soportar ventas por variante y producto nullable");
        jdbcTemplate.execute("PRAGMA foreign_keys = OFF");
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ventas_detalle_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        venta_id INTEGER NOT NULL,
                        producto_id INTEGER,
                        variante_id INTEGER,
                        categoria_nombre TEXT NOT NULL,
                        sku TEXT NOT NULL,
                        nombre_producto TEXT NOT NULL,
                        producto_base_codigo TEXT,
                        marca TEXT NOT NULL,
                        calidad TEXT,
                        tipo_presentacion TEXT,
                        color TEXT,
                        cantidad INTEGER NOT NULL,
                        cantidad_devuelta INTEGER NOT NULL DEFAULT 0,
                        precio_lista_unitario REAL NOT NULL DEFAULT 0,
                        precio_venta_unitario REAL NOT NULL,
                        subtotal REAL NOT NULL,
                        creado_en TEXT NOT NULL,
                        actualizado_en TEXT NOT NULL,
                        FOREIGN KEY (venta_id) REFERENCES ventas(id),
                        FOREIGN KEY (producto_id) REFERENCES productos_inventario(id),
                        FOREIGN KEY (variante_id) REFERENCES productos_variantes(id)
                    )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO ventas_detalle_new (
                        id, venta_id, producto_id, variante_id, categoria_nombre, sku, nombre_producto,
                        producto_base_codigo, marca, calidad, tipo_presentacion, color,
                        cantidad, cantidad_devuelta, precio_lista_unitario, precio_venta_unitario,
                        subtotal, creado_en, actualizado_en
                    )
                    SELECT
                        id,
                        venta_id,
                        producto_id,
                        variante_id,
                        categoria_nombre,
                        sku,
                        nombre_producto,
                        producto_base_codigo,
                        marca,
                        calidad,
                        tipo_presentacion,
                        color,
                        cantidad,
                        COALESCE(cantidad_devuelta, 0),
                        COALESCE(precio_lista_unitario, precio_venta_unitario, 0),
                        precio_venta_unitario,
                        subtotal,
                        creado_en,
                        actualizado_en
                    FROM ventas_detalle
                    """);

            jdbcTemplate.execute("DROP TABLE ventas_detalle");
            jdbcTemplate.execute("ALTER TABLE ventas_detalle_new RENAME TO ventas_detalle");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_detalle_venta ON ventas_detalle(venta_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_detalle_producto ON ventas_detalle(producto_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_detalle_variante ON ventas_detalle(variante_id)");
        } finally {
            jdbcTemplate.execute("PRAGMA foreign_keys = ON");
        }
    }

    private void normalizarTablaVentas() {
        if (!requiereRecrearTablaVentas()) {
            return;
        }

        log.info("Migracion ventas: recreando tabla ventas para ampliar estados permitidos");
        jdbcTemplate.execute("PRAGMA foreign_keys = OFF");
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ventas_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        cliente_id INTEGER NOT NULL,
                        fecha_venta TEXT NOT NULL,
                        numero_comprobante TEXT,
                        tipo_pago TEXT NOT NULL,
                        estado TEXT NOT NULL,
                        total REAL NOT NULL DEFAULT 0,
                        observaciones TEXT,
                        fecha_devolucion TEXT,
                        motivo_devolucion TEXT,
                        creado_en TEXT NOT NULL,
                        actualizado_en TEXT NOT NULL,
                        FOREIGN KEY (cliente_id) REFERENCES clientes(id)
                    )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO ventas_new (
                        id, cliente_id, fecha_venta, numero_comprobante, tipo_pago, estado,
                        total, observaciones, fecha_devolucion, motivo_devolucion, creado_en, actualizado_en
                    )
                    SELECT
                        id, cliente_id, fecha_venta, numero_comprobante, tipo_pago, estado,
                        total, observaciones, fecha_devolucion, motivo_devolucion, creado_en, actualizado_en
                    FROM ventas
                    """);

            jdbcTemplate.execute("DROP TABLE ventas");
            jdbcTemplate.execute("ALTER TABLE ventas_new RENAME TO ventas");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_cliente ON ventas(cliente_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha_venta)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ventas_estado ON ventas(estado)");
        } finally {
            jdbcTemplate.execute("PRAGMA foreign_keys = ON");
        }
    }

    private boolean requiereRecrearTablaVentas() {
        return jdbcTemplate.query(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'ventas'",
                rs -> {
                    if (!rs.next()) {
                        return false;
                    }

                    String sql = rs.getString("sql");
                    if (sql == null) {
                        return false;
                    }

                    String normalizado = sql.toUpperCase(Locale.ROOT);
                    return normalizado.contains("CHECK")
                            && normalizado.contains("REGISTRADA")
                            && normalizado.contains("DEVUELTA")
                            && !normalizado.contains("PARCIALMENTE_DEVUELTA");
                });
    }

    private boolean existeColumna(String tabla, String columna) {
        return jdbcTemplate.query(
                "PRAGMA table_info(" + tabla + ")",
                rs -> {
                    while (rs.next()) {
                        if (columna.equalsIgnoreCase(rs.getString("name"))) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    private boolean requiereRecrearTablaVentasDetalle() {
        return jdbcTemplate.query(
                "PRAGMA table_info(ventas_detalle)",
                rs -> {
                    boolean existeProductoId = false;
                    boolean productoIdNotNull = false;
                    boolean existeVarianteId = false;
                    boolean existePrecioLista = false;
                    boolean existeProductoBaseCodigo = false;
                    boolean existeTipoPresentacion = false;
                    boolean existeColor = false;

                    while (rs.next()) {
                        String nombre = rs.getString("name");
                        if ("producto_id".equalsIgnoreCase(nombre)) {
                            existeProductoId = true;
                            productoIdNotNull = rs.getInt("notnull") == 1;
                        } else if ("variante_id".equalsIgnoreCase(nombre)) {
                            existeVarianteId = true;
                        } else if ("precio_lista_unitario".equalsIgnoreCase(nombre)) {
                            existePrecioLista = true;
                        } else if ("producto_base_codigo".equalsIgnoreCase(nombre)) {
                            existeProductoBaseCodigo = true;
                        } else if ("tipo_presentacion".equalsIgnoreCase(nombre)) {
                            existeTipoPresentacion = true;
                        } else if ("color".equalsIgnoreCase(nombre)) {
                            existeColor = true;
                        }
                    }

                    return !existeProductoId
                            || productoIdNotNull
                            || !existeVarianteId
                            || !existePrecioLista
                            || !existeProductoBaseCodigo
                            || !existeTipoPresentacion
                            || !existeColor;
                });
    }

    private void asegurarColumna(String tabla, String columna, String definicion) {
        if (existeColumna(tabla, columna)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
    }
}
