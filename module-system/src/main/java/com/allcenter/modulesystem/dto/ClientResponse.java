package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.ClientUser;
import java.time.Instant;

public record ClientResponse(
        Long id,
        String email,
        String username,
        String displayName,
        boolean juridica,
        String phone,
        String tipoDocumento,
        String numeroDocumento,
        String direccion,
        String ciudad,
        String distrito,
        String departamento,
        String razonSocial,
        String ruc,
        String nombre,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt,
        String lastLoginIp,
        int loginCount) {

    public static ClientResponse from(ClientUser client) {
        return new ClientResponse(
                client.getId(),
                client.getEmail(),
                client.getUsername(),
                client.getDisplayName(),
                client.isJuridica(),
                client.getPhone(),
                client.getTipoDocumento(),
                client.getDocumentodeindentificacion(),
                client.getDireccion(),
                client.getCiudad(),
                client.getDistrito(),
                client.getDepartamento(),
                client.getRazonSocial(),
                client.getRuc(),
                client.getNombre(),
                client.isActive(),
                client.getCreatedAt(),
                client.getLastLoginAt(),
                client.getLastLoginIp(),
                client.getLoginCount());
    }
}
