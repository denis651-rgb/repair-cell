package com.store.repair.config;

import com.store.repair.domain.Rol;
import com.store.repair.domain.Usuario;
import com.store.repair.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "Admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Yiyo1416";
    private static final String DEFAULT_ADMIN_NAME = "Administrador Principal";
    private static final String DEFAULT_STORE_USERNAME = "Tienda";
    private static final String DEFAULT_STORE_PASSWORD = "Tienda2026";
    private static final String DEFAULT_STORE_NAME = "Usuario Tienda";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-users.enabled:true}")
    private boolean bootstrapUsersEnabled;

    @Value("${app.bootstrap-admin.username:" + DEFAULT_ADMIN_USERNAME + "}")
    private String bootstrapUsername;

    @Value("${app.bootstrap-admin.password:" + DEFAULT_ADMIN_PASSWORD + "}")
    private String bootstrapPassword;

    @Value("${app.bootstrap-admin.nombre:" + DEFAULT_ADMIN_NAME + "}")
    private String bootstrapNombre;

    @Value("${app.bootstrap-store.username:" + DEFAULT_STORE_USERNAME + "}")
    private String bootstrapStoreUsername;

    @Value("${app.bootstrap-store.password:" + DEFAULT_STORE_PASSWORD + "}")
    private String bootstrapStorePassword;

    @Value("${app.bootstrap-store.nombre:" + DEFAULT_STORE_NAME + "}")
    private String bootstrapStoreNombre;

    @Override
    public void run(String... args) {
        if (!bootstrapUsersEnabled) {
            return;
        }

        ensureUser(
                bootstrapUsername,
                bootstrapPassword,
                bootstrapNombre,
                Rol.ADMIN
        );
        ensureUser(
                bootstrapStoreUsername,
                bootstrapStorePassword,
                bootstrapStoreNombre,
                Rol.TIENDA
        );
    }

    private void ensureUser(String usernameValue, String passwordValue, String nombreValue, Rol rol) {
        String username = usernameValue == null || usernameValue.isBlank()
                ? (rol == Rol.ADMIN ? DEFAULT_ADMIN_USERNAME : DEFAULT_STORE_USERNAME)
                : usernameValue.trim();

        if (usuarioRepository.findByUsername(username).isEmpty()) {
            Usuario usuario = Usuario.builder()
                    .username(username)
                    .password(passwordEncoder.encode(passwordValue))
                    .nombre(nombreValue)
                    .rol(rol)
                    .activo(true)
                    .build();
            usuarioRepository.save(usuario);
        }
    }
}
