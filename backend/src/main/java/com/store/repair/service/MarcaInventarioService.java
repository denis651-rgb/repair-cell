package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.repository.MarcaInventarioRepository;
import com.store.repair.repository.ProductoInventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarcaInventarioService {

    private final MarcaInventarioRepository repository;
    private final ProductoInventarioRepository productoRepository;

    public List<MarcaInventario> findAll() {
        return repository.findAllByOrderByNombreAsc();
    }

    public MarcaInventario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada: " + id));
    }

    public MarcaInventario save(MarcaInventario marca) {
        marca.setNombre(SanitizadorTexto.limpiar(marca.getNombre()));
        marca.setDescripcion(SanitizadorTexto.limpiar(marca.getDescripcion()));

        if (marca.getNombre() == null || marca.getNombre().isBlank()) {
            throw new BusinessException("El nombre de la marca es obligatorio");
        }

        boolean existeDuplicado = marca.getId() == null
                ? repository.existsByNombreIgnoreCase(marca.getNombre())
                : repository.existsByNombreIgnoreCaseAndIdNot(marca.getNombre(), marca.getId());

        if (existeDuplicado) {
            throw new BusinessException("Ya existe una marca registrada con ese nombre");
        }

        if (marca.getActiva() == null) {
            marca.setActiva(Boolean.TRUE);
        }

        if (marca.getId() != null) {
            MarcaInventario existente = findById(marca.getId());
            marca.setCreadoEn(existente.getCreadoEn());
        }

        return repository.save(marca);
    }

    public void delete(Long id) {
        findById(id);

        if (productoRepository.existsByMarcaId(id)) {
            throw new BusinessException("No puedes eliminar la marca porque tiene productos asociados");
        }

        repository.deleteById(id);
    }
}
