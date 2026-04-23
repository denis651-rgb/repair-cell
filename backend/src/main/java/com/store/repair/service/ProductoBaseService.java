package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.ProductoBase;
import com.store.repair.dto.ProductoBaseRequest;
import com.store.repair.repository.ProductoBaseRepository;
import com.store.repair.repository.ProductoVarianteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductoBaseService {

    private final ProductoBaseRepository repository;
    private final ProductoVarianteRepository varianteRepository;
    private final CategoriaInventarioService categoriaService;
    private final MarcaInventarioService marcaService;

    public List<ProductoBase> search(String busqueda, Long categoriaId, Long marcaId, String modelo, boolean soloActivos) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                categoriaId,
                marcaId,
                modelo == null ? "" : modelo.trim(),
                soloActivos);
    }

    public ProductoBase findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto base no encontrado: " + id));
    }

    public ProductoBase save(Long id, ProductoBaseRequest request) {
        ProductoBase destino = id == null ? new ProductoBase() : findById(id);
        String codigoNormalizado = normalizarCodigo(request.getCodigoBase(), "El codigo base es obligatorio");

        boolean codigoDuplicado = id == null
                ? repository.existsByCodigoBaseIgnoreCase(codigoNormalizado)
                : repository.existsByCodigoBaseIgnoreCaseAndIdNot(codigoNormalizado, id);
        if (codigoDuplicado) {
            throw new BusinessException("Ya existe un producto base con el codigo " + codigoNormalizado + ".");
        }

        destino.setCodigoBase(codigoNormalizado);
        destino.setNombreBase(validarTextoObligatorio(request.getNombreBase(), "El nombre base es obligatorio"));
        destino.setCategoria(categoriaService.findById(request.getCategoriaId()));
        destino.setMarca(marcaService.findById(request.getMarcaId()));
        destino.setModelo(SanitizadorTexto.limpiar(request.getModelo()));
        destino.setDescripcion(SanitizadorTexto.limpiar(request.getDescripcion()));
        destino.setActivo(request.getActivo() == null ? Boolean.TRUE : request.getActivo());

        return repository.save(destino);
    }

    public void delete(Long id) {
        ProductoBase productoBase = findById(id);
        throw new BusinessException(
                "No se permite borrar fisicamente el producto base " + productoBase.getNombreBase()
                        + ". Usa activo=false para inactivarlo y conservar la trazabilidad.");
    }

    private String validarTextoObligatorio(String valor, String mensaje) {
        String limpio = SanitizadorTexto.limpiar(valor);
        if (limpio == null) {
            throw new BusinessException(mensaje);
        }
        return limpio;
    }

    private String normalizarCodigo(String valor, String mensaje) {
        String limpio = validarTextoObligatorio(valor, mensaje);
        return limpio.replace(' ', '-').toUpperCase(Locale.ROOT);
    }
}
