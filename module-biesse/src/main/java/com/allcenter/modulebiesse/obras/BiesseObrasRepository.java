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
    @Value("${app.biesse.seguimiento-since:2026-09-09}")
    private LocalDate seguimientoSince;

    public Map<String, Object> findOrderById(long orderId) {
        try {
            List<Map<String, Object>> rows =
                    jdbc.queryForList(
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                   nparts, partes_totales, fechacreacion, fecha_modificacion
                            FROM ordenes
                            WHERE orderid = ?
                            """,
                            orderId);
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (DataAccessException ex) {
            log.warn("findOrderById({}) extras falló: {}", orderId, ex.getMostSpecificCause().getMessage());
            try {
                List<Map<String, Object>> rows =
                        jdbc.queryForList(
                                """
                                SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                       fechacreacion, fecha_modificacion
                                FROM ordenes
                                WHERE orderid = ?
                                """,
                                orderId);
                return rows.isEmpty() ? null : rows.getFirst();
            } catch (DataAccessException ex2) {
                try {
                    List<Map<String, Object>> rows =
                            jdbc.queryForList(
                                    """
                                    SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                           fechacreacion
                                    FROM ordenes
                                    WHERE orderid = ?
                                    """,
                                    orderId);
                    return rows.isEmpty() ? null : rows.getFirst();
                } catch (DataAccessException ex3) {
                    List<Map<String, Object>> rows =
                            jdbc.queryForList(
                                    """
                                    SELECT orderid, ordername, bookingcode
                                    FROM ordenes
                                    WHERE orderid = ?
                                    """,
                                    orderId);
                    return rows.isEmpty() ? null : rows.getFirst();
                }
            }
        }
    }

    public record OrderJobMatch(
            Map<String, Object> order, boolean ambiguous, List<Map<String, Object>> candidates) {}

    /** Diagnóstico: filas en {@code ordenes} visibles para este datasource. */
    public int countOrdenes() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ordenes", Integer.class);
        return n == null ? 0 : n;
    }

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

        // Igualdad literal primero (sin funciones) — el caso «BLANCO BLANCO» debe resolver
        // aunque fallen CHR/TRIM/op_codigo en el SQL preferido.
        List<Map<String, Object>> literal =
                queryOrdersBase(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo
                        FROM ordenes
                        WHERE ordername ILIKE ?
                           OR bookingcode ILIKE ?
                        ORDER BY orderid DESC
                        LIMIT 5
                        """,
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE ordername ILIKE ?
                           OR bookingcode ILIKE ?
                        ORDER BY orderid DESC
                        LIMIT 5
                        """,
                        token,
                        token);
        OrderJobMatch fromLiteral = finishMatch(literal, token, op, "literal-ilike");
        if (fromLiteral != null) {
            return fromLiteral;
        }

        // Igualdad literal: respeta '_' y espacios del job (solo case-insensitive).
        List<Map<String, Object>> exact =
                queryOrdersBase(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo
                        FROM ordenes
                        WHERE UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?)
                           OR (bookingcode IS NOT NULL
                               AND UPPER(TRIM(BOTH FROM REPLACE(bookingcode, CHR(160), ' '))) = UPPER(?))
                        ORDER BY orderid DESC
                        LIMIT 10
                        """,
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?)
                           OR (bookingcode IS NOT NULL
                               AND UPPER(TRIM(BOTH FROM REPLACE(bookingcode, CHR(160), ' '))) = UPPER(?))
                        ORDER BY orderid DESC
                        LIMIT 10
                        """,
                        token,
                        token);
        OrderJobMatch fromExact = finishMatch(exact, token, op, "exact");
        if (fromExact != null) {
            return fromExact;
        }

        // Frase completa ANTES de tokens sueltos: "BLANCO BLANCO" no debe caer en
        // "S13336 … DUROLAC BLANCO" por un solo token BLANCO.
        List<Map<String, Object>> phraseHits = queryOrdersIlikeContains(token);
        List<Map<String, Object>> phraseForMatch =
                isWeakJobNameForLooseMatch(token, op)
                        ? onlyExactName(phraseHits, token, compact)
                        : preferExactName(phraseHits, token, compact);
        OrderJobMatch fromPhrase = finishMatch(phraseForMatch, token, op, "phrase");
        if (fromPhrase != null) {
            return fromPhrase;
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        if (op != null) {
            candidates.addAll(queryOrdersByOpPrefix(op));
        }
        if (candidates.isEmpty() && op != null) {
            candidates.addAll(
                    queryOrdersBase(
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo
                            FROM ordenes
                            WHERE UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) = UPPER(?)
                               OR UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) LIKE UPPER(?) || '%'
                               OR UPPER(?) LIKE UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) || '%'
                            ORDER BY orderid DESC
                            LIMIT 40
                            """,
                            """
                            SELECT orderid, ordername, bookingcode
                            FROM ordenes
                            WHERE UPPER(REPLACE(REPLACE(COALESCE(ordername, ''), '_', ''), ' ', '')) = UPPER(?)
                               OR UPPER(REPLACE(REPLACE(COALESCE(ordername, ''), '_', ''), ' ', '')) LIKE UPPER(?) || '%'
                               OR UPPER(?) LIKE UPPER(REPLACE(REPLACE(COALESCE(ordername, ''), '_', ''), ' ', '')) || '%'
                            ORDER BY orderid DESC
                            LIMIT 40
                            """,
                            compact,
                            compact,
                            compact));
        }
        OrderJobMatch fromCand = finishMatch(candidates, token, op, "op/compact");
        if (fromCand != null) {
            return fromCand;
        }

        List<Map<String, Object>> loose = List.of();
        // Jobs cortos / sin OP / tokens duplicados ("BLANCO BLANCO"): no buscar por token
        // suelto — demasiado fácil empatar con cualquier obra que diga BLANCO.
        if (isWeakJobNameForLooseMatch(token, op)) {
            log.info(
                    "resolveOrderForJob skip-loose (job débil) job='{}' op={} — solo exact/phrase",
                    token,
                    op);
        } else {
            loose = queryOrdersLooseByName(token, compact);
            OrderJobMatch fromLoose = finishMatch(loose, token, op, "loose-tokens");
            if (fromLoose != null) {
                return fromLoose;
            }
        }

        // Prefijo OP solo (31313%) — tolera que el resto del nombre difiera (18MM vs 18 MM).
        // Nunca para jobs débiles sin OP («BLANCO BLANCO»).
        if (op != null && !isWeakJobNameForLooseMatch(token, op)) {
            List<Map<String, Object>> byOp = queryOrdersByOpPrefix(op);
            OrderJobMatch fromOp = finishMatch(byOp, token, op, "op-prefix");
            if (fromOp != null) {
                return fromOp;
            }
            if (!byOp.isEmpty()) {
                MatchPick pick = pickBestOrderMatchDetailed(byOp, token, op);
                if (pick.order() != null && !pick.ambiguous()) {
                    log.info(
                            "resolveOrderForJob op-prefix pick job='{}' → orderid={} score={}",
                            token,
                            pick.order().get("orderid"),
                            pick.score());
                    return new OrderJobMatch(pick.order(), false, byOp);
                }
                return new OrderJobMatch(null, true, byOp);
            }
        }

        // Probe: ¿hay filas en ordenes? (diagnóstico en logs)
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ordenes", Integer.class);
            log.warn(
                    "resolveOrderForJob SIN MATCH job='{}' op={} ordenes.count={} phrase={} loose={}",
                    token,
                    op,
                    n,
                    phraseHits.size(),
                    loose.size());
        } catch (DataAccessException ex) {
            log.warn(
                    "resolveOrderForJob SIN MATCH y COUNT ordenes falló: {}",
                    ex.getMostSpecificCause().getMessage());
        }

        return new OrderJobMatch(
                null,
                false,
                !phraseHits.isEmpty() ? phraseHits : (loose.isEmpty() ? candidates : loose));
    }

    /** Si hay match usable, lo envuelve; si ambigua con filas, también; si vacío → null. */
    private OrderJobMatch finishMatch(
            List<Map<String, Object>> rows, String token, String op, String stage) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        // Empates de nombre exacto (reimport): siempre la más reciente. Nunca "ambigua".
        List<Map<String, Object>> exactOnly =
                onlyExactName(rows, token, compactName(token));
        if (!exactOnly.isEmpty()) {
            Map<String, Object> newest = newestOrder(exactOnly);
            log.info(
                    "resolveOrderForJob {} OK (exact-name) job='{}' → orderid={} name='{}' ({} exactas)",
                    stage,
                    token,
                    newest.get("orderid"),
                    newest.get("ordername"),
                    exactOnly.size());
            return new OrderJobMatch(newest, false, exactOnly);
        }
        // Jobs débiles («BLANCO BLANCO»): sin exacto → no alternativas por token/score.
        if (isWeakJobNameForLooseMatch(token, op)) {
            log.info(
                    "resolveOrderForJob {} débil sin exacto job='{}' candidatos={} → no-match",
                    stage,
                    token,
                    rows.size());
            return null;
        }
        MatchPick pick = pickBestOrderMatchDetailed(rows, token, op);
        if (pick.order() != null && !pick.ambiguous()) {
            log.info(
                    "resolveOrderForJob {} OK job='{}' → orderid={} name='{}'",
                    stage,
                    token,
                    pick.order().get("orderid"),
                    pick.order().get("ordername"));
            return new OrderJobMatch(pick.order(), false, rows);
        }
        if (pick.ambiguous()) {
            return new OrderJobMatch(null, true, rows);
        }
        // Un solo candidato solo si el nombre/booking es exacto (ya cubierto arriba) —
        // no adoptar un único falso amigo.
        if (pick.order() != null) {
            return new OrderJobMatch(pick.order(), false, rows);
        }
        return null;
    }

    /** orderid más alto (obra más reciente). */
    private static Map<String, Object> newestOrder(List<Map<String, Object>> rows) {
        Map<String, Object> best = rows.getFirst();
        long bestId = toLongId(best.get("orderid"));
        for (Map<String, Object> row : rows) {
            long id = toLongId(row.get("orderid"));
            if (id > bestId) {
                bestId = id;
                best = row;
            }
        }
        return best;
    }

    private static long toLongId(Object id) {
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id == null) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(String.valueOf(id).trim());
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
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
        OrderJobMatch m = finishMatch(normalized, token, op, "web-rows");
        if (m != null) {
            return m;
        }
        return new OrderJobMatch(null, false, normalized);
    }

    private List<Map<String, Object>> queryOrdersIlikeContains(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        String like = "%" + token + "%";
        try {
            // Igualdad exacta primero (evita que LIMIT 40 se llene de obras con "BLANCO").
            return jdbc.queryForList(
                    """
                    SELECT orderid, ordername, bookingcode, op_codigo
                    FROM ordenes
                    WHERE REPLACE(COALESCE(ordername, ''), CHR(160), ' ') ILIKE ?
                       OR REPLACE(COALESCE(bookingcode, ''), CHR(160), ' ') ILIKE ?
                    ORDER BY
                      CASE
                        WHEN UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?)
                          OR UPPER(TRIM(BOTH FROM REPLACE(COALESCE(bookingcode, ''), CHR(160), ' '))) = UPPER(?)
                        THEN 0 ELSE 1
                      END,
                      orderid DESC
                    LIMIT 40
                    """,
                    like,
                    like,
                    token,
                    token);
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob ILIKE falló: {}", ex.getMostSpecificCause().getMessage());
            try {
                return jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE REPLACE(COALESCE(ordername, ''), CHR(160), ' ') ILIKE ?
                           OR ordername ILIKE ?
                        ORDER BY
                          CASE
                            WHEN UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?)
                            THEN 0 ELSE 1
                          END,
                          orderid DESC
                        LIMIT 40
                        """,
                        like,
                        like,
                        token);
            } catch (DataAccessException ex2) {
                log.warn(
                        "resolveOrderForJob ILIKE mínimo falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                return List.of();
            }
        }
    }

    private List<Map<String, Object>> queryOrdersByOpPrefix(String op) {
        if (op == null || op.isBlank()) {
            return List.of();
        }
        try {
            return jdbc.queryForList(
                    """
                    SELECT orderid, ordername, bookingcode, op_codigo
                    FROM ordenes
                    WHERE UPPER(TRIM(COALESCE(op_codigo, ''))) = UPPER(?)
                       OR REPLACE(COALESCE(ordername, ''), CHR(160), ' ') ILIKE ?
                       OR REPLACE(COALESCE(ordername, ''), CHR(160), ' ') ILIKE ?
                       OR ordername ILIKE ?
                       OR ordername ILIKE ?
                    ORDER BY orderid DESC
                    LIMIT 40
                    """,
                    op,
                    op + " %",
                    op + "%",
                    op + " %",
                    op + "%");
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob op-prefix falló: {}", ex.getMostSpecificCause().getMessage());
            try {
                return jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode
                        FROM ordenes
                        WHERE ordername ILIKE ?
                           OR ordername ILIKE ?
                        ORDER BY orderid DESC
                        LIMIT 40
                        """,
                        op + " %",
                        op + "%");
            } catch (DataAccessException ex2) {
                log.warn(
                        "resolveOrderForJob op-prefix bare falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                return List.of();
            }
        }
    }

    /**
     * Ejecuta SQL preferido; si falla (columna ausente / CHR / etc.), usa el SQL mínimo.
     * Ambos SQL deben tener el mismo número de {@code ?} (mismos args).
     */
    private List<Map<String, Object>> queryOrdersBase(
            String sqlPreferred, String sqlBare, Object... args) {
        try {
            return jdbc.queryForList(sqlPreferred, args);
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob SQL preferido falló: {}", ex.getMostSpecificCause().getMessage());
            try {
                return jdbc.queryForList(sqlBare, args);
            } catch (DataAccessException ex2) {
                log.warn(
                        "resolveOrderForJob SQL bare falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                return List.of();
            }
        }
    }

    /**
     * Misma idea que la lista web ({@code findOrders}): tokens del job en ordername.
     * Si el AND estricto no da, reintenta con tokens largos (>=3) y sin unidades pegadas (18MM→18,MM).
     */
    private List<Map<String, Object>> queryOrdersLooseByName(String token, String compact) {
        List<Map<String, Object>> hit = queryOrdersByTokens(jobSearchTokens(token), token, compact);
        if (!hit.isEmpty()) {
            return hit;
        }
        // 18MM → 18 + MM, etc.
        hit = queryOrdersByTokens(jobSearchTokensSplitUnits(token), token, compact);
        if (!hit.isEmpty()) {
            return hit;
        }
        // Solo tokens >= 3 chars (evita que "19"/"18" rompan el AND).
        String[] longTok =
                java.util.Arrays.stream(jobSearchTokensSplitUnits(token))
                        .filter(t -> t.length() >= 3)
                        .toArray(String[]::new);
        return queryOrdersByTokens(longTok, token, compact);
    }

    private List<Map<String, Object>> queryOrdersByTokens(
            String[] tokens, String token, String compact) {
        if (tokens == null || tokens.length == 0) {
            return List.of();
        }
        String nameNorm =
                "trim(regexp_replace(REPLACE(COALESCE(ordername, ''), CHR(160), ' '), '[^[:alnum:]]+', ' ', 'g'))";
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo
                        FROM ordenes
                        WHERE (
                        """);
        List<Object> args = new ArrayList<>();
        sql.append(" UPPER(TRIM(BOTH FROM REPLACE(COALESCE(ordername, ''), CHR(160), ' '))) = UPPER(?) ");
        args.add(token);
        sql.append(
                " OR UPPER(REPLACE(REPLACE(REPLACE(COALESCE(ordername, ''), CHR(160), ''), '_', ''), ' ', '')) = UPPER(?) ");
        args.add(compact);
        sql.append(" OR ( ");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append("((' ' || lower(")
                    .append(nameNorm)
                    .append(") || ' ') LIKE ('% ' || lower(?) || ' %'))");
            args.add(tokens[i]);
        }
        sql.append(" ) ) ORDER BY orderid DESC LIMIT 40 ");
        try {
            return jdbc.queryForList(sql.toString(), args.toArray());
        } catch (DataAccessException ex) {
            log.warn("resolveOrderForJob tokens falló: {}", ex.getMostSpecificCause().getMessage());
            try {
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
                        "resolveOrderForJob tokens mínimo falló: {}",
                        ex2.getMostSpecificCause().getMessage());
                return List.of();
            }
        }
    }

    /** Tokens alfanuméricos del job (igual que búsqueda web de obras). Dedup. */
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
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (String part : norm.split("\\s+")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                unique.add(t.toUpperCase(Locale.ROOT));
            }
        }
        return unique.toArray(String[]::new);
    }

    /**
     * Jobs sin OP y con poca señal (p.ej. "BLANCO BLANCO" → un solo token único) no deben
     * resolverse por AND de tokens sueltos: cualquier obra con "BLANCO" empataría.
     */
    private static boolean isWeakJobNameForLooseMatch(String token, String op) {
        if (op != null && !op.isBlank()) {
            return false;
        }
        String[] raw =
                token == null || token.isBlank()
                        ? new String[0]
                        : token.trim().split("\\s+");
        String[] unique = jobSearchTokens(token);
        if (unique.length == 0) {
            return true;
        }
        // Un solo token distintivo, o tokens duplicados que colapsan a 1.
        if (unique.length <= 1) {
            return true;
        }
        // Tras dedupe queda mucho menos señal que el nombre original.
        return unique.length < raw.length && unique.length <= 2;
    }

    /** Prioriza igualdad exacta/compact dentro de un hit ILIKE. */
    private static List<Map<String, Object>> preferExactName(
            List<Map<String, Object>> rows, String token, String compact) {
        List<Map<String, Object>> exact = onlyExactName(rows, token, compact);
        if (!exact.isEmpty()) {
            return exact;
        }
        return rows == null ? List.of() : rows;
    }

    /** Solo filas con ordername/booking exactamente igual al job (respeta espacios y `_`). */
    private static List<Map<String, Object>> onlyExactName(
            List<Map<String, Object>> rows, String token, String compact) {
        if (rows == null || rows.isEmpty() || token == null) {
            return List.of();
        }
        String jobNorm = normalizeForCompare(token);
        List<Map<String, Object>> exact = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String nameNorm = normalizeForCompare(str(row.get("ordername")));
            String bookNorm = normalizeForCompare(str(row.get("bookingcode")));
            // No usar compact (quita espacios/_): «blanco_blanco» ≠ «blanco blanco».
            if (nameNorm.equals(jobNorm) || bookNorm.equals(jobNorm)) {
                exact.add(row);
            }
        }
        return exact;
    }

    /** Como {@link #jobSearchTokens} pero parte 18MM → 18 + MM. */
    private static String[] jobSearchTokensSplitUnits(String query) {
        String[] base = jobSearchTokens(query);
        List<String> out = new ArrayList<>();
        for (String t : base) {
            Matcher m = Pattern.compile("(?i)^(\\d+)([A-Z]+)$").matcher(t);
            if (m.matches()) {
                out.add(m.group(1));
                out.add(m.group(2));
            } else {
                out.add(t);
            }
        }
        return out.toArray(String[]::new);
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
            if (nameNorm.equals(jobNorm)) {
                score += 1000;
            }
            // Contiene frase completa del job (no solo una palabra).
            if (!jobNorm.isBlank()
                    && jobNorm.length() >= 5
                    && nameNorm.contains(jobNorm)
                    && !nameNorm.equals(jobNorm)) {
                score += 400;
            }
            if (!jobCompact.isBlank()
                    && jobCompact.length() >= 5
                    && nameCompact.contains(jobCompact)
                    && !nameCompact.equals(jobCompact)) {
                score += 300;
            }
            String jobTail = lastWord(jobNorm);
            String nameTail = lastWord(nameNorm);
            // lastWord solo ayuda si el job tiene OP o ya hay señal fuerte de overlap.
            int overlap = tokenOverlapScore(jobNorm, nameNorm);
            if (jobTail.length() >= 3 && jobTail.equals(nameTail) && (op != null || overlap >= 2)) {
                score += 500;
            } else if (jobTail.length() >= 3
                    && (jobNorm.contains(nameTail) || nameNorm.contains(jobTail))
                    && op != null) {
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
            score += overlap * 20;
            // Penalizar nombres mucho más largos cuando el job no trae OP (falsos +BLANCO).
            if (op == null && !jobNorm.isBlank() && nameNorm.length() > jobNorm.length() + 12) {
                score -= 350;
            }
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
        // Sin OP y sin match exacto: no aceptar lastWord/overlap débil (caso BLANCO BLANCO → S13336).
        // Tampoco marcar ambigua: el caller debe tratarlo como no-match.
        if (op == null && bestScore < 800) {
            return new MatchPick(null, bestScore, false);
        }
        boolean weak = candidates.size() > 1 && bestScore < 200;
        boolean tied = tiedAtBest > 1;
        boolean ambiguous = weak || tied;
        if (ambiguous) {
            return new MatchPick(null, bestScore, true);
        }
        return new MatchPick(best, bestScore, false);
    }

    /** Comparación de nombres: respeta `_` y demás símbolos; solo normaliza NBSP/espacios. */
    private static String normalizeForCompare(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r\\n]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    /** Token distintivo tipo K5IZQ / K5DER / K1DER (ignora espacios/_ solo para la clave K). */
    private static String significantKey(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return "";
        }
        String compact = normalizedName.replace(" ", "").replace("_", "");
        Matcher m2 = Pattern.compile("(K\\d+[A-Z]+)", Pattern.CASE_INSENSITIVE).matcher(compact);
        return m2.find() ? m2.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static String normalizeJobToken(String jobName) {
        // OSI / copiar-pegar: NBSP, zero-width, guiones raros y espacios dobles.
        // NO convierte '_' → espacio: «blanco_blanco» ≠ «blanco blanco».
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
                        .replaceAll("[ \\t\\x0B\\f\\r\\n]+", " ");
        // Quitar sufijo de patrón tipo ".001" pegado al nombre.
        Matcher suffix = PATTERN_SUFFIX.matcher(t);
        if (suffix.find()) {
            t = t.substring(0, t.length() - suffix.group().length()).trim();
        }
        return t;
    }

    private static final Pattern PATTERN_SUFFIX = Pattern.compile("\\.(\\d{3})$");

    /** Compacto solo para búsqueda por OP / candidatos; NO usar para decidir obra exacta. */
    private static String compactName(String value) {
        if (value == null) {
            return "";
        }
        // NBSP (\u00A0) NO entra en \s de Java — hay que normalizarlo antes.
        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("[\\s_]+", "")
                .toUpperCase(Locale.ROOT);
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
                        : (seguimientoSince != null ? seguimientoSince : LocalDate.of(2026, 9, 9));
        List<Map<String, Object>> rows;
        try {
            rows =
                    jdbc.queryForList(
                            """
                            SELECT o.orderid, o.ordername, o.bookingcode, o.op_codigo, o.estado_escaneo,
                                   o.fechacreacion, o.fecha_modificacion,
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
        LocalDate limaToday = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        for (Map<String, Object> row : rows) {
            Map<String, Object> card = toSeguimientoCard(row);
            // Entregado: solo del día (Lima); al día siguiente el tablero queda limpio.
            if (ESTADO_ENTREGADO.equals(str(card.get("estadoEscaneo")))) {
                Object fe = card.get("estadoDesde");
                if (fe == null) {
                    fe = row.get("fecha_modificacion");
                }
                if (fe == null) {
                    fe = row.get("fechacreacion");
                }
                LocalDate d = toLocalDateLima(fe);
                if (d == null || !d.equals(limaToday)) {
                    continue;
                }
            }
            out.add(card);
        }
        return out;
    }

    private static LocalDate toLocalDateLima(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof java.time.Instant instant) {
            return LocalDate.ofInstant(instant, java.time.ZoneId.of("America/Lima"));
        }
        if (value instanceof java.util.Date date) {
            return LocalDate.ofInstant(date.toInstant(), java.time.ZoneId.of("America/Lima"));
        }
        try {
            String s = String.valueOf(value).trim();
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
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
        String stored = normalizeEstadoForUi(str(row.get("estado_escaneo")));
        String seccionador = blankToNull(str(row.get("seccionador")));
        String estado =
                reconcileSeguimientoEstado(
                        stored, totalPartes, partesEsc, piezasTot, piezasEsc, piezasCor, seccionador);
        // Sana BD si quedó LISTO/COMPLETADA sin escaneo real (p.ej. card 0%/0%).
        if (!estado.equals(stored) && ESTADO_LISTO.equals(stored)) {
            Long oid = null;
            Object idObj = row.get("orderid");
            if (idObj instanceof Number n) {
                oid = n.longValue();
            }
            if (oid != null) {
                healEstadoEscaneoIfStaleListo(oid, estado, stored);
            }
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
        obra.put("estado_escaneo", estado);
        obra.put("estadoEscaneo", estado);
        obra.put("fechacreacion", row.get("fechacreacion"));
        Object estadoDesde =
                row.get("fecha_modificacion") != null
                        ? row.get("fecha_modificacion")
                        : row.get("fechacreacion");
        obra.put("estadoDesde", estadoDesde);
        obra.put("estado_desde", estadoDesde);
        obra.put("fecha_modificacion", row.get("fecha_modificacion"));
        obra.put("porcentaje", pct);
        obra.put("avance_label", avance);
        obra.put("avanceLabel", avance);
        obra.put("porcentaje_corte", pctCorte);
        obra.put("porcentajeCorte", pctCorte);
        obra.put("avance_corte_label", avanceCorte);
        obra.put("avanceCorteLabel", avanceCorte);
        obra.put("seccionador", seccionador);
        obra.put("piezas_totales", piezasTot);
        obra.put("piezas_escaneadas", piezasEsc);
        obra.put("piezas_cortadas", piezasCor);
        obra.put("partes_totales", totalPartes);
        obra.put("partes_escaneadas", partesEsc);
        return obra;
    }

    /**
     * LISTO solo con escaneo al 100%. Si en BD quedó LISTO/COMPLETADA con 0% escaneo,
     * se degrada a DESPACHO / PRODUCCION / OPTIMIZADO según el avance real.
     */
    static String reconcileSeguimientoEstado(
            String stored,
            int totalPartes,
            int partesEsc,
            int piezasTot,
            int piezasEsc,
            int piezasCor,
            String seccionador) {
        String e = normalizeEstadoForUi(stored);
        if (ESTADO_ENTREGADO.equals(e)) {
            return ESTADO_ENTREGADO;
        }
        boolean scanDone =
                (piezasTot > 0 && piezasEsc >= piezasTot)
                        || (piezasTot <= 0 && totalPartes > 0 && partesEsc >= totalPartes);
        boolean scanPartial = piezasEsc > 0 || partesEsc > 0;
        boolean cutPartial = piezasCor > 0 || (seccionador != null && !seccionador.isBlank());

        if (scanDone) {
            return ESTADO_LISTO;
        }
        if (ESTADO_LISTO.equals(e)) {
            if (scanPartial) {
                return ESTADO_DESPACHO;
            }
            if (cutPartial) {
                return ESTADO_PRODUCCION;
            }
            return ESTADO_OPTIMIZADO;
        }
        if (scanPartial || ESTADO_DESPACHO.equals(e)) {
            return ESTADO_DESPACHO;
        }
        if (cutPartial && (e.isBlank() || ESTADO_OPTIMIZADO.equals(e) || "PENDIENTE".equals(e))) {
            return ESTADO_PRODUCCION;
        }
        return e.isBlank() ? "PENDIENTE" : e;
    }

    private void healEstadoEscaneoIfStaleListo(long orderId, String corrected, String previous) {
        try {
            int n =
                    jdbc.update(
                            """
                            UPDATE ordenes
                            SET estado_escaneo = ?,
                                fecha_modificacion = CURRENT_TIMESTAMP
                            WHERE orderid = ?
                              AND UPPER(TRIM(COALESCE(estado_escaneo, ''))) IN (
                                  'LISTO_PARA_ENTREGAR', 'COMPLETADA', 'COMPLETADO')
                            """,
                            corrected,
                            orderId);
            if (n > 0) {
                log.warn(
                        "heal LISTO sin escaneo orderId={} {} → {} (seguimiento)",
                        orderId,
                        previous,
                        corrected);
            }
        } catch (DataAccessException ex) {
            log.debug(
                    "heal LISTO orderId={} omitido: {}",
                    orderId,
                    ex.getMostSpecificCause().getMessage());
        }
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
