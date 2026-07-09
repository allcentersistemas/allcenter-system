package com.allcenter.modulesystem.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MediaBackupService {

    public static final String MEDIA_ZIP_PREFIX = "media_files_";
    public static final String ZIP_ENTRY_OPTIMIZACION = "optimizacion-media/";
    public static final String ZIP_ENTRY_RM = "rm-media/";

    private final Path optimizacionRoot;
    private final Path rmRoot;
    private final List<Path> optimizacionSearchRoots;
    private final List<Path> rmSearchRoots;

    public MediaBackupService(
            @Value("${app.optimizacion.storage-dir:./var/optimizacion-media}") String optimizacionDir,
            @Value("${app.rm.media-dir:./var/rm-media}") String rmDir) {
        this.optimizacionRoot = Paths.get(optimizacionDir).toAbsolutePath().normalize();
        this.rmRoot = Paths.get(rmDir).toAbsolutePath().normalize();
        this.optimizacionSearchRoots = buildSearchRoots(optimizacionRoot, "/data/optimizacion-media", "./var/optimizacion-media");
        this.rmSearchRoots = buildSearchRoots(rmRoot, "/data/rm-media", "./var/rm-media");
        log.info("Media backup: optimizacion={}, rm={}", optimizacionRoot, rmRoot);
    }

    public Path optimizacionMediaRoot() {
        return optimizacionRoot;
    }

    public Path rmMediaRoot() {
        return rmRoot;
    }

    public String mediaZipFileName(String stamp) {
        return MEDIA_ZIP_PREFIX + stamp + ".zip";
    }

    public boolean isMediaZipName(String filename) {
        return filename != null
                && filename.startsWith(MEDIA_ZIP_PREFIX)
                && filename.endsWith(".zip");
    }

    /** Crea media_files_{stamp}.zip en targetDir. Devuelve tamaño en bytes. */
    public long createMediaArchive(Path targetDir, String stamp) throws IOException {
        Files.createDirectories(targetDir);
        String fileName = mediaZipFileName(stamp);
        Path zipPath = targetDir.resolve(fileName);
        long filesZipped = 0L;
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            filesZipped += zipExistingTree(zos, pickExistingRoot(optimizacionSearchRoots), ZIP_ENTRY_OPTIMIZACION);
            filesZipped += zipExistingTree(zos, pickExistingRoot(rmSearchRoots), ZIP_ENTRY_RM);
        }
        long size = Files.size(zipPath);
        log.info("Backup de archivos {} ({} archivo(s), {})", zipPath, filesZipped, formatSize(size));
        return size;
    }

    public void restoreMediaArchive(Path zipFile) throws IOException {
        if (!Files.isRegularFile(zipFile)) {
            throw new IOException("Archivo de backup de medios no encontrado");
        }
        Files.createDirectories(optimizacionRoot);
        Files.createDirectories(rmRoot);
        int restored = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = entry.getName().replace('\\', '/');
                Path target = resolveRestoreTarget(normalized);
                if (target == null) {
                    log.warn("Entrada ignorada en restore de medios: {}", normalized);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    zis.transferTo(out);
                }
                restored++;
            }
        }
        log.info("Restauración de archivos completada desde {} ({} archivo(s))", zipFile.getFileName(), restored);
    }

    private Path resolveRestoreTarget(String entryName) throws IOException {
        if (entryName.contains("..")) {
            return null;
        }
        if (entryName.startsWith(ZIP_ENTRY_OPTIMIZACION)) {
            String relative = entryName.substring(ZIP_ENTRY_OPTIMIZACION.length());
            if (relative.isBlank()) {
                return null;
            }
            return optimizacionRoot.resolve(relative).normalize();
        }
        if (entryName.startsWith(ZIP_ENTRY_RM)) {
            String relative = entryName.substring(ZIP_ENTRY_RM.length());
            if (relative.isBlank()) {
                return null;
            }
            return rmRoot.resolve(relative).normalize();
        }
        return null;
    }

    private long zipExistingTree(ZipOutputStream zos, Path sourceRoot, String zipPrefix) throws IOException {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return 0L;
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
        for (Path file : files) {
            String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
            String entryName = zipPrefix + relative;
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            try (InputStream in = Files.newInputStream(file)) {
                in.transferTo(zos);
            }
            zos.closeEntry();
        }
        return files.size();
    }

    private static Path pickExistingRoot(List<Path> roots) {
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                return root;
            }
        }
        return roots.isEmpty() ? null : roots.get(0);
    }

    private static List<Path> buildSearchRoots(Path primary, String... extras) {
        List<Path> roots = new ArrayList<>();
        roots.add(primary);
        for (String extra : extras) {
            Path candidate = Paths.get(extra).toAbsolutePath().normalize();
            if (!roots.contains(candidate)) {
                roots.add(candidate);
            }
        }
        return roots;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
