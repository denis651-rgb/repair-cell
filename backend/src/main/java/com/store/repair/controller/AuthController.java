package com.store.repair.controller;

import com.store.repair.config.RolePermissions;
import com.store.repair.config.JwtService;
import com.store.repair.domain.Usuario;
import com.store.repair.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            throw new IllegalArgumentException("Usuario o contraseña incorrectos");
        } catch (DisabledException ex) {
            throw new IllegalArgumentException("El usuario está deshabilitado");
        }

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername().trim());
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        String token = jwtService.generateToken(user);
        List<String> permisos = RolePermissions.permissionsFor(usuario.getRol()).stream()
                .map(Enum::name)
                .sorted()
                .toList();

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(usuario.getUsername())
                .nombre(usuario.getNombre())
                .rol(usuario.getRol().name())
                .permisos(permisos)
                .build());
    }

    @Data
    public static class AuthRequest {
        @NotBlank(message = "El username es obligatorio")
        private String username;

        @NotBlank(message = "La contraseña es obligatoria")
        private String password;
    }

    @Data
    @Builder
    public static class AuthResponse {
        private String token;
        private String username;
        private String nombre;
        private String rol;
        private List<String> permisos;
    }
}
