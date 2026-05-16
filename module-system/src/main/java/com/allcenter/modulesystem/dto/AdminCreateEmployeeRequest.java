package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.DocumentType;
import com.allcenter.modulesystem.model.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AdminCreateEmployeeRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 120) String firstName,
        @Size(max = 120) String secondLastName,
        @NotBlank @Size(max = 120) String lastName,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 64) String documentNumber,
        @Size(max = 40) String mobilePhone,
        Long branchId,
        LocalDate birthDate,
        Gender gender,
        List<Long> roleIds) {}
