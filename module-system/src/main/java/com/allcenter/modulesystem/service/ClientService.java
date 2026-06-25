package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.ClientAdminUpdateRequest;
import com.allcenter.modulesystem.dto.ClientCreateRequest;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.ClientUpdateRequest;
import com.allcenter.modulesystem.exception.BadRequestException;
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
    public ClientResponse updateAdmin(long id, ClientAdminUpdateRequest request) {
        ClientUser client =
                clientUserRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + id));

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (clientUserRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw new ConflictException("El correo " + email + " ya esta registrado");
            }
            client.setEmail(email);
        }
        if (request.username() != null && !request.username().isBlank()) {
            String username = request.username().trim().toLowerCase(Locale.ROOT);
            if (clientUserRepository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
                throw new ConflictException("El usuario \"" + username + "\" ya esta en uso");
            }
            client.setUsername(username);
        }
        if (request.juridica() != null) {
            client.setJuridica(request.juridica());
        }
        boolean juridica = client.isJuridica();
        if (juridica) {
            if (request.ruc() != null && !request.ruc().isBlank()) {
                String ruc = request.ruc().trim();
                if (clientUserRepository.existsByRucIgnoreCaseAndIdNot(ruc, id)) {
                    throw new ConflictException("El RUC " + ruc + " ya esta registrado");
                }
                client.setRuc(ruc);
            }
            if (request.razonSocial() != null) {
                client.setRazonSocial(trimOrNull(request.razonSocial()));
            }
            if (request.nombre() != null) {
                client.setNombre(trimOrNull(request.nombre()));
            }
        } else {
            if (request.numeroDocumento() != null && !request.numeroDocumento().isBlank()) {
                String doc = request.numeroDocumento().trim();
                if (clientUserRepository.existsByDocumentodeindentificacionIgnoreCaseAndIdNot(doc, id)) {
                    throw new ConflictException("El documento " + doc + " ya esta registrado");
                }
                client.setDocumentodeindentificacion(doc);
            }
            if (request.tipoDocumento() != null) {
                client.setTipoDocumento(trimOrNull(request.tipoDocumento()));
            }
        }
        if (request.displayName() != null) {
            client.setDisplayName(request.displayName().trim());
        }
        if (request.phone() != null) {
            client.setPhone(trimOrNull(request.phone()));
        }
        if (request.direccion() != null) {
            client.setDireccion(trimOrNull(request.direccion()));
        }
        if (request.ciudad() != null) {
            client.setCiudad(trimOrNull(request.ciudad()));
        }
        if (request.distrito() != null) {
            client.setDistrito(trimOrNull(request.distrito()));
        }
        if (request.departamento() != null) {
            client.setDepartamento(trimOrNull(request.departamento()));
        }
        if (request.active() != null) {
            client.setActive(request.active());
        }
        clientUserRepository.save(client);
        return ClientResponse.from(client);
    }

    @Transactional
    public void resetPasswordByAdmin(long id, String newPassword) {
        ClientUser client =
                clientUserRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + id));
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("La nueva contrasena es obligatoria");
        }
        PasswordPolicy.requireStrong(newPassword.trim());
        client.setPassword(passwordEncoder.encode(newPassword.trim()));
        clientUserRepository.save(client);
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

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
