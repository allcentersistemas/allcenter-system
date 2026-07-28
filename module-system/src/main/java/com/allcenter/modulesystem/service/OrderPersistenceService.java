package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.event.ProyectoQuoteSubmittedEvent;
import com.allcenter.modulesystem.model.ClientUser;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Orden;
import com.allcenter.modulesystem.model.OrdenDetalle;
import com.allcenter.modulesystem.model.ProyectoEstado;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import com.allcenter.modulesystem.model.AuditAction;
import com.allcenter.modulesystem.repository.ClientUserRepository;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.repository.OrdenDetalleRepository;
import com.allcenter.modulesystem.repository.OrdenRepository;
import com.allcenter.modulesystem.repository.ProyectoRepository;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.security.PortalRoleNames;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class OrderPersistenceService {

    private final ProyectoRepository proyectoRepository;
    private final OrdenRepository ordenRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final ClientUserRepository clientUserRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;
    private final MaquinaOptimizacionService maquinaService;
    private final com.allcenter.modulesystem.support.OptimizacionStorageService optimizacionStorage;
    private final MailService mailService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderPersistenceService(
            ProyectoRepository proyectoRepository,
            OrdenRepository ordenRepository,
            OrdenDetalleRepository ordenDetalleRepository,
            ClientUserRepository clientUserRepository,
            EmployeeRepository employeeRepository,
            ObjectMapper objectMapper,
            MaquinaOptimizacionService maquinaService,
            com.allcenter.modulesystem.support.OptimizacionStorageService optimizacionStorage,
            MailService mailService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.proyectoRepository = proyectoRepository;
        this.ordenRepository = ordenRepository;
        this.ordenDetalleRepository = ordenDetalleRepository;
        this.clientUserRepository = clientUserRepository;
        this.employeeRepository = employeeRepository;
        this.objectMapper = objectMapper;
        this.maquinaService = maquinaService;
        this.optimizacionStorage = optimizacionStorage;
        this.mailService = mailService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderDtos.ProyectoConOrdenesResponse saveProjectTree(OrderDtos.ProyectoCompuestoPayload payload) {
        return saveProjectTreeInternal(null, payload);
    }

    @Transactional
    public OrderDtos.ProyectoConOrdenesResponse saveProjectTreeForClient(
            long clientUserId, OrderDtos.ProyectoCompuestoPayload payload) {
        return saveProjectTreeInternal(clientUserId, payload);
    }

    @Transactional(readOnly = true)
    public OrderDtos.ProyectoResumenResponse findProjectByNameForClient(long clientUserId, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proyecto es obligatorio");
        }
        return proyectoRepository
                .findFirstByClientUserIdAndNombreIgnoreCase(clientUserId, nombre.trim())
                .map(p -> toResumenResponse(p, clientCanEdit(p)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.ProyectoResumenResponse> listProjectsForClient(long clientUserId) {
        return proyectoRepository.findByClientUserIdOrderByFechacreacionDesc(clientUserId).stream()
                .map(p -> toResumenResponse(p, clientCanEdit(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.ProyectoResumenResponse> listProjectsForEmployee(
            long employeeId,
            String scope,
            String estado,
            String nombre,
            String cliente,
            String vendedor,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {
        List<ProyectoOptimizacion> base =
                "mis".equalsIgnoreCase(scope)
                        ? proyectoRepository.findByVendedorIdOrderByFechacreacionDesc(employeeId)
                        : proyectoRepository.findAllByOrderByFechacreacionDesc();
        ProyectoEstado estadoFilter = ProyectoEstado.fromString(estado);
        String nombreQ = normalizeQuery(nombre);
        String clienteQ = normalizeQuery(cliente);
        String vendedorQ = normalizeQuery(vendedor);
        return base.stream()
                .filter(p -> estadoFilter == null || estadoFilter == p.getEstado())
                .filter(p -> nombreQ == null || containsIgnoreCase(p.getNombre(), nombreQ))
                .filter(p -> clienteQ == null || containsIgnoreCase(p.getCliente(), clienteQ))
                .filter(p -> vendedorQ == null || containsIgnoreCase(resolveVendedorNombre(p.getVendedorId()), vendedorQ))
                .filter(p -> matchesDateRange(p.getFechacreacion(), fechaDesde, fechaHasta))
                .map(p -> toResumenResponse(p, true))
                .toList();
    }

    @Transactional
    public OrderDtos.ProyectoResponse captureProject(long employeeId, Long proyectoId) {
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        if (proyecto.getEstado() == ProyectoEstado.CANCELADO) {
            throw new IllegalArgumentException("No se puede capturar un proyecto cancelado.");
        }
        if (proyecto.getEstado() == ProyectoEstado.VENDIDO) {
            throw new IllegalArgumentException("No se puede capturar un proyecto vendido.");
        }
        if (proyecto.getVendedorId() != null && !proyecto.getVendedorId().equals(employeeId)) {
            throw new IllegalArgumentException("El proyecto ya fue capturado por otro vendedor.");
        }
        if (proyecto.getVendedorId() == null) {
            proyecto.setVendedorId(employeeId);
        }
        if (proyecto.getEstado() == ProyectoEstado.ENVIADO) {
            applyEstadoChange(proyecto, ProyectoEstado.EN_ATENCION);
        }
        ProyectoOptimizacion saved = proyectoRepository.save(proyecto);
        recordProyectoAudit(AuditAction.UPDATE, saved, "Proyecto capturado por vendedor; estado EN_ATENCION");
        return toProyectoResponse(saved, true);
    }

    @Transactional
    public OrderDtos.ProyectoResponse markVendido(long employeeId, Long proyectoId) {
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        if (proyecto.getVendedorId() != null && !proyecto.getVendedorId().equals(employeeId)) {
            throw new IllegalArgumentException("Solo el vendedor asignado puede marcar el proyecto como vendido.");
        }
        if (proyecto.getEstado() != ProyectoEstado.COTIZADO) {
            throw new IllegalArgumentException("Solo se puede marcar como vendido un proyecto cotizado.");
        }
        if (proyecto.getVendedorId() == null) {
            proyecto.setVendedorId(employeeId);
        }
        applyEstadoChange(proyecto, ProyectoEstado.VENDIDO);
        ProyectoOptimizacion saved = proyectoRepository.save(proyecto);
        recordProyectoAudit(AuditAction.UPDATE, saved, "Proyecto marcado como VENDIDO");
        return toProyectoResponse(saved, true);
    }

    @Transactional
    public OrderDtos.ProyectoResponse cancelProjectForEmployee(Long proyectoId) {
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        cancelProjectInternal(proyecto);
        ProyectoOptimizacion saved = proyectoRepository.save(proyecto);
        recordProyectoAudit(AuditAction.UPDATE, saved, "Proyecto cancelado por empleado");
        return toProyectoResponse(saved, true);
    }

    @Transactional
    public OrderDtos.ProyectoResponse cancelProjectForClient(long clientUserId, Long proyectoId) {
        ProyectoOptimizacion proyecto = requireOwnedProject(clientUserId, proyectoId);
        cancelProjectInternal(proyecto);
        ProyectoOptimizacion saved = proyectoRepository.save(proyecto);
        recordProyectoAudit(AuditAction.UPDATE, saved, "Proyecto cancelado por cliente portal");
        return toProyectoResponse(saved, false);
    }

    @Transactional
    public OrderDtos.ProyectoResponse updateProyectoGestion(Long proyectoId, OrderDtos.ProyectoGestionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Datos del proyecto obligatorios.");
        }
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        if (proyecto.getEstado() != null && proyecto.getEstado().isTerminal()) {
            throw new IllegalArgumentException("No se puede editar un proyecto vendido o cancelado.");
        }
        if (payload.nombre() != null && !payload.nombre().isBlank()) {
            proyecto.setNombre(payload.nombre().trim());
        }
        if (payload.clientUserId() != null) {
            proyecto.setClientUserId(payload.clientUserId());
            proyecto.setCliente(resolveClienteLabel(payload.clientUserId()));
        } else if (payload.cliente() != null) {
            proyecto.setCliente(valueOrNull(payload.cliente()));
        }
        if (payload.referencia() != null) {
            proyecto.setReferencia(valueOrNull(payload.referencia()));
        }
        if (payload.descripcion() != null) {
            proyecto.setDescripcion(valueOrNull(payload.descripcion()));
        }
        Long vendedorId = payload.vendedorId();
        if (vendedorId != null) {
            boolean esVendedorActivo =
                    employeeRepository.findAllActiveByRoleName(PortalRoleNames.VENTAS).stream()
                            .anyMatch(e -> vendedorId.equals(e.getId()));
            if (!esVendedorActivo) {
                throw new IllegalArgumentException(
                        "El vendedor asignado debe ser un empleado activo con rol de ventas.");
            }
        }
        proyecto.setVendedorId(vendedorId);
        if (payload.maquinaId() != null) {
            maquinaService.requireActiveMaquina(payload.maquinaId());
            proyecto.setMaquinaId(payload.maquinaId());
        }
        ProyectoOptimizacion saved = proyectoRepository.save(proyecto);
        recordProyectoAudit(AuditAction.UPDATE, saved, "Datos de gestión del proyecto actualizados");
        return toProyectoResponse(saved, true);
    }

    @Transactional
    public OrderDtos.ProyectoResponse updateEstado(Long proyectoId, String estadoRaw) {
        throw new IllegalArgumentException(
                "El estado del proyecto solo cambia mediante las acciones del flujo (capturar, cotizar, vender, cancelar).");
    }

    @Transactional
    public OrderDtos.ProyectoResponse updateMaquina(Long proyectoId, Long maquinaId) {
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        if (maquinaId != null) {
            maquinaService.requireActiveMaquina(maquinaId);
        }
        proyecto.setMaquinaId(maquinaId);
        return toProyectoResponse(proyectoRepository.save(proyecto), true);
    }

    @Transactional
    public OrderDtos.ProyectoResponse updateMaquinaForClient(long clientUserId, Long proyectoId, Long maquinaId) {
        ProyectoOptimizacion proyecto = requireOwnedProject(clientUserId, proyectoId);
        if (maquinaId != null) {
            maquinaService.requireActiveMaquina(maquinaId);
        }
        proyecto.setMaquinaId(maquinaId);
        return toProyectoResponse(proyectoRepository.save(proyecto), false);
    }

    @Transactional
    public OrderDtos.ProyectoResponse uploadCotizacion(long employeeId, Long proyectoId, org.springframework.web.multipart.MultipartFile file) {
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        if (proyecto.getVendedorId() != null && !proyecto.getVendedorId().equals(employeeId)) {
            throw new IllegalArgumentException("Solo el vendedor asignado puede subir la cotización.");
        }
        try {
            String filename = optimizacionStorage.saveCotizacion(proyectoId, file);
            proyecto.setCotizacionArchivo(filename);
            applyEstadoChange(proyecto, ProyectoEstado.COTIZADO);
            if (proyecto.getVendedorId() == null) {
                proyecto.setVendedorId(employeeId);
            }
            ProyectoOptimizacion saved = proyectoRepository.save(proyecto);
            notifyClientCotizacionEmail(saved, filename);
            recordProyectoAudit(AuditAction.UPDATE, saved, "Cotización subida; estado COTIZADO");
            return toProyectoResponse(saved, true);
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("No se pudo guardar la cotización.");
        }
    }

    @Transactional
    public OrderDtos.ProyectoConOrdenesResponse saveProjectTreeForEmployee(OrderDtos.ProyectoCompuestoPayload payload) {
        if (payload == null || payload.project() == null) {
            throw new IllegalArgumentException("La información del proyecto es obligatoria");
        }
        if (payload.projectId() == null) {
            OrderDtos.ProyectoResponse created = saveProyecto(payload.project(), null);
            if (payload.orders() != null) {
                for (OrderDtos.OrdenCompuestaPayload order : payload.orders()) {
                    OrderDtos.OrdenResponse orden =
                            saveOrden(created.id(), new OrderDtos.OrdenPayload(order.codigo(), order.descripcion()));
                    replaceDetalles(orden.id(), order.detalles());
                }
            }
            return getProjectTree(created.id(), true);
        }
        ProyectoOptimizacion proyecto = requireProject(payload.projectId());
        if (payload.project().nombre() != null && !payload.project().nombre().isBlank()) {
            proyecto.setNombre(payload.project().nombre().trim());
        }
        proyecto.setDescripcion(valueOrNull(payload.project().descripcion()));
        proyecto.setCliente(valueOrNull(payload.project().cliente()));
        proyecto.setReferencia(valueOrNull(payload.project().referencia()));
        if (payload.project().maquinaId() != null) {
            maquinaService.requireActiveMaquina(payload.project().maquinaId());
            proyecto.setMaquinaId(payload.project().maquinaId());
        }
        proyectoRepository.save(proyecto);
        replaceProjectOrders(proyecto.getId(), payload.orders());
        recordProyectoAudit(AuditAction.UPDATE, proyecto, "Árbol de proyecto actualizado por empleado");
        return getProjectTree(proyecto.getId(), true);
    }

    @Transactional(readOnly = true)
    public ClientResponse getPortalClientForProject(Long proyectoId) {
        ProyectoOptimizacion proyecto = requireProject(proyectoId);
        Long clientUserId = proyecto.getClientUserId();
        if (clientUserId == null) {
            throw new EntityNotFoundException("Este proyecto no tiene un cliente del portal asociado");
        }
        ClientUser client =
                clientUserRepository
                        .findById(clientUserId)
                        .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));
        return ClientResponse.from(client);
    }

    @Transactional(readOnly = true)
    public OrderDtos.ProyectoConOrdenesResponse getProjectTreeForClient(long clientUserId, Long proyectoId) {
        ProyectoOptimizacion proyecto = requireOwnedProject(clientUserId, proyectoId);
        return getProjectTree(proyecto.getId(), clientCanEdit(proyecto));
    }

    private boolean clientCanEdit(ProyectoOptimizacion proyecto) {
        ProyectoEstado estado = proyecto.getEstado();
        return estado == null || estado == ProyectoEstado.ENVIADO;
    }

    @Transactional(readOnly = true)
    public String getCotizacionStoredFilenameForClient(long clientUserId, Long proyectoId) {
        ProyectoOptimizacion proyecto = requireOwnedProject(clientUserId, proyectoId);
        String archivo = proyecto.getCotizacionArchivo();
        if (archivo == null || archivo.isBlank()) {
            if (!optimizacionStorage.cotizacionExists(proyectoId, null)) {
                throw new EntityNotFoundException("Cotización no disponible para este proyecto.");
            }
            return null;
        }
        return archivo;
    }

    @Transactional(readOnly = true)
    public String getCotizacionFilenameForClient(long clientUserId, Long proyectoId) {
        ProyectoOptimizacion proyecto = requireOwnedProject(clientUserId, proyectoId);
        String resolved =
                optimizacionStorage.resolveCotizacionFilename(proyectoId, proyecto.getCotizacionArchivo());
        if (resolved == null) {
            String archivo = proyecto.getCotizacionArchivo();
            if (archivo != null && !archivo.isBlank()) {
                throw new EntityNotFoundException(
                        "El archivo de cotización no está en el servidor. Ventas debe volver a subirla.");
            }
            throw new EntityNotFoundException("Cotización no disponible para este proyecto.");
        }
        return resolved;
    }

    private OrderDtos.ProyectoConOrdenesResponse saveProjectTreeInternal(
            Long clientUserId, OrderDtos.ProyectoCompuestoPayload payload) {
        if (payload == null || payload.project() == null) {
            throw new IllegalArgumentException("La información del proyecto es obligatoria");
        }
        OrderDtos.ProyectoResponse proyecto;
        if (payload.projectId() != null) {
            if (clientUserId == null) {
                throw new IllegalArgumentException("No se puede actualizar un proyecto sin contexto de cliente");
            }
            ProyectoOptimizacion proyectoEntity = requireOwnedProject(clientUserId, payload.projectId());
            if (!clientCanEdit(proyectoEntity)) {
                throw new IllegalArgumentException(
                        "El proyecto ya está en revisión y no puede modificarse. Contacte a ventas si necesita cambios.");
            }
            if (payload.project().nombre() != null && !payload.project().nombre().isBlank()) {
                proyectoEntity.setNombre(payload.project().nombre().trim());
            }
            proyectoEntity.setDescripcion(valueOrNull(payload.project().descripcion()));
            if (payload.project().maquinaId() != null) {
                maquinaService.requireActiveMaquina(payload.project().maquinaId());
                proyectoEntity.setMaquinaId(payload.project().maquinaId());
            }
            proyectoRepository.save(proyectoEntity);
            replaceProjectOrders(proyectoEntity.getId(), payload.orders());
            recordProyectoAudit(AuditAction.UPDATE, proyectoEntity, "Proyecto actualizado por cliente portal");
            return getProjectTree(proyectoEntity.getId(), clientCanEdit(proyectoEntity));
        } else {
            proyecto = saveProyecto(payload.project(), clientUserId);
            if (payload.orders() != null) {
                for (OrderDtos.OrdenCompuestaPayload order : payload.orders()) {
                    OrderDtos.OrdenResponse created = saveOrden(
                            proyecto.id(),
                            new OrderDtos.OrdenPayload(order.codigo(), order.descripcion()));
                    replaceDetalles(created.id(), order.detalles());
                }
            }
        }
        if (clientUserId != null) {
            ProyectoOptimizacion saved = requireProject(proyecto.id());
            return getProjectTree(proyecto.id(), clientCanEdit(saved));
        }
        return getProjectTree(proyecto.id(), true);
    }

    private void replaceProjectOrders(Long proyectoId, List<OrderDtos.OrdenCompuestaPayload> orders) {
        List<Orden> existing = ordenRepository.findByProyectoOptimizacionId_IdOrderByIdAsc(proyectoId);
        for (Orden orden : existing) {
            ordenDetalleRepository.deleteByOrdenId_Id(orden.getId());
        }
        ordenRepository.deleteByProyectoOptimizacionId_Id(proyectoId);
        if (orders == null) {
            return;
        }
        for (OrderDtos.OrdenCompuestaPayload order : orders) {
            OrderDtos.OrdenResponse created =
                    saveOrden(proyectoId, new OrderDtos.OrdenPayload(order.codigo(), order.descripcion()));
            replaceDetalles(created.id(), order.detalles());
        }
    }

    private ProyectoOptimizacion requireProject(Long proyectoId) {
        return proyectoRepository
                .findById(proyectoId)
                .orElseThrow(() -> new EntityNotFoundException("Proyecto no encontrado"));
    }

    private ProyectoOptimizacion requireOwnedProject(long clientUserId, Long proyectoId) {
        ProyectoOptimizacion proyecto =
                proyectoRepository
                        .findById(proyectoId)
                        .orElseThrow(() -> new EntityNotFoundException("Proyecto no encontrado"));
        if (proyecto.getClientUserId() == null || !proyecto.getClientUserId().equals(clientUserId)) {
            throw new EntityNotFoundException("Proyecto no encontrado");
        }
        return proyecto;
    }

    private String resolveClienteLabel(long clientUserId) {
        ClientUser client =
                clientUserRepository
                        .findById(clientUserId)
                        .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));
        if (client.isJuridica() && client.getRazonSocial() != null && !client.getRazonSocial().isBlank()) {
            return client.getRazonSocial().trim();
        }
        if (client.getDisplayName() != null && !client.getDisplayName().isBlank()) {
            return client.getDisplayName().trim();
        }
        return client.getEmail();
    }

    @Transactional
    public OrderDtos.ProyectoResponse saveProyecto(OrderDtos.ProyectoPayload payload) {
        return saveProyecto(payload, null);
    }

    private OrderDtos.ProyectoResponse saveProyecto(OrderDtos.ProyectoPayload payload, Long clientUserId) {
        if (payload == null || payload.nombre() == null || payload.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proyecto es obligatorio");
        }
        ProyectoOptimizacion proyectoOptimizacion = new ProyectoOptimizacion();
        proyectoOptimizacion.setNombre(payload.nombre().trim());
        if (clientUserId != null) {
            proyectoOptimizacion.setCliente(resolveClienteLabel(clientUserId));
            proyectoOptimizacion.setClientUserId(clientUserId);
            proyectoOptimizacion.setReferencia(null);
        } else {
            proyectoOptimizacion.setCliente(valueOrNull(payload.cliente()));
            proyectoOptimizacion.setReferencia(valueOrNull(payload.referencia()));
        }
        proyectoOptimizacion.setDescripcion(valueOrNull(payload.descripcion()));
        proyectoOptimizacion.setFechacreacion(LocalDateTime.now(ZoneId.of("America/Lima")));
        applyEstadoChange(proyectoOptimizacion, ProyectoEstado.ENVIADO);
        proyectoOptimizacion.setCodigoproyecto(System.currentTimeMillis());
        if (payload.maquinaId() != null) {
            maquinaService.requireActiveMaquina(payload.maquinaId());
            proyectoOptimizacion.setMaquinaId(payload.maquinaId());
        }
        ProyectoOptimizacion saved = proyectoRepository.save(proyectoOptimizacion);
        recordProyectoAudit(
                AuditAction.CREATE,
                saved,
                "Proyecto creado: " + saved.getNombre() + " (estado ENVIADO)");
        if (clientUserId != null && saved.getEstado() == ProyectoEstado.ENVIADO) {
            eventPublisher.publishEvent(
                    new ProyectoQuoteSubmittedEvent(
                            saved.getId(),
                            saved.getNombre(),
                            saved.getCliente(),
                            clientUserId));
        }
        return toProyectoResponse(saved, clientUserId == null);
    }

    @Transactional
    public OrderDtos.OrdenResponse saveOrden(Long proyectoId, OrderDtos.OrdenPayload payload) {
        ProyectoOptimizacion proyectoOptimizacion = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new EntityNotFoundException("Proyecto no encontrado"));
        if (payload == null || payload.codigo() == null || payload.codigo().isBlank()) {
            throw new IllegalArgumentException("El código de la orden es obligatorio");
        }
        Orden orden = new Orden();
        orden.setProyectoOptimizacionId(proyectoOptimizacion);
        orden.setOrderCode(payload.codigo().trim());
        orden.setDescripcion(valueOrNull(payload.descripcion()));
        orden.setOrderName(payload.codigo().trim());
        Orden saved = ordenRepository.save(orden);
        return toOrdenResponse(saved);
    }

    @Transactional
    public List<OrderDtos.DetalleResponse> replaceDetalles(Long ordenId, List<OrderDtos.DetallePayload> payloads) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));
        ordenDetalleRepository.deleteByOrdenId_Id(ordenId);
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        List<OrdenDetalle> created = payloads.stream()
                .map(payload -> fromDetallePayload(orden, payload))
                .map(ordenDetalleRepository::save)
                .toList();
        return created.stream().map(this::toDetalleResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.ProyectoConOrdenesResponse getProjectTree(Long proyectoId) {
        return getProjectTree(proyectoId, true);
    }

    @Transactional
    public void deleteProject(Long proyectoId) {
        requireProject(proyectoId);
        replaceProjectOrders(proyectoId, null);
        proyectoRepository.deleteById(proyectoId);
    }

    @Transactional(readOnly = true)
    public OrderDtos.ProyectoConOrdenesResponse getProjectTree(Long proyectoId, boolean editable) {
        ProyectoOptimizacion proyectoOptimizacion = requireProject(proyectoId);

        List<OrderDtos.OrdenConDetallesResponse> orders =
                ordenRepository.findByProyectoOptimizacionId_IdOrderByIdAsc(proyectoId)
                .stream()
                .map(orden -> {
                    List<OrderDtos.DetalleResponse> detalles = ordenDetalleRepository.findByOrdenId_IdOrderByIdAsc(orden.getId())
                            .stream()
                            .map(this::toDetalleResponse)
                            .toList();
                    return new OrderDtos.OrdenConDetallesResponse(
                            orden.getId(),
                            orden.getProyectoOptimizacionId().getId(),
                            orden.getOrderCode(),
                            orden.getDescripcion(),
                            detalles
                    );
                }).toList();

        return new OrderDtos.ProyectoConOrdenesResponse(toProyectoResponse(proyectoOptimizacion, editable), orders);
    }

    private OrderDtos.ProyectoResumenResponse toResumenResponse(ProyectoOptimizacion proyecto, boolean editable) {
        return new OrderDtos.ProyectoResumenResponse(
                proyecto.getId(),
                proyecto.getCodigoproyecto(),
                proyecto.getNombre(),
                proyecto.getDescripcion(),
                proyecto.getCliente(),
                estadoLabel(proyecto.getEstado()),
                proyecto.getVendedorId(),
                resolveVendedorNombre(proyecto.getVendedorId()),
                proyecto.getFechacreacion(),
                ordenRepository.findByProyectoOptimizacionId_IdOrderByIdAsc(proyecto.getId()).size(),
                editable,
                proyecto.getMaquinaId(),
                maquinaService.resolveParametros(proyecto.getMaquinaId()),
                hasCotizacion(proyecto),
                proyecto.getCotizacionArchivo(),
                toEstadoTiempos(proyecto));
    }

    private boolean hasCotizacion(ProyectoOptimizacion proyecto) {
        String archivo = proyecto.getCotizacionArchivo();
        if (archivo != null && !archivo.isBlank()) {
            return true;
        }
        return optimizacionStorage.cotizacionExists(proyecto.getId(), archivo);
    }

    private OrderDtos.ProyectoResponse toProyectoResponse(ProyectoOptimizacion proyectoOptimizacion, boolean editable) {
        return new OrderDtos.ProyectoResponse(
                proyectoOptimizacion.getId(),
                proyectoOptimizacion.getCodigoproyecto(),
                proyectoOptimizacion.getNombre(),
                proyectoOptimizacion.getCliente(),
                proyectoOptimizacion.getClientUserId(),
                proyectoOptimizacion.getReferencia(),
                proyectoOptimizacion.getDescripcion(),
                estadoLabel(proyectoOptimizacion.getEstado()),
                proyectoOptimizacion.getVendedorId(),
                resolveVendedorNombre(proyectoOptimizacion.getVendedorId()),
                proyectoOptimizacion.getFechacreacion(),
                editable,
                proyectoOptimizacion.getMaquinaId(),
                maquinaService.resolveParametros(proyectoOptimizacion.getMaquinaId()),
                proyectoOptimizacion.getCotizacionArchivo(),
                toEstadoTiempos(proyectoOptimizacion));
    }

    private OrderDtos.ProyectoEstadoTiempos toEstadoTiempos(ProyectoOptimizacion proyecto) {
        return new OrderDtos.ProyectoEstadoTiempos(
                proyecto.getFechaEstadoEnviado(),
                proyecto.getFechaEstadoEnAtencion(),
                proyecto.getFechaEstadoCotizado(),
                proyecto.getFechaEstadoVendido(),
                proyecto.getFechaEstadoCancelado());
    }

    private void applyEstadoChange(ProyectoOptimizacion proyecto, ProyectoEstado nuevo) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Lima"));
        proyecto.setEstado(nuevo);
        switch (nuevo) {
            case ENVIADO -> proyecto.setFechaEstadoEnviado(now);
            case EN_ATENCION -> proyecto.setFechaEstadoEnAtencion(now);
            case COTIZADO -> proyecto.setFechaEstadoCotizado(now);
            case VENDIDO -> proyecto.setFechaEstadoVendido(now);
            case CANCELADO -> proyecto.setFechaEstadoCancelado(now);
        }
    }

    private void recordProyectoAudit(AuditAction action, ProyectoOptimizacion proyecto, String details) {
        if (proyecto == null || proyecto.getId() == null) {
            return;
        }
        auditService.recordEntityChange(
                action, "ProyectoOptimizacion", String.valueOf(proyecto.getId()), details);
    }

    private void notifyClientCotizacionEmail(ProyectoOptimizacion proyecto, String storedFilename) {
        if (!mailService.isEnabled()) {
            log.debug(
                    "Correo deshabilitado; no se envía cotización del proyecto {}",
                    proyecto.getId());
            return;
        }
        Long clientUserId = proyecto.getClientUserId();
        if (clientUserId == null) {
            log.info(
                    "Proyecto {} sin cliente portal; no se envía cotización por correo",
                    proyecto.getId());
            return;
        }
        ClientUser client =
                clientUserRepository.findById(clientUserId).orElse(null);
        if (client == null || client.getEmail() == null || client.getEmail().isBlank()) {
            log.warn(
                    "Cliente {} sin correo; no se envía cotización del proyecto {}",
                    clientUserId,
                    proyecto.getId());
            return;
        }
        try {
            byte[] bytes = optimizacionStorage.readCotizacionBytes(proyecto.getId(), storedFilename);
            String attachmentName =
                    optimizacionStorage.cotizacionDownloadName(
                            proyecto.getId(), storedFilename, proyecto.getNombre());
            String contentType = optimizacionStorage.cotizacionContentType(storedFilename);
            String recipientName = resolveClientDisplayName(client);
            String projectName =
                    proyecto.getNombre() == null || proyecto.getNombre().isBlank()
                            ? "su proyecto"
                            : proyecto.getNombre().trim();
            String html =
                    """
                    <p>Hola %s,</p>
                    <p>Su proyecto <strong>%s</strong> ha sido cotizado.</p>
                    <p>Adjuntamos la cotización. También puede consultarla iniciando sesión en el portal cliente.</p>
                    <p>Saludos,<br/>AllCenter</p>
                    """
                            .formatted(escapeHtml(recipientName), escapeHtml(projectName));
            mailService.sendHtmlWithAttachments(
                    client.getEmail().trim(),
                    "Cotización disponible — " + projectName,
                    html,
                    List.of(new MailService.MailAttachment(attachmentName, bytes, contentType)));
        } catch (Exception ex) {
            log.error(
                    "No se pudo enviar la cotización por correo (proyecto {}): {}",
                    proyecto.getId(),
                    ex.getMessage());
        }
    }

    private static String resolveClientDisplayName(ClientUser client) {
        if (client.isJuridica()
                && client.getRazonSocial() != null
                && !client.getRazonSocial().isBlank()) {
            return client.getRazonSocial().trim();
        }
        if (client.getDisplayName() != null && !client.getDisplayName().isBlank()) {
            return client.getDisplayName().trim();
        }
        if (client.getNombre() != null && !client.getNombre().isBlank()) {
            return client.getNombre().trim();
        }
        return client.getEmail();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void cancelProjectInternal(ProyectoOptimizacion proyecto) {
        ProyectoEstado estado = proyecto.getEstado();
        if (estado == ProyectoEstado.VENDIDO) {
            throw new IllegalArgumentException("No se puede cancelar un proyecto vendido.");
        }
        if (estado == ProyectoEstado.CANCELADO) {
            throw new IllegalArgumentException("El proyecto ya está cancelado.");
        }
        if (estado == ProyectoEstado.COTIZADO) {
            throw new IllegalArgumentException(
                    "No se puede cancelar un proyecto ya cotizado. Contacte a ventas.");
        }
        applyEstadoChange(proyecto, ProyectoEstado.CANCELADO);
    }

    private String estadoLabel(ProyectoEstado estado) {
        return estado == null ? ProyectoEstado.ENVIADO.name() : estado.name();
    }

    private String resolveVendedorNombre(Long vendedorId) {
        if (vendedorId == null) {
            return "";
        }
        return employeeRepository
                .findById(vendedorId)
                .map(this::employeeDisplayName)
                .orElse("");
    }

    private String employeeDisplayName(Employee employee) {
        String first = Objects.toString(employee.getFirstName(), "").trim();
        String last = Objects.toString(employee.getLastName(), "").trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? employee.getEmail() : full;
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private boolean containsIgnoreCase(String haystack, String needleLower) {
        if (needleLower == null) {
            return true;
        }
        return haystack != null && haystack.toLowerCase().contains(needleLower);
    }

    private boolean matchesDateRange(LocalDateTime value, LocalDate desde, LocalDate hasta) {
        if (value == null) {
            return desde == null && hasta == null;
        }
        LocalDate day = value.toLocalDate();
        if (desde != null && day.isBefore(desde)) {
            return false;
        }
        if (hasta != null && day.isAfter(hasta)) {
            return false;
        }
        return true;
    }

    private OrderDtos.OrdenResponse toOrdenResponse(Orden orden) {
        return new OrderDtos.OrdenResponse(
                orden.getId(),
                orden.getProyectoOptimizacionId().getId(),
                orden.getOrderCode(),
                orden.getDescripcion()
        );
    }

    private OrderDtos.DetalleResponse toDetalleResponse(OrdenDetalle detalle) {
        Map<String, Object> extras = parseJsonMap(detalle.getParametros());
        return new OrderDtos.DetalleResponse(
                detalle.getId(),
                detalle.getOrdenId().getId(),
                nullToEmpty(detalle.getMaterial()),
                detalle.getCantidad() == null ? "" : detalle.getCantidad().toString(),
                detalle.getLargo() == null ? "" : detalle.getLargo().toString(),
                detalle.getAncho() == null ? "" : detalle.getAncho().toString(),
                nullToEmpty(detalle.getVeta()),
                str(extras.get("l1")),
                str(extras.get("l2")),
                str(extras.get("a1")),
                str(extras.get("a2")),
                str(extras.get("perforacionCantidad")),
                str(extras.get("perforacionLado1")),
                str(extras.get("perforacionLado2")),
                str(extras.get("ranuraDist")),
                str(extras.get("ranuraProf")),
                str(extras.get("ranuraEs")),
                str(extras.get("ranuraLado")),
                "true".equalsIgnoreCase(str(extras.get("ranuraEspecial"))),
                "true".equalsIgnoreCase(str(extras.get("observado"))),
                valueOrNull(detalle.getDescripcion())
        );
    }

    private OrdenDetalle fromDetallePayload(Orden orden, OrderDtos.DetallePayload payload) {
        OrdenDetalle detalle = new OrdenDetalle();
        detalle.setOrdenId(orden);
        detalle.setMaterial(valueOrNull(payload.tablero()));
        detalle.setCantidad(parseInteger(payload.cantidad()));
        detalle.setLargo(parseInteger(payload.largoVeta()));
        detalle.setAncho(parseInteger(payload.ancho()));
        detalle.setVeta(normalizeVeta(payload.veta()));
        detalle.setDescripcion(valueOrNull(payload.observacion()));

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("l1", payload.l1());
        extras.put("l2", payload.l2());
        extras.put("a1", payload.a1());
        extras.put("a2", payload.a2());
        extras.put("perforacionCantidad", payload.perforacionCantidad());
        extras.put("perforacionLado1", payload.perforacionLado1());
        extras.put("perforacionLado2", payload.perforacionLado2());
        extras.put("ranuraDist", payload.ranuraDist());
        extras.put("ranuraProf", payload.ranuraProf());
        extras.put("ranuraEs", payload.ranuraEs());
        extras.put("ranuraLado", payload.ranuraLado());
        extras.put("ranuraEspecial", payload.ranuraEspecial());
        extras.put("observado", payload.observado());
        detalle.setParametros(writeJson(extras));
        detalle.setDescripcion1(payload.observado() ? "OK" : null);
        return detalle;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> parseJsonMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String normalizeVeta(String value) {
        if (value == null || value.isBlank()) {
            return "0-No";
        }
        String trimmed = value.trim();
        if ("1-Longitud".equalsIgnoreCase(trimmed) || trimmed.startsWith("1")) {
            return "1-Longitud";
        }
        return "0-No";
    }

    private String valueOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
