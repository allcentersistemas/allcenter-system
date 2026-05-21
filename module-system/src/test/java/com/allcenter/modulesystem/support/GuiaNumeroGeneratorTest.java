package com.allcenter.modulesystem.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GuiaNumeroGeneratorTest {

    @Test
    void formatPadsSequence() {
        assertEquals("G-000001", GuiaNumeroGenerator.format(1));
        assertEquals("G-000042", GuiaNumeroGenerator.format(42));
    }

    @Test
    void nextSequenceIncrementsMax() {
        assertEquals(1, GuiaNumeroGenerator.nextSequence(0));
        assertEquals(43, GuiaNumeroGenerator.nextSequence(42));
    }

    @Test
    void formatRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> GuiaNumeroGenerator.format(0));
    }
}
