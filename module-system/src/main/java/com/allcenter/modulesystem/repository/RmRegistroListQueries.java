package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmActaConformidad;
import com.allcenter.modulesystem.model.RmRegistroEntrada;
import com.allcenter.modulesystem.model.RmRegistroSalida;
import com.allcenter.modulesystem.model.RmRegistroVehiculo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Listados RM paginados con JPQL dinámico: el filtro de texto solo se añade cuando hay búsqueda,
 * evitando inferencia bytea/text en PostgreSQL con columnas legacy.
 */
@Repository
@RequiredArgsConstructor
public class RmRegistroListQueries {

    private final EntityManager em;

    public Page<RmRegistroVehiculo> pageVehiculos(
            Pageable pageable, String qPattern, LocalDate fechaDesde, LocalDate fechaHasta, String tipoRegistro) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendDateRange(where, params, "v.fecha", fechaDesde, fechaHasta);
        appendTipoRegistro(where, params, "v.tiporegistro", tipoRegistro);
        if (qPattern != null) {
            where.append(
                    """
                     AND (
                       CONCAT('', v.numeroregistro, '') LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.placa AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.chofer AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.marca AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.tiporegistro AS string), '')) LIKE :qPattern OR
                       EXISTS (
                         SELECT 1 FROM RmRegistroEntrada e WHERE e.registroVehiculo = v AND (
                           LOWER(COALESCE(CAST(e.numeroGuia AS string), '')) LIKE :qPattern OR
                           LOWER(COALESCE(CAST(e.ocNumero AS string), '')) LIKE :qPattern
                         )
                       ) OR
                       EXISTS (
                         SELECT 1 FROM RmRegistroSalida s WHERE s.registroVehiculo = v AND (
                           LOWER(COALESCE(CAST(s.numeroGuia AS string), '')) LIKE :qPattern OR
                           LOWER(COALESCE(CAST(s.ocNumero AS string), '')) LIKE :qPattern
                         )
                       )
                     )""");
            params.put("qPattern", qPattern);
        }
        return page(
                "SELECT DISTINCT v FROM RmRegistroVehiculo v" + where + " ORDER BY v.createdAt DESC",
                "SELECT COUNT(DISTINCT v) FROM RmRegistroVehiculo v" + where,
                params,
                pageable,
                RmRegistroVehiculo.class);
    }

    public Page<RmRegistroEntrada> pageEntradas(
            Pageable pageable, String qPattern, LocalDate fechaDesde, LocalDate fechaHasta, String tipoRegistro) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendDateRange(where, params, "e.fecha", fechaDesde, fechaHasta);
        appendTipoRegistroJoin(where, params, tipoRegistro);
        if (qPattern != null) {
            where.append(
                    """
                     AND (
                       CONCAT('', e.numeroregistro, '') LIKE :qPattern OR
                       LOWER(COALESCE(CAST(e.ocNumero AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(e.numeroGuia AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.placa AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.chofer AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.marca AS string), '')) LIKE :qPattern OR
                       EXISTS (
                         SELECT 1 FROM Guia g WHERE g.id = e.guiaInventarioId AND (
                           LOWER(CAST(g.numeroGuia AS string)) LIKE :qPattern OR
                           LOWER(COALESCE(CAST(g.ordenCompra AS string), '')) LIKE :qPattern
                         )
                       )
                     )""");
            params.put("qPattern", qPattern);
        }
        String from = " FROM RmRegistroEntrada e LEFT JOIN FETCH e.registroVehiculo v";
        String countFrom = " FROM RmRegistroEntrada e LEFT JOIN e.registroVehiculo v";
        return page(
                "SELECT DISTINCT e" + from + where + " ORDER BY e.createdAt DESC",
                "SELECT COUNT(DISTINCT e)" + countFrom + where,
                params,
                pageable,
                RmRegistroEntrada.class);
    }

    public Page<RmRegistroSalida> pageSalidas(
            Pageable pageable, String qPattern, LocalDate fechaDesde, LocalDate fechaHasta, String tipoRegistro) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendDateRange(where, params, "s.fecha", fechaDesde, fechaHasta);
        appendTipoRegistroJoinSalida(where, params, tipoRegistro);
        if (qPattern != null) {
            where.append(
                    """
                     AND (
                       CONCAT('', s.numeroregistro, '') LIKE :qPattern OR
                       LOWER(COALESCE(CAST(s.numeroGuia AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(s.ocNumero AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.placa AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.chofer AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(v.marca AS string), '')) LIKE :qPattern OR
                       EXISTS (
                         SELECT 1 FROM Guia g WHERE g.id = s.guiaInventarioId AND (
                           LOWER(CAST(g.numeroGuia AS string)) LIKE :qPattern OR
                           LOWER(COALESCE(CAST(g.ordenCompra AS string), '')) LIKE :qPattern
                         )
                       )
                     )""");
            params.put("qPattern", qPattern);
        }
        String from = " FROM RmRegistroSalida s LEFT JOIN FETCH s.registroVehiculo v";
        String countFrom = " FROM RmRegistroSalida s LEFT JOIN s.registroVehiculo v";
        return page(
                "SELECT DISTINCT s" + from + where + " ORDER BY s.createdAt DESC",
                "SELECT COUNT(DISTINCT s)" + countFrom + where,
                params,
                pageable,
                RmRegistroSalida.class);
    }

    public Page<RmActaConformidad> pageActas(Pageable pageable, String qPattern, Instant desde, Instant hasta) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (desde != null) {
            where.append(" AND a.createdAt >= :desde");
            params.put("desde", desde);
        }
        if (hasta != null) {
            where.append(" AND a.createdAt <= :hasta");
            params.put("hasta", hasta);
        }
        if (qPattern != null) {
            where.append(
                    """
                     AND (
                       LOWER(COALESCE(CAST(a.razonSocialNombre AS string), '')) LIKE :qPattern OR
                       LOWER(COALESCE(CAST(a.decision AS string), '')) LIKE :qPattern
                     )""");
            params.put("qPattern", qPattern);
        }
        return page(
                "SELECT a FROM RmActaConformidad a" + where + " ORDER BY a.createdAt DESC",
                "SELECT COUNT(a) FROM RmActaConformidad a" + where,
                params,
                pageable,
                RmActaConformidad.class);
    }

    private static void appendDateRange(
            StringBuilder where,
            Map<String, Object> params,
            String field,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {
        if (fechaDesde != null) {
            where.append(" AND ").append(field).append(" >= :fechaDesde");
            params.put("fechaDesde", fechaDesde);
        }
        if (fechaHasta != null) {
            where.append(" AND ").append(field).append(" <= :fechaHasta");
            params.put("fechaHasta", fechaHasta);
        }
    }

    private static void appendTipoRegistro(
            StringBuilder where, Map<String, Object> params, String field, String tipoRegistro) {
        if (tipoRegistro != null) {
            where.append(" AND LOWER(COALESCE(CAST(")
                    .append(field)
                    .append(" AS string), '')) = :tipoRegistro");
            params.put("tipoRegistro", tipoRegistro);
        }
    }

    private static void appendTipoRegistroJoin(StringBuilder where, Map<String, Object> params, String tipoRegistro) {
        if (tipoRegistro != null) {
            where.append(
                    " AND LOWER(COALESCE(CAST(v.tiporegistro AS string), '')) = :tipoRegistro");
            params.put("tipoRegistro", tipoRegistro);
        }
    }

    private static void appendTipoRegistroJoinSalida(
            StringBuilder where, Map<String, Object> params, String tipoRegistro) {
        appendTipoRegistroJoin(where, params, tipoRegistro);
    }

    private <T> Page<T> page(
            String dataJpql, String countJpql, Map<String, Object> params, Pageable pageable, Class<T> type) {
        TypedQuery<Long> countQuery = em.createQuery(countJpql, Long.class);
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<T> dataQuery = em.createQuery(dataJpql, type);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        List<T> content = dataQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }
}
