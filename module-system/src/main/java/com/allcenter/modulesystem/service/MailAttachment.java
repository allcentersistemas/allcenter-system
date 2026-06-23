package com.allcenter.modulesystem.service;

import java.util.List;

public record MailAttachment(String filename, byte[] content, String contentType) {

    public MailAttachment(String filename, byte[] content) {
        this(filename, content, "application/octet-stream");
    }
}
