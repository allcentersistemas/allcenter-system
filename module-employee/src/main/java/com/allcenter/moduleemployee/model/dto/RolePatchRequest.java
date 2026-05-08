package com.allcenter.moduleemployee.model.dto;

import jakarta.validation.constraints.Size;

public record RolePatchRequest(
        @Size(max = 64) String name,
        @Size(max = 500) String description) {}
