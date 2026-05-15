package com.allcenter.modulerm.support;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public final class RmMultipartUtil {

    private RmMultipartUtil() {}

    public static List<MultipartFile> normalizePhotos(List<MultipartFile> photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream().filter(p -> p != null && !p.isEmpty()).toList();
    }
}
