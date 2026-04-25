package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CategoriaInventario;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.domain.ProductoBase;
import com.store.repair.domain.ProductoBaseCompatibilidad;
import com.store.repair.dto.ProductoBaseCompatibilidadRequest;
import com.store.repair.dto.ProductoBaseRequest;
import com.store.repair.repository.ProductoBaseRepository;
import com.store.repair.repository.ProductoVarianteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoBaseService {

    private static final int MAX_INTENTOS_GENERACION = 8;

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

    public String sugerirCodigo(Long categoriaId, Long marcaId) {
        CategoriaInventario categoria = categoriaService.findById(categoriaId);
        MarcaInventario marca = marcaService.findById(marcaId);
        return generarSiguienteCodigoBase(categoria, marca);
    }

    @Transactional
    public ProductoBase save(Long id, ProductoBaseRequest request) {
        CategoriaInventario categoria = categoriaService.findById(request.getCategoriaId());
        MarcaInventario marca = marcaService.findById(request.getMarcaId());

        if (id == null) {
            return crearProductoBase(request, categoria, marca);
        }

        return actualizarProductoBase(id, request, categoria, marca);
    }

    public void delete(Long id) {
        ProductoBase productoBase = findById(id);
        throw new BusinessException(
                "No se permite borrar fisicamente el producto base " + productoBase.getNombreBase()
                        + ". Usa activo=false para inactivarlo y conservar la trazabilidad.");
    }

    private ProductoBase crearProductoBase(
            ProductoBaseRequest request,
            CategoriaInventario categoria,
            MarcaInventario marca) {
        for (int intento = 0; intento < MAX_INTENTOS_GENERACION; intento++) {
            ProductoBase destino = new ProductoBase();
            aplicarCamposBase(destino, request, categoria, marca);
            destino.setCodigoBase(generarSiguienteCodigoBase(categoria, marca));

            try {
                return repository.saveAndFlush(destino);
            } catch (DataIntegrityViolationException exception) {
                if (esConflictoDeCodigo(exception)) {
                    continue;
                }
                throw exception;
            }
        }

        throw new BusinessException("No se pudo generar un codigo unico para el producto base. Intenta nuevamente.");
    }

    private ProductoBase actualizarProductoBase(
            Long id,
            ProductoBaseRequest request,
            CategoriaInventario categoria,
            MarcaInventario marca) {
        ProductoBase destino = findById(id);
        aplicarCamposBase(destino, request, categoria, marca);

        String codigoExistente = SanitizadorTexto.limpiar(destino.getCodigoBase());
        if (codigoExistente == null) {
            destino.setCodigoBase(generarSiguienteCodigoBase(categoria, marca));
        } else {
            destino.setCodigoBase(normalizarCodigo(codigoExistente, "El codigo base es obligatorio"));
        }

        boolean codigoDuplicado = repository.existsByCodigoBaseIgnoreCaseAndIdNot(destino.getCodigoBase(), id);
        if (codigoDuplicado) {
            throw new BusinessException("Ya existe un producto base con el codigo " + destino.getCodigoBase() + ".");
        }

        try {
            return repository.saveAndFlush(destino);
        } catch (DataIntegrityViolationException exception) {
            if (esConflictoDeCodigo(exception)) {
                throw new BusinessException("No se pudo actualizar el producto base porque el codigo ya existe.");
            }
            throw exception;
        }
    }

    private void aplicarCamposBase(
            ProductoBase destino,
            ProductoBaseRequest request,
            CategoriaInventario categoria,
            MarcaInventario marca) {
        destino.setNombreBase(validarTextoObligatorio(request.getNombreBase(), "El nombre base es obligatorio"));
        destino.setCategoria(categoria);
        destino.setMarca(marca);
        destino.setModelo(SanitizadorTexto.limpiar(request.getModelo()));
        destino.setDescripcion(SanitizadorTexto.limpiar(request.getDescripcion()));
        destino.setActivo(request.getActivo() == null ? Boolean.TRUE : request.getActivo());
        destino.replaceCompatibilidades(normalizarCompatibilidades(request.getCompatibilidades(), destino));
    }

    private String generarSiguienteCodigoBase(CategoriaInventario categoria, MarcaInventario marca) {
        String prefijo = construirPrefijoBase(categoria, marca);
        String ultimoCodigo = repository.findTopByCodigoBaseStartingWithOrderByCodigoBaseDesc(prefijo + "-")
                .map(ProductoBase::getCodigoBase)
                .orElse(null);
        int siguienteCorrelativo = extraerSiguienteCorrelativo(ultimoCodigo, 4);
        return prefijo + "-" + String.format("%04d", siguienteCorrelativo);
    }

    private String construirPrefijoBase(CategoriaInventario categoria, MarcaInventario marca) {
        String categoriaCodigo = abreviarSegmentoCodigo(categoria == null ? null : categoria.getNombre());
        String marcaCodigo = abreviarSegmentoCodigo(marca == null ? null : marca.getNombre());
        if (categoriaCodigo == null || marcaCodigo == null) {
            throw new BusinessException("No se pudo generar el codigo del producto base por falta de categoria o marca.");
        }
        return categoriaCodigo + "-" + marcaCodigo;
    }

    private int extraerSiguienteCorrelativo(String ultimoCodigo, int longitudEsperada) {
        if (ultimoCodigo == null || ultimoCodigo.isBlank()) {
            return 1;
        }

        int indice = ultimoCodigo.lastIndexOf('-');
        if (indice < 0 || indice == ultimoCodigo.length() - 1) {
            return 1;
        }

        String sufijo = ultimoCodigo.substring(indice + 1).trim();
        if (sufijo.length() != longitudEsperada) {
            return 1;
        }

        try {
            return Integer.parseInt(sufijo) + 1;
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private String abreviarSegmentoCodigo(String valor) {
        String limpio = SanitizadorTexto.limpiar(valor);
        if (limpio == null) {
            return null;
        }

        String normalizado = java.text.Normalizer.normalize(limpio, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace(" ", "");

        if (normalizado.isBlank()) {
            return null;
        }

        return normalizado.length() <= 3 ? normalizado : normalizado.substring(0, 3);
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

    private List<ProductoBaseCompatibilidad> normalizarCompatibilidades(
            List<ProductoBaseCompatibilidadRequest> compatibilidades,
            ProductoBase productoBase) {
        if (compatibilidades == null || compatibilidades.isEmpty()) {
            return List.of();
        }

        Map<String, ProductoBaseCompatibilidad> unicas = new LinkedHashMap<>();

        for (ProductoBaseCompatibilidadRequest item : compatibilidades) {
            if (item == null) {
                continue;
            }

            String marcaCompatible = SanitizadorTexto.limpiar(item.getMarcaCompatible());
            String modeloCompatible = SanitizadorTexto.limpiar(item.getModeloCompatible());
            String codigoReferencia = SanitizadorTexto.limpiar(item.getCodigoReferencia());
            String nota = SanitizadorTexto.limpiar(item.getNota());
            boolean filaVacia = marcaCompatible == null
                    && modeloCompatible == null
                    && codigoReferencia == null
                    && nota == null
                    && item.getActiva() == null;

            if (filaVacia) {
                continue;
            }

            if (modeloCompatible == null) {
                throw new BusinessException("El modelo compatible es obligatorio cuando agregas una compatibilidad.");
            }

            String marcaNormalizada = marcaCompatible != null
                    ? marcaCompatible
                    : productoBase.getMarca().getNombre();
            String llave = (marcaNormalizada + "|" + modeloCompatible).toLowerCase(Locale.ROOT).trim();
            if (unicas.containsKey(llave)) {
                continue;
            }

            unicas.put(llave, ProductoBaseCompatibilidad.builder()
                    .marcaCompatible(marcaNormalizada)
                    .modeloCompatible(modeloCompatible)
                    .codigoReferencia(codigoReferencia)
                    .nota(nota)
                    .activa(item.getActiva() == null ? Boolean.TRUE : item.getActiva())
                    .build());
        }

        return new ArrayList<>(unicas.values());
    }

    private boolean esConflictoDeCodigo(DataIntegrityViolationException exception) {
        String mensaje = exception.getMostSpecificCause() == null
                ? exception.getMessage()
                : exception.getMostSpecificCause().getMessage();
        if (mensaje == null) {
            return false;
        }
        String mensajeNormalizado = mensaje.toLowerCase(Locale.ROOT);
        return mensajeNormalizado.contains("codigo_base")
                || mensajeNormalizado.contains("productos_base.codigo_base")
                || mensajeNormalizado.contains("unique");
    }
}
