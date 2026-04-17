package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.Cliente;
import com.store.repair.domain.Dispositivo;
import com.store.repair.repository.DispositivoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DispositivoService {

    private final DispositivoRepository repository;
    private final ClienteService clienteService;

    public List<Dispositivo> findAll() {
        return repository.findAll();
    }

    public Page<Dispositivo> findPage(String busqueda, int pagina, int tamano) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public List<Dispositivo> findByCliente(Long clienteId) {
        clienteService.findById(clienteId);
        return repository.findAllByClienteId(clienteId);
    }

    public Dispositivo findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispositivo no encontrado: " + id));
    }

    public Dispositivo save(Dispositivo dispositivo, Long clienteId) {
        Cliente cliente = clienteService.findById(clienteId);

        dispositivo.setCliente(cliente);
        dispositivo.setMarca(SanitizadorTexto.limpiar(dispositivo.getMarca()));
        dispositivo.setModelo(SanitizadorTexto.limpiar(dispositivo.getModelo()));
        dispositivo.setImeiSerie(SanitizadorTexto.limpiar(dispositivo.getImeiSerie()));
        dispositivo.setColor(SanitizadorTexto.limpiar(dispositivo.getColor()));
        dispositivo.setCodigoBloqueo(SanitizadorTexto.limpiar(dispositivo.getCodigoBloqueo()));
        dispositivo.setAccesorios(SanitizadorTexto.limpiar(dispositivo.getAccesorios()));
        dispositivo.setObservaciones(SanitizadorTexto.limpiar(dispositivo.getObservaciones()));

        if (dispositivo.getId() != null) {
            findById(dispositivo.getId());
        }

        return repository.save(dispositivo);
    }

    public void delete(Long id) {
        findById(id);
        repository.deleteById(id);
    }
}
