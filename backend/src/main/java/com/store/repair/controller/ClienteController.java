package com.store.repair.controller;

import com.store.repair.domain.Cliente;
import com.store.repair.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_CLIENTES_VIEW')")
public class ClienteController {

    private final ClienteService service;

    @GetMapping
    public List<Cliente> findAll() {
        return service.findAll();
    }

    @GetMapping("/paginado")
    public Page<Cliente> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(busqueda, pagina, tamano);
    }

    @GetMapping("/{id}")
    public Cliente findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Cliente create(@Valid @RequestBody Cliente cliente) {
        return service.save(cliente);
    }

    @PutMapping("/{id}")
    public Cliente update(@PathVariable Long id, @Valid @RequestBody Cliente cliente) {
        cliente.setId(id);
        return service.save(cliente);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
