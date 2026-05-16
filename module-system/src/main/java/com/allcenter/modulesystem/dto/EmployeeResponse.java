package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.ContractType;
import com.allcenter.modulesystem.model.DirectorySource;
import com.allcenter.modulesystem.model.DocumentType;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Gender;
import com.allcenter.modulesystem.model.MaritalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String email,
        DirectorySource directorySource,
        String externalDirectoryId,
        String securityIdentifier,
        String samAccountName,
        String userPrincipalName,
        String distinguishedName,
        LocalDateTime lastDirectorySyncAt,
        String firstName,
        String secondLastName,
        String lastName,
        LocalDate birthDate,
        Gender gender,
        MaritalStatus maritalStatus,
        DocumentType documentType,
        String documentNumber,
        String nationality,
        String phone,
        String mobilePhone,
        String personalEmail,
        String addressLine1,
        String addressLine2,
        String city,
        String provinceOrState,
        String postalCode,
        String country,
        String jobTitle,
        String department,
        LocalDate hireDate,
        LocalDate terminationDate,
        LocalDate probationEndDate,
        ContractType contractType,
        String workLocation,
        String workScheduleDescription,
        Integer workHoursPerWeek,
        String taxId,
        String socialSecurityNumber,
        BigDecimal baseSalaryMonthly,
        String salaryCurrency,
        String emergencyContactName,
        String emergencyContactRelation,
        String emergencyContactPhone,
        Long managerId,
        Long branchId,
        String notes,
        List<RoleResponse> roles,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static EmployeeResponse from(Employee e) {
        List<RoleResponse> roleDtos =
                e.getRoles() == null
                        ? List.of()
                        : e.getRoles().stream()
                                .map(RoleResponse::from)
                                .sorted(
                                        Comparator.comparing(
                                                RoleResponse::name, String.CASE_INSENSITIVE_ORDER))
                                .toList();
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getEmail(),
                e.getDirectorySource(),
                e.getExternalDirectoryId(),
                e.getSecurityIdentifier(),
                e.getSamAccountName(),
                e.getUserPrincipalName(),
                e.getDistinguishedName(),
                e.getLastDirectorySyncAt(),
                e.getFirstName(),
                e.getSecondLastName(),
                e.getLastName(),
                e.getBirthDate(),
                e.getGender(),
                e.getMaritalStatus(),
                e.getDocumentType(),
                e.getDocumentNumber(),
                e.getNationality(),
                e.getPhone(),
                e.getMobilePhone(),
                e.getPersonalEmail(),
                e.getAddressLine1(),
                e.getAddressLine2(),
                e.getCity(),
                e.getProvinceOrState(),
                e.getPostalCode(),
                e.getCountry(),
                e.getJobTitle(),
                e.getDepartment(),
                e.getHireDate(),
                e.getTerminationDate(),
                e.getProbationEndDate(),
                e.getContractType(),
                e.getWorkLocation(),
                e.getWorkScheduleDescription(),
                e.getWorkHoursPerWeek(),
                e.getTaxId(),
                e.getSocialSecurityNumber(),
                e.getBaseSalaryMonthly(),
                e.getSalaryCurrency(),
                e.getEmergencyContactName(),
                e.getEmergencyContactRelation(),
                e.getEmergencyContactPhone(),
                e.getManagerId(),
                e.getBranchId(),
                e.getNotes(),
                roleDtos,
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
