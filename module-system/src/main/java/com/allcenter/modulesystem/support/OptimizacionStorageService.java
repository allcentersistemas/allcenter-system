package com.allcenter.modulesystem.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OptimizacionStorageService {

    private static final Logger log = LoggerFactory.getLogger(OptimizacionStorageService.class);

    private final Path root;

    public OptimizacionStorageService(
            @Value("${app.optimizacion.storage-dir:./var/optimizacion-media}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root.resolve("cotizacion"));
        } catch (IOException ex) {
            log.warn("No se pudo crear directorio de cotizaciones en {}: {}", this.root, ex.getMessage());
        }
    }

    public String saveCotizacion(long proyectoId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Archivo vacío");
        }
        String ext = safeExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + ext;
        Path dir = cotizacionDir(proyectoId);
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        return name;
    }

    public Resource loadCotizacion(long proyectoId, String filename) {
        Path file = findCotizacionFile(proyectoId, filename);
        if (file == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "El archivo de cotización no está en el servidor. Ventas debe volver a subirla.");
        }
        return new FileSystemResource(file);
    }

    public byte[] readCotizacionBytes(long proyectoId, String filename) throws IOException {
        Path file = findCotizacionFile(proyectoId, filename);
        if (file == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "El archivo de cotización no está en el servidor. Ventas debe volver a subirla.");
        }
        return Files.readAllBytes(file);
    }

    public String cotizacionContentType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        return "application/octet-stream";
    }

    public String cotizacionDownloadName(long proyectoId, String storedFilename, String proyectoNombre) {
        String ext = ".pdf";
        String normalized = normalizeStoredFilename(storedFilename);
        if (normalized != null && normalized.contains(".")) {
            ext = normalized.substring(normalized.lastIndexOf('.'));
        }
        String base =
                proyectoNombre == null || proyectoNombre.isBlank()
                        ? "cotizacion-proyecto-" + proyectoId
                        : proyectoNombre.trim().replaceAll("[^\\w\\-. ]+", "_");
        return "Cotizacion-" + base + ext;
    }

    public boolean cotizacionExists(long proyectoId, String filename) {
        return findCotizacionFile(proyectoId, filename) != null;
    }

    /** Nombre de archivo listo para descargar (resuelve rutas antiguas o BD vacía). */
    public String resolveCotizacionFilename(long proyectoId, String storedFilename) {
        Path found = findCotizacionFile(proyectoId, storedFilename);
        return found == null ? null : found.getFileName().toString();
    }

    private Path findCotizacionFile(long proyectoId, String storedFilename) {
        String filename = normalizeStoredFilename(storedFilename);

        if (filename != null) {
            List<Path> candidates = new ArrayList<>();
            candidates.add(cotizacionDir(proyectoId).resolve(filename));
            candidates.add(root.resolve("cotizacion").resolve(filename));
            candidates.add(root.resolve(filename));

            for (Path candidate : candidates) {
                if (isReadableFile(candidate)) {
                    return candidate;
                }
            }

            Path fromProjectDir = findInProjectDir(proyectoId, filename);
            if (fromProjectDir != null) {
                return fromProjectDir;
            }

            Path recursive = findCotizacionFileRecursive(filename);
            if (recursive != null) {
                log.info(
                        "Cotización proyecto {}: archivo {} encontrado en ruta {}",
                        proyectoId,
                        filename,
                        recursive);
                return recursive;
            }
        }

        return findLatestCotizacionInProjectDir(proyectoId);
    }

    private Path findCotizacionFileRecursive(String filename) {
        if (filename == null) {
            return null;
        }
        Path cotizacionRoot = root.resolve("cotizacion");
        if (!Files.isDirectory(cotizacionRoot)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(cotizacionRoot, 4)) {
            return stream
                    .filter(OptimizacionStorageService::isReadableFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            log.warn("No se pudo buscar cotización recursiva {}: {}", filename, ex.getMessage());
            return null;
        }
    }

    private Path findInProjectDir(long proyectoId, String filename) {
        Path projectDir = cotizacionDir(proyectoId);
        if (!Files.isDirectory(projectDir)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(projectDir)) {
            List<Path> files = stream.filter(OptimizacionStorageService::isReadableFile).toList();
            Path exactIgnoreCase =
                    files.stream()
                            .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                            .findFirst()
                            .orElse(null);
            if (exactIgnoreCase != null) {
                return exactIgnoreCase;
            }
            if (files.size() == 1) {
                log.info(
                        "Cotización proyecto {}: usando único archivo en disco ({}) aunque BD indique {}",
                        proyectoId,
                        files.get(0).getFileName(),
                        filename);
                return files.get(0);
            }
        } catch (IOException ex) {
            log.warn("No se pudo listar cotizaciones del proyecto {}: {}", proyectoId, ex.getMessage());
        }
        return null;
    }

    private Path findLatestCotizacionInProjectDir(long proyectoId) {
        Path projectDir = cotizacionDir(proyectoId);
        if (!Files.isDirectory(projectDir)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(projectDir)) {
            List<Path> files = stream.filter(OptimizacionStorageService::isReadableFile).toList();
            if (files.isEmpty()) {
                return null;
            }
            if (files.size() == 1) {
                log.info(
                        "Cotización proyecto {}: usando único archivo en disco ({}) sin nombre en BD",
                        proyectoId,
                        files.get(0).getFileName());
                return files.get(0);
            }
            Path latest =
                    files.stream()
                            .max(
                                    (a, b) -> {
                                        try {
                                            return Files.getLastModifiedTime(a)
                                                    .compareTo(Files.getLastModifiedTime(b));
                                        } catch (IOException ex) {
                                            return a.getFileName()
                                                    .toString()
                                                    .compareToIgnoreCase(b.getFileName().toString());
                                        }
                                    })
                            .orElse(null);
            if (latest != null) {
                log.info(
                        "Cotización proyecto {}: usando archivo más reciente ({}) sin coincidencia exacta en BD",
                        proyectoId,
                        latest.getFileName());
            }
            return latest;
        } catch (IOException ex) {
            log.warn("No se pudo listar cotizaciones del proyecto {}: {}", proyectoId, ex.getMessage());
            return null;
        }
    }

    private Path cotizacionDir(long proyectoId) {
        return root.resolve("cotizacion").resolve(Long.toString(proyectoId));
    }

    private static boolean isReadableFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    /** Acepta solo el nombre de archivo aunque en BD quedó una ruta antigua. */
    static String normalizeStoredFilename(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String trimmed = stored.trim().replace('\\', '/');
        int slash = trimmed.lastIndexOf('/');
        String name = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
        if (name.isBlank() || name.contains("..")) {
            return null;
        }
        return name;
    }

    private static String safeExtension(String original) {
        if (original == null || !original.contains(".")) {
            return ".pdf";
        }
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return ".pdf";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return lower.endsWith(".xlsx") ? ".xlsx" : ".xls";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return lower.endsWith(".docx") ? ".docx" : ".doc";
        }
        return ".bin";
    }
}
