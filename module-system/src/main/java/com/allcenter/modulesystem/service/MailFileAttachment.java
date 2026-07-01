package com.allcenter.modulesystem.service;

import java.nio.file.Path;

public record MailFileAttachment(String filename, Path path, String contentType) {

    public MailFileAttachment(String filename, Path path) {
        this(filename, path, "application/octet-stream");
    }
}
