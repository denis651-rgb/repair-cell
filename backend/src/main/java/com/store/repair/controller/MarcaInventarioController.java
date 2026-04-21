package com.store.repair.controller;

import com.store.repair.domain.MarcaInventario;
import com.store.repair.service.MarcaInventarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/marcas")
@RequiredArgsConstructor
public class MarcaInventarioController {

    private final MarcaInventarioService service;

    @GetMapping
    public List<MarcaInventario> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public MarcaInventario findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public MarcaInventario create(@Valid @RequestBody MarcaInventario marca) {
        return service.save(marca);
    }

    @PutMapping("/{id}")
    public MarcaInventario update(@PathVariable Long id, @Valid @RequestBody MarcaInventario marca) {
        marca.setId(id);
        return service.save(marca);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
