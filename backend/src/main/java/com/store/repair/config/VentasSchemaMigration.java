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
}
