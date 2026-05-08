package com.allcenter.moduletransport.service;

import com.allcenter.moduletransport.dto.TransportDtos.ApiMessage;
import com.allcenter.moduletransport.dto.TransportDtos.AddTransporteCargaDetalleRequest;
import com.allcenter.moduletransport.dto.TransportDtos.CreateTransporteCargaRequest;
import com.allcenter.moduletransport.dto.TransportDtos.CreateTransporteRequest;
import com.allcenter.moduletransport.dto.TransportDtos.TransporteCargaDetalleDto;
import com.allcenter.moduletransport.dto.TransportDtos.TransporteCargaHeaderDto;
import com.allcenter.moduletransport.dto.TransportDtos.TransporteCargaResponse;
import com.allcenter.moduletransport.dto.TransportDtos.TransporteDto;
import com.allcenter.moduletransport.dto.TransportDtos.UpdateTransporteCargaRequest;
import com.allcenter.moduletransport.dto.TransportDtos.UpdateTransporteRequest;
import com.allcenter.moduletransport.model.Transporte;
import com.allcenter.moduletransport.model.TransporteCarga;
import com.allcenter.moduletransport.model.TransporteCargaDetalle;
import com.allcenter.moduletransport.repository.TransporteCargaDetalleRepository;
import com.allcenter.moduletransport.repository.TransporteCargaRepository;
import com.allcenter.moduletransport.repository.TransporteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TransportService {

    private static final String ESTADO_BORRADOR = "BORRADOR";
    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";
    private static final String ESTADO_EN_RUTA = "EN_RUTA";
    private static final String ESTADO_ENTREGADA = "ENTREGADA";
    private static final String ESTADO_CANCELADA = "CANCELADA";
    private static final String PALE_ESTADO_CERRADO = "CERRADO";

    private final TransporteRepository transporteRepository;
    private final TransporteCargaRepository cargaRepository;
    private final TransporteCargaDetalleRepository detalleRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.order.base-url:http://localhost:8083}")
    private String orderBaseUrl;

    public List<TransporteDto> listTransportes() {
        return transporteRepository.findAll().stream()
                .map(this::toTransporteDto)
                .toList();
    }

    public TransporteDto getTransporte(Long id) {
        Transporte transporte = transporteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));
        return toTransporteDto(transporte);
    }

    @Transactional
    public TransporteDto createTransporte(CreateTransporteRequest request) {
        String placa = normalizeRequired(request.placa(), "placa");
        transporteRepository.findByPlacaIgnoreCase(placa)
                .ifPresent(t -> {
                    throw new ResponseStatusException(CONFLICT, "Ya existe un transporte con esa placa");
                });

        Transporte transporte = new Transporte();
        transporte.setPlaca(placa);
        transporte.setNumeroserie(normalizeOptional(request.numeroSerie()));
        transporte.setModelo(normalizeOptional(request.modelo()));
        transporte.setMarca(normalizeOptional(request.marca()));
        transporte.setColor(normalizeOptional(request.color()));
        transporte.setDescripcion(normalizeOptional(request.descripcion()));
        transporte.setTipoVehiculo(normalizeOptional(request.tipoVehiculo()));
        transporte.setCapacidad(request.capacidad());
        transporte.setActivo(request.activo() == null ? Boolean.TRUE : request.activo());
        transporte.setFechaCreacion(LocalDateTime.now());

        transporte = transporteRepository.save(transporte);
        return toTransporteDto(transporte);
    }

    @Transactional
    public TransporteDto updateTransporte(Long id, UpdateTransporteRequest request) {
        Transporte transporte = transporteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));

        if (request.placa() != null) {
            String placa = normalizeRequired(request.placa(), "placa");
            transporteRepository.findByPlacaIgnoreCase(placa)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(CONFLICT, "La placa ya esta registrada");
                    });
            transporte.setPlaca(placa);
        }
        if (request.numeroSerie() != null) {
            transporte.setNumeroserie(normalizeOptional(request.numeroSerie()));
        }
        if (request.modelo() != null) {
            transporte.setModelo(normalizeOptional(request.modelo()));
        }
        if (request.color() != null) {
            transporte.setColor(normalizeOptional(request.color()));
        }
        if (request.descripcion() != null) {
            transporte.setDescripcion(normalizeOptional(request.descripcion()));
        }
        if (request.tipoVehiculo() != null) {
            transporte.setTipoVehiculo(normalizeOptional(request.tipoVehiculo()));
        }
        if (request.capacidad() != null) {
            transporte.setCapacidad(request.capacidad());
        }
        if (request.activo() != null) {
            transporte.setActivo(request.activo());
        }

        transporte = transporteRepository.save(transporte);
        return toTransporteDto(transporte);
    }

    public List<TransporteCargaHeaderDto> listCargas() {
        return cargaRepository.findAllWithTransporte().stream()
                .sorted((a, b) -> b.getFechaCreacion().compareTo(a.getFechaCreacion()))
                .map(this::toCargaHeaderDto)
                .toList();
    }

    public TransporteCargaResponse getCargaById(Long id) {
        TransporteCarga carga = cargaRepository.findByIdWithTransporte(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carga no encontrada"));
        return toCargaResponse(carga);
    }

    @Transactional
    public TransporteCargaResponse createCarga(CreateTransporteCargaRequest request) {
        Transporte transporte = transporteRepository.findById(request.transporteId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));
        if (Boolean.FALSE.equals(transporte.getActivo())) {
            throw new ResponseStatusException(BAD_REQUEST, "El transporte seleccionado esta inactivo");
        }

        TransporteCarga carga = new TransporteCarga();
        carga.setTransporte(transporte);
        carga.setChoferNombre(normalizeRequired(request.choferNombre(), "choferNombre"));
        carga.setChoferDocumento(normalizeOptional(request.choferDocumento()));
        carga.setEstado(ESTADO_BORRADOR);
        carga.setNotas(normalizeOptional(request.notas()));
        carga.setFechaSalida(request.fechaSalida());
        carga.setFechaEntrega(null);
        carga.setCreadoPor(request.creadoPor());
        carga.setFechaCreacion(LocalDateTime.now());
        carga = cargaRepository.save(carga);

        return toCargaResponse(carga);
    }

    @Transactional
    public TransporteCargaResponse updateCarga(Long id, UpdateTransporteCargaRequest request) {
        TransporteCarga carga = cargaRepository.findByIdWithTransporte(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carga no encontrada"));

        if (request.choferNombre() != null) {
            carga.setChoferNombre(normalizeRequired(request.choferNombre(), "choferNombre"));
        }
        if (request.choferDocumento() != null) {
            carga.setChoferDocumento(normalizeOptional(request.choferDocumento()));
        }
        if (request.notas() != null) {
            carga.setNotas(normalizeOptional(request.notas()));
        }
        if (request.fechaSalida() != null) {
            carga.setFechaSalida(request.fechaSalida());
        }
        if (request.fechaEntrega() != null) {
            carga.setFechaEntrega(request.fechaEntrega());
        }
        if (request.estado() != null) {
            String normalizedEstado = normalizeEstado(request.estado());
            validateEstadoTransition(carga.getEstado(), normalizedEstado);
            carga.setEstado(normalizedEstado);
            if (ESTADO_ENTREGADA.equals(normalizedEstado) && carga.getFechaEntrega() == null) {
                carga.setFechaEntrega(LocalDateTime.now());
            }
        }
        carga = cargaRepository.save(carga);
        return toCargaResponse(carga);
    }

    @Transactional
    public TransporteCargaResponse addDetalle(Long cargaId, AddTransporteCargaDetalleRequest request) {
        TransporteCarga carga = cargaRepository.findByIdWithTransporte(cargaId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carga no encontrada"));
        validateCargaEditableForDetails(carga);

        if (detalleRepository.existsByTransporteCargaIdAndPaleEnvioId(cargaId, request.paleEnvioId())) {
            throw new ResponseStatusException(CONFLICT, "Ese pale ya fue agregado a la carga");
        }

        OrderPallet orderPallet = fetchPalletFromOrder(request.paleEnvioId());
        validatePalletReadyForTransport(orderPallet);

        TransporteCargaDetalle detalle = new TransporteCargaDetalle();
        detalle.setTransporteCarga(carga);
        detalle.setPaleEnvioId(request.paleEnvioId());
        detalle.setPaleCodigo(resolvePaleCodigo(request.paleCodigo(), orderPallet.codigo()));
        detalle.setCantidad(request.cantidad());
        detalle.setObservacion(normalizeOptional(request.observacion()));
        detalle.setFechaRegistro(LocalDateTime.now());
        detalleRepository.save(detalle);

        return toCargaResponse(carga);
    }

    @Transactional
    public ApiMessage removeDetalle(Long cargaId, Long detalleId) {
        TransporteCarga carga = cargaRepository.findByIdWithTransporte(cargaId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carga no encontrada"));
        validateCargaEditableForDetails(carga);

        TransporteCargaDetalle detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Detalle no encontrado"));
        if (!detalle.getTransporteCarga().getId().equals(cargaId)) {
            throw new ResponseStatusException(BAD_REQUEST, "El detalle no pertenece a la carga indicada");
        }
        detalleRepository.delete(detalle);
        return new ApiMessage(true, "Detalle removido de la carga");
    }

    private void validateCargaEditableForDetails(TransporteCarga carga) {
        if (ESTADO_ENTREGADA.equals(carga.getEstado()) || ESTADO_CANCELADA.equals(carga.getEstado())) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pueden modificar detalles en una carga cerrada");
        }
    }

    private String normalizeEstado(String estado) {
        String normalized = normalizeRequired(estado, "estado").toUpperCase();
        if (!ESTADO_BORRADOR.equals(normalized)
                && !ESTADO_CONFIRMADA.equals(normalized)
                && !ESTADO_EN_RUTA.equals(normalized)
                && !ESTADO_ENTREGADA.equals(normalized)
                && !ESTADO_CANCELADA.equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Estado de carga no valido");
        }
        return normalized;
    }

    private void validateEstadoTransition(String actual, String siguiente) {
        if (actual == null || actual.equals(siguiente)) {
            return;
        }
        if (ESTADO_CANCELADA.equals(actual) || ESTADO_ENTREGADA.equals(actual)) {
            throw new ResponseStatusException(BAD_REQUEST, "La carga ya esta finalizada y no acepta cambios de estado");
        }
        if (ESTADO_BORRADOR.equals(actual) && ESTADO_ENTREGADA.equals(siguiente)) {
            throw new ResponseStatusException(BAD_REQUEST, "No se puede marcar entregada sin pasar por ruta");
        }
    }

    private OrderPallet fetchPalletFromOrder(Long paleEnvioId) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    orderBaseUrl + "/api/order/pallets/" + paleEnvioId,
                    HttpMethod.GET,
                    null,
                    Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Respuesta invalida de module-order para el pale");
            }
            Object palletObj = body.get("pallet");
            if (!(palletObj instanceof Map<?, ?> palletMap)) {
                throw new ResponseStatusException(BAD_REQUEST, "No se encontro informacion de pallet en module-order");
            }
            String codigo = readRequiredText(palletMap, "codigo", "El pale en module-order no tiene codigo");
            String estado = readRequiredText(palletMap, "estado", "El pale en module-order no tiene estado");
            return new OrderPallet(codigo, estado);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(NOT_FOUND, "El pale no existe en module-order");
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "module-order rechazo la validacion del pale");
        } catch (HttpServerErrorException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "module-order no esta disponible en este momento");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo validar el pale contra module-order");
        }
    }

    private void validatePalletReadyForTransport(OrderPallet pallet) {
        if (!PALE_ESTADO_CERRADO.equalsIgnoreCase(pallet.estado())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Solo se pueden cargar pales cerrados. Estado actual: " + pallet.estado());
        }
    }

    private String resolvePaleCodigo(String requestCode, String orderCode) {
        String requestNormalized = normalizeOptional(requestCode);
        if (requestNormalized == null) {
            return orderCode;
        }
        if (!requestNormalized.equalsIgnoreCase(orderCode)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "El paleCodigo no coincide con el registro en module-order");
        }
        return orderCode;
    }

    private String readRequiredText(Map<?, ?> map, String key, String errorMessage) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, errorMessage);
        }
        return value.toString().trim();
    }

    private TransporteCargaResponse toCargaResponse(TransporteCarga carga) {
        List<TransporteCargaDetalleDto> detalles =
                detalleRepository.findByTransporteCargaIdOrderByFechaRegistroDesc(carga.getId()).stream()
                        .map(this::toDetalleDto)
                        .toList();
        return new TransporteCargaResponse(toCargaHeaderDto(carga), detalles);
    }

    private TransporteCargaHeaderDto toCargaHeaderDto(TransporteCarga carga) {
        Long transporteId = carga.getTransporte() == null ? null : carga.getTransporte().getId();
        String placa = carga.getTransporte() == null ? null : carga.getTransporte().getPlaca();
        Integer totalPales = Math.toIntExact(detalleRepository.countByTransporteCargaId(carga.getId()));
        return new TransporteCargaHeaderDto(
                carga.getId(),
                transporteId,
                placa,
                carga.getChoferNombre(),
                carga.getChoferDocumento(),
                carga.getEstado(),
                carga.getNotas(),
                totalPales,
                carga.getFechaSalida(),
                carga.getFechaEntrega(),
                carga.getFechaCreacion());
    }

    private TransporteCargaDetalleDto toDetalleDto(TransporteCargaDetalle detalle) {
        return new TransporteCargaDetalleDto(
                detalle.getId(),
                detalle.getPaleEnvioId(),
                detalle.getPaleCodigo(),
                detalle.getCantidad(),
                detalle.getObservacion(),
                detalle.getFechaRegistro());
    }

    private TransporteDto toTransporteDto(Transporte transporte) {
        return new TransporteDto(
                transporte.getId(),
                transporte.getPlaca(),
                transporte.getNumeroserie(),
                transporte.getModelo(),
                transporte.getMarca(),
                transporte.getColor(),
                transporte.getDescripcion(),
                transporte.getTipoVehiculo(),
                transporte.getCapacidad(),
                transporte.getActivo(),
                transporte.getFechaCreacion());
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record OrderPallet(String codigo, String estado) {}
}
