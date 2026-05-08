package com.allcenter.moduleclient.model.dto;

public record AuthSessionResponse(
        ClientResponse client,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInMs,
        long refreshExpiresInMs) {

    public static AuthSessionResponse of(
            ClientResponse client,
            String accessToken,
            String refreshToken,
            long accessExpiresInMs,
            long refreshExpiresInMs) {
        return new AuthSessionResponse(
                client, accessToken, refreshToken, "Bearer", accessExpiresInMs, refreshExpiresInMs);
    }
}
