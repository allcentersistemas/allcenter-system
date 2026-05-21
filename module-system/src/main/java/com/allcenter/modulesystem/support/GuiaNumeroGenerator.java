package com.allcenter.modulesystem.support;

/** Número correlativo de guía: G-000001, G-000002, … */
public final class GuiaNumeroGenerator {

    private GuiaNumeroGenerator() {}

    public static String format(long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence debe ser >= 1");
        }
        return String.format("G-%06d", sequence);
    }

    public static long nextSequence(long currentMax) {
        return currentMax + 1;
    }
}
