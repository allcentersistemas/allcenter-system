package com.allcenter.modulesystem.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class PhotoFilenameCodec {

    private final ObjectMapper objectMapper;

    public PhotoFilenameCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    public String writeList(List<String> names) {
        return objectMapper.writeValueAsString(names == null ? List.of() : names);
    }

    public List<String> mutableCopy(List<String> names) {
        return new ArrayList<>(names == null ? List.of() : names);
    }
}
