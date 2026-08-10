package com.allcenter.modulesystem.support;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.annotation.PostConstruct;
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
    private final List<Path> searchRoots;

    public OptimizacionStorageService(
            @Value("${app.optimizacion.storage-dir:./var/optimizacion-media}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
        this.searchRoots = buildSearchRoots(this.root);
        log.info("Almacenamiento de cotizaciones: raíz activa={}", this.root);
    }

    @PostConstruct
    void initialize() {
        try {
            ensureReady();
            migrateLegacyMedia();
        } catch (IOException ex) {
            log.warn("No se pudo preparar almacenamiento de cotizaciones en {}: {}", root, ex.getMessage());
        }
    }

    private static final String PLANTILLA_DIR = "plantillas";
    private static final String PLANTILLA_FILE = "listado_piezas.xlsx";
    private static final String PLANTILLA_META = "listado_piezas.meta.txt";

    /** Crea el árbol bajo la raíz persistente (igual que RM en /data/rm-media). */
    public void ensureReady() throws IOException {
        Files.createDirectories(root.resolve("cotizacion"));
        Files.createDirectories(root.resolve("planos"));
        Files.createDirectories(root.resolve(PLANTILLA_DIR));
    }

    public record PlantillaInfo(boolean available, String filename, long sizeBytes, String uploadedAt) {}

    public PlantillaInfo getPlantillaInfo() {
        Path file = plantillaPath();
        if (!Files.isRegularFile(file)) {
            return new PlantillaInfo(false, null, 0L, null);
        }
        try {
            long size = Files.size(file);
            String uploadedAt = Files.getLastModifiedTime(file).toInstant().toString();
            return new PlantillaInfo(true, readPlantillaOriginalName(), size, uploadedAt);
        } catch (IOException ex) {
            log.warn("No se pudo leer info de plantilla: {}", ex.getMessage());
            return new PlantillaInfo(false, null, 0L, null);
        }
    }

    public String savePlantilla(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Archivo vacío");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String lower = original.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new ResponseStatusException(BAD_REQUEST, "Solo se admiten archivos Excel (.xlsx o .xls)");
        }
        ensureReady();
        Path target = plantillaPath();
        file.transferTo(target.toFile());
        String safeName =
                original.isBlank()
                        ? "plantilla_listado_piezas.xlsx"
                        : Paths.get(original.replace('\\', '/')).getFileName().toString();
        Files.writeString(plantillaMetaPath(), safeName);
        log.info("Plantilla de planilla guardada en {} (nombre={})", target, safeName);
        return safeName;
    }

    public Resource loadPlantilla() {
        Path file = plantillaPath();
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(NOT_FOUND, "No hay plantilla cargada en el servidor");
        }
        return new FileSystemResource(file);
    }

    public String plantillaDownloadName() {
        return readPlantillaOriginalName();
    }

    public void deletePlantilla() throws IOException {
        Files.deleteIfExists(plantillaPath());
        Files.deleteIfExists(plantillaMetaPath());
    }

    private Path plantillaPath() {
        return root.resolve(PLANTILLA_DIR).resolve(PLANTILLA_FILE);
    }

    private Path plantillaMetaPath() {
        return root.resolve(PLANTILLA_DIR).resolve(PLANTILLA_META);
    }

    private String readPlantillaOriginalName() {
        try {
            Path meta = plantillaMetaPath();
            if (Files.isRegularFile(meta)) {
                String name = Files.readString(meta).trim();
                if (!name.isBlank() && !name.contains("..") && !name.contains("/") && !name.contains("\\")) {
                    return name;
                }
            }
        } catch (IOException ex) {
            log.warn("No se pudo leer meta de plantilla: {}", ex.getMessage());
        }
        return "plantilla_listado_piezas.xlsx";
    }

    /**
     * Copia cotizaciones desde rutas efímeras del contenedor (/app/var/…) al volumen persistente
     * configurado, para que no se pierdan al redeployar.
     */
    private void migrateLegacyMedia() throws IOException {
        int migrated = 0;
        for (Path legacyRoot : legacyMigrationRoots()) {
            if (!Files.isDirectory(legacyRoot)) {
                continue;
            }
            migrated += migrateTree(legacyRoot, root);
        }
        if (migrated > 0) {
            log.info("Migradas {} cotización(es) a {}", migrated, root);
        }
    }

    private int migrateTree(Path sourceRoot, Path targetRoot) throws IOException {
        if (sourceRoot.equals(targetRoot)) {
            return 0;
        }
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(
                sourceRoot,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        files.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
        int copied = 0;
        for (Path source : files) {
            String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
            if (relative.contains("..")) {
                continue;
            }
            Path target = targetRoot.resolve(relative).normalize();
            if (!target.startsWith(targetRoot)) {
                continue;
            }
            if (Files.isRegularFile(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            copied++;
            log.info("Cotización migrada: {} → {}", source, target);
        }
        return copied;
    }

    private List<Path> legacyMigrationRoots() {
        List<Path> roots = new ArrayList<>();
        addDistinctRoot(roots, Paths.get("/app/var/optimizacion-media"));
        addDistinctRoot(roots, Paths.get("./var/optimizacion-media"));
        addDistinctRoot(roots, Paths.get("/opt/allcenter/var/optimizacion-media"));
        for (Path searchRoot : searchRoots) {
            addDistinctRoot(roots, searchRoot);
        }
        roots.remove(root);
        return roots;
    }

    private static void addDistinctRoot(List<Path> roots, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) {
            roots.add(normalized);
        }
    }

    public String saveCotizacion(long proyectoId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Archivo vacío");
        }
        ensureReady();
        String ext = safeExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + ext;
        Path dir = cotizacionDir(root, proyectoId);
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        log.info("Cotización guardada proyecto {} en {}", proyectoId, target);
        return name;
    }

    public Resource loadCotizacion(long proyectoId, String filename) {
        Path file = findCotizacionFile(proyectoId, filename);
        if (file == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    buildMissingFileMessage(proyectoId, filename));
        }
        return new FileSystemResource(file);
    }

    public byte[] readCotizacionBytes(long proyectoId, String filename) throws IOException {
        Path file = findCotizacionFile(proyectoId, filename);
        if (file == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    buildMissingFileMessage(proyectoId, filename));
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
        for (Path searchRoot : searchRoots) {
            Path found = findCotizacionFileInRoot(searchRoot, proyectoId, storedFilename);
            if (found != null) {
                if (!searchRoot.equals(root)) {
                    log.warn(
                            "Cotización proyecto {} leída desde ruta legada {} (raíz activa {})",
                            proyectoId,
                            found,
                            root);
                }
                return found;
            }
        }

        String filename = normalizeStoredFilename(storedFilename);
        log.warn(
                "Cotización no encontrada: proyectoId={} archivo={} raízActiva={} rutasBuscadas={}",
                proyectoId,
                filename,
                root,
                searchRoots);
        return null;
    }

    private Path findCotizacionFileInRoot(Path searchRoot, long proyectoId, String storedFilename) {
        String filename = normalizeStoredFilename(storedFilename);

        if (filename != null) {
            List<Path> candidates = new ArrayList<>();
            candidates.add(cotizacionDir(searchRoot, proyectoId).resolve(filename));
            candidates.add(searchRoot.resolve("cotizacion").resolve(filename));
            candidates.add(searchRoot.resolve(filename));

            for (Path candidate : candidates) {
                if (isReadableFile(candidate)) {
                    return candidate;
                }
            }

            Path fromProjectDir = findInProjectDir(searchRoot, proyectoId, filename);
            if (fromProjectDir != null) {
                return fromProjectDir;
            }

            Path recursive = findCotizacionFileRecursive(searchRoot, filename);
            if (recursive != null) {
                return recursive;
            }
        }

        return findLatestCotizacionInProjectDir(searchRoot, proyectoId);
    }

    private Path findCotizacionFileRecursive(Path searchRoot, String filename) {
        if (filename == null) {
            return null;
        }
        Path cotizacionRoot = searchRoot.resolve("cotizacion");
        if (!Files.isDirectory(cotizacionRoot)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(cotizacionRoot, 6)) {
            return stream
                    .filter(OptimizacionStorageService::isReadableFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            log.warn("No se pudo buscar cotización recursiva {} en {}: {}", filename, searchRoot, ex.getMessage());
            return null;
        }
    }

    private Path findInProjectDir(Path searchRoot, long proyectoId, String filename) {
        Path projectDir = cotizacionDir(searchRoot, proyectoId);
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
                return files.get(0);
            }
        } catch (IOException ex) {
            log.warn("No se pudo listar cotizaciones del proyecto {} en {}: {}", proyectoId, searchRoot, ex.getMessage());
        }
        return null;
    }

    private Path findLatestCotizacionInProjectDir(Path searchRoot, long proyectoId) {
        Path projectDir = cotizacionDir(searchRoot, proyectoId);
        if (!Files.isDirectory(projectDir)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(projectDir)) {
            List<Path> files = stream.filter(OptimizacionStorageService::isReadableFile).toList();
            if (files.isEmpty()) {
                return null;
            }
            if (files.size() == 1) {
                return files.get(0);
            }
            return files.stream()
                    .max(
                            (a, b) -> {
                                try {
                                    return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                                } catch (IOException ex) {
                                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                                }
                            })
                    .orElse(null);
        } catch (IOException ex) {
            log.warn("No se pudo listar cotizaciones del proyecto {} en {}: {}", proyectoId, searchRoot, ex.getMessage());
            return null;
        }
    }

    private static Path cotizacionDir(Path searchRoot, long proyectoId) {
        return searchRoot.resolve("cotizacion").resolve(Long.toString(proyectoId));
    }

    public String savePlano(long proyectoId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Archivo vacío");
        }
        String original = file.getOriginalFilename();
        String lower = original == null ? "" : original.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".pdf") && !contentType.contains("pdf")) {
            throw new ResponseStatusException(BAD_REQUEST, "Los planos deben ser un archivo PDF.");
        }
        ensureReady();
        String name = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + ".pdf";
        Path dir = planoDir(root, proyectoId);
        Files.createDirectories(dir);
        // Un solo PDF por proyecto: limpia archivos previos en la carpeta.
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(OptimizacionStorageService::isReadableFile).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        log.info("Plano guardado proyecto {} en {}", proyectoId, target);
        return name;
    }

    public Resource loadPlano(long proyectoId, String filename) {
        Path file = findPlanoFile(proyectoId, filename);
        if (file == null) {
            throw new ResponseStatusException(NOT_FOUND, "No se encontró el archivo de planos en el servidor");
        }
        return new FileSystemResource(file);
    }

    public boolean planoExists(long proyectoId, String filename) {
        return findPlanoFile(proyectoId, filename) != null;
    }

    public String resolvePlanoFilename(long proyectoId, String storedFilename) {
        Path found = findPlanoFile(proyectoId, storedFilename);
        return found == null ? null : found.getFileName().toString();
    }

    public String planoDownloadName(long proyectoId, String storedFilename, String proyectoNombre) {
        String base =
                proyectoNombre == null || proyectoNombre.isBlank()
                        ? "planos-proyecto-" + proyectoId
                        : proyectoNombre.trim().replaceAll("[^\\w\\-. ]+", "_");
        return "Planos-" + base + ".pdf";
    }

    private Path findPlanoFile(long proyectoId, String storedFilename) {
        String filename = normalizeStoredFilename(storedFilename);
        for (Path searchRoot : searchRoots) {
            Path dir = planoDir(searchRoot, proyectoId);
            if (filename != null) {
                Path candidate = dir.resolve(filename);
                if (isReadableFile(candidate)) {
                    return candidate;
                }
            }
            if (Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.list(dir)) {
                    List<Path> pdfs =
                            stream.filter(OptimizacionStorageService::isReadableFile)
                                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                                    .toList();
                    if (pdfs.size() == 1) {
                        return pdfs.get(0);
                    }
                    if (!pdfs.isEmpty() && filename != null) {
                        Path match =
                                pdfs.stream()
                                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                                        .findFirst()
                                        .orElse(null);
                        if (match != null) {
                            return match;
                        }
                    }
                    if (pdfs.size() > 1) {
                        return pdfs.stream()
                                .max((a, b) -> {
                                    try {
                                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                                    } catch (IOException ex) {
                                        return 0;
                                    }
                                })
                                .orElse(null);
                    }
                } catch (IOException ex) {
                    log.warn("No se pudo listar planos del proyecto {} en {}: {}", proyectoId, searchRoot, ex.getMessage());
                }
            }
        }
        return null;
    }

    private static Path planoDir(Path searchRoot, long proyectoId) {
        return searchRoot.resolve("planos").resolve(Long.toString(proyectoId));
    }

    private static boolean isReadableFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    private String buildMissingFileMessage(long proyectoId, String storedFilename) {
        String filename = normalizeStoredFilename(storedFilename);
        String expected =
                filename == null
                        ? cotizacionDir(root, proyectoId).toString()
                        : cotizacionDir(root, proyectoId).resolve(filename).toString();
        return "No se Encontro Archivo en Servidor";
    }

    private static List<Path> buildSearchRoots(Path primaryRoot) {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(primaryRoot.toAbsolutePath().normalize());
        roots.add(Paths.get("/data/optimizacion-media").toAbsolutePath().normalize());
        addRootIfExists(roots, Paths.get("./var/optimizacion-media"));
        addRootIfExists(roots, Paths.get("/app/var/optimizacion-media"));
        addRootIfExists(roots, Paths.get("/opt/allcenter/var/optimizacion-media"));
        return List.copyOf(roots);
    }

    private static void addRootIfExists(Set<Path> roots, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (Files.exists(normalized) && !roots.contains(normalized)) {
            roots.add(normalized);
        }
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
