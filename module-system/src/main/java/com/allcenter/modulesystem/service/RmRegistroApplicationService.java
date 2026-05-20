package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.RmApiModels;
import com.allcenter.modulesystem.dto.RmPayloadModels;
import com.allcenter.modulesystem.model.RmActaConformidad;
import com.allcenter.modulesystem.model.RmRegistroEntrada;
import com.allcenter.modulesystem.model.RmRegistroEntradaDetalle;
import com.allcenter.modulesystem.model.RmRegistroSalida;
import com.allcenter.modulesystem.model.RmRegistroSalidaDetalle;
import com.allcenter.modulesystem.model.RmRegistroVehiculo;
import com.allcenter.modulesystem.repository.RmActaConformidadRepository;
import com.allcenter.modulesystem.repository.RmRegistroEntradaDetalleRepository;
import com.allcenter.modulesystem.repository.RmRegistroEntradaRepository;
import com.allcenter.modulesystem.repository.RmRegistroSalidaDetalleRepository;
import com.allcenter.modulesystem.repository.RmRegistroSalidaRepository;
import com.allcenter.modulesystem.repository.RmRegistroVehiculoRepository;
import com.allcenter.modulesystem.support.PhotoFilenameCodec;
import com.allcenter.modulesystem.support.RmMediaKinds;
import com.allcenter.modulesystem.support.RmMultipartUtil;
import com.allcenter.modulesystem.support.RmStorageService;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RmRegistroApplicationService {

    private final ObjectMapper objectMapper;
    private final PhotoFilenameCodec photoFilenameCodec;
    private final RmStorageService storageService;
    private final RmRegistroEntradaRepository entradaRepository;
    private final RmRegistroEntradaDetalleRepository entradaDetalleRepository;
    private final RmRegistroSalidaRepository salidaRepository;
    private final RmRegistroSalidaDetalleRepository salidaDetalleRepository;
    private final RmRegistroVehiculoRepository vehiculoRepository;
    private final RmActaConformidadRepository actaRepository;
    private final EmployeeAuthService employeeAuthService;

    @PostConstruct
    void initStorage() throws IOException {
        storageService.ensureReady();
    }

    @Transactional
    public RmApiModels.Created createRegistroEntrada(
            byte[] dataJsonBytes, List<MultipartFile> photos, String createdByEmail) {
        RmPayloadModels.EntradaPayload payload = readJson(dataJsonBytes, RmPayloadModels.EntradaPayload.class);
        validateEntradaPayload(payload);
        if (Boolean.TRUE.equals(payload.recepcionConformidadCerrada())) {
            requireChoferValidacionPassword(
                    payload.choferValidacionEmpleadoId(), payload.confirmPassword(), "recepción");
        }
        RmRegistroVehiculo vehiculo = vehiculoRepository
                .findById(payload.registroVehiculoId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registro de vehiculo no encontrado"));

        List<MultipartFile> plist = RmMultipartUtil.normalizePhotos(photos);
        int docCount = payload.documentoFotosCount();
        int expected =
                docCount + payload.detalles().stream().mapToInt(RmPayloadModels.EntradaDetalle::fotosCount).sum();
        if (expected != plist.size()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "El numero de fotos no coincide con documentoFotosCount y fotosCount");
        }

        String tipoDoc = normalizeTipoDocumento(payload.tipoDocumento());
        RmRegistroEntrada ent = new RmRegistroEntrada();
        ent.setRegistroVehiculo(vehiculo);
        ent.setFecha(resolveEntradaFecha(payload, vehiculo));
        ent.setHora(resolveEntradaHora(payload, vehiculo));
        ent.setTipoDocumento(tipoDoc);
        if ("OC".equals(tipoDoc)) {
            ent.setOcNumero(trimMax(payload.ocNumero(), 128));
            ent.setGuiaNumero(null);
        } else {
            ent.setGuiaNumero(trimMax(payload.guiaNumero(), 128));
            ent.setOcNumero(null);
        }
        ent.setCreatedByEmail(trimMaxNullable(createdByEmail, 320));
        ent.setDocumentoPhotoFilenamesJson("[]");

        if (Boolean.TRUE.equals(payload.recepcionConformidadCerrada())) {
            if (createdByEmail == null || createdByEmail.isBlank()) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Se requiere usuario autenticado (cabecera X-User-Email) para validar la recepcion");
            }
            ent.setRecepcionEstado("VALIDADO");
            ent.setValidadoAt(Instant.now());
            ent.setValidadoPorEmail(trimMaxNullable(createdByEmail, 320));
            ent.setChoferValidacionEmpleadoId(payload.choferValidacionEmpleadoId());
            ent.setChoferValidacionNombre(trimMaxNullable(payload.choferValidacionNombre(), 256));
        }

        for (RmPayloadModels.EntradaDetalle d : payload.detalles()) {
            RmRegistroEntradaDetalle row = new RmRegistroEntradaDetalle();
            row.setRegistroEntrada(ent);
            row.setProveedor(trimMax(d.proveedor(), 512));
            row.setMaterial(trimMax(d.material(), 512));
            row.setColorModelo(trimMaxNullable(d.colorModelo(), 256));
            row.setCantidadRecibida(trimMaxNullable(d.cantidadRecibida(), 64));
            row.setUnidad(trimMaxNullable(d.unidad(), 64));
            row.setPhotoFilenamesJson("[]");
            ent.getDetalles().add(row);
        }

        entradaRepository.saveAndFlush(ent);

        int pi = 0;
        if (docCount > 0) {
            List<String> docNames = new ArrayList<>();
            for (int c = 0; c < docCount; c++) {
                docNames.add(savePhoto(RmMediaKinds.ENTRADA_DOCUMENTO, ent.getId(), plist.get(pi++)));
            }
            try {
                ent.setDocumentoPhotoFilenamesJson(photoFilenameCodec.writeList(docNames));
            } catch (RuntimeException e) {
                throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar fotos del documento");
            }
            entradaRepository.save(ent);
        }

        for (int i = 0; i < ent.getDetalles().size(); i++) {
            RmRegistroEntradaDetalle row = ent.getDetalles().get(i);
            RmPayloadModels.EntradaDetalle spec = payload.detalles().get(i);
            List<String> names = new ArrayList<>();
            for (int k = 0; k < spec.fotosCount(); k++) {
                names.add(savePhoto(RmMediaKinds.ENTRADA_DETALLE, row.getId(), plist.get(pi++)));
            }
            try {
                row.setPhotoFilenamesJson(photoFilenameCodec.writeList(names));
            } catch (RuntimeException e) {
                throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar fotos de producto");
            }
        }
        entradaDetalleRepository.saveAll(ent.getDetalles());

        return new RmApiModels.Created(ent.getId());
    }

    @Transactional(readOnly = true)
    public List<RmRegistroEntrada> listEntradasByVehiculo(long registroVehiculoId) {
        if (!vehiculoRepository.existsById(registroVehiculoId)) {
            throw new ResponseStatusException(NOT_FOUND, "Registro de vehiculo no encontrado");
        }
        return entradaRepository.findByRegistroVehiculoIdOrderByCreatedAtDesc(registroVehiculoId);
    }

    @Transactional(readOnly = true)
    public Page<RmRegistroEntrada> pageEntradas(Pageable pageable) {
        return entradaRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public RmRegistroEntrada getEntrada(long id) {
        return entradaRepository
                .findByIdWithDetalles(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registro de entrada no encontrado"));
    }

    @Transactional
    public RmApiModels.Created createRegistroSalida(
            byte[] dataJsonBytes, List<MultipartFile> photos, String createdByEmail) {
        RmPayloadModels.SalidaPayload payload = readJson(dataJsonBytes, RmPayloadModels.SalidaPayload.class);
        validateSalidaPayload(payload);
        if (Boolean.TRUE.equals(payload.salidaConformidadCerrada())) {
            requireChoferValidacionPassword(
                    payload.choferValidacionEmpleadoId(), payload.confirmPassword(), "salida");
        }
        List<MultipartFile> plist = RmMultipartUtil.normalizePhotos(photos);
        int expected = payload.cabeceraFotosCount()
                + payload.detalles().stream().mapToInt(RmPayloadModels.SalidaDetalle::fotosCount).sum();
        if (expected != plist.size()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "El numero de fotos no coincide con cabeceraFotosCount y fotosCount");
        }

        RmRegistroSalida sal = new RmRegistroSalida();
        sal.setFecha(LocalDate.parse(payload.fecha().trim()));
        sal.setHoraCabecera(trimMax(payload.hora(), 16));
        sal.setTransporteId(payload.transporteId());
        sal.setCreatedByEmail(trimMaxNullable(createdByEmail, 320));
        sal.setChoferSalidaEmpleadoId(payload.choferSalidaEmpleadoId());
        sal.setChoferSalidaNombre(trimMaxNullable(payload.choferSalidaNombre(), 256));
        if (Boolean.TRUE.equals(payload.salidaConformidadCerrada())) {
            if (createdByEmail == null || createdByEmail.isBlank()) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Se requiere usuario autenticado (cabecera X-User-Email) para validar la salida");
            }
            sal.setRecepcionEstado("VALIDADO");
            sal.setValidadoAt(Instant.now());
            sal.setValidadoPorEmail(trimMaxNullable(createdByEmail, 320));
            sal.setChoferValidacionEmpleadoId(payload.choferValidacionEmpleadoId());
            sal.setChoferValidacionNombre(trimMaxNullable(payload.choferValidacionNombre(), 256));
        } else {
            sal.setRecepcionEstado(null);
            sal.setValidadoAt(null);
            sal.setValidadoPorEmail(null);
            sal.setChoferValidacionEmpleadoId(null);
            sal.setChoferValidacionNombre(null);
        }
        sal.setCabeceraPhotoFilenamesJson("[]");

        for (RmPayloadModels.SalidaDetalle d : payload.detalles()) {
            RmRegistroSalidaDetalle row = new RmRegistroSalidaDetalle();
            row.setRegistroSalida(sal);
            row.setHora(trimMaxNullable(d.hora(), 16));
            row.setDestino(trimMax(d.destino(), 512));
            row.setNoRqmVale(trimMaxNullable(d.noRqmVale(), 128));
            row.setNoGuia(trimMaxNullable(d.noGuia(), 128));
            row.setMaterialProducto(trimMax(d.materialProducto(), 512));
            row.setCantidad(trimMax(d.cantidad(), 64));
            row.setUnidad(trimMax(d.unidad(), 64));
            row.setRecibeFirma(trimMax(d.recibeFirma(), 256));
            row.setEntregaRci(trimMax(d.entregaRci(), 256));
            row.setPhotoFilenamesJson("[]");
            sal.getDetalles().add(row);
        }

        salidaRepository.saveAndFlush(sal);

        int pi = 0;
        List<String> cabNames = new ArrayList<>();
        for (int c = 0; c < payload.cabeceraFotosCount(); c++) {
            cabNames.add(savePhoto(RmMediaKinds.SALIDA_CABECERA, sal.getId(), plist.get(pi++)));
        }
        try {
            sal.setCabeceraPhotoFilenamesJson(photoFilenameCodec.writeList(cabNames));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar fotos de cabecera");
        }
        salidaRepository.save(sal);

        for (int i = 0; i < sal.getDetalles().size(); i++) {
            RmRegistroSalidaDetalle row = sal.getDetalles().get(i);
            RmPayloadModels.SalidaDetalle spec = payload.detalles().get(i);
            List<String> names = new ArrayList<>();
            for (int k = 0; k < spec.fotosCount(); k++) {
                names.add(savePhoto(RmMediaKinds.SALIDA_DETALLE, row.getId(), plist.get(pi++)));
            }
            try {
                row.setPhotoFilenamesJson(photoFilenameCodec.writeList(names));
            } catch (RuntimeException e) {
                throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar fotos de detalle");
            }
        }
        salidaDetalleRepository.saveAll(sal.getDetalles());
        return new RmApiModels.Created(sal.getId());
    }

    @Transactional(readOnly = true)
    public Page<RmRegistroSalida> pageSalidas(Pageable pageable) {
        return salidaRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public RmRegistroSalida getSalida(long id) {
        return salidaRepository
                .findByIdWithDetalles(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registro de salida no encontrado"));
    }

    @Transactional
    public RmApiModels.Created createRegistroVehiculo(
            byte[] dataJsonBytes, List<MultipartFile> photos, String createdByEmail) {
        RmPayloadModels.VehiculoPayload payload = readJson(dataJsonBytes, RmPayloadModels.VehiculoPayload.class);
        validateVehiculoPayload(payload);
        List<MultipartFile> plist = RmMultipartUtil.normalizePhotos(photos);
        if (payload.fotosCount() != plist.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "El numero de fotos no coincide con fotosCount");
        }

        RmRegistroVehiculo v = new RmRegistroVehiculo();
        v.setFecha(LocalDate.parse(payload.fecha().trim()));
        v.setHoraIngreso(trimMaxNullable(payload.horaIngreso(), 16));
        v.setMarca(trimMax(payload.marca(), 128));
        v.setPlaca(trimMax(payload.placa(), 32).toUpperCase(Locale.ROOT));
        v.setChofer(trimMax(payload.chofer(), 256));
        v.setKilometraje(trimMaxNullable(payload.kilometraje(), 32));
        v.setHoraSalida(trimMaxNullable(payload.horaSalida(), 16));
        v.setCreatedByEmail(trimMaxNullable(createdByEmail, 320));
        v.setPhotoFilenamesJson("[]");
        vehiculoRepository.saveAndFlush(v);

        List<String> names = new ArrayList<>();
        for (MultipartFile p : plist) {
            names.add(savePhoto(RmMediaKinds.VEHICULO, v.getId(), p));
        }
        try {
            v.setPhotoFilenamesJson(photoFilenameCodec.writeList(names));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar fotos");
        }
        vehiculoRepository.save(v);
        return new RmApiModels.Created(v.getId());
    }

    @Transactional(readOnly = true)
    public Page<RmRegistroVehiculo> pageVehiculos(Pageable pageable) {
        return vehiculoRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public RmRegistroVehiculo getVehiculo(long id) {
        return vehiculoRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Registro de vehiculo no encontrado"));
    }

    @Transactional
    public RmApiModels.Created createActaConformidad(
            byte[] dataJsonBytes, List<MultipartFile> photos, String createdByEmail) {
        RmPayloadModels.ActaPayload payload = readJson(dataJsonBytes, RmPayloadModels.ActaPayload.class);
        validateActaPayload(payload);
        List<MultipartFile> plist = RmMultipartUtil.normalizePhotos(photos);
        if (payload.fotosCount() != plist.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "El numero de fotos no coincide con fotosCount");
        }

        RmActaConformidad a = new RmActaConformidad();
        a.setRazonSocialNombre(trimMax(payload.razonSocialNombre(), 512));
        a.setGuiaRemisionNum(trimMaxNullable(payload.guiaRemisionNum(), 128));
        a.setFacturaOrdenCompraNum(trimMaxNullable(payload.facturaOrdenCompraNum(), 128));
        a.setTransportistaNombrePlaca(trimMaxNullable(payload.transportistaNombrePlaca(), 512));
        try {
            a.setTiposJson(objectMapper.writeValueAsString(payload.tipos() == null ? List.of() : payload.tipos()));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar tipos");
        }
        a.setDescripcionAmpliada(payload.descripcionAmpliada().trim());
        a.setDecision(trimMax(payload.decision(), 64).toUpperCase(Locale.ROOT));
        a.setCantidadConformeUnidades(payload.cantidadConformeUnidades());
        a.setObservacionesDecision(trimMaxNullable(payload.observacionesDecision(), 4000));
        a.setCreatedByEmail(trimMaxNullable(createdByEmail, 320));
        a.setPhotoFilenamesJson("[]");
        actaRepository.saveAndFlush(a);

        List<String> names = new ArrayList<>();
        for (MultipartFile p : plist) {
            names.add(savePhoto(RmMediaKinds.ACTA, a.getId(), p));
        }
        try {
            a.setPhotoFilenamesJson(photoFilenameCodec.writeList(names));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo serializar fotos");
        }
        actaRepository.save(a);
        return new RmApiModels.Created(a.getId());
    }

    @Transactional(readOnly = true)
    public Page<RmActaConformidad> pageActas(Pageable pageable) {
        return actaRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public RmActaConformidad getActa(long id) {
        return actaRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Acta no encontrada"));
    }

    @Transactional(readOnly = true)
    public Resource loadMediaFile(String kind, long recordId, String filename) {
        String k = RmMediaKinds.normalize(kind);
        assertMediaAllowed(k, recordId, filename);
        return storageService.load(k, recordId, filename);
    }

    @Transactional(readOnly = true)
    public void assertMediaAllowed(String kind, long recordId, String filename) {
        RmMediaKinds.requireKnown(kind);
        List<String> allowed =
                switch (kind) {
                    case RmMediaKinds.ENTRADA_DETALLE -> photoFilenameCodec.readList(
                            entradaDetalleRepository
                                    .findById(recordId)
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Detalle no encontrado"))
                                    .getPhotoFilenamesJson());
                    case RmMediaKinds.ENTRADA_DOCUMENTO -> photoFilenameCodec.readList(
                            entradaRepository
                                    .findById(recordId)
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Entrada no encontrada"))
                                    .getDocumentoPhotoFilenamesJson());
                    case RmMediaKinds.SALIDA_CABECERA -> photoFilenameCodec.readList(
                            salidaRepository
                                    .findById(recordId)
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Salida no encontrada"))
                                    .getCabeceraPhotoFilenamesJson());
                    case RmMediaKinds.SALIDA_DETALLE -> photoFilenameCodec.readList(
                            salidaDetalleRepository
                                    .findById(recordId)
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Detalle no encontrado"))
                                    .getPhotoFilenamesJson());
                    case RmMediaKinds.ACTA -> photoFilenameCodec.readList(
                            actaRepository
                                    .findById(recordId)
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Acta no encontrada"))
                                    .getPhotoFilenamesJson());
                    case RmMediaKinds.VEHICULO -> photoFilenameCodec.readList(
                            vehiculoRepository
                                    .findById(recordId)
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vehiculo no encontrado"))
                                    .getPhotoFilenamesJson());
                    default -> throw new ResponseStatusException(BAD_REQUEST, "Tipo de media no soportado");
                };
        if (!filenameAllowed(allowed, filename)) {
            throw new ResponseStatusException(NOT_FOUND, "Archivo no asociado al registro");
        }
    }

    private static boolean filenameAllowed(List<String> allowed, String filename) {
        String requested = mediaBasename(filename);
        for (String entry : allowed) {
            if (entry != null && mediaBasename(entry).equals(requested)) {
                return true;
            }
        }
        return false;
    }

    private static String mediaBasename(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String t = value.trim();
        int q = t.indexOf('?');
        if (q >= 0) {
            t = t.substring(0, q);
        }
        int slash = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
        return slash >= 0 ? t.substring(slash + 1) : t;
    }

    private String savePhoto(String kind, long recordId, MultipartFile file) {
        try {
            return storageService.saveUploaded(kind, recordId, file);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo guardar la foto");
        }
    }

    private <T> T readJson(byte[] bytes, Class<T> type) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Parte data vacia");
        }
        try {
            return objectMapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "JSON invalido en parte data");
        }
    }

    /** Valida la contraseña del chofer seleccionado en conformidad (rol CHOFER en catálogo). */
    private void requireChoferValidacionPassword(Long choferEmpleadoId, String confirmPassword, String flowLabel) {
        if (choferEmpleadoId == null || choferEmpleadoId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Chofer que valida (empleado) obligatorio");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "La contraseña del chofer que valida es obligatoria para cerrar la " + flowLabel);
        }
        employeeAuthService.verifyCurrentPassword(choferEmpleadoId, confirmPassword);
    }

    private static void validateEntradaPayload(RmPayloadModels.EntradaPayload payload) {
        if (payload.registroVehiculoId() == null || payload.registroVehiculoId() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "registroVehiculoId obligatorio");
        }
        if (payload.detalles() == null || payload.detalles().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Debe haber al menos un producto (detalle)");
        }
        String tipo = normalizeTipoDocumento(payload.tipoDocumento());
        if ("OC".equals(tipo)) {
            if (payload.ocNumero() == null || payload.ocNumero().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "ocNumero obligatorio para documento OC");
            }
        } else if ("NG".equals(tipo)) {
            if (payload.guiaNumero() == null || payload.guiaNumero().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "guiaNumero obligatorio para documento NG");
            }
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "tipoDocumento debe ser OC o NG");
        }
        if (payload.documentoFotosCount() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "documentoFotosCount invalido");
        }
        if (Boolean.TRUE.equals(payload.recepcionConformidadCerrada())) {
            if (payload.documentoFotosCount() < 1) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Debe incluir al menos una foto del documento (documentoFotosCount >= 1)");
            }
            if (payload.choferValidacionEmpleadoId() == null || payload.choferValidacionEmpleadoId() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Chofer que valida (empleado) obligatorio");
            }
            if (payload.choferValidacionNombre() == null || payload.choferValidacionNombre().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Nombre de chofer que valida obligatorio");
            }
        }
        for (RmPayloadModels.EntradaDetalle d : payload.detalles()) {
            if (d.proveedor() == null || d.proveedor().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Proveedor obligatorio en cada producto");
            }
            if (d.material() == null || d.material().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Material obligatorio en cada producto");
            }
            if (d.fotosCount() < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "fotosCount invalido");
            }
            if (d.fotosCount() > 4) {
                throw new ResponseStatusException(BAD_REQUEST, "Cada producto admite como maximo 4 fotos");
            }
        }
    }

    private static String normalizeTipoDocumento(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if ("OC".equals(t) || "ORDEN_COMPRA".equals(t) || "ORDEN DE COMPRA".equals(t)) {
            return "OC";
        }
        if ("NG".equals(t) || "GUIA".equals(t) || "GUÍA".equals(t) || "NUMERO_GUIA".equals(t)) {
            return "NG";
        }
        return t;
    }

    private static LocalDate resolveEntradaFecha(RmPayloadModels.EntradaPayload payload, RmRegistroVehiculo vehiculo) {
        if (payload.fecha() != null && !payload.fecha().isBlank()) {
            return LocalDate.parse(payload.fecha().trim());
        }
        if (vehiculo.getFecha() != null) {
            return vehiculo.getFecha();
        }
        throw new ResponseStatusException(BAD_REQUEST, "Fecha obligatoria si el vehiculo no tiene fecha");
    }

    private static String resolveEntradaHora(RmPayloadModels.EntradaPayload payload, RmRegistroVehiculo vehiculo) {
        if (payload.hora() != null && !payload.hora().isBlank()) {
            return trimMax(payload.hora(), 16);
        }
        if (vehiculo.getHoraIngreso() != null && !vehiculo.getHoraIngreso().isBlank()) {
            return trimMax(vehiculo.getHoraIngreso(), 16);
        }
        throw new ResponseStatusException(BAD_REQUEST, "Hora obligatoria si el vehiculo no tiene hora de ingreso");
    }

    private static void validateSalidaPayload(RmPayloadModels.SalidaPayload payload) {
        if (payload.detalles() == null || payload.detalles().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Debe haber al menos un detalle");
        }
        if (payload.fecha() == null || payload.fecha().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Fecha obligatoria");
        }
        if (payload.hora() == null || payload.hora().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Hora de cabecera obligatoria");
        }
        if (payload.transporteId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Debe indicar transporteId del vehículo registrado");
        }
        if (payload.choferSalidaEmpleadoId() == null || payload.choferSalidaEmpleadoId() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Chofer de salida (empleado) obligatorio");
        }
        if (payload.choferSalidaNombre() == null || payload.choferSalidaNombre().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nombre de chofer de salida obligatorio");
        }
        if (payload.cabeceraFotosCount() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "cabeceraFotosCount invalido");
        }
        if (Boolean.TRUE.equals(payload.salidaConformidadCerrada())) {
            if (payload.cabeceraFotosCount() < 1) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Conformidad de salida requiere al menos una foto de cabecera (cabeceraFotosCount >= 1)");
            }
            if (payload.choferValidacionEmpleadoId() == null || payload.choferValidacionEmpleadoId() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Chofer que valida la salida (empleado) obligatorio");
            }
            if (payload.choferValidacionNombre() == null || payload.choferValidacionNombre().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Nombre de chofer que valida la salida obligatorio");
            }
        }
        for (RmPayloadModels.SalidaDetalle d : payload.detalles()) {
            if (d.destino() == null || d.destino().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Destino obligatorio");
            }
            if (d.materialProducto() == null || d.materialProducto().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Material/producto obligatorio");
            }
            if (d.cantidad() == null || d.cantidad().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Cantidad obligatoria");
            }
            if (d.unidad() == null || d.unidad().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Unidad obligatoria");
            }
            if (d.recibeFirma() == null || d.recibeFirma().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Recibe obligatorio");
            }
            if (d.entregaRci() == null || d.entregaRci().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Entrega RCI obligatorio");
            }
            if (d.fotosCount() < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "fotosCount invalido");
            }
        }
    }

    private static void validateVehiculoPayload(RmPayloadModels.VehiculoPayload p) {
        if (p.fecha() == null || p.fecha().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Fecha obligatoria");
        }
        if (p.marca() == null || p.marca().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Marca obligatoria");
        }
        if (p.placa() == null || p.placa().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Placa obligatoria");
        }
        if (p.chofer() == null || p.chofer().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Chofer obligatorio");
        }
        if (p.fotosCount() < 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Debe incluir al menos una foto del vehiculo (fotosCount >= 1)");
        }
    }

    private static void validateActaPayload(RmPayloadModels.ActaPayload p) {
        if (p.razonSocialNombre() == null || p.razonSocialNombre().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Razon social obligatoria");
        }
        if (p.descripcionAmpliada() == null || p.descripcionAmpliada().trim().length() < 30) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Descripcion ampliada obligatoria (minimo 30 caracteres)");
        }
        if (p.tipos() == null || p.tipos().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Tipos obligatorios");
        }
        long marcados = p.tipos().stream().filter(RmPayloadModels.NcTipo::marcado).count();
        if (marcados == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Marca al menos un tipo de no conformidad");
        }
        for (RmPayloadModels.NcTipo t : p.tipos()) {
            if (t.marcado() && (t.detalle() == null || t.detalle().trim().length() < 3)) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Cada tipo marcado requiere detalle de al menos 3 caracteres");
            }
        }
        if (p.decision() == null || p.decision().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Decision obligatoria");
        }
        String decisionNorm = p.decision().trim().toUpperCase(Locale.ROOT);
        if ("RECHAZO_PARCIAL".equals(decisionNorm)
                && (p.cantidadConformeUnidades() == null || p.cantidadConformeUnidades() <= 0)) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Rechazo parcial requiere cantidadConformeUnidades > 0");
        }
        if (p.fotosCount() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "fotosCount invalido");
        }
    }

    private static String trimMax(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String trimMaxNullable(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }
}
