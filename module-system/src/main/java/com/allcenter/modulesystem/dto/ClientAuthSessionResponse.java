package com.allcenter.modulesystem.dto;

public record ClientAuthSessionResponse(
        ClientResponse client,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInMs,
        long refreshExpiresInMs) {

    public static ClientAuthSessionResponse of(
            ClientResponse client,
            String accessToken,
            String refreshToken,
            long accessExpiresInMs,
            long refreshExpiresInMs) {
        return new ClientAuthSessionResponse(
                client, accessToken, refreshToken, "Bearer", accessExpiresInMs, refreshExpiresInMs);
    }
}
