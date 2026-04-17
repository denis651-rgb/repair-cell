package com.store.repair.service;

import com.store.repair.domain.OrdenHistorial;
import com.store.repair.repository.OrdenHistorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final OrdenHistorialRepository repository;

    @Transactional
    public void logChange(Long ordenId, String estadoAnterior, String estadoNuevo, String notas) {
        String usuario = SecurityContextHolder.getContext().getAuthentication() != null 
                ? SecurityContextHolder.getContext().getAuthentication().getName() 
                : "Sistema";

        OrdenHistorial historial = OrdenHistorial.builder()
                .ordenId(ordenId)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(estadoNuevo)
                .usuario(usuario)
                .notas(notas)
                .fecha(LocalDateTime.now())
                .build();
        repository.save(historial);
    }

    public List<OrdenHistorial> getHistory(Long ordenId) {
        return repository.findByOrdenIdOrderByFechaDesc(ordenId);
    }
}
