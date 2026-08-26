package com.allcenter.modulebiesse.obras;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a órdenes/partes/trazabilidad en BD {@code obras} (sin tablas de agente). */
@Repository
@RequiredArgsConstructor
public class BiesseObrasRepository {

    public static final String ESTADO_OPTIMIZADO = "OPTIMIZADO";
    public static final String ESTADO_PRODUCCION = "PRODUCCION";
    public static final String ESTADO_DESPACHO = "DESPACHO";
    public static final String ESTADO_LISTO = "LISTO_PARA_ENTREGAR";
    public static final String ESTADO_ENTREGADO = "ENTREGADO";
    /** Legacy; se trata como {@link #ESTADO_LISTO} en lecturas y nuevas escrituras. */
    public static final String ESTADO_COMPLETADA_LEGACY = "COMPLETADA";

    private static final Set<String> BLOQUEA_PRODUCCION =
            Set.of(
                    ESTADO_PRODUCCION,
                    ESTADO_DESPACHO,
                    ESTADO_LISTO,
                    ESTADO_ENTREGADO,
                    ESTADO_COMPLETADA_LEGACY,
                    "COMPLETADO");
    private static final Set<String> FROM_DESPACHO = Set.of(ESTADO_OPTIMIZADO, ESTADO_PRODUCCION);
    private static final Set<String> FROM_ENTREGADO =
            Set.of(ESTADO_LISTO, ESTADO_COMPLETADA_LEGACY, "COMPLETADO", ESTADO_DESPACHO);
    private static final Set<String> SEGUIMIENTO_ESTADOS =
            Set.of(
                    ESTADO_OPTIMIZADO,
                    ESTADO_PRODUCCION,
                    ESTADO_DESPACHO,
                    ESTADO_LISTO,
                    ESTADO_ENTREGADO,
                    ESTADO_COMPLETADA_LEGACY,
                    "COMPLETADO");

