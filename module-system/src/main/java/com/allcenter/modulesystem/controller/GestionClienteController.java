package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.ClientCreateRequest;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.ClientUpdateRequest;
import com.allcenter.modulesystem.service.ClientService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gestion/clientes")
@RequiredArgsConstructor
@PreAuthorize("@portalAuth.canGestion()")
public class GestionClienteController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<List<ClientResponse>> list() {
        return ResponseEntity.ok(clientService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getById(@PathVariable long id) {
        return ResponseEntity.ok(clientService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> update(
            @PathVariable long id, @Valid @RequestBody ClientUpdateRequest request) {
        return ResponseEntity.ok(clientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
