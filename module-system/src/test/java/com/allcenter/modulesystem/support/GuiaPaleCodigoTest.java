package com.allcenter.modulesystem.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GuiaPaleCodigoTest {

    @Test
    void buildFirstLineUsesGuiaNumberOnly() {
        assertEquals("G-NG-2024-001", GuiaPaleCodigo.build("NG-2024-001", 1));
    }

    @Test
    void buildSecondLineAppendsSuffix() {
        assertEquals("G-NG-2024-001-2", GuiaPaleCodigo.build("NG-2024-001", 2));
    }

    @Test
    void normalizeTrimsAndUppercases() {
        assertEquals("NG-2024-001", GuiaPaleCodigo.normalizeNumeroGuia("  ng-2024-001 "));
    }

    @Test
    void buildRejectsBlankNumero() {
        assertThrows(IllegalArgumentException.class, () -> GuiaPaleCodigo.build("   ", 1));
    }
}
