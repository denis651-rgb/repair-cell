package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.Proveedor;
import com.store.repair.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository repository;

    public List<Proveedor> findAll() {
        return repository.findAllByOrderByNombreComercialAsc();
    }

    public Page<Proveedor> findPage(String busqueda, int pagina, int tamano) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public Proveedor findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado: " + id));
    }

    public Proveedor save(Proveedor proveedor) {
        proveedor.setNombreComercial(SanitizadorTexto.limpiar(proveedor.getNombreComercial()));
        proveedor.setRazonSocial(SanitizadorTexto.limpiar(proveedor.getRazonSocial()));
        proveedor.setTelefono(SanitizadorTexto.limpiar(proveedor.getTelefono()));
        proveedor.setCiudad(SanitizadorTexto.limpiar(proveedor.getCiudad()));
        proveedor.setDireccion(SanitizadorTexto.limpiar(proveedor.getDireccion()));
        proveedor.setNit(SanitizadorTexto.limpiar(proveedor.getNit()));
        proveedor.setObservaciones(SanitizadorTexto.limpiar(proveedor.getObservaciones()));

        if (proveedor.getActivo() == null) {
            proveedor.setActivo(Boolean.TRUE);
        }

        if (proveedor.getId() != null) {
            findById(proveedor.getId());
        }

        return repository.save(proveedor);
    }

    public void delete(Long id) {
        findById(id);
        repository.deleteById(id);
    }

    public String construirPrefijoCodigoProveedor(Long proveedorId) {
        Proveedor proveedor = findById(proveedorId);
        String nombre = SanitizadorTexto.limpiar(proveedor.getNombreComercial());
        if (nombre == null) {
            throw new BusinessException("No se pudo generar el codigo del proveedor porque el nombre comercial es invalido.");
        }

        String normalizado = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace(" ", "");

        if (normalizado.isBlank()) {
            throw new BusinessException("No se pudo generar el codigo del proveedor porque el nombre comercial es invalido.");
        }

        return normalizado.length() <= 4 ? normalizado : normalizado.substring(0, 4);
    }
}
