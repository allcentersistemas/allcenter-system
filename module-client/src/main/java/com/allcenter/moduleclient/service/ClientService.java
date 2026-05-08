package com.allcenter.moduleclient.service;

import com.allcenter.moduleclient.exception.ConflictException;
import com.allcenter.moduleclient.exception.NotFoundException;
import com.allcenter.moduleclient.model.ClientUser;
import com.allcenter.moduleclient.model.dto.ClientCreateRequest;
import com.allcenter.moduleclient.model.dto.ClientResponse;
import com.allcenter.moduleclient.model.dto.ClientUpdateRequest;
import com.allcenter.moduleclient.repository.ClientUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientUserRepository clientUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        return clientUserRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse getById(long id) {
        ClientUser client =
                clientUserRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + id));
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request) {
        String email = request.email().trim().toLowerCase();
        if (clientUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya esta registrado");
        }
        ClientUser client = new ClientUser();
        client.setEmail(email);
        client.setPassword(passwordEncoder.encode(request.password()));
        client.setDisplayName(request.displayName().trim());
        client.setCompanyName(request.companyName() != null ? request.companyName().trim() : null);
        client.setPhone(request.phone() != null ? request.phone().trim() : null);
        client.setTaxId(request.taxId() != null ? request.taxId().trim() : null);
        client.setActive(request.active() == null || request.active());
        clientUserRepository.save(client);
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse update(long id, ClientUpdateRequest request) {
        ClientUser client =
                clientUserRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + id));
        if (request.displayName() != null) {
            client.setDisplayName(request.displayName().trim());
        }
        if (request.companyName() != null) {
            client.setCompanyName(request.companyName().trim());
        }
        if (request.phone() != null) {
            client.setPhone(request.phone().trim());
        }
        if (request.taxId() != null) {
            client.setTaxId(request.taxId().trim());
        }
        if (request.active() != null) {
            client.setActive(request.active());
        }
        clientUserRepository.save(client);
        return ClientResponse.from(client);
    }

    @Transactional
    public void delete(long id) {
        if (!clientUserRepository.existsById(id)) {
            throw new NotFoundException("No existe un cliente con id " + id);
        }
        clientUserRepository.deleteById(id);
    }
}
