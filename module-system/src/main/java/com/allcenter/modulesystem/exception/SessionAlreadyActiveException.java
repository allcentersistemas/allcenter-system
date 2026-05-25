package com.allcenter.modulesystem.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** El empleado ya tiene una sesión activa (un solo dispositivo a la vez). */
@Getter
public class SessionAlreadyActiveException extends ApiException {

    private final Map<String, String> details;

    public SessionAlreadyActiveException(String clientIp, String clientHostname) {
        super(
                HttpStatus.CONFLICT,
                "SESSION_ALREADY_ACTIVE",
                buildMessage(clientIp, clientHostname));
        this.details = new LinkedHashMap<>();
        if (clientIp != null && !clientIp.isBlank()) {
            details.put("clientIp", clientIp.trim());
        }
        if (clientHostname != null && !clientHostname.isBlank()) {
            details.put("clientHostname", clientHostname.trim());
        }
    }

    private static String buildMessage(String clientIp, String clientHostname) {
        String ip = clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "desconocida";
        String host =
                clientHostname != null && !clientHostname.isBlank()
                        ? clientHostname.trim()
                        : "desconocido";
        return "Ya hay una sesión activa para este usuario (IP "
                + ip
                + ", equipo "
                + host
                + "). Cierre sesión en el otro dispositivo o espere a que expire.";
    }
}
