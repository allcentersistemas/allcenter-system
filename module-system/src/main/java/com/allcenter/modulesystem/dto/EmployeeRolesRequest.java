package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record EmployeeRolesRequest(@NotEmpty List<Long> roleIds) {}
