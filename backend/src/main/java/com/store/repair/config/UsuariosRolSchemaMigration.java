package com.store.repair.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsuariosRolSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!existeTablaUsuarios(connection)) {
                return;
            }

            String createSql = obtenerCreateTableUsuarios(connection);
            if (createSql == null) {
                return;
            }

            if (tablaUsuariosYaPermiteRolTienda(createSql)) {
                return;
            }

            migrarCheckRolUsuarios(connection);
            log.info("Migracion usuarios: CHECK de rol actualizado para permitir ADMIN, TIENDA, TECNICO y CAJERO");
        }
    }

    private void migrarCheckRolUsuarios(Connection connection) throws SQLException {
        boolean autoCommitOriginal = connection.getAutoCommit();

        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys=OFF");
        }

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS usuarios_migracion");

            statement.execute("""
                    CREATE TABLE usuarios_migracion (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        actualizado_en varchar(255) NOT NULL,
                        creado_en varchar(255) NOT NULL,
                        activo boolean NOT NULL,
                        nombre varchar(255) NOT NULL,
                        password varchar(255) NOT NULL,
                        rol varchar(255) NOT NULL CHECK (rol in ('ADMIN','TIENDA','TECNICO','CAJERO')),
                        username varchar(255) NOT NULL UNIQUE
                    )
                    """);

            statement.execute("""
                    INSERT INTO usuarios_migracion (
                        id,
                        actualizado_en,
                        creado_en,
                        activo,
                        nombre,
                        password,
                        rol,
                        username
                    )
                    SELECT
                        id,
                        actualizado_en,
                        creado_en,
                        activo,
                        nombre,
                        password,
                        rol,
                        username
                    FROM usuarios
                    """);

            statement.execute("DROP TABLE usuarios");
            statement.execute("ALTER TABLE usuarios_migracion RENAME TO usuarios");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_usuarios_username ON usuarios(username)");

            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommitOriginal);
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys=ON");
            }
        }
    }

    private boolean tablaUsuariosYaPermiteRolTienda(String createSql) {
        String normalizedSql = createSql.toUpperCase(Locale.ROOT)
                .replace("\"", "'")
                .replaceAll("\\s+", " ");

        return normalizedSql.contains("CHECK (ROL IN ('ADMIN','TIENDA','TECNICO','CAJERO'))")
                || normalizedSql.contains("CHECK ( ROL IN ('ADMIN','TIENDA','TECNICO','CAJERO') )")
                || normalizedSql.contains("'TIENDA'");
    }

    private boolean existeTablaUsuarios(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name = 'usuarios'
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String obtenerCreateTableUsuarios(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sql FROM sqlite_master
                WHERE type = 'table' AND name = 'usuarios'
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("sql");
                }
                return null;
            }
        }
    }
}
