package com.allcenter.moduleemployee.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Convierte nombres de rol de BD a authorities de Spring ({@code ROLE_*}). */
public final class SpringSecurityRoles {

    private SpringSecurityRoles() {}

    public static String toAuthority(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "ROLE_USER";
        }
        String s = roleName.trim().toUpperCase();
        return s.startsWith("ROLE_") ? s : "ROLE_" + s;
    }

    public static SimpleGrantedAuthority asGrantedAuthority(String roleName) {
        return new SimpleGrantedAuthority(toAuthority(roleName));
    }
}
