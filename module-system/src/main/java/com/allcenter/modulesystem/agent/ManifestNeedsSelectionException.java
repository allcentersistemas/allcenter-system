package com.allcenter.modulesystem.agent;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Match de obra falló o quedó ambiguo: el agente debe mostrar candidatos al operador.
 */
@Getter
public class ManifestNeedsSelectionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String job;
    private final List<Map<String, Object>> candidates;

    public ManifestNeedsSelectionException(
            HttpStatus status,
            String code,
            String job,
            String message,
            List<Map<String, Object>> candidates) {
        super(message);
        this.status = status;
        this.code = code;
        this.job = job;
        this.candidates = candidates != null ? candidates : List.of();
    }
}
