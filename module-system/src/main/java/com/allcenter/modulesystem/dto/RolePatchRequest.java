package com.allcenter.modulesystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RolePatchRequest(
        @Size(max = 64) String name,
        @Size(max = 500) String description,
        @Valid List<PermissionRuleDto> permissions) {}
