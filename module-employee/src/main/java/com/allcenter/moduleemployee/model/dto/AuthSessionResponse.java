package com.allcenter.moduleemployee.model.dto;

public record AuthSessionResponse(
        EmployeeResponse employee,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInMs,
        long refreshExpiresInMs) {

    public static AuthSessionResponse of(
            EmployeeResponse employee,
            String accessToken,
            String refreshToken,
            long accessExpiresInMs,
            long refreshExpiresInMs) {
        return new AuthSessionResponse(
                employee, accessToken, refreshToken, "Bearer", accessExpiresInMs, refreshExpiresInMs);
    }
}
