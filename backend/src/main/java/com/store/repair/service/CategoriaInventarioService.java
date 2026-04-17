package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CategoriaInventario;
import com.store.repair.repository.CategoriaInventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaInventarioService {

    private final CategoriaInventarioRepository repository;

    public List<CategoriaInventario> findAll() {
        return repository.findAll();
    }

    public CategoriaInventario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }

    public CategoriaInventario save(CategoriaInventario categoria) {
        categoria.setNombre(SanitizadorTexto.limpiar(categoria.getNombre()));
        categoria.setDescripcion(SanitizadorTexto.limpiar(categoria.getDescripcion()));

        if (categoria.getId() != null) {
            findById(categoria.getId());
        }

        return repository.save(categoria);
    }

    public void delete(Long id) {
        findById(id);
        repository.deleteById(id);
    }
}