package com.allcenter.modulebiesse.obras;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a órdenes/partes/trazabilidad en BD {@code obras} (sin tablas de agente). */
@Repository
@RequiredArgsConstructor
public class BiesseObrasRepository {

    private static final Pattern OP_PATTERN = Pattern.compile("^([A-Za-z]?\\d{3,})\\b");
    private static final Pattern PART_PATTERN =
            Pattern.compile("(?i)^Part\\s*(P?\\d+)", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    public Map<String, Object> findOrderById(long orderId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                               nparts, partes_totales
                        FROM ordenes
                        WHERE orderid = ?
                        """,
                        orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> findOrderForJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return null;
        }
        String token = jobName.trim();
        String op = extractOp(token);

        List<Map<String, Object>> exact =
                jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                               nparts, partes_totales
                        FROM ordenes
                        WHERE UPPER(TRIM(ordername)) = UPPER(TRIM(?))
                           OR (bookingcode IS NOT NULL AND UPPER(TRIM(bookingcode)) = UPPER(TRIM(?)))
                        ORDER BY fechacreacion DESC
                        LIMIT 1
                        """,
                        token,
                        token);
        if (!exact.isEmpty()) {
            return exact.getFirst();
        }

        if (op != null) {
            List<Map<String, Object>> byOp =
                    jdbc.queryForList(
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                   nparts, partes_totales
                            FROM ordenes
                            WHERE UPPER(TRIM(COALESCE(op_codigo, ''))) = UPPER(?)
                               OR UPPER(TRIM(ordername)) LIKE UPPER(?) || ' %'
                               OR UPPER(REPLACE(ordername, ' ', '')) LIKE UPPER(REPLACE(?, ' ', '')) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 1
                            """,
                            op,
                            op,
                            token);
            if (!byOp.isEmpty()) {
                return byOp.getFirst();
            }
        }

        String noSpaces = token.replaceAll("\\s+", "");
        List<Map<String, Object>> fuzzy =
                jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                               nparts, partes_totales
                        FROM ordenes
                        WHERE UPPER(REPLACE(ordername, ' ', '')) = UPPER(?)
                           OR UPPER(REPLACE(ordername, ' ', '')) LIKE UPPER(?) || '%'
                           OR UPPER(?) LIKE UPPER(REPLACE(ordername, ' ', '')) || '%'
                        ORDER BY fechacreacion DESC
                        LIMIT 1
                        """,
                        noSpaces,
                        noSpaces,
                        noSpaces);
        return fuzzy.isEmpty() ? null : fuzzy.getFirst();
    }

    public boolean markOrderProduccion(long orderId) {
        int updated =
                jdbc.update(
                        """
                        UPDATE ordenes
                        SET estado_escaneo = 'PRODUCCION',
                            fecha_modificacion = CURRENT_TIMESTAMP
                        WHERE orderid = ?
                          AND COALESCE(UPPER(estado_escaneo), '') NOT IN ('COMPLETADA', 'PRODUCCION')
                        """,
                        orderId);
        return updated > 0;
    }

    public void registrarTrazabilidad(
            String opCodigo,
            Long orderId,
            String orderName,
            String estado,
            String accion,
            String detalle,
            int piezas,
            int partes,
            String usuario) {
        jdbc.update(
                """
                INSERT INTO op_trazabilidad
                    (op_codigo, orderid, ordername, estado, accion, detalle,
                     piezas_totales, partes_totales, usuario, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                opCodigo != null ? opCodigo : extractOp(orderName),
                orderId,
                orderName,
                estado,
                accion,
                detalle,
                piezas,
                partes,
                usuario);
    }

    public Map<String, Object> findPartForOsi(long orderId, String osiPartText) {
        Integer partNumber = parsePartNumber(osiPartText);
        if (partNumber == null) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT partid, orderid, partcode, partnumber, cantidad, material,
                               descripcion, descripcion1, longitud, ancho, escaneado
                        FROM partes
                        WHERE orderid = ?
                          AND (partnumber = ? OR UPPER(TRIM(partcode)) = UPPER(?) OR UPPER(TRIM(partcode)) = UPPER(?))
                        ORDER BY partid
                        LIMIT 1
                        """,
                        orderId,
                        partNumber,
                        "P" + partNumber,
                        String.valueOf(partNumber));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Siguiente número de pieza aún no cortada por el agente. No inventa filas al marcar:
     * si todas están cortadas, devuelve max+1 (el mark fallará sin crear pieza).
     */
    public Integer nextPieceNumber(long partId) {
        List<Map<String, Object>> pending =
                jdbc.queryForList(
                        """
                        SELECT MIN(numero_pieza) AS n
                        FROM piezas
                        WHERE partid = ?
                          AND COALESCE(cortada, FALSE) = FALSE
                        """,
                        partId);
        if (!pending.isEmpty() && pending.getFirst().get("n") != null) {
            return ((Number) pending.getFirst().get("n")).intValue();
        }
        List<Map<String, Object>> maxRows =
                jdbc.queryForList(
                        """
                        SELECT COALESCE(MAX(numero_pieza), 0) AS n
                        FROM piezas
                        WHERE partid = ?
                        """,
                        partId);
        int max = 0;
        if (!maxRows.isEmpty() && maxRows.getFirst().get("n") != null) {
            max = ((Number) maxRows.getFirst().get("n")).intValue();
        }
        return max + 1;
    }

    /**
     * Marca pieza cortada por el seccionador (solo filas existentes; idempotente).
     *
     * @return mapa con piezaid / already / updated, o null si no hay pieza
     */
    public Map<String, Object> markPiezaCortada(long partId, int pieceNumber, String machineName) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT piezaid, numero_pieza, COALESCE(cortada, FALSE) AS cortada
                        FROM piezas
                        WHERE partid = ? AND numero_pieza = ?
                        LIMIT 1
                        """,
                        partId,
                        pieceNumber);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> piece = rows.getFirst();
        boolean already = Boolean.TRUE.equals(piece.get("cortada"))
                || "t".equalsIgnoreCase(String.valueOf(piece.get("cortada")))
                || "true".equalsIgnoreCase(String.valueOf(piece.get("cortada")));
        if (!already) {
            jdbc.update(
                    """
                    UPDATE piezas
                    SET cortada = TRUE,
                        cortada_at = CURRENT_TIMESTAMP,
                        cortada_por = ?
                    WHERE piezaid = ?
                      AND COALESCE(cortada, FALSE) = FALSE
                    """,
                    machineName != null && !machineName.isBlank() ? machineName.trim() : null,
                    ((Number) piece.get("piezaid")).longValue());
        }
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("piezaId", ((Number) piece.get("piezaid")).longValue());
        out.put("numeroPieza", pieceNumber);
        out.put("partId", partId);
        out.put("already", already);
        out.put("updated", !already);
        out.put("cortada", true);
        return out;
    }

    public List<Map<String, Object>> listTrazabilidad(String opCodigo, Long orderId, int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        String op = opCodigo;
        if ((op == null || op.isBlank()) && orderId != null) {
            List<Map<String, Object>> ord =
                    jdbc.queryForList(
                            "SELECT ordername, op_codigo FROM ordenes WHERE orderid = ?", orderId);
            if (!ord.isEmpty()) {
                op = str(ord.getFirst().get("op_codigo"));
                if (op == null || op.isBlank()) {
                    op = extractOp(str(ord.getFirst().get("ordername")));
                }
            }
        }
        if (orderId != null && op != null && !op.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE orderid = ? OR UPPER(TRIM(op_codigo)) = UPPER(TRIM(?))
                    ORDER BY fecha DESC, id DESC
                    LIMIT ?
                    """,
                    orderId,
                    op,
                    safe);
        }
        if (orderId != null) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE orderid = ?
                    ORDER BY fecha DESC, id DESC
                    LIMIT ?
                    """,
                    orderId,
                    safe);
        }
        if (op != null && !op.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE UPPER(TRIM(op_codigo)) = UPPER(TRIM(?))
                    ORDER BY fecha DESC, id DESC
                    LIMIT ?
                    """,
                    op.trim(),
                    safe);
        }
        return jdbc.queryForList(
                """
                SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                       xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                FROM op_trazabilidad
                ORDER BY fecha DESC, id DESC
                LIMIT ?
                """,
                safe);
    }

    public static String extractOp(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Matcher m = OP_PATTERN.matcher(name.trim());
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    public static Integer parsePartNumber(String osiPartText) {
        if (osiPartText == null || osiPartText.isBlank()) {
            return null;
        }
        Matcher m = PART_PATTERN.matcher(osiPartText.trim());
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1).toUpperCase().replace("P", "");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
