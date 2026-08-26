package com.allcenter.modulesystem.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** Contratos JSON del agente Win10 (`/api/biesse/agent` en module-system). */
public final class BiesseAgentDtos {

    private BiesseAgentDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeResponse(
            boolean ok,
            @JsonProperty("machine_id") Integer machineId,
            @JsonProperty("machine_name") String machineName,
            @JsonProperty("company_id") Integer companyId,
            Boolean online,
            String state,
            String message) {}

    public record HeartbeatRequest(
            @JsonProperty("agent_version") String agentVersion,
            @JsonProperty("compatible_profile") String compatibleProfile,
            @JsonProperty("printer_name") String printerName,
            @JsonProperty("printer_enabled") Boolean printerEnabled,
            @JsonProperty("plant_name") String plantName,
            String hostname,
            @JsonProperty("log_path") String logPath,
            @JsonProperty("pending_queue_size") Integer pendingQueueSize,
            @JsonProperty("log_byte_offset") Long logByteOffset,
            @JsonProperty("log_encoding") String logEncoding,
            @JsonProperty("health_status") String healthStatus,
            @JsonProperty("last_error") String lastError) {}

    public record StatusPayload(
            String state,
            @JsonProperty("job_name") String jobName,
            @JsonProperty("pattern_name") String patternName,
            @JsonProperty("active_command") String activeCommand,
            @JsonProperty("last_part") String lastPart,
            @JsonProperty("boards_done") Integer boardsDone,
            @JsonProperty("pieces_produced") Integer piecesProduced,
            @JsonProperty("alarm_codes") List<Integer> alarmCodes,
            String severity,
            @JsonProperty("osi_session_id") String osiSessionId,
            @JsonProperty("ui_version") String uiVersion,
            @JsonProperty("plc_version") String plcVersion,
            @JsonProperty("event_time") String eventTime,
            @JsonProperty("pending_queue_size") Integer pendingQueueSize,
            @JsonProperty("log_byte_offset") Long logByteOffset,
            @JsonProperty("health_status") String healthStatus) {}

    public record AgentEventDto(
            @JsonProperty("event_uid") String eventUid,
            @JsonProperty("event_type") String eventType,
            String code,
            String description,
            String severity,
            @JsonProperty("event_time") String eventTime) {}

    public record EventsRequest(
            String cursor,
            @JsonProperty("pending_queue_size") Integer pendingQueueSize,
            @JsonProperty("log_byte_offset") Long logByteOffset,
            @JsonProperty("health_status") String healthStatus,
            List<AgentEventDto> events) {
        public List<AgentEventDto> eventsOrEmpty() {
            return events != null ? events : List.of();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LabelDto(
            @JsonProperty("cut_piece_id") Long cutPieceId,
            @JsonProperty("event_uid") String eventUid,
            @JsonProperty("osi_part_id") String osiPartId,
            @JsonProperty("unit_code") String unitCode,
            @JsonProperty("map_status") String mapStatus,
            String zpl,
            @JsonProperty("print_locally") Boolean printLocally) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventsResponse(
            boolean ok,
            int accepted,
            int duplicates,
            @JsonProperty("print_mode") String printMode,
            List<LabelDto> labels) {
        public static EventsResponse of(int accepted, int duplicates, String printMode, List<LabelDto> labels) {
            return new EventsResponse(
                    true,
                    accepted,
                    duplicates,
                    printMode,
                    labels != null ? labels : new ArrayList<>());
        }
    }

    public record PrintAckItem(
            @JsonProperty("cut_piece_id") Long cutPieceId,
            @JsonProperty("event_uid") String eventUid,
            boolean printed,
            String error) {}

    public record PrintAckRequest(List<PrintAckItem> labels) {
        public List<PrintAckItem> labelsOrEmpty() {
            return labels != null ? labels : List.of();
        }
    }

    public record OkResponse(boolean ok) {
        public static OkResponse success() {
            return new OkResponse(true);
        }
    }
}
