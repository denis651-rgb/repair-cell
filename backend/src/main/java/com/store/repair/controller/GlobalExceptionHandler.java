package com.store.repair.controller;

import com.store.repair.service.BusinessException;
import com.store.repair.service.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Recurso no encontrado en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Regla de negocio en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("Argumento invalido en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex,
            HttpServletRequest request) {
        log.warn("Estado invalido en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(" | "));

        log.warn("Error de validacion en {} {}: {}", request.getMethod(), request.getRequestURI(), mensaje, ex);
        return build(HttpStatus.BAD_REQUEST, mensaje, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex,
            HttpServletRequest request) {
        log.warn("Constraint violation en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        log.warn("Tipo invalido en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, "Parametro invalido: " + ex.getName(), request.getRequestURI());
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyResult(EmptyResultDataAccessException ex,
            HttpServletRequest request) {
        log.warn("Intento de eliminar recurso inexistente en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.NOT_FOUND, "El recurso que intentas eliminar no existe", request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.error("Error de integridad en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, resolverMensajePersistencia(ex), request.getRequestURI());
    }

    @ExceptionHandler({ TransactionSystemException.class, JpaSystemException.class })
    public ResponseEntity<Map<String, Object>> handleJpaSystem(Exception ex, HttpServletRequest request) {
        log.error("Error JPA en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_REQUEST, resolverMensajePersistencia(ex), request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Acceso denegado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.FORBIDDEN, "Acceso denegado", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error interno no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("estado", status.value());
        body.put("error", message);
        body.put("ruta", path);
        return ResponseEntity.status(status).body(body);
    }

    private String resolverMensajePersistencia(Throwable throwable) {
        String mensaje = extraerMensajeMasUtil(throwable).toLowerCase();

        if (mensaje.contains("unique") || mensaje.contains("duplicate")) {
            return "Ya existe un registro con ese mismo valor. Revisa nombre, codigo o SKU antes de guardar.";
        }

        if (mensaje.contains("not null")) {
            return "Faltan datos obligatorios para guardar el registro. Revisa los campos requeridos e intenta nuevamente.";
        }

        if (mensaje.contains("foreign key")) {
            return "No se pudo completar la operacion porque el registro esta relacionado con otros datos del sistema.";
        }

        if (mensaje.contains("constraint")) {
            return "No se pudo guardar el registro por una restriccion de datos. Revisa la informacion ingresada.";
        }

        return "No se pudo guardar el registro por un problema de persistencia de datos.";
    }

    private String extraerMensajeMasUtil(Throwable throwable) {
        Throwable actual = throwable;
        String ultimoMensaje = throwable.getMessage() == null ? "" : throwable.getMessage();

        while (actual != null) {
            if (actual.getMessage() != null && !actual.getMessage().isBlank()) {
                ultimoMensaje = actual.getMessage();
            }
            actual = actual.getCause();
        }

        return ultimoMensaje == null ? "" : ultimoMensaje;
    }
}
