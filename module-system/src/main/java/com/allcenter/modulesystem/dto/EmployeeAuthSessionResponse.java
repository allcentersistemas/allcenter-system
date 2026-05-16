package com.allcenter.modulesystem.dto;

public record EmployeeAuthSessionResponse(
        EmployeeResponse employee,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInMs,
        long refreshExpiresInMs) {

    public static EmployeeAuthSessionResponse of(
            EmployeeResponse employee,
            String accessToken,
            String refreshToken,
            long accessExpiresInMs,
            long refreshExpiresInMs) {
        return new EmployeeAuthSessionResponse(
                employee, accessToken, refreshToken, "Bearer", accessExpiresInMs, refreshExpiresInMs);
    }
}
