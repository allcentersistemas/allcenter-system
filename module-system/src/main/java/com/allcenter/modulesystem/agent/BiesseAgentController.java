package com.allcenter.modulesystem.agent;

import com.allcenter.modulesystem.agent.BiesseAgentDtos.EventsRequest;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.EventsResponse;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.HeartbeatRequest;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.MeResponse;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.OkResponse;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.PrintAckRequest;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.StatusPayload;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/biesse/agent")
@RequiredArgsConstructor
public class BiesseAgentController {

    public static final String ATTR_MACHINE = "biesseAgentMachine";
    public static final String HEADER_TOKEN = "X-Agent-Token";

    private final BiesseAgentService agentService;
    private final BiesseAgentSchemaAligner schemaAligner;

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        return agentService.me(requireMachine(request));
    }

    @PostMapping("/heartbeat")
    public OkResponse heartbeat(HttpServletRequest request, @RequestBody(required = false) HeartbeatRequest body) {
        return agentService.heartbeat(requireMachine(request), body);
    }

    @PostMapping("/status")
    public OkResponse status(HttpServletRequest request, @RequestBody(required = false) StatusPayload body) {
        return agentService.status(requireMachine(request), body);
    }

    @PostMapping("/events")
    public EventsResponse events(HttpServletRequest request, @RequestBody EventsRequest body) {
        return agentService.events(
                requireMachine(request),
                body != null ? body : new EventsRequest(null, null, null, null, null));
    }

    @PostMapping("/print-ack")
    public OkResponse printAck(HttpServletRequest request, @RequestBody(required = false) PrintAckRequest body) {
        return agentService.printAck(
                requireMachine(request), body != null ? body : new PrintAckRequest(null));
    }

    @GetMapping("/order-manifest")
    public Map<String, Object> orderManifest(
            HttpServletRequest request, @RequestParam("job") String jobName) {
        return agentService.orderManifest(requireMachine(request), jobName);
    }

    @GetMapping("/label-zpl")
    public Map<String, Object> labelZpl(
            HttpServletRequest request,
            @RequestParam("job") String jobName,
            @RequestParam("osiPart") String osiPart,
            @RequestParam("pieceNumber") int pieceNumber,
            @RequestParam("unitCode") String unitCode) {
        return agentService.labelZpl(requireMachine(request), jobName, osiPart, pieceNumber, unitCode);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMachine(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_MACHINE);
        if (attr instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        String token = request.getHeader(HEADER_TOKEN);
        Map<String, Object> machine = schemaAligner.findMachineByToken(token);
        if (machine == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Agent-Token");
        }
        return machine;
    }
}
