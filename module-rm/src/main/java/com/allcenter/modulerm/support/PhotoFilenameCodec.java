package com.allcenter.modulerm.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

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

    public String writeList(List<String> names) throws JsonProcessingException {
        return objectMapper.writeValueAsString(names == null ? List.of() : names);
    }

    public List<String> mutableCopy(List<String> names) {
        return new ArrayList<>(names == null ? List.of() : names);
    }
}
