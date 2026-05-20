package com.allcenter.modulesystem.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RmStorageService {

    private final Path root;
    private final Path legacyRoot;

    public RmStorageService(@Value("${app.rm.media-dir:./var/rm-media}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
        Path legacy = Paths.get("./var/rm-media").toAbsolutePath().normalize();
        this.legacyRoot = legacy.equals(this.root) ? null : legacy;
    }

    public void ensureReady() throws IOException {
        Files.createDirectories(root);
    }

    /** Guarda un archivo bajo {@code root/kind/recordId/filenameGenerado}. Devuelve solo el nombre de archivo generado. */
    public String saveUploaded(String kind, long recordId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Foto vacia");
        }
        String original = file.getOriginalFilename();
        String ext = safeExtension(original);
        String name = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + ext;
        Path dir = root.resolve(kind).resolve(Long.toString(recordId));
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        return name;
    }

    public Resource load(String kind, long recordId, String filename) {
        assertSafeFilename(filename);
        Path file = resolveExisting(kind, recordId, filename);
        return new FileSystemResource(file);
    }

    public Path resolveExisting(String kind, long recordId, String filename) {
        assertSafeFilename(filename);
        Path p = resolveUnderRoot(root, kind, recordId, filename);
        if (Files.isRegularFile(p)) {
            return p;
        }
        if (RmMediaKinds.ENTRADA_DOCUMENTO.equals(kind)) {
            Path legacyKind = resolveUnderRoot(root, "entrada-cabecera-vehiculo", recordId, filename);
            if (Files.isRegularFile(legacyKind)) {
                return legacyKind;
            }
        }
        if (legacyRoot != null) {
            Path legacy = resolveUnderRoot(legacyRoot, kind, recordId, filename);
            if (Files.isRegularFile(legacy)) {
                return legacy;
            }
        }
        throw new ResponseStatusException(NOT_FOUND, "Archivo no encontrado");
    }

    private static Path resolveUnderRoot(Path base, String kind, long recordId, String filename) {
        Path p = base.resolve(kind).resolve(Long.toString(recordId)).resolve(filename).normalize();
        if (!p.startsWith(base)) {
            throw new ResponseStatusException(BAD_REQUEST, "Ruta invalida");
        }
        return p;
    }

    private static void assertSafeFilename(String filename) {
        if (filename == null
                || filename.isBlank()
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")) {
            throw new ResponseStatusException(BAD_REQUEST, "Nombre de archivo invalido");
        }
    }

    private static String safeExtension(String original) {
        if (original == null || !original.contains(".")) {
            return ".bin";
        }
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        return ".bin";
    }
}
