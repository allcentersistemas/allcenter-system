package com.allcenter.modulesystem.agent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autenticación del agente CNC por header X-Agent-Token. */
public class BiesseAgentAuthFilter extends OncePerRequestFilter implements Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final BiesseAgentSchemaAligner schemaAligner;

    public BiesseAgentAuthFilter(BiesseAgentSchemaAligner schemaAligner) {
        this.schemaAligner = schemaAligner;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !PATH_MATCHER.match("/api/biesse/agent/**", path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader(BiesseAgentController.HEADER_TOKEN);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "MISSING_AGENT_TOKEN", "X-Agent-Token is required");
            return;
        }
        Map<String, Object> machine = schemaAligner.findMachineByToken(token.trim());
        if (machine == null) {
            writeUnauthorized(response, "INVALID_AGENT_TOKEN", "Invalid agent token");
            return;
        }
        request.setAttribute(BiesseAgentController.ATTR_MACHINE, machine);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body =
                "{\"code\":\""
                        + code
                        + "\",\"message\":\""
                        + message.replace("\"", "\\\"")
                        + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
