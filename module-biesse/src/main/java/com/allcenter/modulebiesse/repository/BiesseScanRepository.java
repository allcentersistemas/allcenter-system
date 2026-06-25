package com.allcenter.modulebiesse.repository;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
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
        insertScanAudit(
                employeeId,
                orderId,
                partId,
                "ESCANEAR_PIEZA",
                "Pieza piezaid="
                        + pieceId
                        + " de parte "
                        + pieceInfo.get("partcode")
                        + " escaneada. Acumulado partes: "
                        + effectiveScanned
                        + "/"
                        + (scheduledQty != null ? scheduledQty : 0),
                "AUTOMATICO",
                equipment != null ? equipment : "");
        syncOrderScanProgress(orderId);
        completeOrderIfNeeded(orderId, employeeId);
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

        try {
            jdbcTemplate.update(
                    """
                    UPDATE ordenes
                    SET estado_escaneo = 'COMPLETADA',
                        fecha_completado = CURRENT_TIMESTAMP,
                        usuario_completado_id = ?,
                        procesado = TRUE,
                        partes_escaneadas = ?,
                        partes_totales = ?,
                        porcentaje_completado = 100,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE orderid = ?
                    """,
                    employeeId,
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
                        SELECT o.orderid, o.ordername, o.bookingcode, o.fechacreacion,
                               st.estado_escaneo,
                               NULL::timestamp AS fecha_completado,
                               st.partes_escaneadas,
                               st.partes_totales,
                               st.porcentaje_completado,
                               COALESCE(pz.total_piezas, 0) AS total_piezas,
                               COALESCE(pz.piezas_escaneadas, 0) AS piezas_escaneadas
                        FROM ordenes o
                        LEFT JOIN LATERAL (
                            SELECT COUNT(*)::int AS partes_totales,
                                   COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE))::int AS partes_escaneadas,
                                   CASE WHEN COUNT(*) = 0 THEN 0::numeric
                                        ELSE ROUND(
                                            100.0 * COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) / COUNT(*), 2)
                                   END AS porcentaje_completado,
                                   CASE WHEN COUNT(*) = 0 THEN 'PENDIENTE'
                                        WHEN COUNT(*) FILTER (WHERE NOT COALESCE(p.escaneado, FALSE)) = 0
                                            THEN 'COMPLETADA'
                                        WHEN COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) > 0
                                            THEN 'EN_PROCESO'
                                        ELSE 'PENDIENTE'
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
                        ) pz ON TRUE
                        """);
        try {
            return queryOrders(sql, orderId, state, query, fromDate, toDate, limit, offset);
        } catch (DataAccessException ex) {
            StringBuilder sqlWithoutPiezas =
                    new StringBuilder(
                            """
                            SELECT o.orderid, o.ordername, o.bookingcode, o.fechacreacion,
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
                                       CASE WHEN COUNT(*) = 0 THEN 'PENDIENTE'
                                            WHEN COUNT(*) FILTER (WHERE NOT COALESCE(p.escaneado, FALSE)) = 0
                                                THEN 'COMPLETADA'
                                            WHEN COUNT(*) FILTER (WHERE COALESCE(p.escaneado, FALSE)) > 0
                                                THEN 'EN_PROCESO'
                                            ELSE 'PENDIENTE'
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
            if (hasOrderId) {
                sql.append(" AND o.orderid = ? ");
            }
            if (hasState) {
                sql.append(" AND st.estado_escaneo = ? ");
            }
            if (hasQuery) {
                sql.append(
                        " AND (o.ordername ILIKE ? OR COALESCE(o.bookingcode, '') ILIKE ? OR CAST(o.orderid AS TEXT) LIKE ?) ");
            }
            if (hasFromDate) {
                sql.append(" AND DATE(o.fechacreacion) >= CAST(? AS DATE) ");
            }
            if (hasToDate) {
                sql.append(" AND DATE(o.fechacreacion) <= CAST(? AS DATE) ");
            }
            sql.append(" ORDER BY o.fechacreacion DESC LIMIT ? OFFSET ? ");
            java.util.List<Object> args = new java.util.ArrayList<>();
            if (hasOrderId) {
                args.add(orderId);
            }
            if (hasState) {
                args.add(state);
            }
            if (hasQuery) {
                String like = "%" + query.trim() + "%";
                args.add(like);
                args.add(like);
                args.add(like);
            }
            if (hasFromDate) {
                args.add(fromDate.trim());
            }
            if (hasToDate) {
                args.add(toDate.trim());
            }
            args.add(limit);
            args.add(offset);
            return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        }
        sql.append(" ORDER BY o.fechacreacion DESC LIMIT ? OFFSET ? ");
        return jdbcTemplate.queryForList(sql.toString(), limit, offset);
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
        if (pieceId == null) {
            return false;
        }
        try {
            Integer count =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM piezas WHERE piezaid = ?", Integer.class, pieceId);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isPieceScanned(Long pieceId) {
        if (pieceId == null) {
            return false;
        }
        try {
            Boolean scanned =
                    jdbcTemplate.query(
                            "SELECT escaneado FROM piezas WHERE piezaid = ?",
                            rs -> rs.next() ? rs.getBoolean("escaneado") : null,
                            pieceId);
            return Boolean.TRUE.equals(scanned);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Si la pieza figura en un palé (tabla compartida con module-system cuando existe en la misma BD).
     */
    public Map<String, Object> findPaleAssignmentByPieceId(Long pieceId) {
        if (pieceId == null) {
            return null;
        }
        String[] queries = {
            """
            SELECT p.codigo, p.paleeid AS pale_id, p.estado
            FROM paledetalle pd
            JOIN pale p ON p.paleeid = pd.paleenvioid
            WHERE pd.piezaid = ?
            LIMIT 1
            """,
            """
            SELECT p.codigo, p.paleenvioid AS pale_id, p.estado
            FROM paledetalle pd
            JOIN pale p ON p.paleenvioid = pd.paleenvioid
            WHERE pd.piezaid = ?
            LIMIT 1
            """
        };
        for (String sql : queries) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pieceId);
                if (!rows.isEmpty()) {
                    return rows.getFirst();
                }
            } catch (Exception ignored) {
                // Esquema legacy o tabla en otra BD
            }
        }
        return null;
    }

    public boolean deleteOrderById(Long orderId) {
        try {
            jdbcTemplate.update("DELETE FROM synclogs WHERE orden_id = ?", orderId);
        } catch (Exception ignored) {
            // synclogs puede no existir en todos los despliegues
        }
        return jdbcTemplate.update("DELETE FROM ordenes WHERE orderid = ?", orderId) > 0;
    }

    public List<Map<String, Object>> findScanAudit(Long orderId, Long partId, String action, int limit, int offset) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT auditoriaid, usuarioid, orderid, partid, accion, detalles, equipo, metodo, exito, fecha
                        FROM auditoriaescaneos
                        WHERE 1=1
                        """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (orderId != null) {
            sql.append(" AND orderid = ? ");
            args.add(orderId);
        }
        if (partId != null) {
            sql.append(" AND partid = ? ");
            args.add(partId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND UPPER(accion) = UPPER(?) ");
            args.add(action.trim());
        }
        sql.append(" ORDER BY fecha DESC LIMIT ? OFFSET ? ");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> findOrderById(Long orderId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, fechacreacion
                        FROM ordenes
                        WHERE orderid = ?
                        """,
                        orderId);
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
        String estado;
        if (total == 0) {
            estado = "PENDIENTE";
        } else if (escaneadas >= total) {
            estado = "COMPLETADA";
        } else if (escaneadas > 0) {
            estado = "EN_PROCESO";
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
        String composite = buildCompositeScanCode(orderName, partToken, pieceToken);
        Long detectedOrderId = detectOrderIdFromCode(composite);
        if (detectedOrderId != null) {
            Map<String, Object> byDetected = findPieceInOrderContext(detectedOrderId, null, partToken, pieceToken);
            if (byDetected != null) {
                return byDetected;
            }
        }
        for (Long orderId : findCandidateOrderIdsForToken(orderName)) {
            Map<String, Object> candidate = findPieceInOrderContext(orderId, null, partToken, pieceToken);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
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

    private java.util.List<Long> findCandidateOrderIdsForToken(String orderToken) {
        if (orderToken == null || orderToken.isBlank()) {
            return List.of();
        }
        String norm = orderToken.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        ORDER BY LENGTH(ordername) DESC, fechacreacion DESC
                        LIMIT 500
                        """);
        for (Map<String, Object> row : rows) {
            String orderName =
                    String.valueOf(row.get("ordername")).replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
            Object booking = row.get("bookingcode");
            String bookingNorm =
                    booking != null
                            ? String.valueOf(booking).replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT)
                            : "";
            if (norm.equals(orderName)
                    || norm.equals(bookingNorm)
                    || norm.startsWith(orderName)
                    || (!bookingNorm.isBlank() && norm.startsWith(bookingNorm))
                    || orderName.startsWith(norm)
                    || (!bookingNorm.isBlank() && bookingNorm.startsWith(norm))) {
                ids.add(((Number) row.get("orderid")).longValue());
            }
        }
        return new java.util.ArrayList<>(ids);
    }

    private static String buildCompositeScanCode(String orderName, String partToken, String pieceToken) {
        return orderName + "-P" + partToken + "-" + pieceToken;
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
        String fullNoSpaces = upper.replaceAll("\\s+", "");

        Map<String, Object> exact = findOrderByNameToken(tokenBeforePart);
        if (exact != null) {
            return ((Number) exact.get("orderid")).longValue();
        }

        List<Map<String, Object>> candidates =
                jdbcTemplate.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        ORDER BY fechacreacion DESC
                        LIMIT 500
                        """);
        for (Map<String, Object> row : candidates) {
            String orderName = String.valueOf(row.get("ordername")).replaceAll("\\s+", "").toUpperCase();
            Object booking = row.get("bookingcode");
            String bookingNorm =
                    booking != null ? String.valueOf(booking).replaceAll("\\s+", "").toUpperCase() : "";
            if (orderName.equals(tokenNoSpaces) || (!bookingNorm.isBlank() && bookingNorm.equals(tokenNoSpaces))) {
                return ((Number) row.get("orderid")).longValue();
            }
            if (fullNoSpaces.startsWith(orderName)
                    || (!bookingNorm.isBlank() && fullNoSpaces.startsWith(bookingNorm))) {
                return ((Number) row.get("orderid")).longValue();
            }
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

    public List<Map<String, Object>> findOrderPieces(Long orderId) {
        try {
            return jdbcTemplate.queryForList(
                    """
                    SELECT z.piezaid, z.partid, p.orderid, z.numero_pieza, z.escaneado, z.fecha_escaneo
                    FROM piezas z
                    JOIN partes p ON p.partid = z.partid
                    WHERE p.orderid = ?
                    ORDER BY z.partid, z.numero_pieza
                    """,
                    orderId);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    public boolean completeOrderManual(Long orderId, Long employeeId, String method) {
        Map<String, Object> stats = findOrderPartStats(orderId);
        int total = ((Number) stats.getOrDefault("total", 0)).intValue();
        int done = ((Number) stats.getOrDefault("escaneadas", 0)).intValue();
        if (total == 0 || done < total) {
            return false;
        }

        try {
            jdbcTemplate.update(
                    """
                    UPDATE ordenes
                    SET estado_escaneo = 'COMPLETADA',
                        fecha_completado = CURRENT_TIMESTAMP,
                        usuario_completado_id = ?,
                        procesado = TRUE,
                        partes_escaneadas = ?,
                        partes_totales = ?,
                        porcentaje_completado = 100,
                        fecha_modificacion = CURRENT_TIMESTAMP
                    WHERE orderid = ?
                    """,
                    employeeId,
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
}
