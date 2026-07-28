package com.allcenter.modulesystem.event;

/** Cliente portal envió un proyecto nuevo a cotizar (estado ENVIADO). */
public record ProyectoQuoteSubmittedEvent(Long proyectoId, String nombre, String cliente, Long clientUserId) {}
