package com.store.repair.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrdenesSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                asegurarVarianteIdEnPartes(connection);
                migrarCostoFinalDesdePartes(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void migrarCostoFinalDesdePartes(Connection connection) throws SQLException {
        if (!existeTabla(connection, "ordenes_reparacion") || !existeTabla(connection, "partes_orden_reparacion")) {
            return;
        }

        String sql = """
                UPDATE ordenes_reparacion
                SET costo_final = (
                    SELECT COALESCE(SUM(COALESCE(precio_unitario, 0) * COALESCE(cantidad, 0)), 0)
                    FROM partes_orden_reparacion parte
                    WHERE parte.orden_reparacion_id = ordenes_reparacion.id
                )
                WHERE COALESCE(costo_final, 0) <= 0
                  AND EXISTS (
                    SELECT 1
                    FROM partes_orden_reparacion parte
                    WHERE parte.orden_reparacion_id = ordenes_reparacion.id
                      AND COALESCE(parte.precio_unitario, 0) > 0
                      AND COALESCE(parte.cantidad, 0) > 0
                )
                """;

        try (Statement statement = connection.createStatement()) {
            int actualizadas = statement.executeUpdate(sql);
            if (actualizadas > 0) {
                log.info("Migracion ordenes: costo_final recalculado desde partes en {} orden(es)", actualizadas);
            }
        }
    }

    private void asegurarVarianteIdEnPartes(Connection connection) throws SQLException {
        if (!existeTabla(connection, "partes_orden_reparacion")) {
            return;
        }

        if (!existeColumna(connection, "partes_orden_reparacion", "variante_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE partes_orden_reparacion ADD COLUMN variante_id INTEGER");
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_partes_orden_reparacion_variante ON partes_orden_reparacion(variante_id)");
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

    private boolean existeColumna(Connection connection, String tabla, String columna) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tabla + ")")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (columna.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
