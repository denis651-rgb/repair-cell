package com.store.repair.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
public class ComprasCatalogoMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                asegurarColumnasCompraDetalle(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void asegurarColumnasCompraDetalle(Connection connection) throws SQLException {
        if (!existeTabla(connection, "compras_detalle")) {
            return;
        }

        asegurarColumna(connection, "compras_detalle", "variante_id", "INTEGER");
        asegurarColumna(connection, "compras_detalle", "producto_base_codigo", "TEXT");
        asegurarColumna(connection, "compras_detalle", "tipo_presentacion", "TEXT");
        asegurarColumna(connection, "compras_detalle", "color", "TEXT");
        asegurarColumna(connection, "compras_detalle", "codigo_proveedor", "TEXT");
        asegurarColumna(connection, "compras_detalle", "codigo_lote", "TEXT");
    }

    private boolean existeTabla(Connection connection, String tabla) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '" + tabla + "'")) {
            return resultSet.next();
        }
    }

    private boolean existeColumna(Connection connection, String tabla, String columna) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (resultSet.next()) {
                if (columna.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void asegurarColumna(Connection connection, String tabla, String columna, String definicion) throws SQLException {
        if (existeColumna(connection, tabla, columna)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
        }
    }
}
