package com.store.repair.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
public class CatalogoSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                crearProductosBase(connection);
                crearProductosVariantes(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void crearProductosBase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS productos_base (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        codigo_base TEXT NOT NULL UNIQUE,
                        nombre_base TEXT NOT NULL,
                        categoria_id INTEGER NOT NULL,
                        marca_id INTEGER NOT NULL,
                        modelo TEXT,
                        descripcion TEXT,
                        activo INTEGER NOT NULL DEFAULT 1,
                        creado_en TEXT NOT NULL,
                        actualizado_en TEXT NOT NULL,
                        FOREIGN KEY (categoria_id) REFERENCES categorias_inventario(id),
                        FOREIGN KEY (marca_id) REFERENCES marcas_inventario(id)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_productos_base_categoria ON productos_base(categoria_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_productos_base_marca ON productos_base(marca_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_productos_base_modelo ON productos_base(modelo)");
        }
    }

    private void crearProductosVariantes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS productos_variantes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        producto_base_id INTEGER NOT NULL,
                        codigo_variante TEXT NOT NULL UNIQUE,
                        calidad TEXT NOT NULL,
                        tipo_presentacion TEXT,
                        color TEXT,
                        precio_venta_sugerido REAL NOT NULL DEFAULT 0,
                        activo INTEGER NOT NULL DEFAULT 1,
                        creado_en TEXT NOT NULL,
                        actualizado_en TEXT NOT NULL,
                        FOREIGN KEY (producto_base_id) REFERENCES productos_base(id)
                    )
                    """);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_productos_variantes_producto_base ON productos_variantes(producto_base_id)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_productos_variantes_calidad ON productos_variantes(calidad)");
        }
    }
}