    private static final Pattern OP_PATTERN = Pattern.compile("^([A-Za-z]?\\d{3,})\\b");
    private static final Pattern PART_PATTERN =
            Pattern.compile("(?i)^Part\\s*(P?\\d+)", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    /**
     * Cutoff del tablero Seguimiento por XML: solo obras con {@code fechacreacion >=} esta fecha
     * (ISO {@code yyyy-MM-dd}). Configurable con {@code app.biesse.seguimiento-since} /
     * env {@code APP_BIESSE_SEGUIMIENTO_SINCE}.
     */
    @Value("${app.biesse.seguimiento-since:2026-08-26}")
    private LocalDate seguimientoSince;

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

    /**
     * Agente CNC/seccionador: OPTIMIZADO (u vacío) → PRODUCCION.
     * No retrocede ni pisa DESPACHO / LISTO / ENTREGADO.
     */
    public boolean markOrderProduccion(long orderId) {
        Map<String, Object> order = findOrderById(orderId);
        if (order == null) {
            return false;
        }
        String current = normalizeEstado(str(order.get("estado_escaneo")));
        if (BLOQUEA_PRODUCCION.contains(current)) {
            return false;
        }
        int updated =
                jdbc.update(
                        """
                        UPDATE ordenes
                        SET estado_escaneo = ?,
                            fecha_modificacion = CURRENT_TIMESTAMP
                        WHERE orderid = ?
                          AND COALESCE(UPPER(TRIM(estado_escaneo)), '') NOT IN (
                              'PRODUCCION', 'DESPACHO', 'LISTO_PARA_ENTREGAR',
                              'ENTREGADO', 'COMPLETADA', 'COMPLETADO')
                        """,
                        ESTADO_PRODUCCION,
                        orderId);
        if (updated > 0) {
            registrarTrazabilidad(
                    str(order.get("op_codigo")),
                    orderId,
                    str(order.get("ordername")),
                    ESTADO_PRODUCCION,
                    "PRODUCCION",
                    "Agente seccionador detectó XML/job",
                    numberInt(order.get("nparts")),
                    numberInt(order.get("partes_totales")),
                    "agente-cnc");
        }
        return updated > 0;
    }

    /**
     * Primer escaneo parcial Android: OPTIMIZADO/PRODUCCION → DESPACHO.
     */
    public boolean markOrderDespacho(long orderId, String usuario) {
        Map<String, Object> order = findOrderById(orderId);
        if (order == null) {
            return false;
        }
        String current = normalizeEstado(str(order.get("estado_escaneo")));
        if (ESTADO_DESPACHO.equals(current)
                || ESTADO_LISTO.equals(current)
                || ESTADO_ENTREGADO.equals(current)) {
            return false;
        }
        if (!FROM_DESPACHO.contains(current) && !current.isBlank() && !"PENDIENTE".equals(current)) {
            return false;
        }
        int updated =
                jdbc.update(
                        """
                        UPDATE ordenes
                        SET estado_escaneo = ?,
                            fecha_modificacion = CURRENT_TIMESTAMP
                        WHERE orderid = ?
                          AND COALESCE(UPPER(TRIM(estado_escaneo)), '') IN ('OPTIMIZADO', 'PRODUCCION', '', 'PENDIENTE')
                        """,
                        ESTADO_DESPACHO,
                        orderId);
        if (updated > 0) {
            registrarTrazabilidad(
                    str(order.get("op_codigo")),
                    orderId,
                    str(order.get("ordername")),
                    ESTADO_DESPACHO,
                    "DESPACHO",
                    "Primer escaneo de piezas en Android",
                    numberInt(order.get("nparts")),
                    numberInt(order.get("partes_totales")),
                    usuario != null ? usuario : "android-scan");
        }
        return updated > 0;
    }

    /**
     * Escaneo al 100%: → LISTO_PARA_ENTREGAR (reemplaza escrituras legacy COMPLETADA).
     */
    public boolean markOrderListoParaEntregar(long orderId, Long employeeId) {
        Map<String, Object> order = findOrderById(orderId);
        if (order == null) {
            return false;
        }
        String current = normalizeEstado(str(order.get("estado_escaneo")));
        if (ESTADO_LISTO.equals(current) || ESTADO_ENTREGADO.equals(current)) {
            return false;
        }
        int updated;
        try {
            updated =
                    jdbc.update(
                            """
                            UPDATE ordenes
                            SET estado_escaneo = ?,
                                fecha_completado = CURRENT_TIMESTAMP,
                                usuario_completado_id = ?,
                                procesado = TRUE,
                                porcentaje_completado = 100,
                                fecha_modificacion = CURRENT_TIMESTAMP
                            WHERE orderid = ?
                              AND COALESCE(UPPER(TRIM(estado_escaneo)), '') NOT IN ('LISTO_PARA_ENTREGAR', 'ENTREGADO')
                            """,
                            ESTADO_LISTO,
                            employeeId,
                            orderId);
        } catch (DataAccessException ex) {
            updated =
                    jdbc.update(
                            """
                            UPDATE ordenes
                            SET estado_escaneo = ?,
                                fecha_modificacion = CURRENT_TIMESTAMP
                            WHERE orderid = ?
                              AND COALESCE(UPPER(TRIM(estado_escaneo)), '') NOT IN ('LISTO_PARA_ENTREGAR', 'ENTREGADO')
                            """,
                            ESTADO_LISTO,
                            orderId);
        }
        if (updated > 0) {
            registrarTrazabilidad(
                    str(order.get("op_codigo")),
                    orderId,
                    str(order.get("ordername")),
                    ESTADO_LISTO,
                    "LISTO_PARA_ENTREGAR",
                    "Escaneo al 100% de piezas/partes",
                    numberInt(order.get("nparts")),
                    numberInt(order.get("partes_totales")),
                    employeeId != null ? "emp:" + employeeId : "android-scan");
        }
        return updated > 0;
    }

    public boolean markOrderEntregado(long orderId, String usuario) {
        Map<String, Object> order = findOrderById(orderId);
        if (order == null) {
            return false;
        }
        String current = normalizeEstado(str(order.get("estado_escaneo")));
        if (ESTADO_ENTREGADO.equals(current)) {
            return true;
        }
        if (!FROM_ENTREGADO.contains(current)) {
            return false;
        }
        int updated =
                jdbc.update(
                        """
                        UPDATE ordenes
                        SET estado_escaneo = ?,
                            fecha_modificacion = CURRENT_TIMESTAMP
                        WHERE orderid = ?
                          AND COALESCE(UPPER(TRIM(estado_escaneo)), '') IN (
                              'LISTO_PARA_ENTREGAR', 'COMPLETADA', 'COMPLETADO', 'DESPACHO')
                        """,
                        ESTADO_ENTREGADO,
                        orderId);
        if (updated > 0) {
            registrarTrazabilidad(
                    str(order.get("op_codigo")),
                    orderId,
                    str(order.get("ordername")),
                    ESTADO_ENTREGADO,
                    "ENTREGADO",
                    "Obra marcada como entregada",
                    numberInt(order.get("nparts")),
                    numberInt(order.get("partes_totales")),
                    usuario != null ? usuario : "android");
        }
        return updated > 0;
    }

    public Map<String, Object> findOrderByNameOrBooking(String orderName, String bookingCode) {
        if ((orderName == null || orderName.isBlank()) && (bookingCode == null || bookingCode.isBlank())) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                               nparts, partes_totales
                        FROM ordenes
                        WHERE (? IS NOT NULL AND UPPER(TRIM(ordername)) = UPPER(TRIM(?)))
                           OR (? IS NOT NULL AND bookingcode IS NOT NULL
                               AND UPPER(TRIM(bookingcode)) = UPPER(TRIM(?)))
                        ORDER BY fechacreacion DESC
                        LIMIT 1
                        """,
                        blankToNull(orderName),
                        blankToNull(orderName),
                        blankToNull(bookingCode),
                        blankToNull(bookingCode));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Tablero Seguimiento: obras en flujo post-XML (no kanban CRM).
     * Solo incluye obras con {@code fechacreacion >= app.biesse.seguimiento-since} para no listar
     * histórico anterior al arranque del tablero limpio.
     */
    public List<Map<String, Object>> listSeguimientoObras(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        LocalDate since = seguimientoSince != null ? seguimientoSince : LocalDate.of(2026, 8, 26);
        List<Map<String, Object>> rows;
        try {
            rows =
                    jdbc.queryForList(
                            """
                            SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.estado_escaneo,
                                   o.fechacreacion,
                                   (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid) AS total_partes,
                                   (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid AND COALESCE(p.escaneado, FALSE)) AS partes_escaneadas,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid WHERE p.orderid = o.orderid) AS piezas_totales,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid WHERE p.orderid = o.orderid AND COALESCE(z.escaneado, FALSE)) AS piezas_escaneadas,
                                   (SELECT z.cortada_por FROM piezas z
                                      JOIN partes p ON p.partid = z.partid
                                     WHERE p.orderid = o.orderid AND z.cortada_por IS NOT NULL AND TRIM(z.cortada_por) <> ''
                                     ORDER BY z.cortada_at DESC NULLS LAST
                                     LIMIT 1) AS seccionador
                            FROM ordenes o
                            WHERE UPPER(TRIM(COALESCE(o.estado_escaneo, ''))) IN (
                                'OPTIMIZADO', 'PRODUCCION', 'DESPACHO',
                                'LISTO_PARA_ENTREGAR', 'ENTREGADO', 'COMPLETADA', 'COMPLETADO')
                              AND o.fechacreacion IS NOT NULL
                              AND DATE(o.fechacreacion) >= CAST(? AS DATE)
                            ORDER BY o.fechacreacion DESC NULLS LAST, o.orderid DESC
                            LIMIT ?
                            """,
                            since,
                            safe);
        } catch (DataAccessException ex) {
            rows =
                    jdbc.queryForList(
                            """
                            SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.estado_escaneo,
                                   o.fechacreacion,
                                   (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid) AS total_partes,
                                   (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid AND COALESCE(p.escaneado, FALSE)) AS partes_escaneadas,
                                   0 AS piezas_totales,
                                   0 AS piezas_escaneadas,
                                   NULL AS seccionador
                            FROM ordenes o
                            WHERE UPPER(TRIM(COALESCE(o.estado_escaneo, ''))) IN (
                                'OPTIMIZADO', 'PRODUCCION', 'DESPACHO',
                                'LISTO_PARA_ENTREGAR', 'ENTREGADO', 'COMPLETADA', 'COMPLETADO')
                              AND o.fechacreacion IS NOT NULL
                              AND DATE(o.fechacreacion) >= CAST(? AS DATE)
                            ORDER BY o.fechacreacion DESC NULLS LAST, o.orderid DESC
                            LIMIT ?
                            """,
                            since,
                            safe);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(toSeguimientoCard(row));
        }
        return out;
    }

    private Map<String, Object> toSeguimientoCard(Map<String, Object> row) {
        int totalPartes = numberInt(row.get("total_partes"));
        int partesEsc = numberInt(row.get("partes_escaneadas"));
        int piezasTot = numberInt(row.get("piezas_totales"));
        int piezasEsc = numberInt(row.get("piezas_escaneadas"));
        double pct;
        String avance;
        if (piezasTot > 0) {
            pct = Math.round(piezasEsc * 1000.0 / piezasTot) / 10.0;
            avance = piezasEsc + "/" + piezasTot + " piezas";
        } else if (totalPartes > 0) {
            pct = Math.round(partesEsc * 1000.0 / totalPartes) / 10.0;
            avance = partesEsc + "/" + totalPartes + " partes";
        } else {
            pct = 0;
            avance = "0/0";
        }
        Map<String, Object> obra = new LinkedHashMap<>();
        obra.put("orderid", row.get("orderid"));
        obra.put("orderId", row.get("orderid"));
        obra.put("ordername", row.get("ordername"));
        obra.put("orderName", row.get("ordername"));
        obra.put("bookingcode", row.get("bookingcode"));
        obra.put("bookingCode", row.get("bookingcode"));
        obra.put("op_codigo", row.get("op_codigo"));
        obra.put("opCodigo", row.get("op_codigo"));
        obra.put("estado_escaneo", normalizeEstadoForUi(str(row.get("estado_escaneo"))));
        obra.put("estadoEscaneo", normalizeEstadoForUi(str(row.get("estado_escaneo"))));
        obra.put("fechacreacion", row.get("fechacreacion"));
        obra.put("porcentaje", pct);
        obra.put("avance_label", avance);
        obra.put("avanceLabel", avance);
        obra.put("seccionador", blankToNull(str(row.get("seccionador"))));
        obra.put("piezas_totales", piezasTot);
        obra.put("piezas_escaneadas", piezasEsc);
        obra.put("partes_totales", totalPartes);
        obra.put("partes_escaneadas", partesEsc);
        return obra;
    }

    /** Normaliza COMPLETADA → LISTO_PARA_ENTREGAR para UI/API. */
    public static String normalizeEstadoForUi(String raw) {
        String e = normalizeEstado(raw);
        if (ESTADO_COMPLETADA_LEGACY.equals(e) || "COMPLETADO".equals(e)) {
            return ESTADO_LISTO;
        }
        return e;
    }

    public static String normalizeEstado(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    public static boolean isSeguimientoEstado(String raw) {
        return SEGUIMIENTO_ESTADOS.contains(normalizeEstado(raw));
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
        // op_codigo / estado / accion son NOT NULL en op_trazabilidad.
        // Primer escaneo Android → DESPACHO suele llegar con op_codigo null si la orden
        // no matchea OP_PATTERN (p.ej. nombres sin prefijo numérico).
        String resolvedOp = resolveOpCodigo(opCodigo, orderName, orderId);
        String resolvedEstado =
                blankToNull(estado) != null ? estado.trim().toUpperCase(Locale.ROOT) : "DESCONOCIDO";
        String resolvedAccion =
                blankToNull(accion) != null ? accion.trim().toUpperCase(Locale.ROOT) : "EVENTO";
        jdbc.update(
                """
                INSERT INTO op_trazabilidad
                    (op_codigo, orderid, ordername, estado, accion, detalle,
                     piezas_totales, partes_totales, usuario, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                resolvedOp,
                orderId,
                orderName,
                resolvedEstado,
                resolvedAccion,
                detalle,
                piezas,
                partes,
                usuario);
        persistOpCodigoIfMissing(orderId, resolvedOp);
    }

    /**
     * Resuelve un op_codigo nunca-null para inserts NOT NULL.
     * Orden: valor dado → extractOp(ordername) → ordername truncado → ORD-{id} → SIN_OP.
     */
    public static String resolveOpCodigo(String opCodigo, String orderName, Long orderId) {
        String fromCol = blankToNull(opCodigo);
        if (fromCol != null) {
            return truncate(fromCol, 40);
        }
        String fromName = extractOp(orderName);
        if (fromName != null) {
            return truncate(fromName, 40);
        }
        String name = blankToNull(orderName);
        if (name != null) {
            return truncate(name, 40);
        }
        if (orderId != null) {
            return "ORD-" + orderId;
        }
        return "SIN_OP";
    }

    private void persistOpCodigoIfMissing(Long orderId, String resolvedOp) {
        if (orderId == null || resolvedOp == null || resolvedOp.isBlank()) {
            return;
        }
        try {
            jdbc.update(
                    """
                    UPDATE ordenes
                    SET op_codigo = ?
                    WHERE orderid = ?
                      AND (op_codigo IS NULL OR TRIM(op_codigo) = '')
                    """,
                    truncate(resolvedOp, 40),
                    orderId);
        } catch (DataAccessException ignored) {
            // Columna ausente en esquemas muy antiguos
        }
    }

    private static String truncate(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
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
     * Asegura filas en {@code piezas} 1..N de la parte (N = max(cantidad, minCount)).
     * Sin esto el agente puede registrar el corte en monitor pero {@code markPiezaCortada} no pinta nada.
     */
    public int ensurePiezasForPart(long partId) {
        return ensurePiezasForPart(partId, 0);
    }

    public int ensurePiezasForPart(long partId, int minCount) {
        List<Map<String, Object>> partRows =
                jdbc.queryForList(
                        "SELECT cantidad FROM partes WHERE partid = ? LIMIT 1", partId);
        if (partRows.isEmpty()) {
            return 0;
        }
        int qty = 0;
        Object raw = partRows.getFirst().get("cantidad");
        if (raw instanceof Number n) {
            qty = n.intValue();
        }
        qty = Math.max(qty, Math.max(0, minCount));
        if (qty <= 0) {
            return 0;
        }
        int created = 0;
        for (int i = 1; i <= qty; i++) {
            try {
                int n =
                        jdbc.update(
                                """
                                INSERT INTO piezas (partid, numero_pieza, escaneado, cortada)
                                SELECT ?, ?, FALSE, FALSE
                                WHERE NOT EXISTS (
                                    SELECT 1 FROM piezas z
                                    WHERE z.partid = ? AND z.numero_pieza = ?
                                )
                                """,
                                partId,
                                i,
                                partId,
                                i);
                created += Math.max(n, 0);
            } catch (DataAccessException ex) {
                // Esquema sin cortada / columnas distintas: intentar insert mínimo.
                try {
                    int n =
                            jdbc.update(
                                    """
                                    INSERT INTO piezas (partid, numero_pieza, escaneado)
                                    SELECT ?, ?, FALSE
                                    WHERE NOT EXISTS (
                                        SELECT 1 FROM piezas z
                                        WHERE z.partid = ? AND z.numero_pieza = ?
                                    )
                                    """,
                                    partId,
                                    i,
                                    partId,
                                    i);
                    created += Math.max(n, 0);
                } catch (DataAccessException ignored) {
                    // no-op
                }
            }
        }
        return created;
    }

    /**
     * Siguiente número de pieza aún no cortada por el agente. No inventa filas al marcar:
     * si todas están cortadas, devuelve max+1 (el mark fallará sin crear pieza).
     */
    public Integer nextPieceNumber(long partId) {
        ensurePiezasForPart(partId);
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

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
    }

    private static int numberInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
