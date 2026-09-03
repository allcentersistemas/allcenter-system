package com.allcenter.modulebiesse.repository;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BiesseScanRepository {

    private final JdbcTemplate jdbcTemplate;
    private final BiesseObrasRepository obrasRepository;

    public List<PendingPartResponse> findPendingParts(int limit) {
        String sql =
                """
                SELECT p.partid, p.partcode, p.descripcion, p.cantidad, p.longitud, p.ancho, p.material,
                       o.ordername, o.bookingcode, o.orderid
                FROM partes p
                JOIN ordenes o ON p.orderid = o.orderid
                WHERE p.escaneado = FALSE
                ORDER BY o.fechacreacion DESC, p.partnumber
                LIMIT ?
                """;
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) ->
                            new PendingPartResponse(
                                    rs.getLong("partid"),
                                    rs.getString("partcode"),
                                    rs.getString("descripcion"),
                                    rs.getInt("cantidad"),
                                    rs.getObject("longitud", Double.class),
                                    rs.getObject("ancho", Double.class),
                                    rs.getString("material"),
                                    rs.getString("ordername"),
                                    rs.getString("bookingcode"),
                                    rs.getLong("orderid")),
                    limit);
        } catch (DataAccessException ex) {
            return jdbcTemplate.query(
                    """
                    SELECT p.partid, p.partcode, p.descripcion, p.cantidad, p.longitud, p.ancho, p.material,
                           o.ordername, o.bookingcode, o.orderid
                    FROM partes p
                    JOIN ordenes o ON p.orderid = o.orderid
                    WHERE COALESCE(p.escaneado, FALSE) = FALSE
                    ORDER BY o.fechacreacion DESC, p.partid
                    LIMIT ?
                    """,
                    (rs, rowNum) ->
                            new PendingPartResponse(
                                    rs.getLong("partid"),
                                    rs.getString("partcode"),
                                    rs.getString("descripcion"),
                                    rs.getInt("cantidad"),
                                    rs.getObject("longitud", Double.class),
                                    rs.getObject("ancho", Double.class),
                                    rs.getString("material"),
                                    rs.getString("ordername"),
                                    rs.getString("bookingcode"),
                                    rs.getLong("orderid")),
                    limit);
        }
    }

    public Map<String, Object> findPartById(Long partId) {
        String sql = "SELECT partid, orderid, partcode, cantidad, escaneado FROM partes WHERE partid = ?";
        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Map.of(
                        "partid", rs.getLong("partid"),
                        "orderid", rs.getLong("orderid"),
                        "partcode", rs.getString("partcode"),
                        "cantidad", rs.getInt("cantidad"),
                        "escaneado", rs.getBoolean("escaneado")) : null,
                partId);
    }

    public int updatePartScan(Long employeeId, ScanPartRequest req, int difference, String method) {
        try {
            return jdbcTemplate.update(
                    """
                    UPDATE partes
                    SET escaneado = TRUE,
                        fecha_escaneo = CURRENT_TIMESTAMP,
                        usuario_modificacion = ?,
                        observaciones_escaneo = ?,
                        cantidad_escaneada = ?,
                        diferencia_cantidad = ?,
                        metodo_escaneo = ?,
                        tiempo_escaneo_ms = ?,
                        equipo_escaneo = ?,
                        ubicacion_escaneo = ?,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE partid = ?
                    """,
                    employeeId,
                    req.observations(),
                    req.scannedQuantity(),
                    difference,
                    method,
                    req.scanTimeMs(),
                    req.equipment(),
                    req.location(),
                    req.partId());
        } catch (DataAccessException ex) {
            try {
                return jdbcTemplate.update(
                        """
                        UPDATE partes
                        SET escaneado = TRUE,
                            fecha_escaneo = CURRENT_TIMESTAMP,
                            usuario_modificacion = ?,
                            observaciones_escaneo = ?,
                            cantidad_escaneada = ?,
                            diferencia_cantidad = ?,
                            metodo_escaneo = ?
                        WHERE partid = ?
                        """,
                        employeeId,
                        req.observations(),
                        req.scannedQuantity(),
                        difference,
                        method,
                        req.partId());
            } catch (DataAccessException ignored) {
                return jdbcTemplate.update(
                        """
                        UPDATE partes
                        SET escaneado = TRUE,
                            fecha_escaneo = CURRENT_TIMESTAMP
                        WHERE partid = ?
                        """,
                        req.partId());
            }
        }
    }

    public void insertScanAudit(
            Long employeeId, Long orderId, Long partId, String action, String details, String method, String equipment) {
        insertScanAudit(employeeId, orderId, partId, action, details, method, equipment, null);
    }

    /**
     * Mismo formato que aplicacion_escaneo / servicio_sincronizacion (auditoriaescaneos).
     */
    public void insertScanAudit(
            Long employeeId,
            Long orderId,
            Long partId,
            String action,
            String details,
            String method,
            String equipment,
            Integer tiempoRespuestaMs) {
        String equipo = equipment != null ? equipment : "";
        String metodo = method != null && !method.isBlank() ? method : "AUTOMATICO";

        if (tiempoRespuestaMs != null) {
            try {
                jdbcTemplate.update(
                        """
                        INSERT INTO auditoriaescaneos
                        (usuarioid, orderid, partid, accion, detalles, equipo, metodo, tiempo_respuesta_ms, exito)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                        """,
                        employeeId,
                        orderId,
                        partId,
                        action,
                        details,
                        equipo,
                        metodo,
                        tiempoRespuestaMs);
                return;
            } catch (DataAccessException ex) {
                // Columna tiempo_respuesta_ms ausente en algún despliegue legacy
            }
        }

        jdbcTemplate.update(
                """
                INSERT INTO auditoriaescaneos
                (usuarioid, orderid, partid, accion, detalles, equipo, metodo, exito)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """,
                employeeId,
                orderId,
                partId,
                action,
                details,
                equipo,
                metodo);
    }

    private int markPieceScanned(Long employeeId, Long pieceId) {
        try {
            return jdbcTemplate.update(
                    """
                    UPDATE piezas
                    SET escaneado = TRUE,
                        fecha_escaneo = CURRENT_TIMESTAMP,
                        usuario_modificacion = ?,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE piezaid = ? AND escaneado = FALSE
                    """,
                    employeeId,
                    pieceId);
        } catch (DataAccessException ex) {
            return jdbcTemplate.update(
                    """
                    UPDATE piezas
                    SET escaneado = TRUE,
                        fecha_escaneo = CURRENT_TIMESTAMP
                    WHERE piezaid = ? AND escaneado = FALSE
                    """,
                    pieceId);
        }
    }

    private int markPieceUnscanned(Long employeeId, Long pieceId) {
        try {
            return jdbcTemplate.update(
                    """
                    UPDATE piezas
                    SET escaneado = FALSE,
                        fecha_escaneo = NULL,
                        usuario_modificacion = ?,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE piezaid = ? AND escaneado = TRUE
                    """,
                    employeeId,
                    pieceId);
        } catch (DataAccessException ex) {
            return jdbcTemplate.update(
                    """
                    UPDATE piezas
                    SET escaneado = FALSE,
                        fecha_escaneo = NULL
                    WHERE piezaid = ? AND escaneado = TRUE
                    """,
                    pieceId);
        }
    }

    private void syncPartProgressFromPieces(
            Long employeeId, Long partId, int effectiveScanned, boolean completed, String observations, String equipment) {
        try {
            jdbcTemplate.update(
                    """
                    UPDATE partes
                    SET cantidad_escaneada = ?,
                        diferencia_cantidad = ? - COALESCE(cantidad, 0),
                        escaneado = ?,
                        fecha_escaneo = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE fecha_escaneo END,
                        usuario_modificacion = ?,
                        observaciones_escaneo = COALESCE(?, observaciones_escaneo),
                        equipo_escaneo = COALESCE(?, equipo_escaneo),
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE partid = ?
                    """,
                    effectiveScanned,
                    effectiveScanned,
                    completed,
                    completed,
                    employeeId,
                    observations,
                    equipment,
                    partId);
        } catch (DataAccessException ex) {
            try {
                jdbcTemplate.update(
                        """
                        UPDATE partes
                        SET cantidad_escaneada = ?,
                            diferencia_cantidad = ? - COALESCE(cantidad, 0),
                            escaneado = ?,
                            fecha_escaneo = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE fecha_escaneo END,
                            usuario_modificacion = ?
                        WHERE partid = ?
                        """,
                        effectiveScanned,
                        effectiveScanned,
                        completed,
                        completed,
                        employeeId,
                        partId);
            } catch (DataAccessException ignored) {
                if (completed) {
                    jdbcTemplate.update(
                            """
                            UPDATE partes
                            SET escaneado = TRUE,
                                fecha_escaneo = CURRENT_TIMESTAMP
                            WHERE partid = ?
                            """,
                            partId);
                } else {
                    jdbcTemplate.update(
                            """
                            UPDATE partes
                            SET escaneado = FALSE
                            WHERE partid = ?
                            """,
                            partId);
                }
            }
        }
    }

    public boolean scanPiece(Long employeeId, Long pieceId, String observations, String equipment) {
        String pieceSql =
                """
                SELECT z.piezaid, p.partid, p.orderid, p.partcode, p.cantidad
                FROM piezas z
                JOIN partes p ON z.partid = p.partid
                WHERE z.piezaid = ?
                """;
        Map<String, Object> pieceInfo =
                jdbcTemplate.query(
                        pieceSql,
                        rs -> rs.next() ? Map.of(
                                "partid", rs.getLong("partid"),
                                "orderid", rs.getLong("orderid"),
                                "partcode", rs.getString("partcode"),
                                "cantidad", rs.getInt("cantidad")) : null,
                        pieceId);
        if (pieceInfo == null) {
            return false;
        }

        int updatedPiece = markPieceScanned(employeeId, pieceId);
        if (updatedPiece == 0) {
            return false;
        }

        Long partId = ((Number) pieceInfo.get("partid")).longValue();
        Integer scheduledQty = (Integer) pieceInfo.get("cantidad");
        Integer scannedQty =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM piezas WHERE partid = ? AND escaneado = TRUE",
                        Integer.class,
                        partId);
        int effectiveScanned = scannedQty == null ? 0 : scannedQty;
        boolean completed = scheduledQty != null && scheduledQty > 0 && effectiveScanned >= scheduledQty;

        syncPartProgressFromPieces(employeeId, partId, effectiveScanned, completed, observations, equipment);

        Long orderId = ((Number) pieceInfo.get("orderid")).longValue();
        String detalles =
                "piezaid="
                        + pieceId
                        + " parte="
                        + pieceInfo.get("partcode")
                        + " acumulado="
                        + effectiveScanned
                        + "/"
                        + (scheduledQty != null ? scheduledQty : 0);
        if (equipment != null && equipment.equalsIgnoreCase("PALLET") && observations != null) {
            String obs = observations.trim();
            if (!obs.isEmpty()) {
                detalles += " | " + obs;
                String paleCode = extractPaleCodeFromObservations(obs);
                if (paleCode != null) {
                    detalles += " pale=" + paleCode;
                }
            }
        }
        insertScanAudit(
                employeeId,
                orderId,
                partId,
                "ESCANEAR_PIEZA",
                detalles,
                "AUTOMATICO",
                equipment != null ? equipment : "");
        syncOrderScanProgress(orderId);
        advanceObraEstadoOnScan(orderId, employeeId);
        return true;
    }

    public boolean unscanPiece(Long employeeId, Long pieceId, String observations, String equipment) {
        String pieceSql =
                """
                SELECT z.piezaid, p.partid, p.orderid, p.partcode, p.cantidad
                FROM piezas z
                JOIN partes p ON z.partid = p.partid
                WHERE z.piezaid = ?
                """;
        Map<String, Object> pieceInfo =
                jdbcTemplate.query(
                        pieceSql,
                        rs -> rs.next() ? Map.of(
                                "partid", rs.getLong("partid"),
                                "orderid", rs.getLong("orderid"),
                                "partcode", rs.getString("partcode"),
                                "cantidad", rs.getInt("cantidad")) : null,
                        pieceId);
        if (pieceInfo == null) {
            return false;
        }

        int updatedPiece = markPieceUnscanned(employeeId, pieceId);
        if (updatedPiece == 0) {
            return false;
        }

        Long partId = ((Number) pieceInfo.get("partid")).longValue();
        Integer scheduledQty = (Integer) pieceInfo.get("cantidad");
        Integer scannedQty =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM piezas WHERE partid = ? AND escaneado = TRUE",
                        Integer.class,
                        partId);
        int effectiveScanned = scannedQty == null ? 0 : scannedQty;
        boolean completed = scheduledQty != null && scheduledQty > 0 && effectiveScanned >= scheduledQty;

        syncPartProgressFromPieces(employeeId, partId, effectiveScanned, completed, observations, equipment);

        insertScanAudit(
                employeeId,
                ((Number) pieceInfo.get("orderid")).longValue(),
                partId,
                "DESAESCANEAR_PIEZA",
                "Pieza " + pieceId + " liberada de parte " + pieceInfo.get("partcode"),
                "AUTOMATICO",
                equipment);
        Long orderId = ((Number) pieceInfo.get("orderid")).longValue();
        syncOrderScanProgress(orderId);
        return true;
    }

    public UserScanStatsResponse getUserStats(Long employeeId) {
        long totalScanned = count("SELECT COUNT(*) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE", employeeId);
        long scannedToday =
                count(
                        "SELECT COUNT(*) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE AND DATE(fecha_escaneo) = CURRENT_DATE",
                        employeeId);
        long scannedWeek =
                count(
                        "SELECT COUNT(*) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE AND fecha_escaneo >= DATE_TRUNC('week', CURRENT_DATE)",
                        employeeId);
        long scannedMonth =
                count(
                        "SELECT COUNT(*) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE AND fecha_escaneo >= DATE_TRUNC('month', CURRENT_DATE)",
                        employeeId);
        long totalDifference;
        try {
            totalDifference =
                    count(
                            "SELECT COALESCE(SUM(ABS(diferencia_cantidad)), 0) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE",
                            employeeId);
        } catch (DataAccessException ex) {
            totalDifference = 0L;
        }
        long contributedOrders =
                count(
                        "SELECT COUNT(DISTINCT orderid) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE",
                        employeeId);
        return new UserScanStatsResponse(
                totalScanned, scannedToday, scannedWeek, scannedMonth, totalDifference, contributedOrders);
    }

    /**
     * Recalcula contadores en memoria al leer; no escribe columnas extra en ordenes.
     */
    public void syncOrderScanProgress(Long orderId) {
        // Sin actualizar ordenes: el listado/detalle calcula el avance desde partes.
    }

    public void completeOrderIfNeeded(Long orderId, Long employeeId) {
        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM partes WHERE orderid = ?",
                        Integer.class,
                        orderId);
        Integer done =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM partes WHERE orderid = ? AND escaneado = TRUE",
                        Integer.class,
                        orderId);
        if (total == null || total == 0 || done == null || !done.equals(total)) {
            return;
        }

        boolean changed = obrasRepository.markOrderListoParaEntregar(orderId, employeeId);
        if (!changed) {
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                    UPDATE ordenes
                    SET partes_escaneadas = ?,
                        partes_totales = ?,
                        porcentaje_completado = 100,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE orderid = ?
                    """,
                    done,
                    total,
                    orderId);
        } catch (Exception ignored) {
            // Esquema legacy sin columnas denormalizadas en ordenes
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO finalizacionordenes
                    (orderid, usuarioid, partesescaneadas, partestotales, metodofinalizacion, fechafinalizacion)
                    VALUES (?, ?, ?, ?, 'AUTOMATICA', CURRENT_TIMESTAMP)
                    """,
                    orderId,
                    employeeId,
                    done,
                    total);
        } catch (Exception ignored) {
            // Tabla opcional en algunos despliegues
        }
    }

    /**
     * Primer escaneo → DESPACHO; 100% → LISTO_PARA_ENTREGAR.
     * Usa piezas si existen; si no, partes.
     */
    public void advanceObraEstadoOnScan(Long orderId, Long employeeId) {
        if (orderId == null) {
            return;
        }
        boolean complete = isOrderScanComplete(orderId);
        if (complete) {
            completeOrderIfNeeded(orderId, employeeId);
            // Si el complete por partes no aplica (solo piezas), forzar listo
            if (!isStoredListoOrEntregado(orderId)) {
                obrasRepository.markOrderListoParaEntregar(orderId, employeeId);
            }
            return;
        }
        if (hasAnyScanProgress(orderId)) {
            obrasRepository.markOrderDespacho(
                    orderId, employeeId != null ? "emp:" + employeeId : "android-scan");
        }
    }

    private boolean isStoredListoOrEntregado(Long orderId) {
        Map<String, Object> order = obrasRepository.findOrderById(orderId);
        if (order == null) {
            return false;
        }
        String e = BiesseObrasRepository.normalizeEstadoForUi(String.valueOf(order.get("estado_escaneo")));
        return BiesseObrasRepository.ESTADO_LISTO.equals(e)
                || BiesseObrasRepository.ESTADO_ENTREGADO.equals(e);
    }

    private boolean hasAnyScanProgress(Long orderId) {
        try {
            Integer piezas =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM piezas z
                            JOIN partes p ON p.partid = z.partid
                            WHERE p.orderid = ? AND COALESCE(z.escaneado, FALSE)
                            """,
                            Integer.class,
                            orderId);
            if (piezas != null && piezas > 0) {
                return true;
            }
        } catch (DataAccessException ignored) {
            // sin tabla piezas
        }
        Integer partes =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM partes WHERE orderid = ? AND escaneado = TRUE",
                        Integer.class,
                        orderId);
        return partes != null && partes > 0;
    }

    /**
     * Completa si todas las piezas están escaneadas; si no hay filas de piezas, usa partes.
     */
    public boolean isOrderScanComplete(Long orderId) {
        if (orderId == null) {
            return false;
        }
        try {
            Integer totalPiezas =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM piezas z
                            JOIN partes p ON p.partid = z.partid
                            WHERE p.orderid = ?
                            """,
                            Integer.class,
                            orderId);
            if (totalPiezas != null && totalPiezas > 0) {
                Integer donePiezas =
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM piezas z
                                JOIN partes p ON p.partid = z.partid
                                WHERE p.orderid = ? AND z.escaneado = TRUE
                                """,
                                Integer.class,
                                orderId);
                return donePiezas != null && donePiezas.equals(totalPiezas);
            }
        } catch (Exception ignored) {
            // Esquema sin piezas
        }
        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM partes WHERE orderid = ?", Integer.class, orderId);
        Integer done =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM partes WHERE orderid = ? AND escaneado = TRUE",
                        Integer.class,
                        orderId);
        return total != null && total > 0 && done != null && done.equals(total);
    }

    public List<Map<String, Object>> findScannedPartsByUser(
            Long employeeId, String fromDate, String toDate, int limit) {
        try {
            return findScannedPartsByUserExtended(employeeId, fromDate, toDate, limit);
        } catch (DataAccessException ex) {
            return findScannedPartsByUserBasic(employeeId, fromDate, toDate, limit);
        }
    }

    private List<Map<String, Object>> findScannedPartsByUserExtended(
            Long employeeId, String fromDate, String toDate, int limit) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT p.partid, p.partcode, p.descripcion, p.cantidad, p.cantidad_escaneada,
                               p.diferencia_cantidad, p.fecha_escaneo, p.observaciones_escaneo, p.metodo_escaneo,
                               p.tiempo_escaneo_ms, o.orderid, o.ordername, o.bookingcode
                        FROM partes p
                        JOIN ordenes o ON p.orderid = o.orderid
                        WHERE p.usuario_modificacion = ? AND p.escaneado = TRUE
                        """);
        return queryScannedPartsByUser(sql, employeeId, fromDate, toDate, limit, true);
    }

    private List<Map<String, Object>> findScannedPartsByUserBasic(
            Long employeeId, String fromDate, String toDate, int limit) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT p.partid, p.partcode, p.descripcion, p.cantidad, p.fecha_escaneo,
                               o.orderid, o.ordername, o.bookingcode
                        FROM partes p
                        JOIN ordenes o ON p.orderid = o.orderid
                        WHERE p.escaneado = TRUE
                        """);
        return queryScannedPartsByUser(sql, employeeId, fromDate, toDate, limit, false);
    }

    private List<Map<String, Object>> queryScannedPartsByUser(
            StringBuilder sql,
            Long employeeId,
            String fromDate,
            String toDate,
            int limit,
            boolean filterByEmployee) {
        if (fromDate != null && !fromDate.isBlank()) {
            sql.append(" AND DATE(p.fecha_escaneo) >= ? ");
        }
        if (toDate != null && !toDate.isBlank()) {
            sql.append(" AND DATE(p.fecha_escaneo) <= ? ");
        }
        sql.append(" ORDER BY p.fecha_escaneo DESC LIMIT ? ");

        java.util.List<Object> args = new java.util.ArrayList<>();
        if (filterByEmployee) {
            args.add(employeeId);
        }
        if (fromDate != null && !fromDate.isBlank()) {
            args.add(fromDate);
        }
        if (toDate != null && !toDate.isBlank()) {
            args.add(toDate);
        }
        args.add(limit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> findOrders(
            Long orderId, String state, String query, String fromDate, String toDate, int limit, int offset) {
        // Solo columnas base de ordenes; avance de escaneo calculado desde partes/piezas (sin migrar BD).
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.fechacreacion,
                               st.estado_escaneo,
                               NULL::timestamp AS fecha_completado,
                               st.partes_escaneadas,
                               st.partes_totales,
                               st.porcentaje_completado,
                               COALESCE(pz.total_piezas, 0) AS total_piezas,
                               CASE
                                    WHEN st.partes_totales > 0
                                         AND st.partes_escaneadas >= st.partes_totales
                                        THEN COALESCE(pz.total_piezas, 0)
                                    ELSE COALESCE(pz.piezas_escaneadas, 0)
                               END AS piezas_escaneadas
                        FROM ordenes o
                        LEFT JOIN LATERAL (
                            SELECT COUNT(*)::int AS partes_totales,
                                   COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE))::int AS partes_escaneadas,
                                   CASE WHEN COUNT(*) = 0 THEN 0::numeric
                                        ELSE ROUND(
                                            100.0 * COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) / COUNT(*), 2)
                                   END AS porcentaje_completado,
                                   CASE
                                        WHEN UPPER(COALESCE(o.estado_escaneo, '')) IN ('ENTREGADO')
                                            THEN 'ENTREGADO'
                                        WHEN UPPER(COALESCE(o.estado_escaneo, '')) IN ('LISTO_PARA_ENTREGAR', 'COMPLETADA', 'COMPLETADO')
                                             OR (COUNT(*) > 0
                                                 AND COUNT(*) FILTER (WHERE NOT COALESCE(p.escaneado, FALSE)) = 0)
                                            THEN 'LISTO_PARA_ENTREGAR'
                                        WHEN UPPER(COALESCE(o.estado_escaneo, '')) = 'DESPACHO'
                                             OR COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) > 0
                                            THEN 'DESPACHO'
                                        WHEN UPPER(COALESCE(o.estado_escaneo, '')) IN ('PRODUCCION', 'OPTIMIZADO')
                                            THEN UPPER(o.estado_escaneo)
                                        ELSE COALESCE(NULLIF(UPPER(TRIM(o.estado_escaneo)), ''), 'PENDIENTE')
                                   END AS estado_escaneo
                            FROM partes p
                            WHERE p.orderid = o.orderid
                        ) st ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT COUNT(*)::int AS total_piezas,
                                   COUNT(*) FILTER (WHERE COALESCE(z.escaneado, FALSE))::int AS piezas_escaneadas
                            FROM piezas z
                            INNER JOIN partes p ON p.partid = z.partid
                            WHERE p.orderid = o.orderid
                              AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                        ) pz ON TRUE
                        """);
        try {
            return queryOrders(sql, orderId, state, query, fromDate, toDate, limit, offset);
        } catch (DataAccessException ex) {
            StringBuilder sqlWithoutPiezas =
                    new StringBuilder(
                            """
                            SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.fechacreacion,
                                   st.estado_escaneo,
                                   NULL::timestamp AS fecha_completado,
                                   st.partes_escaneadas,
                                   st.partes_totales,
                                   st.porcentaje_completado,
                                   0 AS total_piezas,
                                   0 AS piezas_escaneadas
                            FROM ordenes o
                            LEFT JOIN LATERAL (
                                SELECT COUNT(*)::int AS partes_totales,
                                       COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE))::int AS partes_escaneadas,
                                       CASE WHEN COUNT(*) = 0 THEN 0::numeric
                                            ELSE ROUND(
                                                100.0 * COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) / COUNT(*), 2)
                                       END AS porcentaje_completado,
                                       CASE
                                            WHEN UPPER(COALESCE(o.estado_escaneo, '')) IN ('ENTREGADO')
                                                THEN 'ENTREGADO'
                                            WHEN UPPER(COALESCE(o.estado_escaneo, '')) IN ('LISTO_PARA_ENTREGAR', 'COMPLETADA', 'COMPLETADO')
                                                 OR (COUNT(*) > 0
                                                     AND COUNT(*) FILTER (WHERE NOT COALESCE(p.escaneado, FALSE)) = 0)
                                                THEN 'LISTO_PARA_ENTREGAR'
                                            WHEN UPPER(COALESCE(o.estado_escaneo, '')) = 'DESPACHO'
                                                 OR COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) > 0
                                                THEN 'DESPACHO'
                                            WHEN UPPER(COALESCE(o.estado_escaneo, '')) IN ('PRODUCCION', 'OPTIMIZADO')
                                                THEN UPPER(o.estado_escaneo)
                                            ELSE COALESCE(NULLIF(UPPER(TRIM(o.estado_escaneo)), ''), 'PENDIENTE')
                                       END AS estado_escaneo
                                FROM partes p
                                WHERE p.orderid = o.orderid
                            ) st ON TRUE
                            """);
            return queryOrders(sqlWithoutPiezas, orderId, state, query, fromDate, toDate, limit, offset);
        }
    }

    private List<Map<String, Object>> queryOrders(
            StringBuilder sql,
            Long orderId,
            String state,
            String query,
            String fromDate,
            String toDate,
            int limit,
            int offset) {
        boolean hasOrderId = orderId != null;
        boolean hasState = state != null && !state.isBlank();
        boolean hasQuery = query != null && !query.isBlank();
        boolean hasFromDate = fromDate != null && !fromDate.isBlank();
        boolean hasToDate = toDate != null && !toDate.isBlank();

        if (hasOrderId || hasState || hasQuery || hasFromDate || hasToDate) {
            sql.append(" WHERE 1=1 ");
            java.util.List<Object> args = new java.util.ArrayList<>();
            if (hasOrderId) {
                sql.append(" AND o.orderid = ? ");
                args.add(orderId);
            }
            if (hasState) {
                sql.append(" AND st.estado_escaneo = ? ");
                args.add(normalizeOrderScanState(state));
            }
            if (hasQuery) {
                // Varios tokens → TODOS deben aparecer (así K5_IZQ (1)4 no trae las 16 de S14783).
                // Un solo token (p.ej. S14783) → sigue listando toda la OP.
                String[] tokens = searchTokens(query);
                sql.append(" AND ( ");
                if (tokens.length == 0) {
                    sql.append(" TRUE ");
                } else {
                    appendTokenAndSearch(
                            sql,
                            args,
                            tokens,
                            "o.ordername",
                            "COALESCE(o.bookingcode, '')",
                            "COALESCE(o.op_codigo, '')",
                            true);
                }
                sql.append(" ) ");
            }
            if (hasFromDate) {
                sql.append(" AND DATE(o.fechacreacion) >= CAST(? AS DATE) ");
                args.add(fromDate.trim());
            }
            if (hasToDate) {
                sql.append(" AND DATE(o.fechacreacion) <= CAST(? AS DATE) ");
                args.add(toDate.trim());
            }
            sql.append(" ORDER BY o.fechacreacion DESC LIMIT ? OFFSET ? ");
            args.add(limit);
            args.add(offset);
            return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        }
        sql.append(" ORDER BY o.fechacreacion DESC LIMIT ? OFFSET ? ");
        return jdbcTemplate.queryForList(sql.toString(), limit, offset);
    }

    /**
     * Expresión SQL: deja solo alfanuméricos separados por espacio.
     * Así {@code K1_DER(x25)} y {@code K1 DER x25} coinciden.
     */
    private static String sqlSearchNorm(String expr) {
        return "trim(regexp_replace(" + expr + ", '[^[:alnum:]]+', ' ', 'g'))";
    }

    /** Tokens de búsqueda (min 1 char); ignora vacíos tras normalizar _/(). */
    private static String[] searchTokens(String query) {
        if (query == null || query.isBlank()) {
            return new String[0];
        }
        String norm =
                query.trim()
                        .replace('_', ' ')
                        .replace('(', ' ')
                        .replace(')', ' ')
                        .replaceAll("[^A-Za-z0-9]+", " ")
                        .trim();
        if (norm.isEmpty()) {
            return new String[0];
        }
        return java.util.Arrays.stream(norm.split("\\s+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * AND de tokens por palabra completa (8MM ≠ 18MM; (1)4 ≠ (1)3).
     */
    private static void appendTokenAndSearch(
            StringBuilder sql,
            java.util.List<Object> args,
            String[] tokens,
            String nameExpr,
            String bookingExpr,
            String opExpr,
            boolean includeOrderId) {
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(" ( ");
            // Token exacto en el array de palabras (no substring: 8MM no está en 18MM).
            sql.append("lower(?) = ANY(string_to_array(lower(")
                    .append(sqlSearchNorm(nameExpr))
                    .append("), ' '))");
            args.add(tokens[i]);
            sql.append(" OR lower(?) = ANY(string_to_array(lower(")
                    .append(sqlSearchNorm(bookingExpr))
                    .append("), ' '))");
            args.add(tokens[i]);
            sql.append(" OR lower(?) = ANY(string_to_array(lower(")
                    .append(sqlSearchNorm(opExpr))
                    .append("), ' '))");
            args.add(tokens[i]);
            if (includeOrderId) {
                sql.append(" OR CAST(o.orderid AS TEXT) = ?");
                args.add(tokens[i]);
            }
            sql.append(" ) ");
        }
    }

    private static String normalizeOrderScanState(String state) {
        if (state == null) {
            return "";
        }
        String normalized = state.trim().toUpperCase();
        if ("COMPLETADO".equals(normalized) || "COMPLETADA".equals(normalized)) {
            return BiesseObrasRepository.ESTADO_LISTO;
        }
        if ("EN_PROCESO".equals(normalized)) {
            return BiesseObrasRepository.ESTADO_DESPACHO;
        }
        return normalized;
    }

    public int updateOrderObservaciones(Long orderId, String observaciones) {
        try {
            return jdbcTemplate.update(
                    """
                    UPDATE ordenes
                    SET observaciones = ?, fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE orderid = ?
                    """,
                    observaciones,
                    orderId);
        } catch (Exception ex) {
            try {
                return jdbcTemplate.update(
                        "UPDATE ordenes SET observaciones = ? WHERE orderid = ?", observaciones, orderId);
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    public int countPaleDetailsByOrderId(Long orderId) {
        try {
            Integer count =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM paledetalle WHERE orderid = ?", Integer.class, orderId);
            return count != null ? count : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    public boolean pieceExists(Long pieceId) {
        return getPieceScanState(pieceId) != null;
    }

    public boolean isPieceScanned(Long pieceId) {
        PieceScanState state = getPieceScanState(pieceId);
        return state != null && state.scanned();
    }

    /** Una sola consulta para existencia y estado de escaneo. */
    public PieceScanState getPieceScanState(Long pieceId) {
        if (pieceId == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    "SELECT piezaid, escaneado FROM piezas WHERE piezaid = ?",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        return new PieceScanState(rs.getLong("piezaid"), rs.getBoolean("escaneado"));
                    },
                    pieceId);
        } catch (Exception ex) {
            return null;
        }
    }

    public record PieceScanState(long pieceId, boolean scanned) {}

    /**
     * Si la pieza figura en un palé (tabla compartida con module-system cuando existe en la misma BD).
     */
    public Map<String, Object> findPaleAssignmentByPieceId(Long pieceId) {
        if (pieceId == null) {
            return null;
        }
        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT p.codigo, p.paleeid AS pale_id, p.estado
                            FROM paledetalle pd
                            JOIN pale p ON p.paleeid = pd.paleenvioid
                            WHERE pd.piezaid = ?
                            LIMIT 1
                            """,
                            pieceId);
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean deleteOrderById(Long orderId) {
        try {
            jdbcTemplate.update("DELETE FROM synclogs WHERE orden_id = ?", orderId);
        } catch (Exception ignored) {
            // synclogs puede no existir en todos los despliegues
        }
        return jdbcTemplate.update("DELETE FROM ordenes WHERE orderid = ?", orderId) > 0;
    }

    public List<Map<String, Object>> findScanAudit(
            Long orderId,
            Long partId,
            String orderQ,
            String partQ,
            String action,
            int limit,
            int offset) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT a.auditoriaid, a.usuarioid, a.orderid, a.partid, a.accion, a.detalles, a.equipo, a.metodo, a.exito, a.fecha,
                               o.ordername, p.partcode
                        FROM auditoriaescaneos a
                        LEFT JOIN ordenes o ON o.orderid = a.orderid
                        LEFT JOIN partes p ON p.partid = a.partid
                        WHERE 1=1
                        """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        appendOrderAuditFilter(sql, args, orderId, orderQ);
        appendPartAuditFilter(sql, args, partId, partQ);
        if (action != null && !action.isBlank()) {
            sql.append(" AND UPPER(a.accion) = UPPER(?) ");
            args.add(action.trim());
        }
        sql.append(" ORDER BY a.fecha DESC LIMIT ? OFFSET ? ");
        args.add(limit);
        args.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        enrichScanAuditRows(rows);
        return rows;
    }

    private static void appendOrderAuditFilter(
            StringBuilder sql, java.util.List<Object> args, Long orderId, String orderQ) {
        if (orderId != null) {
            sql.append(" AND a.orderid = ? ");
            args.add(orderId);
            return;
        }
        if (orderQ == null || orderQ.isBlank()) {
            return;
        }
        String token = orderQ.trim();
        if (token.matches("\\d+")) {
            sql.append(" AND a.orderid = ? ");
            args.add(Long.parseLong(token));
            return;
        }
        sql.append(
                """
                 AND (
                   strpos(lower(COALESCE(o.ordername, '')), lower(?)) > 0
                   OR strpos(lower(COALESCE(o.bookingcode, '')), lower(?)) > 0
                 )
                """);
        args.add(token);
        args.add(token);
    }

    private static void appendPartAuditFilter(
            StringBuilder sql, java.util.List<Object> args, Long partId, String partQ) {
        if (partId != null) {
            sql.append(" AND a.partid = ? ");
            args.add(partId);
            return;
        }
        if (partQ == null || partQ.isBlank()) {
            return;
        }
        String token = partQ.trim();
        if (token.matches("\\d+")) {
            sql.append(" AND a.partid = ? ");
            args.add(Long.parseLong(token));
            return;
        }
        String normalized = token.toUpperCase(java.util.Locale.ROOT);
        String withP =
                normalized.startsWith("P") ? normalized : "P" + normalized.replaceAll("^P", "");
        sql.append(
                """
                 AND (
                   UPPER(TRIM(COALESCE(p.partcode, ''))) = UPPER(TRIM(?))
                   OR UPPER(TRIM(COALESCE(p.partcode, ''))) = UPPER(TRIM(?))
                   OR strpos(lower(COALESCE(p.partcode, '')), lower(?)) > 0
                   OR CAST(p.partnumber AS TEXT) = ?
                 )
                """);
        args.add(token);
        args.add(withP);
        args.add(token);
        args.add(token.replaceAll("\\D", "").isEmpty() ? token : token.replaceAll("\\D", ""));
    }

    private void enrichScanAuditRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.Set<Long> pieceIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Long piezaId = extractPiezaIdFromAuditRow(row);
            if (piezaId != null) {
                row.put("piezaid", piezaId);
                pieceIds.add(piezaId);
            }
        }
        if (pieceIds.isEmpty()) {
            return;
        }
        String placeholders = pieceIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql =
                "SELECT piezaid, numero_pieza FROM piezas WHERE piezaid IN (" + placeholders + ")";
        List<Map<String, Object>> pieces =
                jdbcTemplate.queryForList(sql, pieceIds.toArray());
        Map<Long, Integer> numeroById = new java.util.HashMap<>();
        for (Map<String, Object> piece : pieces) {
            Object idRaw = piece.get("piezaid");
            Object numRaw = piece.get("numero_pieza");
            if (idRaw instanceof Number n && numRaw instanceof Number num) {
                numeroById.put(n.longValue(), num.intValue());
            }
        }
        for (Map<String, Object> row : rows) {
            Object idRaw = row.get("piezaid");
            if (idRaw instanceof Number n) {
                Integer numero = numeroById.get(n.longValue());
                if (numero != null) {
                    row.put("numero_pieza", numero);
                }
            }
        }
    }

    private static Long extractPiezaIdFromAuditRow(Map<String, Object> row) {
        Object existing = row.get("piezaid");
        if (existing instanceof Number n) {
            return n.longValue();
        }
        String detalles = row.get("detalles") == null ? "" : String.valueOf(row.get("detalles"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("piezaid\\s*=\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(detalles);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** @deprecated use overload with orderQ/partQ */
    public List<Map<String, Object>> findScanAudit(Long orderId, Long partId, String action, int limit, int offset) {
        return findScanAudit(orderId, partId, null, null, action, limit, offset);
    }

    public Map<String, Object> findOrderById(Long orderId) {
        List<Map<String, Object>> rows;
        try {
            rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT orderid, ordername, bookingcode, fechacreacion, estado_escaneo, op_codigo
                            FROM ordenes
                            WHERE orderid = ?
                            """,
                            orderId);
        } catch (DataAccessException ex) {
            rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT orderid, ordername, bookingcode, fechacreacion, estado_escaneo
                            FROM ordenes
                            WHERE orderid = ?
                            """,
                            orderId);
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> order = new java.util.HashMap<>(rows.getFirst());
        enrichOrderScanFields(order, orderId);
        return order;
    }

    private void enrichOrderScanFields(Map<String, Object> order, Long orderId) {
        Map<String, Object> stats = findOrderPartStats(orderId);
        long total = ((Number) stats.getOrDefault("total", 0)).longValue();
        long escaneadas = ((Number) stats.getOrDefault("escaneadas", 0)).longValue();
        double pct = total == 0 ? 0.0 : Math.min(100.0, (escaneadas * 100.0) / total);
        String stored =
                order.get("estado_escaneo") != null
                        ? String.valueOf(order.get("estado_escaneo")).trim().toUpperCase()
                        : "";
        String estado;
        if ("ENTREGADO".equals(stored)) {
            estado = BiesseObrasRepository.ESTADO_ENTREGADO;
        } else if ((total > 0 && escaneadas >= total)
                || "LISTO_PARA_ENTREGAR".equals(stored)
                || "COMPLETADA".equals(stored)
                || "COMPLETADO".equals(stored)) {
            estado = BiesseObrasRepository.ESTADO_LISTO;
        } else if (escaneadas > 0 || "DESPACHO".equals(stored)) {
            estado = BiesseObrasRepository.ESTADO_DESPACHO;
        } else if ("PRODUCCION".equals(stored) || "OPTIMIZADO".equals(stored)) {
            estado = stored;
        } else if (!stored.isBlank()) {
            estado = BiesseObrasRepository.normalizeEstadoForUi(stored);
        } else {
            estado = "PENDIENTE";
        }
        order.put("estado_escaneo", estado);
        order.put("fecha_completado", null);
        order.put("partes_escaneadas", escaneadas);
        order.put("partes_totales", total);
        order.put("porcentaje_completado", pct);
        order.put("observaciones", null);
        try {
            String observaciones =
                    jdbcTemplate.queryForObject(
                            "SELECT observaciones FROM ordenes WHERE orderid = ?", String.class, orderId);
            order.put("observaciones", observaciones);
        } catch (Exception ignored) {
            // Columna opcional en esquemas legacy
        }
    }

    public Map<String, Object> resolvePieceByCompositeCode(String orderName, String partToken, String pieceToken) {
        Map<String, Object> direct = findPieceInOrderContext(null, orderName, partToken, pieceToken);
        if (direct != null) {
            return direct;
        }
        return findPieceByFuzzyOrderToken(orderName, partToken, pieceToken);
    }

    public Map<String, Object> resolvePieceFromScanCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String code = normalizeScanCode(rawCode);
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^(.*)-P?(\\d+)-(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(code);
        if (!matcher.matches()) {
            return null;
        }
        return resolvePieceByCompositeCode(matcher.group(1).trim(), matcher.group(2).trim(), matcher.group(3).trim());
    }

    private Map<String, Object> findPieceInOrderContext(
            Long orderId, String orderToken, String partToken, String pieceToken) {
        if (partToken == null || partToken.isBlank() || pieceToken == null || pieceToken.isBlank()) {
            return null;
        }
        if (orderId == null && (orderToken == null || orderToken.isBlank())) {
            return null;
        }
        String trimmedPart = partToken.trim();
        String trimmedPiece = pieceToken.trim();
        String partWithP = trimmedPart.toUpperCase(java.util.Locale.ROOT).startsWith("P")
                ? trimmedPart
                : "P" + trimmedPart;
        Integer partNumber = parsePositiveInt(trimmedPart);

        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT z.piezaid, z.numero_pieza, p.partid, p.partnumber, p.partcode, p.orderid, o.ordername, o.bookingcode,
                               p.longitud AS longitud_parte, p.ancho AS ancho_parte
                        FROM piezas z
                        JOIN partes p ON p.partid = z.partid
                        JOIN ordenes o ON o.orderid = p.orderid
                        WHERE CAST(z.numero_pieza AS TEXT) = ?
                        """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(trimmedPiece);

        if (orderId != null) {
            sql.append(" AND o.orderid = ? ");
            args.add(orderId);
        } else {
            sql.append(
                    """
                     AND (
                          UPPER(TRIM(o.ordername)) = UPPER(TRIM(?))
                          OR (o.bookingcode IS NOT NULL AND UPPER(TRIM(o.bookingcode)) = UPPER(TRIM(?)))
                          OR UPPER(REPLACE(o.ordername, ' ', '')) = UPPER(REPLACE(?, ' ', ''))
                          OR (o.bookingcode IS NOT NULL AND UPPER(REPLACE(o.bookingcode, ' ', '')) = UPPER(REPLACE(?, ' ', '')))
                         )
                    """);
            String token = orderToken.trim();
            args.add(token);
            args.add(token);
            args.add(token);
            args.add(token);
        }

        sql.append(
                """
                 AND (
                      CAST(p.partnumber AS TEXT) = ?
                      OR CAST(p.partid AS TEXT) = ?
                      OR UPPER(TRIM(p.partcode)) = UPPER(TRIM(?))
                      OR UPPER(TRIM(p.partcode)) = UPPER(TRIM(?))
                      OR UPPER(REPLACE(p.partcode, ' ', '')) = UPPER(REPLACE(?, ' ', ''))
                """);
        args.add(trimmedPart);
        args.add(trimmedPart);
        args.add(trimmedPart);
        args.add(partWithP);
        args.add(partWithP);

        if (partNumber != null) {
            sql.append(" OR p.partnumber = ? ");
            args.add(partNumber);
        }
        sql.append(" ) ORDER BY o.fechacreacion DESC LIMIT 1 ");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Una consulta con coincidencia flexible de nombre de orden (sin cargar cientos de órdenes en memoria). */
    private Map<String, Object> findPieceByFuzzyOrderToken(String orderToken, String partToken, String pieceToken) {
        if (orderToken == null || orderToken.isBlank()) {
            return null;
        }
        String token = orderToken.trim();
        String tokenNoSpaces = token.replaceAll("\\s+", "");
        String trimmedPart = partToken.trim();
        String partWithP = trimmedPart.toUpperCase(java.util.Locale.ROOT).startsWith("P")
                ? trimmedPart
                : "P" + trimmedPart;
        Integer partNumber = parsePositiveInt(trimmedPart);
        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT z.piezaid, z.numero_pieza, p.partid, p.partnumber, p.partcode, p.orderid, o.ordername, o.bookingcode,
                                   p.longitud AS longitud_parte, p.ancho AS ancho_parte
                            FROM piezas z
                            JOIN partes p ON p.partid = z.partid
                            JOIN ordenes o ON o.orderid = p.orderid
                            WHERE CAST(z.numero_pieza AS TEXT) = ?
                              AND (
                                   CAST(p.partnumber AS TEXT) = ?
                                   OR CAST(p.partid AS TEXT) = ?
                                   OR UPPER(TRIM(p.partcode)) = UPPER(TRIM(?))
                                   OR UPPER(TRIM(p.partcode)) = UPPER(TRIM(?))
                                   OR (? IS NOT NULL AND p.partnumber = ?)
                                  )
                              AND (
                                   UPPER(TRIM(o.ordername)) = UPPER(TRIM(?))
                                   OR (o.bookingcode IS NOT NULL AND UPPER(TRIM(o.bookingcode)) = UPPER(TRIM(?)))
                                   OR UPPER(REPLACE(o.ordername, ' ', '')) = UPPER(?)
                                   OR (o.bookingcode IS NOT NULL AND UPPER(REPLACE(o.bookingcode, ' ', '')) = UPPER(?))
                                   OR UPPER(REPLACE(o.ordername, ' ', '')) LIKE UPPER(?) || '%'
                                   OR UPPER(?) LIKE UPPER(REPLACE(o.ordername, ' ', '')) || '%'
                                  )
                            ORDER BY o.fechacreacion DESC
                            LIMIT 1
                            """,
                            pieceToken.trim(),
                            trimmedPart,
                            trimmedPart,
                            trimmedPart,
                            partWithP,
                            partNumber,
                            partNumber,
                            token,
                            token,
                            tokenNoSpaces,
                            tokenNoSpaces,
                            tokenNoSpaces,
                            tokenNoSpaces);
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeScanCode(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            switch (c) {
                case '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212', '\uFE63', '\uFF0D' -> out.append('-');
                default -> {
                    if (!Character.isISOControl(c) || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString().trim();
    }

    private static Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Map<String, Object> findOrderByNameToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE UPPER(TRIM(ordername)) = UPPER(TRIM(?))
                           OR (bookingcode IS NOT NULL AND UPPER(TRIM(bookingcode)) = UPPER(TRIM(?)))
                        ORDER BY fechacreacion DESC
                        LIMIT 1
                        """,
                        token.trim(),
                        token.trim());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Long detectOrderIdFromCode(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String upper = normalized.trim().toUpperCase();
        int partIdx = upper.indexOf("-P");
        String tokenBeforePart = partIdx >= 0 ? upper.substring(0, partIdx).trim() : upper;
        String tokenNoSpaces = tokenBeforePart.replaceAll("\\s+", "");

        Map<String, Object> exact = findOrderByNameToken(tokenBeforePart);
        if (exact != null) {
            return ((Number) exact.get("orderid")).longValue();
        }

        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT orderid
                            FROM ordenes
                            WHERE UPPER(REPLACE(ordername, ' ', '')) = UPPER(?)
                               OR (bookingcode IS NOT NULL AND UPPER(REPLACE(bookingcode, ' ', '')) = UPPER(?))
                               OR UPPER(REPLACE(ordername, ' ', '')) LIKE UPPER(?) || '%'
                               OR UPPER(?) LIKE UPPER(REPLACE(ordername, ' ', '')) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 1
                            """,
                            tokenNoSpaces,
                            tokenNoSpaces,
                            tokenNoSpaces,
                            tokenNoSpaces);
            if (!rows.isEmpty()) {
                return ((Number) rows.getFirst().get("orderid")).longValue();
            }
        } catch (Exception ignored) {
            // fallback silencioso
        }
        return null;
    }

    public Map<String, Object> findPartByOrderAndToken(Long orderId, String partToken) {
        if (orderId == null || partToken == null || partToken.isBlank()) {
            return null;
        }
        String trimmedPart = partToken.trim();
        String partWithP = trimmedPart.toUpperCase(java.util.Locale.ROOT).startsWith("P")
                ? trimmedPart
                : "P" + trimmedPart;
        Integer partNumber = parsePositiveInt(trimmedPart);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT partid, orderid, partcode, partnumber, cantidad, escaneado
                        FROM partes
                        WHERE orderid = ?
                          AND (
                               CAST(partnumber AS TEXT) = ?
                               OR CAST(partid AS TEXT) = ?
                               OR UPPER(TRIM(partcode)) = UPPER(TRIM(?))
                               OR UPPER(TRIM(partcode)) = UPPER(TRIM(?))
                               OR UPPER(REPLACE(partcode, ' ', '')) = UPPER(REPLACE(?, ' ', ''))
                        """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(orderId);
        args.add(trimmedPart);
        args.add(trimmedPart);
        args.add(trimmedPart);
        args.add(partWithP);
        args.add(partWithP);
        if (partNumber != null) {
            sql.append(" OR partnumber = ? ");
            args.add(partNumber);
        }
        sql.append(" ) LIMIT 1 ");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> findPieceByOrderPartAndNumber(Long orderId, String partToken, int pieceNumber) {
        if (orderId == null || partToken == null || partToken.isBlank() || pieceNumber < 1) {
            return null;
        }
        return findPieceInOrderContext(orderId, null, partToken, String.valueOf(pieceNumber));
    }

    public Map<String, Object> findPieceById(Long pieceId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT z.piezaid, z.numero_pieza, p.partid, p.partnumber, p.partcode, p.cantidad AS cantidad_parte,
                               p.orderid, o.ordername, o.bookingcode,
                               p.descripcion AS part_descripcion, p.descripcion1 AS part_descripcion1,
                               p.longitud AS longitud_parte, p.ancho AS ancho_parte
                        FROM piezas z
                        JOIN partes p ON p.partid = z.partid
                        JOIN ordenes o ON o.orderid = p.orderid
                        WHERE z.piezaid = ?
                        LIMIT 1
                        """,
                        pieceId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> findOrderPartStats(Long orderId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN escaneado = TRUE THEN 1 ELSE 0 END) AS escaneadas,
                       SUM(CASE WHEN escaneado = FALSE THEN 1 ELSE 0 END) AS pendientes
                FROM partes
                WHERE orderid = ?
                """,
                orderId);
    }

    public List<Map<String, Object>> findOrderParts(Long orderId) {
        try {
            return jdbcTemplate.queryForList(
                    """
                    SELECT partid, partcode, descripcion, descripcion1, cantidad, escaneado, cantidad_escaneada,
                           diferencia_cantidad, fecha_escaneo, metodo_escaneo, partnumber,
                           longitud, ancho, material,
                           matedgeup, matedgelo, matedgel, matedger
                    FROM partes
                    WHERE orderid = ?
                    ORDER BY partnumber
                    """,
                    orderId);
        } catch (DataAccessException ex) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT partid, partcode, descripcion, descripcion1, cantidad, escaneado, partnumber,
                           longitud, ancho, material,
                           matedgeup, matedgelo, matedgel, matedger
                    FROM partes
                    WHERE orderid = ?
                    ORDER BY partid
                    """,
                    orderId);
        }
    }

    /** Máximo numero_pieza ya marcada cortada para una parte (sync contador agente). */
    public int maxCortadaPieceNumber(long partId) {
        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT COALESCE(MAX(numero_pieza), 0) AS n
                            FROM piezas
                            WHERE partid = ?
                              AND COALESCE(cortada, FALSE) = TRUE
                            """,
                            partId);
            if (rows.isEmpty() || rows.getFirst().get("n") == null) {
                return 0;
            }
            return ((Number) rows.getFirst().get("n")).intValue();
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    public List<Map<String, Object>> findOrderPieces(Long orderId) {
        try {
            return jdbcTemplate.queryForList(
                    """
                    SELECT z.piezaid, z.partid, p.orderid, z.numero_pieza, z.escaneado, z.fecha_escaneo,
                           COALESCE(z.cortada, FALSE) AS cortada, z.cortada_at, z.cortada_por,
                           COALESCE(z.corte_error, FALSE) AS corte_error, z.corte_error_at, z.corte_error_msg,
                           COALESCE(z.corte_count, CASE WHEN COALESCE(z.cortada, FALSE) THEN 1 ELSE 0 END) AS corte_count
                    FROM piezas z
                    JOIN partes p ON p.partid = z.partid
                    WHERE p.orderid = ?
                      AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                    ORDER BY z.partid, z.numero_pieza
                    """,
                    orderId);
        } catch (DataAccessException ex) {
            try {
                return jdbcTemplate.queryForList(
                        """
                        SELECT z.piezaid, z.partid, p.orderid, z.numero_pieza, z.escaneado, z.fecha_escaneo,
                               COALESCE(z.cortada, FALSE) AS cortada, z.cortada_at, z.cortada_por,
                               COALESCE(z.corte_error, FALSE) AS corte_error, z.corte_error_at, z.corte_error_msg,
                               CASE WHEN COALESCE(z.cortada, FALSE) THEN 1 ELSE 0 END AS corte_count
                        FROM piezas z
                        JOIN partes p ON p.partid = z.partid
                        WHERE p.orderid = ?
                          AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                        ORDER BY z.partid, z.numero_pieza
                        """,
                        orderId);
            } catch (DataAccessException ex2) {
                try {
                    return jdbcTemplate.queryForList(
                            """
                            SELECT z.piezaid, z.partid, p.orderid, z.numero_pieza, z.escaneado, z.fecha_escaneo,
                                   FALSE AS cortada, NULL::timestamp AS cortada_at, NULL::varchar AS cortada_por,
                                   FALSE AS corte_error, NULL::timestamp AS corte_error_at, NULL::varchar AS corte_error_msg,
                                   0 AS corte_count
                            FROM piezas z
                            JOIN partes p ON p.partid = z.partid
                            WHERE p.orderid = ?
                              AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                            ORDER BY z.partid, z.numero_pieza
                            """,
                            orderId);
                } catch (DataAccessException ignored) {
                    return List.of();
                }
            }
        }
    }

    public boolean completeOrderManual(Long orderId, Long employeeId, String method) {
        Map<String, Object> stats = findOrderPartStats(orderId);
        int total = ((Number) stats.getOrDefault("total", 0)).intValue();
        int done = ((Number) stats.getOrDefault("escaneadas", 0)).intValue();
        if (total == 0 || done < total) {
            return false;
        }

        obrasRepository.markOrderListoParaEntregar(orderId, employeeId);

        try {
            jdbcTemplate.update(
                    """
                    UPDATE ordenes
                    SET partes_escaneadas = ?,
                        partes_totales = ?,
                        porcentaje_completado = 100,
                        procesado = TRUE,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE orderid = ?
                    """,
                    done,
                    total,
                    orderId);
        } catch (Exception ignored) {
            // Esquema legacy sin columnas denormalizadas en ordenes
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO finalizacionordenes
                    (orderid, usuarioid, partesescaneadas, partestotales, metodofinalizacion, fechafinalizacion)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    orderId,
                    employeeId,
                    done,
                    total,
                    method);
        } catch (Exception ignored) {
            // Tabla opcional en algunos despliegues
        }

        insertScanAudit(
                employeeId,
                orderId,
                null,
                "ORDEN_COMPLETADA",
                "Orden completada manualmente",
                method,
                null);
        return true;
    }

    public Map<String, Object> getGeneralStats() {
        long totalOrders = countGeneric("SELECT COUNT(*) FROM ordenes");
        long completedOrders =
                countGeneric(
                        """
                        SELECT COUNT(*) FROM (
                            SELECT o.orderid
                            FROM ordenes o
                            WHERE EXISTS (SELECT 1 FROM partes p WHERE p.orderid = o.orderid)
                              AND NOT EXISTS (
                                  SELECT 1 FROM partes p
                                  WHERE p.orderid = o.orderid AND NOT COALESCE(p.escaneado, FALSE)
                              )
                        ) completed
                        """);
        long totalParts = countGeneric("SELECT COUNT(*) FROM partes");
        long scannedParts = countGeneric("SELECT COUNT(*) FROM partes WHERE escaneado = TRUE");
        long pendingParts = countGeneric("SELECT COUNT(*) FROM partes WHERE escaneado = FALSE");
        long scansToday =
                countGeneric(
                        "SELECT COUNT(*) FROM partes WHERE escaneado = TRUE AND DATE(fecha_escaneo) = CURRENT_DATE");

        double percent = totalParts == 0 ? 0.0 : ((double) scannedParts / (double) totalParts) * 100.0;
        return Map.of(
                "total_orders", totalOrders,
                "completed_orders", completedOrders,
                "total_parts", totalParts,
                "scanned_parts", scannedParts,
                "pending_parts", pendingParts,
                "scans_today", scansToday,
                "completion_percent", Math.round(percent * 100.0) / 100.0);
    }

    private long count(String sql, Long employeeId) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, employeeId);
        return value == null ? 0L : value.longValue();
    }

    private long countGeneric(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0L : value.longValue();
    }

  private static String extractPaleCodeFromObservations(String observations) {
        if (observations == null || observations.isBlank()) {
            return null;
        }
        String lower = observations.toLowerCase();
        int idx = lower.indexOf("agregada a pale ");
        if (idx < 0) {
            idx = lower.indexOf("pale ");
        }
        if (idx < 0) {
            return null;
        }
        String tail = observations.substring(idx).replaceFirst("(?i)^.*pale\\s+", "").trim();
        if (tail.isEmpty()) {
            return null;
        }
        int space = tail.indexOf(' ');
        return space > 0 ? tail.substring(0, space).trim() : tail.trim();
    }

    public void ensureOpCodigoColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS op_codigo VARCHAR(40)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_ordenes_op_codigo ON ordenes(op_codigo)");
        } catch (Exception ignored) {
            // no-op
        }
    }

    public int backfillOpCodigos(int limit) {
        ensureOpCodigoColumn();
        int safe = Math.max(1, Math.min(limit, 5000));
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT orderid, ordername FROM ordenes
                        WHERE op_codigo IS NULL OR TRIM(op_codigo) = ''
                        LIMIT ?
                        """,
                        safe);
        int updated = 0;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^([A-Za-z]?\\d{3,})\\b");
        for (Map<String, Object> row : rows) {
            String name = row.get("ordername") != null ? String.valueOf(row.get("ordername")) : "";
            java.util.regex.Matcher m = p.matcher(name.trim());
            if (!m.find()) {
                continue;
            }
            String op = m.group(1).toUpperCase();
            jdbcTemplate.update(
                    "UPDATE ordenes SET op_codigo = ? WHERE orderid = ?",
                    op,
                    ((Number) row.get("orderid")).longValue());
            updated++;
        }
        return updated;
    }

    public int countOps(String searchText) {
        ensureOpCodigoColumn();
        backfillOpCodigos(2000);
        if (searchText != null && !searchText.isBlank()) {
            String[] tokens = searchTokens(searchText);
            if (tokens.length == 0) {
                return 0;
            }
            StringBuilder sql = new StringBuilder();
            sql.append(
                    """
                    SELECT COUNT(DISTINCT COALESCE(NULLIF(TRIM(op_codigo), ''), ordername))
                    FROM ordenes
                    WHERE
                    """);
            java.util.List<Object> args = new java.util.ArrayList<>();
            appendTokenAndSearch(
                    sql,
                    args,
                    tokens,
                    "ordername",
                    "COALESCE(bookingcode, '')",
                    "COALESCE(op_codigo, '')",
                    false);
            Number n = jdbcTemplate.queryForObject(sql.toString(), Number.class, args.toArray());
            return n == null ? 0 : n.intValue();
        }
        Number n =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(DISTINCT COALESCE(NULLIF(TRIM(op_codigo), ''), ordername))
                        FROM ordenes
                        """,
                        Number.class);
        return n == null ? 0 : n.intValue();
    }

    public List<Map<String, Object>> findOpsPage(String searchText, int limit, int offset) {
        ensureOpCodigoColumn();
        backfillOpCodigos(2000);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        List<Map<String, Object>> opKeys;
        if (searchText != null && !searchText.isBlank()) {
            String[] tokens = searchTokens(searchText);
            if (tokens.length == 0) {
                opKeys = List.of();
            } else {
                StringBuilder sql = new StringBuilder();
                sql.append(
                        """
                        SELECT COALESCE(NULLIF(TRIM(o.op_codigo), ''), o.ordername) AS op_key
                        FROM ordenes o
                        WHERE
                        """);
                java.util.List<Object> args = new java.util.ArrayList<>();
                appendTokenAndSearch(
                        sql,
                        args,
                        tokens,
                        "o.ordername",
                        "COALESCE(o.bookingcode, '')",
                        "COALESCE(o.op_codigo, '')",
                        false);
                sql.append(
                        """
                         GROUP BY COALESCE(NULLIF(TRIM(o.op_codigo), ''), o.ordername)
                         ORDER BY MAX(o.fechacreacion) DESC NULLS LAST
                         LIMIT ? OFFSET ?
                        """);
                args.add(safeLimit);
                args.add(safeOffset);
                opKeys = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            }
        } else {
            opKeys =
                    jdbcTemplate.queryForList(
                            """
                            SELECT COALESCE(NULLIF(TRIM(o.op_codigo), ''), o.ordername) AS op_key
                            FROM ordenes o
                            GROUP BY COALESCE(NULLIF(TRIM(o.op_codigo), ''), o.ordername)
                            ORDER BY MAX(o.fechacreacion) DESC NULLS LAST
                            LIMIT ? OFFSET ?
                            """,
                            safeLimit,
                            safeOffset);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> keyRow : opKeys) {
            String opKey = String.valueOf(keyRow.get("op_key"));
            List<Map<String, Object>> obras = findObrasByOp(opKey);
            int piezasTot = 0;
            int piezasEsc = 0;
            int partesTot = 0;
            int partesEsc = 0;
            double pctSum = 0;
            for (Map<String, Object> o : obras) {
                piezasTot += ((Number) o.getOrDefault("piezas_totales", 0)).intValue();
                piezasEsc += ((Number) o.getOrDefault("piezas_escaneadas", 0)).intValue();
                partesTot += ((Number) o.getOrDefault("total_partes", 0)).intValue();
                partesEsc += ((Number) o.getOrDefault("partes_escaneadas", 0)).intValue();
                pctSum += ((Number) o.getOrDefault("porcentaje", 0)).doubleValue();
            }
            double pct;
            String avance;
            if (!obras.isEmpty()) {
                // Promedio de cada obra (ya alineado con detalle: partes 100% = listo).
                pct = Math.round((pctSum / obras.size()) * 10.0) / 10.0;
                if (piezasTot > 0 && piezasEsc >= piezasTot) {
                    avance = piezasEsc + "/" + piezasTot + " piezas";
                } else if (partesTot > 0 && partesEsc >= partesTot) {
                    avance = partesEsc + "/" + partesTot + " partes";
                } else if (piezasTot > 0) {
                    avance = piezasEsc + "/" + piezasTot + " piezas";
                } else if (partesTot > 0) {
                    avance = partesEsc + "/" + partesTot + " partes";
                } else {
                    avance = "0/0";
                }
            } else {
                pct = 0;
                avance = "0/0";
            }
            Map<String, Object> op = new java.util.LinkedHashMap<>();
            op.put("op_codigo", opKey);
            op.put("total_obras", obras.size());
            op.put("porcentaje", pct);
            op.put("avance_label", avance);
            op.put("obras", obras);
            result.add(op);
        }
        return result;
    }

    public List<Map<String, Object>> findObrasByOp(String opCodigo) {
        if (opCodigo == null || opCodigo.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT o.orderid, o.ordername, o.bookingcode, o.fechacreacion, o.op_codigo,
                               o.estado_escaneo,
                               (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid) AS total_partes,
                               (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid AND p.escaneado = TRUE) AS partes_escaneadas,
                               (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                 WHERE p.orderid = o.orderid
                                   AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)) AS piezas_totales,
                               (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                 WHERE p.orderid = o.orderid
                                   AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                                   AND z.escaneado = TRUE) AS piezas_escaneadas
                        FROM ordenes o
                        WHERE COALESCE(NULLIF(TRIM(o.op_codigo), ''), o.ordername) = ?
                        ORDER BY o.fechacreacion DESC NULLS LAST, o.orderid DESC
                        """,
                        opCodigo);
        List<Map<String, Object>> obras = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            int totalPartes = ((Number) row.getOrDefault("total_partes", 0)).intValue();
            int partesEsc = ((Number) row.getOrDefault("partes_escaneadas", 0)).intValue();
            int piezasTot = ((Number) row.getOrDefault("piezas_totales", 0)).intValue();
            int piezasEsc = ((Number) row.getOrDefault("piezas_escaneadas", 0)).intValue();
            String stored =
                    row.get("estado_escaneo") != null
                            ? String.valueOf(row.get("estado_escaneo")).trim().toUpperCase()
                            : "";
            boolean partesDone = totalPartes > 0 && partesEsc >= totalPartes;
            boolean piezasDone = piezasTot > 0 && piezasEsc >= piezasTot;
            String estado;
            if ("ENTREGADO".equals(stored)) {
                estado = BiesseObrasRepository.ESTADO_ENTREGADO;
            } else if (piezasDone
                    || partesDone
                    || "LISTO_PARA_ENTREGAR".equals(stored)
                    || "COMPLETADA".equals(stored)
                    || "COMPLETADO".equals(stored)) {
                // Misma regla que detalle / findOrders: partes al 100% = listo
                // (aunque queden filas piezas.escaneado desfasadas).
                estado = BiesseObrasRepository.ESTADO_LISTO;
            } else if (piezasEsc > 0 || partesEsc > 0 || "DESPACHO".equals(stored)) {
                estado = BiesseObrasRepository.ESTADO_DESPACHO;
            } else if ("PRODUCCION".equals(stored) || "OPTIMIZADO".equals(stored)) {
                estado = stored;
            } else if (!stored.isBlank()) {
                estado = BiesseObrasRepository.normalizeEstadoForUi(stored);
            } else {
                estado = "PENDIENTE";
            }
            double pct;
            String avance;
            if (partesDone || piezasDone) {
                if (piezasTot > 0) {
                    // Si las partes están al 100%, la UI muestra piezas completas
                    // (alineado con detalle web por cantidad_escaneada).
                    int showEsc = piezasDone ? piezasEsc : piezasTot;
                    pct = Math.round(showEsc * 1000.0 / piezasTot) / 10.0;
                    avance = showEsc + "/" + piezasTot + " piezas";
                } else {
                    pct = Math.round(partesEsc * 1000.0 / totalPartes) / 10.0;
                    avance = partesEsc + "/" + totalPartes + " partes";
                }
            } else if (piezasTot > 0) {
                pct = Math.round(piezasEsc * 1000.0 / piezasTot) / 10.0;
                avance = piezasEsc + "/" + piezasTot + " piezas";
            } else if (totalPartes > 0) {
                pct = Math.round(partesEsc * 1000.0 / totalPartes) / 10.0;
                avance = partesEsc + "/" + totalPartes + " partes";
            } else {
                pct = 0;
                avance = "0/0";
            }
            Map<String, Object> obra = new java.util.LinkedHashMap<>();
            obra.put("orderid", row.get("orderid"));
            obra.put("ordername", row.get("ordername"));
            obra.put("bookingcode", row.get("bookingcode"));
            obra.put("fechacreacion", row.get("fechacreacion"));
            obra.put("op_codigo", row.get("op_codigo") != null ? row.get("op_codigo") : opCodigo);
            obra.put("estado_escaneo", estado);
            obra.put("total_partes", totalPartes);
            obra.put("partes_escaneadas", partesEsc);
            obra.put("piezas_totales", piezasTot);
            // Si partes al 100%, reportar piezas completas para UI (web/teléfono).
            obra.put("piezas_escaneadas", (partesDone && piezasTot > 0 && !piezasDone) ? piezasTot : piezasEsc);
            obra.put("porcentaje", pct);
            obra.put("avance_label", avance);
            obras.add(obra);
        }
        return obras;
    }
}
