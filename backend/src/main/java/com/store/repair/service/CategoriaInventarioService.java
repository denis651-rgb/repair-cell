package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CategoriaInventario;
import com.store.repair.repository.CategoriaInventarioRepository;
import com.store.repair.repository.ProductoBaseRepository;
import com.store.repair.repository.ProductoInventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaInventarioService {

    private final CategoriaInventarioRepository repository;
    private final ProductoInventarioRepository productoRepository;
    private final ProductoBaseRepository productoBaseRepository;

    public List<CategoriaInventario> findAll() {
        return repository.findAllByOrderByNombreAsc();
    }

    public CategoriaInventario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }

    public CategoriaInventario save(CategoriaInventario categoria) {
        categoria.setNombre(SanitizadorTexto.limpiar(categoria.getNombre()));
        categoria.setDescripcion(SanitizadorTexto.limpiar(categoria.getDescripcion()));

        if (categoria.getNombre() == null || categoria.getNombre().isBlank()) {
            throw new BusinessException("El nombre de la categoria es obligatorio");
        }

        boolean existeDuplicado = categoria.getId() == null
                ? repository.existsByNombreIgnoreCase(categoria.getNombre())
                : repository.existsByNombreIgnoreCaseAndIdNot(categoria.getNombre(), categoria.getId());

        if (existeDuplicado) {
            throw new BusinessException("Ya existe una categoria registrada con ese nombre");
        }

        if (categoria.getId() != null) {
            CategoriaInventario existente = findById(categoria.getId());
            categoria.setCreadoEn(existente.getCreadoEn());
        }

        return repository.save(categoria);
    }

    public void delete(Long id) {
        findById(id);

        if (productoRepository.existsByCategoriaId(id) || productoBaseRepository.existsByCategoriaId(id)) {
            throw new BusinessException("No puedes eliminar la categoria porque tiene productos o productos base asociados");
        }

        repository.deleteById(id);
    }
}
