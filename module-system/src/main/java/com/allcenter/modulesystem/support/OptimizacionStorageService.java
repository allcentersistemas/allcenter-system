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
public class OptimizacionStorageService {

    private final Path root;

    public OptimizacionStorageService(
            @Value("${app.optimizacion.storage-dir:./var/optimizacion-media}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
    }

    public String saveCotizacion(long proyectoId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Archivo vacío");
        }
        String ext = safeExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + ext;
        Path dir = root.resolve("cotizacion").resolve(Long.toString(proyectoId));
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        return name;
    }

    public Resource loadCotizacion(long proyectoId, String filename) {
        assertSafeFilename(filename);
        Path file = resolve(proyectoId, filename);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(NOT_FOUND, "Archivo no encontrado");
        }
        return new FileSystemResource(file);
    }

    private Path resolve(long proyectoId, String filename) {
        Path p = root.resolve("cotizacion").resolve(Long.toString(proyectoId)).resolve(filename).normalize();
        if (!p.startsWith(root)) {
            throw new ResponseStatusException(BAD_REQUEST, "Ruta inválida");
        }
        return p;
    }

    private static void assertSafeFilename(String filename) {
        if (filename == null
                || filename.isBlank()
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")) {
            throw new ResponseStatusException(BAD_REQUEST, "Nombre de archivo inválido");
        }
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
