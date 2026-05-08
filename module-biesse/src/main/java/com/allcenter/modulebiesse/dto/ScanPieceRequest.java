package com.allcenter.modulebiesse.dto;

import jakarta.validation.constraints.NotNull;

public record ScanPieceRequest(@NotNull Long pieceId, String observations, String equipment) {}
