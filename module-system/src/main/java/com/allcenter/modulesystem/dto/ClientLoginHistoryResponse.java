package com.allcenter.modulesystem.dto;

import java.util.List;

public record ClientLoginHistoryResponse(
        List<ClientLoginEventResponse> items, int page, int size, long totalElements) {}
