package com.allcenter.moduleemployee.model.dto;

public record EmployeeSelfPatchRequest(
        String firstName,
        String secondLastName,
        String lastName,
        String phone,
        String mobilePhone,
        String personalEmail,
        String addressLine1,
        String addressLine2,
        String city,
        String provinceOrState,
        String postalCode,
        String country,
        String emergencyContactName,
        String emergencyContactRelation,
        String emergencyContactPhone,
        String notes) {}
