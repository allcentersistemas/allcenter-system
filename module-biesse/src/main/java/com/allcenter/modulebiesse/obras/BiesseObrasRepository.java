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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a órdenes/partes/trazabilidad en BD {@code obras} (sin tablas de agente). */
@Repository
@RequiredArgsConstructor
@Slf4j
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

    private static final Pattern OP_PATTERN = Pattern.compile("^([A-Za-z]?\\d{3,})(?:_|$|\\s|[-.])");
    private static final Pattern PART_PATTERN =
            Pattern.compile("(?i)^Part\\s*(P?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIM_PATTERN =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[x×]\\s*(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);

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

    public record OrderJobMatch(
            Map<String, Object> order, boolean ambiguous, List<Map<String, Object>> candidates) {}

    /** Resuelve job OSI → obra ERP; {@code ambiguous=true} si hay empate o señal débil. */
    public OrderJobMatch resolveOrderForJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return new OrderJobMatch(null, false, List.of());
        }
        String token = normalizeJobToken(jobName);
        if (token.isBlank()) {
            return new OrderJobMatch(null, false, List.of());
        }
        String op = extractOp(token);
        String compact = compactName(token);
        log.info(
                "resolveOrderForJob job='{}' token='{}' compact='{}' op={}",
                jobName,
                token,
                compact,
                op);

        // TRIM solo quita espacio ASCII; NBSP (CHR(160)) en ordername rompe el = exacto.
        List<Map<String, Object>> exact = queryOrdersForJobMatch(
                """
                SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                       nparts, partes_totales
                FROM ordenes
                WHERE UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?)
                   OR UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) = UPPER(?)
                   OR UPPER(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ' '), ' ', '')) = UPPER(?)
                   OR (bookingcode IS NOT NULL AND UPPER(TRIM(BOTH FROM REPLACE(bookingcode, CHR(160), ' '))) = UPPER(?))
                ORDER BY fechacreacion DESC
                LIMIT 5
                """,
                """
                SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo
                FROM ordenes
                WHERE UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?)
                   OR UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) = UPPER(?)
                   OR UPPER(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ' '), ' ', '')) = UPPER(?)
                   OR (bookingcode IS NOT NULL AND UPPER(TRIM(BOTH FROM REPLACE(bookingcode, CHR(160), ' '))) = UPPER(?))
                ORDER BY fechacreacion DESC
                LIMIT 5
                """,
                token,
                compact,
                compact,
                token);
        MatchPick pickExact = pickBestOrderMatchDetailed(exact, token, op);
        if (pickExact.order() != null && !pickExact.ambiguous()) {
            return new OrderJobMatch(pickExact.order(), false, exact);
        }
        if (pickExact.ambiguous()) {
            return new OrderJobMatch(null, true, exact);
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        if (op != null) {
            candidates.addAll(
                    queryOrdersForJobMatch(
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                   nparts, partes_totales
                            FROM ordenes
                            WHERE UPPER(TRIM(COALESCE(op_codigo, ''))) = UPPER(?)
                               OR UPPER(TRIM(ordername)) LIKE UPPER(?) || ' %'
                               OR UPPER(TRIM(ordername)) LIKE UPPER(?) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 40
                            """,
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo
                            FROM ordenes
                            WHERE UPPER(TRIM(COALESCE(op_codigo, ''))) = UPPER(?)
                               OR UPPER(TRIM(ordername)) LIKE UPPER(?) || ' %'
                               OR UPPER(TRIM(ordername)) LIKE UPPER(?) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 40
                            """,
                            op,
                            op,
                            op));
        }
        if (candidates.isEmpty()) {
            candidates.addAll(
                    queryOrdersForJobMatch(
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                   nparts, partes_totales
                            FROM ordenes
                            WHERE UPPER(REPLACE(REPLACE(ordername, '_', ''), ' ', '')) = UPPER(?)
                               OR UPPER(REPLACE(REPLACE(ordername, '_', ''), ' ', '')) LIKE UPPER(?) || '%'
                               OR UPPER(?) LIKE UPPER(REPLACE(REPLACE(ordername, '_', ''), ' ', '')) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 40
                            """,
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo
                            FROM ordenes
                            WHERE UPPER(REPLACE(REPLACE(ordername, '_', ''), ' ', '')) = UPPER(?)
                               OR UPPER(REPLACE(REPLACE(ordername, '_', ''), ' ', '')) LIKE UPPER(?) || '%'
                               OR UPPER(?) LIKE UPPER(REPLACE(REPLACE(ordername, '_', ''), ' ', '')) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 40
                            """,
                            compact,
                            compact,
                            compact));
        }
        MatchPick pick = pickBestOrderMatchDetailed(candidates, token, op);
        if (pick.order() != null && !pick.ambiguous()) {
            return new OrderJobMatch(pick.order(), false, candidates);
        }
        if (pick.ambiguous()) {
            return new OrderJobMatch(null, true, candidates);
        }

        // Fallback: misma idea que la lista web (ILIKE / espacios raros). Evita 404 cuando
        // el nombre en BD tiene NBSP u el SELECT estricto falló por columnas opcionales.
        List<Map<String, Object>> loose = queryOrdersLooseByName(token, compact);
        MatchPick pickLoose = pickBestOrderMatchDetailed(loose, token, op);
        if (pickLoose.order() != null && !pickLoose.ambiguous()) {
            log.info(
                    "resolveOrderForJob fallback loose OK job='{}' → orderid={} name='{}'",
                    token,
                    pickLoose.order().get("orderid"),
                    pickLoose.order().get("ordername"));
            return new OrderJobMatch(pickLoose.order(), false, loose);
        }
        if (pickLoose.ambiguous()) {
            return new OrderJobMatch(null, true, loose);
        }
        // Un solo resultado por búsqueda parcial de nombre completo → aceptar.
        if (loose.size() == 1) {
            return new OrderJobMatch(loose.getFirst(), false, loose);
        }

        // Último recurso: ILIKE %token% sin columnas opcionales.
        List<Map<String, Object>> ilike = queryOrdersIlikeContains(token);
        MatchPick pickIlike = pickBestOrderMatchDetailed(ilike, token, op);
        if (pickIlike.order() != null && !pickIlike.ambiguous()) {
            log.info(
                    "resolveOrderForJob fallback ILIKE OK job='{}' → orderid={} name='{}'",
                    token,
                    pickIlike.order().get("orderid"),
                    pickIlike.order().get("ordername"));
            return new OrderJobMatch(pickIlike.order(), false, ilike);
        }
        if (pickIlike.ambiguous()) {
            return new OrderJobMatch(null, true, ilike);
        }
        if (ilike.size() == 1) {
            return new OrderJobMatch(ilike.getFirst(), false, ilike);
        }
        return new OrderJobMatch(
                null, false, !ilike.isEmpty() ? ilike : (loose.isEmpty() ? candidates : loose));
    }

    /**
     * Elige obra entre filas ya obtenidas (p.ej. {@code findOrders} / lista web).
     * Normaliza claves orderid/ordername si vienen en camelCase.
     */
    public OrderJobMatch resolveFromCandidateRows(String jobName, List<Map<String, Object>> rows) {
        if (jobName == null || jobName.isBlank() || rows == null || rows.isEmpty()) {
            return new OrderJobMatch(null, false, List.of());
        }
        String token = normalizeJobToken(jobName);
        String op = extractOp(token);
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> n = new LinkedHashMap<>(row);
            if (!n.containsKey("orderid") && n.get("orderId") != null) {
                n.put("orderid", n.get("orderId"));
            }
            if (!n.containsKey("ordername") && n.get("orderName") != null) {
                n.put("ordername", n.get("orderName"));
            }
            if (!n.containsKey("op_codigo") && n.get("opCodigo") != null) {
                n.put("op_codigo", n.get("opCodigo"));
            }
            normalized.add(n);
        }
        MatchPick pick = pickBestOrderMatchDetailed(normalized, token, op);
        if (pick.order() != null && !pick.ambiguous()) {
            return new OrderJobMatch(pick.order(), false, normalized);
        }
        if (pick.ambiguous()) {
            return new OrderJobMatch(null, true, normalized);
        }
        if (normalized.size() == 1) {
            return new OrderJobMatch(normalized.getFirst(), false, normalized);
        }
        return new OrderJobMatch(null, false, normalized);
    }

    private List<Map<String, Object>> queryOrdersIlikeContains(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        try {
            return jdbc.queryForList(
                    """
                    SELECT orderid, ordername, bookingcode, op_codigo
                    FROM ordenes
                    WHERE REPLACE(COALESCE(ordername, ''), CHR(160), ' ') ILIKE ?
                    ORDER BY orderid DESC
                    LIMIT 40
                    """,
                    "%" + token + "%");
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob ILIKE falló: {}", ex.getMostSpecificCause().getMessage());
            try {
                return jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE ordername ILIKE ?
                        ORDER BY orderid DESC
                        LIMIT 40
                        """,
                        "%" + token + "%");
            } catch (DataAccessException ex2) {
                log.warn(
                        "resolveOrderForJob ILIKE mínimo falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                return List.of();
            }
        }
    }

    /**
     * Intenta SELECT con columnas denormalizadas; si el esquema no las tiene, reintenta sin ellas.
     */
    private List<Map<String, Object>> queryOrdersForJobMatch(
            String sqlWithExtras, String sqlMinimal, Object... args) {
        try {
            return jdbc.queryForList(sqlWithExtras, args);
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob SQL extras falló: {}", ex.getMostSpecificCause().getMessage());
            try {
                return jdbc.queryForList(sqlMinimal, args);
            } catch (DataAccessException ex2) {
                log.warn(
                        "resolveOrderForJob SQL minimal falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                String noOrderBy =
                        sqlMinimal.replaceAll("(?is)\\s+ORDER BY\\s+fechacreacion\\s+DESC\\s*", " ");
                try {
                    return jdbc.queryForList(noOrderBy, args);
                } catch (DataAccessException ex3) {
                    log.warn(
                            "resolveOrderForJob SQL sin ORDER BY falló: {}",
                            ex3.getMostSpecificCause().getMessage());
                    return List.of();
                }
            }
        }
    }

    /**
     * Misma idea que la lista web ({@code findOrders}): TODOS los tokens del job deben
     * aparecer en ordername/booking/op (palabra completa tras normalizar no-alfanuméricos).
     */
    private List<Map<String, Object>> queryOrdersLooseByName(String token, String compact) {
        String[] tokens = jobSearchTokens(token);
        if (tokens.length == 0) {
            return List.of();
        }
        String nameNorm = "trim(regexp_replace(REPLACE(COALESCE(ordername, ''), CHR(160), ' '), '[^[:alnum:]]+', ' ', 'g'))";
        String bookingNorm =
                "trim(regexp_replace(REPLACE(COALESCE(bookingcode, ''), CHR(160), ' '), '[^[:alnum:]]+', ' ', 'g'))";
        String opNorm =
                "trim(regexp_replace(REPLACE(COALESCE(op_codigo, ''), CHR(160), ' '), '[^[:alnum:]]+', ' ', 'g'))";
        StringBuilder sql = new StringBuilder(
                """
                SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo
                FROM ordenes
                WHERE (
                """);
        List<Object> args = new ArrayList<>();
        // Exacto / compacto primero (barato).
        sql.append(" UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?) ");
        args.add(token);
        sql.append(" OR UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) = UPPER(?) ");
        args.add(compact);
        sql.append(" OR ( ");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(" ( ");
            sql.append("((' ' || lower(").append(nameNorm).append(") || ' ') LIKE ('% ' || lower(?) || ' %'))");
            args.add(tokens[i]);
            sql.append(" OR ((' ' || lower(").append(bookingNorm).append(") || ' ') LIKE ('% ' || lower(?) || ' %'))");
            args.add(tokens[i]);
            sql.append(" OR ((' ' || lower(").append(opNorm).append(") || ' ') LIKE ('% ' || lower(?) || ' %'))");
            args.add(tokens[i]);
            sql.append(" ) ");
        }
        sql.append(" ) ) ORDER BY orderid DESC LIMIT 40 ");
        try {
            return jdbc.queryForList(sql.toString(), args.toArray());
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob loose falló: {}", ex.getMostSpecificCause().getMessage());
            try {
                // Sin regexp / op_codigo: ILIKE parcial con el nombre completo.
                return jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE REPLACE(COALESCE(ordername, ''), CHR(160), ' ') ILIKE ?
                           OR REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '') ILIKE ?
                        ORDER BY orderid DESC
                        LIMIT 40
                        """,
                        "%" + token + "%",
                        "%" + compact + "%");
            } catch (DataAccessException ex2) {
                log.warn(
                        "resolveOrderForJob loose mínimo falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                return List.of();
            }
        }
    }

    /** Tokens alfanuméricos del job (igual que búsqueda web de obras). */
    private static String[] jobSearchTokens(String query) {
        if (query == null || query.isBlank()) {
            return new String[0];
        }
        String norm =
                query.trim()
                        .replace('\u00A0', ' ')
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

    public Map<String, Object> findOrderForJob(String jobName) {
        OrderJobMatch match = resolveOrderForJob(jobName);
        if (match.ambiguous() || match.order() == null) {
            return null;
        }
        return match.order();
    }

    private record MatchPick(Map<String, Object> order, int score, boolean ambiguous) {}

    /**
     * Con varias obras bajo la misma OP (p.ej. TAUPE vs PANELA), elige la que mejor
     * coincide con el job del Event.log — no la más reciente a ciegas.
     */
    private static MatchPick pickBestOrderMatchDetailed(
            List<Map<String, Object>> candidates, String jobToken, String op) {
        if (candidates == null || candidates.isEmpty()) {
            return new MatchPick(null, Integer.MIN_VALUE, false);
        }
        if (candidates.size() == 1) {
            return new MatchPick(candidates.getFirst(), 1000, false);
        }
        String job = jobToken != null ? jobToken.trim().toUpperCase(Locale.ROOT) : "";
        String jobNorm = normalizeForCompare(job);
        String jobCompact = compactName(job);
        Map<String, Object> best = null;
        int bestScore = Integer.MIN_VALUE;
        int tiedAtBest = 0;
        for (Map<String, Object> row : candidates) {
            String name = str(row.get("ordername"));
            if (name == null || name.isBlank()) {
                continue;
            }
            String nameU = name.trim().toUpperCase(Locale.ROOT);
            String nameNorm = normalizeForCompare(nameU);
            String nameCompact = compactName(nameU);
            int score = 0;
            if (nameU.equals(job) || nameNorm.equals(jobNorm) || nameCompact.equals(jobCompact)) {
                score += 1000;
            }
            if (!jobNorm.isBlank() && (jobNorm.contains(nameNorm) || nameNorm.contains(jobNorm))) {
                score += 400;
            }
            if (!jobCompact.isBlank()
                    && (jobCompact.contains(nameCompact) || nameCompact.contains(jobCompact))) {
                score += 300;
            }
            String jobTail = lastWord(jobNorm);
            String nameTail = lastWord(nameNorm);
            if (jobTail.length() >= 3 && jobTail.equals(nameTail)) {
                score += 500;
            } else if (jobTail.length() >= 3
                    && (jobNorm.contains(nameTail) || nameNorm.contains(jobTail))) {
                score += 250;
            }
            // Discriminar K5_IZQ vs K5_DER / K1_DER dentro de la misma OP.
            String jobKey = significantKey(jobNorm);
            String nameKey = significantKey(nameNorm);
            if (jobKey.length() >= 4 && jobKey.equals(nameKey)) {
                score += 600;
            } else if (jobKey.length() >= 4
                    && (jobNorm.contains(jobKey) && nameNorm.contains(jobKey))) {
                score += 350;
            }
            if (op != null && nameU.startsWith(op.toUpperCase(Locale.ROOT))) {
                score += 50;
            }
            score += tokenOverlapScore(jobNorm, nameNorm) * 20;
            if (score > bestScore) {
                bestScore = score;
                best = row;
                tiedAtBest = 1;
            } else if (score == bestScore && score > Integer.MIN_VALUE) {
                tiedAtBest++;
            }
        }
        if (best == null) {
            return new MatchPick(null, Integer.MIN_VALUE, false);
        }
        // Empate en nombre exacto (reimport XML): tomar la más reciente (ya viene ORDER BY fecha DESC).
        if (bestScore >= 1000) {
            return new MatchPick(best, bestScore, false);
        }
        boolean weak = candidates.size() > 1 && bestScore < 200;
        boolean tied = tiedAtBest > 1;
        boolean ambiguous = weak || tied;
        if (ambiguous) {
            return new MatchPick(null, bestScore, true);
        }
        return new MatchPick(best, bestScore, false);
    }

    /** Unifica espacios/_ para comparar job OSI vs ordername ERP. */
    private static String normalizeForCompare(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    /** Token distintivo tipo K5IZQ / K5DER / K1DER. */
    private static String significantKey(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return "";
        }
        String compact = normalizedName.replace(" ", "");
        Matcher m2 = Pattern.compile("(K\\d+[A-Z]+)", Pattern.CASE_INSENSITIVE).matcher(compact);
        return m2.find() ? m2.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static String normalizeJobToken(String jobName) {
        // OSI / copiar-pegar: NBSP, zero-width, guiones raros y espacios dobles.
        String t =
                jobName
                        .replace('\u00A0', ' ')
                        .replace('\u202F', ' ')
                        .replace("\u200B", "")
                        .replace("\uFEFF", "")
                        .replace('\u2013', '-')
                        .replace('\u2014', '-')
                        .replace('\u2018', '\'')
                        .replace('\u2019', '\'')
                        .replace('\u201C', '"')
                        .replace('\u201D', '"')
                        .trim()
                        .replace('_', ' ')
                        .replaceAll("\\s+", " ");
        // Quitar sufijo de patrón tipo ".001" pegado al nombre.
        Matcher suffix = PATTERN_SUFFIX.matcher(t);
        if (suffix.find()) {
            t = t.substring(0, t.length() - suffix.group().length()).trim();
        }
        return t;
    }

    private static final Pattern PATTERN_SUFFIX = Pattern.compile("\\.(\\d{3})$");

    private static String compactName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s_]+", "").toUpperCase(Locale.ROOT);
    }

    private static String lastWord(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.trim().split("\\s+");
        return parts[parts.length - 1].replaceAll("[^A-Z0-9]", "");
    }

    private static int tokenOverlapScore(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0;
        }
        Set<String> left = new java.util.HashSet<>();
        for (String p : a.split("\\s+")) {
            String t = p.replaceAll("[^A-Z0-9]", "");
            if (t.length() >= 3) {
                left.add(t);
            }
        }
        int n = 0;
        for (String p : b.split("\\s+")) {
            String t = p.replaceAll("[^A-Z0-9]", "");
            if (t.length() >= 3 && left.contains(t)) {
                n++;
            }
        }
        return n;
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
     *
     * <p>Incluye obras con {@code fechacreacion} o {@code fecha_modificacion} >= cutoff.
     * El cutoff es {@code sinceOverride} si viene informado; si no,
     * {@code app.biesse.seguimiento-since}.
     */
    public List<Map<String, Object>> listSeguimientoObras(int limit) {
        return listSeguimientoObras(limit, null);
    }

    public List<Map<String, Object>> listSeguimientoObras(int limit, LocalDate sinceOverride) {
        int safe = Math.max(1, Math.min(limit, 500));
        LocalDate since =
                sinceOverride != null
                        ? sinceOverride
                        : (seguimientoSince != null ? seguimientoSince : LocalDate.of(2026, 8, 26));
        List<Map<String, Object>> rows;
        try {
            rows =
                    jdbc.queryForList(
                            """
                            SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.estado_escaneo,
                                   o.fechacreacion,
                                   (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid) AS total_partes,
                                   (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid AND COALESCE(p.escaneado, FALSE)) AS partes_escaneadas,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                      WHERE p.orderid = o.orderid
                                        AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)) AS piezas_totales,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                      WHERE p.orderid = o.orderid
                                        AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                                        AND COALESCE(z.escaneado, FALSE)) AS piezas_escaneadas,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                      WHERE p.orderid = o.orderid
                                        AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                                        AND COALESCE(z.cortada, FALSE)) AS piezas_cortadas,
                                   (SELECT z.cortada_por FROM piezas z
                                      JOIN partes p ON p.partid = z.partid
                                     WHERE p.orderid = o.orderid AND z.cortada_por IS NOT NULL AND TRIM(z.cortada_por) <> ''
                                     ORDER BY z.cortada_at DESC NULLS LAST
                                     LIMIT 1) AS seccionador
                            FROM ordenes o
                            WHERE UPPER(TRIM(COALESCE(o.estado_escaneo, ''))) IN (
                                'OPTIMIZADO', 'PRODUCCION', 'DESPACHO',
                                'LISTO_PARA_ENTREGAR', 'ENTREGADO', 'COMPLETADA', 'COMPLETADO')
                              AND (
                                    (o.fechacreacion IS NOT NULL AND DATE(o.fechacreacion) >= CAST(? AS DATE))
                                 OR (o.fecha_modificacion IS NOT NULL AND DATE(o.fecha_modificacion) >= CAST(? AS DATE))
                              )
                            ORDER BY COALESCE(o.fecha_modificacion, o.fechacreacion) DESC NULLS LAST, o.orderid DESC
                            LIMIT ?
                            """,
                            since,
                            since,
                            safe);
        } catch (DataAccessException ex) {
            // Esquema sin fecha_modificacion: solo fechacreacion.
            try {
                rows =
                        jdbc.queryForList(
                                """
                                SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.estado_escaneo,
                                       o.fechacreacion,
                                       (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid) AS total_partes,
                                       (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid AND COALESCE(p.escaneado, FALSE)) AS partes_escaneadas,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                      WHERE p.orderid = o.orderid
                                        AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)) AS piezas_totales,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                      WHERE p.orderid = o.orderid
                                        AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                                        AND COALESCE(z.escaneado, FALSE)) AS piezas_escaneadas,
                                   (SELECT COUNT(*) FROM piezas z JOIN partes p ON p.partid = z.partid
                                      WHERE p.orderid = o.orderid
                                        AND (COALESCE(p.cantidad, 0) <= 0 OR z.numero_pieza <= p.cantidad)
                                        AND COALESCE(z.cortada, FALSE)) AS piezas_cortadas,
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
            } catch (DataAccessException ex2) {
                rows =
                        jdbc.queryForList(
                                """
                                SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.estado_escaneo,
                                       o.fechacreacion,
                                       (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid) AS total_partes,
                                       (SELECT COUNT(*) FROM partes p WHERE p.orderid = o.orderid AND COALESCE(p.escaneado, FALSE)) AS partes_escaneadas,
                                       0 AS piezas_totales,
                                       0 AS piezas_escaneadas,
                                       0 AS piezas_cortadas,
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
        int piezasCor = numberInt(row.get("piezas_cortadas"));
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
        double pctCorte;
        String avanceCorte;
        if (piezasTot > 0) {
            pctCorte = Math.round(piezasCor * 1000.0 / piezasTot) / 10.0;
            avanceCorte = piezasCor + "/" + piezasTot + " cortes";
        } else {
            pctCorte = 0;
            avanceCorte = "0/0 cortes";
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
        obra.put("porcentaje_corte", pctCorte);
        obra.put("porcentajeCorte", pctCorte);
        obra.put("avance_corte_label", avanceCorte);
        obra.put("avanceCorteLabel", avanceCorte);
        obra.put("seccionador", blankToNull(str(row.get("seccionador"))));
        obra.put("piezas_totales", piezasTot);
        obra.put("piezas_escaneadas", piezasEsc);
        obra.put("piezas_cortadas", piezasCor);
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

    /**
     * Mapea línea OSI ({@code Part P146 599.00x329.00 Q:1}) a una parte ERP.
     * 1) por partnumber/partcode; 2) por medidas L×W (el id OSI del patrón suele ≠ P# del XML).
     */
    public Map<String, Object> findPartForOsi(long orderId, String osiPartText) {
        Integer partNumber = parsePartNumber(osiPartText);
        if (partNumber != null) {
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
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        }
        double[] dims = parseDimensions(osiPartText);
        if (dims == null) {
            return null;
        }
        return findPartByDimensions(orderId, dims[0], dims[1]);
    }

    /**
     * Empareja por longitud×ancho (±0.6 mm; también L↔W).
     * Si hay varias iguales, prefiere la que aún tenga piezas sin cortar.
     */
    public Map<String, Object> findPartByDimensions(long orderId, double length, double width) {
        if (length <= 0 || width <= 0) {
            return null;
        }
        final double tol = 0.6;
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT p.partid, p.orderid, p.partcode, p.partnumber, p.cantidad, p.material,
                               p.descripcion, p.descripcion1, p.longitud, p.ancho, p.escaneado,
                               (SELECT COUNT(*) FROM piezas z
                                  WHERE z.partid = p.partid AND COALESCE(z.cortada, FALSE) = TRUE) AS cortadas
                        FROM partes p
                        WHERE p.orderid = ?
                          AND p.longitud IS NOT NULL AND p.ancho IS NOT NULL
                          AND (
                                (ABS(p.longitud - ?) <= ? AND ABS(p.ancho - ?) <= ?)
                             OR (ABS(p.longitud - ?) <= ? AND ABS(p.ancho - ?) <= ?)
                          )
                        ORDER BY
                          CASE WHEN COALESCE(p.cantidad, 0) > 0
                                    AND (SELECT COUNT(*) FROM piezas z
                                           WHERE z.partid = p.partid AND COALESCE(z.cortada, FALSE) = TRUE)
                                         < p.cantidad
                               THEN 0 ELSE 1 END,
                          p.partnumber NULLS LAST,
                          p.partid
                        LIMIT 1
                        """,
                        orderId,
                        length,
                        tol,
                        width,
                        tol,
                        width,
                        tol,
                        length,
                        tol);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Extrae L×W de textos OSI {@code Part P10 440.00x155.00 Q:2}. */
    public static double[] parseDimensions(String osiPartText) {
        if (osiPartText == null || osiPartText.isBlank()) {
            return null;
        }
        Matcher m = DIM_PATTERN.matcher(osiPartText.trim());
        if (!m.find()) {
            return null;
        }
        try {
            double a = Double.parseDouble(m.group(1).replace(',', '.'));
            double b = Double.parseDouble(m.group(2).replace(',', '.'));
            if (a <= 0 || b <= 0) {
                return null;
            }
            return new double[] {a, b};
        } catch (NumberFormatException e) {
            return null;
        }
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
        Long orderId = null;
        try {
            List<Map<String, Object>> orderRows =
                    jdbc.queryForList("SELECT orderid FROM partes WHERE partid = ? LIMIT 1", partId);
            if (!orderRows.isEmpty() && orderRows.getFirst().get("orderid") instanceof Number n) {
                orderId = n.longValue();
            }
        } catch (DataAccessException ignored) {
            // partes sin orderid en esquemas antiguos
        }

        int created = 0;
        for (int i = 1; i <= qty; i++) {
            try {
                int n =
                        jdbc.update(
                                """
                                INSERT INTO piezas (partid, orderid, numero_pieza, escaneado, cortada)
                                SELECT ?, ?, ?, FALSE, FALSE
                                WHERE NOT EXISTS (
                                    SELECT 1 FROM piezas z
                                    WHERE z.partid = ? AND z.numero_pieza = ?
                                )
                                """,
                                partId,
                                orderId,
                                i,
                                partId,
                                i);
                created += Math.max(n, 0);
            } catch (DataAccessException ex) {
                // Esquema sin cortada / orderid: intentar insert mínimo.
                try {
                    int n =
                            jdbc.update(
                                    """
                                    INSERT INTO piezas (partid, orderid, numero_pieza, escaneado)
                                    SELECT ?, ?, ?, FALSE
                                    WHERE NOT EXISTS (
                                        SELECT 1 FROM piezas z
                                        WHERE z.partid = ? AND z.numero_pieza = ?
                                    )
                                    """,
                                    partId,
                                    orderId,
                                    i,
                                    partId,
                                    i);
                    created += Math.max(n, 0);
                } catch (DataAccessException ignored) {
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
                    } catch (DataAccessException ignored2) {
                        // no-op
                    }
                }
            }
        }
        return created;
    }

    /**
     * Cantidad planificada de la parte, o {@code null} si no hay fila / valor.
     */
    public Integer partCantidad(long partId) {
        try {
            List<Map<String, Object>> partRows =
                    jdbc.queryForList("SELECT cantidad FROM partes WHERE partid = ? LIMIT 1", partId);
            if (!partRows.isEmpty() && partRows.getFirst().get("cantidad") instanceof Number n) {
                int qty = n.intValue();
                return qty > 0 ? qty : null;
            }
        } catch (DataAccessException ignored) {
            // sin cantidad
        }
        return null;
    }

    /**
     * Asegura una sola fila en {@code piezas} (sin crear el resto de la parte).
     * Usado al marcar corte del agente; no altera {@code escaneado}.
     */
    public boolean ensurePiezaRow(long partId, int pieceNumber) {
        if (pieceNumber <= 0) {
            return false;
        }
        try {
            List<Map<String, Object>> partRows =
                    jdbc.queryForList("SELECT cantidad FROM partes WHERE partid = ? LIMIT 1", partId);
            if (!partRows.isEmpty() && partRows.getFirst().get("cantidad") instanceof Number n) {
                int qty = n.intValue();
                if (qty > 0 && pieceNumber > qty) {
                    return false;
                }
            }
        } catch (DataAccessException ignored) {
            // sin cantidad
        }
        List<Map<String, Object>> exists =
                jdbc.queryForList(
                        """
                        SELECT piezaid FROM piezas
                        WHERE partid = ? AND numero_pieza = ?
                        LIMIT 1
                        """,
                        partId,
                        pieceNumber);
        if (!exists.isEmpty()) {
            return true;
        }
        Long orderId = null;
        try {
            List<Map<String, Object>> orderRows =
                    jdbc.queryForList("SELECT orderid FROM partes WHERE partid = ? LIMIT 1", partId);
            if (!orderRows.isEmpty() && orderRows.getFirst().get("orderid") instanceof Number n) {
                orderId = n.longValue();
            }
        } catch (DataAccessException ignored) {
            // partes sin orderid
        }
        try {
            int n =
                    jdbc.update(
                            """
                            INSERT INTO piezas (partid, orderid, numero_pieza, escaneado, cortada)
                            VALUES (?, ?, ?, FALSE, FALSE)
                            """,
                            partId,
                            orderId,
                            pieceNumber);
            return n > 0;
        } catch (DataAccessException ex) {
            try {
                int n =
                        jdbc.update(
                                """
                                INSERT INTO piezas (partid, orderid, numero_pieza, escaneado)
                                VALUES (?, ?, ?, FALSE)
                                """,
                                partId,
                                orderId,
                                pieceNumber);
                return n > 0;
            } catch (DataAccessException ignored) {
                try {
                    int n =
                            jdbc.update(
                                    """
                                    INSERT INTO piezas (partid, numero_pieza, escaneado)
                                    VALUES (?, ?, FALSE)
                                    """,
                                    partId,
                                    pieceNumber);
                    return n > 0;
                } catch (DataAccessException ignored2) {
                    return false;
                }
            }
        }
    }

    /**
     * Siguiente número de pieza aún no cortada, acotado a {@code partes.cantidad}.
     * No inventa números por encima del plan (evita 1→7 fantasmas).
     *
     * @return número 1..cantidad, o {@code null} si ya no hay hueco dentro del plan
     */
    public Integer nextPieceNumber(long partId) {
        int qty = 0;
        try {
            List<Map<String, Object>> partRows =
                    jdbc.queryForList("SELECT cantidad FROM partes WHERE partid = ? LIMIT 1", partId);
            if (!partRows.isEmpty() && partRows.getFirst().get("cantidad") instanceof Number n) {
                qty = Math.max(0, n.intValue());
            }
        } catch (DataAccessException ignored) {
            qty = 0;
        }

        List<Map<String, Object>> pending =
                jdbc.queryForList(
                        """
                        SELECT MIN(numero_pieza) AS n
                        FROM piezas
                        WHERE partid = ?
                          AND COALESCE(cortada, FALSE) = FALSE
                          AND (? <= 0 OR numero_pieza <= ?)
                        """,
                        partId,
                        qty,
                        qty);
        if (!pending.isEmpty() && pending.getFirst().get("n") != null) {
            int n = ((Number) pending.getFirst().get("n")).intValue();
            if (qty <= 0 || n <= qty) {
                return n;
            }
        }

        List<Map<String, Object>> maxRows =
                jdbc.queryForList(
                        """
                        SELECT COALESCE(MAX(numero_pieza), 0) AS n
                        FROM piezas
                        WHERE partid = ?
                          AND (? <= 0 OR numero_pieza <= ?)
                        """,
                        partId,
                        qty,
                        qty);
        int max = 0;
        if (!maxRows.isEmpty() && maxRows.getFirst().get("n") != null) {
            max = ((Number) maxRows.getFirst().get("n")).intValue();
        }
        if (qty > 0) {
            if (max >= qty) {
                return null;
            }
            return max + 1;
        }
        // Sin cantidad conocida: no inventar más allá de lo existente + 1 una sola vez.
        return max + 1;
    }

    /**
     * Elige número de pieza para un corte nuevo.
     * <ul>
     *   <li>Si viene override del agente → ese número
     *   <li>Si la misma parte se marcó hace ≤2s (evento duplicado mismo segundo) → misma pieza (recorte)
     *   <li>Si no → siguiente libre 1..cantidad
     *   <li>Si el plan está lleno → última pieza (recorte), nunca inventa fuera de cantidad
     * </ul>
     */
    public Integer resolvePieceNumberForCut(long partId, Integer pieceOverride) {
        if (pieceOverride != null && pieceOverride > 0) {
            return pieceOverride;
        }
        Integer recent = lastCortadaPieceIfWithinSeconds(partId, 2);
        if (recent != null) {
            return recent;
        }
        Integer next = nextPieceNumber(partId);
        if (next != null) {
            return next;
        }
        Integer qty = partCantidad(partId);
        return qty != null && qty > 0 ? qty : null;
    }

    /** Última pieza cortada de la parte si {@code cortada_at} está dentro de {@code withinSeconds}. */
    public Integer lastCortadaPieceIfWithinSeconds(long partId, int withinSeconds) {
        int safe = Math.max(1, Math.min(withinSeconds, 60));
        try {
            List<Map<String, Object>> rows =
                    jdbc.queryForList(
                            """
                            SELECT numero_pieza
                            FROM piezas
                            WHERE partid = ?
                              AND COALESCE(cortada, FALSE) = TRUE
                              AND cortada_at IS NOT NULL
                              AND cortada_at >= CURRENT_TIMESTAMP - (? || ' seconds')::interval
                            ORDER BY cortada_at DESC, numero_pieza DESC
                            LIMIT 1
                            """,
                            partId,
                            String.valueOf(safe));
            if (!rows.isEmpty() && rows.getFirst().get("numero_pieza") instanceof Number n) {
                return n.intValue();
            }
        } catch (DataAccessException ignored) {
            // columna ausente
        }
        return null;
    }

    /**
     * Marca pieza cortada por el seccionador. Idempotente por defecto (sync/poll no infla contador).
     * Con {@code allowRecorte=true}, un segundo corte sobre la misma pieza sube {@code corte_count} (morado).
     */
    public Map<String, Object> markPiezaCortada(long partId, int pieceNumber, String machineName) {
        return markPiezaCortada(partId, pieceNumber, machineName, false);
    }

    public Map<String, Object> markPiezaCortada(
            long partId, int pieceNumber, String machineName, boolean allowRecorte) {
        List<Map<String, Object>> rows;
        try {
            rows =
                    jdbc.queryForList(
                            """
                            SELECT piezaid, numero_pieza,
                                   COALESCE(cortada, FALSE) AS cortada,
                                   COALESCE(corte_count, 0) AS corte_count
                            FROM piezas
                            WHERE partid = ? AND numero_pieza = ?
                            LIMIT 1
                            """,
                            partId,
                            pieceNumber);
        } catch (DataAccessException ex) {
            rows =
                    jdbc.queryForList(
                            """
                            SELECT piezaid, numero_pieza, COALESCE(cortada, FALSE) AS cortada
                            FROM piezas
                            WHERE partid = ? AND numero_pieza = ?
                            LIMIT 1
                            """,
                            partId,
                            pieceNumber);
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> piece = rows.getFirst();
        long piezaId = ((Number) piece.get("piezaid")).longValue();
        boolean already = Boolean.TRUE.equals(piece.get("cortada"))
                || "t".equalsIgnoreCase(String.valueOf(piece.get("cortada")))
                || "true".equalsIgnoreCase(String.valueOf(piece.get("cortada")));
        int prevCount = 0;
        if (piece.get("corte_count") instanceof Number n) {
            prevCount = Math.max(0, n.intValue());
        } else if (already) {
            prevCount = 1;
        }
        String por = machineName != null && !machineName.isBlank() ? machineName.trim() : null;
        int newCount = already ? Math.max(prevCount, 1) : 0;
        if (!already) {
            newCount = 1;
            try {
                jdbc.update(
                        """
                        UPDATE piezas
                        SET cortada = TRUE,
                            cortada_at = CURRENT_TIMESTAMP,
                            cortada_por = ?,
                            corte_count = 1,
                            corte_error = FALSE,
                            corte_error_at = NULL,
                            corte_error_msg = NULL
                        WHERE piezaid = ?
                          AND COALESCE(cortada, FALSE) = FALSE
                        """,
                        por,
                        piezaId);
            } catch (DataAccessException ex) {
                try {
                    jdbc.update(
                            """
                            UPDATE piezas
                            SET cortada = TRUE,
                                cortada_at = CURRENT_TIMESTAMP,
                                cortada_por = ?,
                                corte_count = 1
                            WHERE piezaid = ?
                              AND COALESCE(cortada, FALSE) = FALSE
                            """,
                            por,
                            piezaId);
                } catch (DataAccessException ex2) {
                    jdbc.update(
                            """
                            UPDATE piezas
                            SET cortada = TRUE,
                                cortada_at = CURRENT_TIMESTAMP,
                                cortada_por = ?
                            WHERE piezaid = ?
                              AND COALESCE(cortada, FALSE) = FALSE
                            """,
                            por,
                            piezaId);
                }
            }
        } else if (allowRecorte) {
            newCount = Math.max(prevCount, 1) + 1;
            try {
                jdbc.update(
                        """
                        UPDATE piezas
                        SET corte_count = ?,
                            cortada_at = CURRENT_TIMESTAMP,
                            cortada_por = COALESCE(?, cortada_por),
                            corte_error = FALSE,
                            corte_error_at = NULL,
                            corte_error_msg = NULL
                        WHERE piezaid = ?
                        """,
                        newCount,
                        por,
                        piezaId);
            } catch (DataAccessException ex) {
                // sin columna corte_count
            }
        }
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("piezaId", piezaId);
        out.put("numeroPieza", pieceNumber);
        out.put("partId", partId);
        out.put("already", already);
        out.put("updated", !already);
        out.put("recorte", already && allowRecorte);
        out.put("cortada", true);
        out.put("corteCount", newCount > 0 ? newCount : 1);
        return out;
    }

    /**
     * Marca error visual de captura (rojo en UI). No toca escaneado ni estado de la orden.
     * No marca {@code cortada}.
     */
    public Map<String, Object> markPiezaCorteError(long partId, int pieceNumber, String message) {
        if (pieceNumber <= 0 || !ensurePiezaRow(partId, pieceNumber)) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT piezaid,
                               COALESCE(cortada, FALSE) AS cortada,
                               COALESCE(escaneado, FALSE) AS escaneado
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
        boolean cortada = Boolean.TRUE.equals(piece.get("cortada"))
                || "t".equalsIgnoreCase(String.valueOf(piece.get("cortada")))
                || "true".equalsIgnoreCase(String.valueOf(piece.get("cortada")));
        boolean escaneado = Boolean.TRUE.equals(piece.get("escaneado"))
                || "t".equalsIgnoreCase(String.valueOf(piece.get("escaneado")))
                || "true".equalsIgnoreCase(String.valueOf(piece.get("escaneado")));
        // Ya cortada o escaneada: no pintar error encima del avance real.
        if (cortada || escaneado) {
            return null;
        }
        String msg = message != null ? truncate(message.trim(), 240) : "Error al capturar pieza";
        try {
            jdbc.update(
                    """
                    UPDATE piezas
                    SET corte_error = TRUE,
                        corte_error_at = CURRENT_TIMESTAMP,
                        corte_error_msg = ?
                    WHERE piezaid = ?
                      AND COALESCE(cortada, FALSE) = FALSE
                      AND COALESCE(escaneado, FALSE) = FALSE
                    """,
                    msg,
                    ((Number) piece.get("piezaid")).longValue());
        } catch (DataAccessException ex) {
            // Columna ausente: no-op (schema aligner la creará en el próximo arranque).
            return null;
        }
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("piezaId", ((Number) piece.get("piezaid")).longValue());
        out.put("numeroPieza", pieceNumber);
        out.put("partId", partId);
        out.put("corteError", true);
        out.put("corteErrorMsg", msg);
        return out;
    }

    public List<Map<String, Object>> listTrazabilidad(String opCodigo, Long orderId, int limit) {
        return listTrazabilidad(opCodigo, orderId, limit, false);
    }

    /**
     * @param soloCorte si true, solo filas {@code CORTE_INICIO}/{@code CORTE_FIN} (tiempos de corte).
     */
    public List<Map<String, Object>> listTrazabilidad(
            String opCodigo, Long orderId, int limit, boolean soloCorte) {
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
        String corteFilter =
                soloCorte
                        ? " AND UPPER(TRIM(accion)) IN ('CORTE_INICIO', 'CORTE_FIN') "
                        : "";
        if (orderId != null && op != null && !op.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE (orderid = ? OR UPPER(TRIM(op_codigo)) = UPPER(TRIM(?)))
                    """
                            + corteFilter
                            + """
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
                    """
                            + corteFilter
                            + """
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
                    """
                            + corteFilter
                            + """
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
                WHERE 1=1
                """
                        + corteFilter
                        + """
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
