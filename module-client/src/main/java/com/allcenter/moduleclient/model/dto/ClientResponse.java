package com.allcenter.moduleclient.model.dto;

import com.allcenter.moduleclient.model.ClientUser;
import java.time.Instant;

public record ClientResponse(
        Long id,
        String email,
        String displayName,
        String companyName,
        String phone,
        String taxId,
        boolean active,
        Instant createdAt) {

    public static ClientResponse from(ClientUser client) {
        return new ClientResponse(
                client.getId(),
                client.getEmail(),
                client.getDisplayName(),
                client.getCompanyName(),
                client.getPhone(),
                client.getTaxId(),
                client.isActive(),
                client.getCreatedAt());
    }
}
