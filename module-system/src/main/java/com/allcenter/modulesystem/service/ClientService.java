package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.ClientCreateRequest;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.ClientUpdateRequest;
import com.allcenter.modulesystem.exception.ConflictException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.ClientUser;
import com.allcenter.modulesystem.repository.ClientUserRepository;
import com.allcenter.modulesystem.support.PasswordPolicy;
import java.util.List;
import java.util.Locale;
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
        String username = deriveUsername(request.username(), email);
        if (clientUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("El usuario \"" + username + "\" ya esta en uso");
        }
        PasswordPolicy.requireStrong(request.password());
        ClientUser client = new ClientUser();
        client.setEmail(email);
        client.setUsername(username);
        client.setPassword(passwordEncoder.encode(request.password()));
        client.setDisplayName(request.displayName().trim());
        client.setJuridica(false);
        client.setPhone(request.phone() != null ? request.phone().trim() : null);
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
        if (request.phone() != null) {
            client.setPhone(request.phone().trim());
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

    private static String deriveUsername(String requested, String email) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toLowerCase(Locale.ROOT);
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
