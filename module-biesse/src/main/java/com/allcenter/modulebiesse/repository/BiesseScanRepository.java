package com.allcenter.modulebiesse.repository;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
        String sql =
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
                """;
        return jdbcTemplate.update(
                sql,
                employeeId,
                req.observations(),
                req.scannedQuantity(),
                difference,
                method,
                req.scanTimeMs(),
                req.equipment(),
                req.location(),
                req.partId());
    }

    public void insertScanAudit(
            Long employeeId, Long orderId, Long partId, String action, String details, String method, String equipment) {
        String sql =
                """
                INSERT INTO auditoriaescaneos
                (usuarioid, orderid, partid, accion, detalles, equipo, metodo, exito, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
                """;
        jdbcTemplate.update(sql, employeeId, orderId, partId, action, details, equipment, method);
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

        int updatedPiece =
                jdbcTemplate.update(
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

        insertScanAudit(
                employeeId,
                ((Number) pieceInfo.get("orderid")).longValue(),
                partId,
                "ESCANEAR_PIEZA",
                "Pieza " + pieceId + " escaneada de parte " + pieceInfo.get("partcode"),
                "AUTOMATICO",
                equipment);
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
        long totalDifference =
                count(
                        "SELECT COALESCE(SUM(ABS(diferencia_cantidad)), 0) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE",
                        employeeId);
        long contributedOrders =
                count(
                        "SELECT COUNT(DISTINCT orderid) FROM partes WHERE usuario_modificacion = ? AND escaneado = TRUE",
                        employeeId);
        return new UserScanStatsResponse(
                totalScanned, scannedToday, scannedWeek, scannedMonth, totalDifference, contributedOrders);
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
    }

    public List<Map<String, Object>> findScannedPartsByUser(
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
        if (fromDate != null && !fromDate.isBlank()) {
            sql.append(" AND DATE(p.fecha_escaneo) >= ? ");
        }
        if (toDate != null && !toDate.isBlank()) {
            sql.append(" AND DATE(p.fecha_escaneo) <= ? ");
        }
        sql.append(" ORDER BY p.fecha_escaneo DESC LIMIT ? ");

        if (fromDate != null && !fromDate.isBlank() && toDate != null && !toDate.isBlank()) {
            return jdbcTemplate.queryForList(sql.toString(), employeeId, fromDate, toDate, limit);
        }
        if (fromDate != null && !fromDate.isBlank()) {
            return jdbcTemplate.queryForList(sql.toString(), employeeId, fromDate, limit);
        }
        if (toDate != null && !toDate.isBlank()) {
            return jdbcTemplate.queryForList(sql.toString(), employeeId, toDate, limit);
        }
        return jdbcTemplate.queryForList(sql.toString(), employeeId, limit);
    }

    public List<Map<String, Object>> findOrders(String state, String query, int limit, int offset) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT o.orderid, o.ordername, o.bookingcode, o.fechacreacion, o.estado_escaneo,
                               o.fecha_completado, o.partes_escaneadas, o.partes_totales, o.porcentaje_completado,
                               (SELECT COUNT(*)::int FROM piezas z WHERE z.orderid = o.orderid) AS total_piezas,
                               (SELECT COUNT(*)::int FROM piezas z WHERE z.orderid = o.orderid AND z.escaneado = TRUE) AS piezas_escaneadas
                        FROM ordenes o
                        """);
        boolean hasState = state != null && !state.isBlank();
        boolean hasQuery = query != null && !query.isBlank();

        if (hasState || hasQuery) {
            sql.append(" WHERE 1=1 ");
            if (hasState) {
                sql.append(" AND o.estado_escaneo = ? ");
            }
            if (hasQuery) {
                sql.append(" AND (o.ordername ILIKE ? OR COALESCE(o.bookingcode, '') ILIKE ?) ");
            }
            sql.append(" ORDER BY o.fechacreacion DESC LIMIT ? OFFSET ? ");
            if (hasState && hasQuery) {
                String like = "%" + query.trim() + "%";
                return jdbcTemplate.queryForList(sql.toString(), state, like, like, limit, offset);
            }
            if (hasState) {
                return jdbcTemplate.queryForList(sql.toString(), state, limit, offset);
            }
            String like = "%" + query.trim() + "%";
            return jdbcTemplate.queryForList(sql.toString(), like, like, limit, offset);
        }
        sql.append(" ORDER BY o.fechacreacion DESC LIMIT ? OFFSET ? ");
        return jdbcTemplate.queryForList(sql.toString(), limit, offset);
    }

    public Map<String, Object> findOrderById(Long orderId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, fechacreacion, estado_escaneo, fecha_completado,
                               partes_escaneadas, partes_totales, porcentaje_completado
                        FROM ordenes
                        WHERE orderid = ?
                        """,
                        orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> resolvePieceByCompositeCode(String orderName, String partToken, String pieceToken) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT z.piezaid, z.numero_pieza, p.partid, p.partnumber, p.partcode, p.orderid, o.ordername, o.bookingcode,
                               p.longitud AS longitud_parte, p.ancho AS ancho_parte
                        FROM piezas z
                        JOIN partes p ON p.partid = z.partid
                        JOIN ordenes o ON o.orderid = p.orderid
                        WHERE UPPER(TRIM(o.ordername)) = UPPER(TRIM(?))
                          AND (
                               CAST(p.partnumber AS TEXT) = ?
                               OR UPPER(TRIM(p.partcode)) = UPPER(TRIM(?))
                              )
                          AND CAST(z.numero_pieza AS TEXT) = ?
                        LIMIT 1
                        """,
                        orderName,
                        partToken,
                        "P" + partToken,
                        pieceToken);
        return rows.isEmpty() ? null : rows.getFirst();
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
    }

    public List<Map<String, Object>> findOrderPieces(Long orderId) {
        return jdbcTemplate.queryForList(
                """
                SELECT z.piezaid, z.partid, z.orderid, z.numero_pieza, z.escaneado, z.fecha_escaneo
                FROM piezas z
                WHERE z.orderid = ?
                ORDER BY z.partid, z.numero_pieza
                """,
                orderId);
    }

    public boolean completeOrderManual(Long orderId, Long employeeId, String method) {
        Map<String, Object> stats = findOrderPartStats(orderId);
        int total = ((Number) stats.getOrDefault("total", 0)).intValue();
        int done = ((Number) stats.getOrDefault("escaneadas", 0)).intValue();
        if (total == 0 || done < total) {
            return false;
        }

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
        long completedOrders = countGeneric("SELECT COUNT(*) FROM ordenes WHERE estado_escaneo = 'COMPLETADA'");
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
