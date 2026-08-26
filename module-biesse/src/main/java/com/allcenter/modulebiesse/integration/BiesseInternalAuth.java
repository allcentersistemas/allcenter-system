package com.allcenter.modulebiesse.integration;

import com.allcenter.security.BiessePortalRoleAuthorization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Autoriza llamadas de module-system (X-Internal-Token) o JWT de portal.
 */
@Component
public class BiesseInternalAuth {

    public static final String HEADER_INTERNAL = "X-Internal-Token";

    private final BiessePortalRoleAuthorization portalAuth;
    private final String internalToken;

    public BiesseInternalAuth(
            BiessePortalRoleAuthorization portalAuth,
            @Value("${app.biesse.internal-token:dev-biesse-internal}") String internalToken) {
        this.portalAuth = portalAuth;
        this.internalToken = internalToken != null ? internalToken.trim() : "";
    }

    public void requireRead(String authorization, String internalHeader) {
        if (matchesInternal(internalHeader)) {
            return;
        }
        portalAuth.requireRead(authorization);
    }

    public void requireWrite(String authorization, String internalHeader) {
        if (matchesInternal(internalHeader)) {
            return;
        }
        portalAuth.requireCreate(authorization);
    }

    private boolean matchesInternal(String header) {
        if (!StringUtils.hasText(internalToken) || !StringUtils.hasText(header)) {
            return false;
        }
        return internalToken.equals(header.trim());
    }

    public void denyIfNoAuth(String authorization, String internalHeader) {
        if (matchesInternal(internalHeader)) {
            return;
        }
        if (!StringUtils.hasText(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Auth required");
        }
    }
}
