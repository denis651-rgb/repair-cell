package com.store.repair.config;

import com.store.repair.domain.Rol;
import com.store.repair.domain.Usuario;
import com.store.repair.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.enabled:false}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap-admin.username:admin}")
    private String bootstrapUsername;

    @Value("${app.bootstrap-admin.password:admin123}")
    private String bootstrapPassword;

    @Value("${app.bootstrap-admin.nombre:Administrador Principal}")
    private String bootstrapNombre;

    @Override
    public void run(String... args) {
        if (!bootstrapAdminEnabled) {
            return;
        }

        String username = bootstrapUsername == null || bootstrapUsername.isBlank() ? "admin" : bootstrapUsername.trim();

        if (usuarioRepository.findByUsername(username).isEmpty()) {
            Usuario admin = Usuario.builder()
                    .username(username)
                    .password(passwordEncoder.encode(bootstrapPassword))
                    .nombre(bootstrapNombre)
                    .rol(Rol.ADMIN)
                    .activo(true)
                    .build();

            usuarioRepository.save(admin);
        }
    }
}
