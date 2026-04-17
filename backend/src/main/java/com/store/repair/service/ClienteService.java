package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.Cliente;
import com.store.repair.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repository;

    public List<Cliente> findAll() {
        return repository.findAll();
    }

    public Page<Cliente> findPage(String busqueda, int pagina, int tamano) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public Cliente findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + id));
    }

    public Cliente save(Cliente cliente) {
        if (cliente.getId() == null) {
            return crear(cliente);
        }
        return actualizar(cliente);
    }

    private Cliente crear(Cliente cliente) {
        cliente.setNombreCompleto(SanitizadorTexto.limpiar(cliente.getNombreCompleto()));
        cliente.setTelefono(SanitizadorTexto.limpiar(cliente.getTelefono()));
        cliente.setEmail(SanitizadorTexto.limpiar(cliente.getEmail()));
        cliente.setDireccion(SanitizadorTexto.limpiar(cliente.getDireccion()));
        cliente.setNotas(SanitizadorTexto.limpiar(cliente.getNotas()));

        return repository.save(cliente);
    }

    private Cliente actualizar(Cliente cliente) {
        Cliente existente = findById(cliente.getId());

        existente.setNombreCompleto(SanitizadorTexto.limpiar(cliente.getNombreCompleto()));
        existente.setTelefono(SanitizadorTexto.limpiar(cliente.getTelefono()));
        existente.setEmail(SanitizadorTexto.limpiar(cliente.getEmail()));
        existente.setDireccion(SanitizadorTexto.limpiar(cliente.getDireccion()));
        existente.setNotas(SanitizadorTexto.limpiar(cliente.getNotas()));

        return repository.save(existente);
    }

    public void delete(Long id) {
        Cliente existente = findById(id);
        repository.delete(existente);
    }
}
