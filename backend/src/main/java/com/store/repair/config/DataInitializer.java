package com.store.repair.config;

import com.store.repair.domain.Rol;
import com.store.repair.domain.Usuario;
import com.store.repair.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev.seed-admin.enabled:false}")
    private boolean seedAdminEnabled;

    @Override
    public void run(String... args) {
        if (!seedAdminEnabled) {
            return;
        }

        if (usuarioRepository.findByUsername("admin").isEmpty()) {
            Usuario admin = Usuario.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nombre("Administrador Principal")
                    .rol(Rol.ADMIN)
                    .activo(true)
                    .build();

            usuarioRepository.save(admin);
        }
    }
}