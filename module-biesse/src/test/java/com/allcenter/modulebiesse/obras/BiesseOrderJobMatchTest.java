package com.allcenter.modulebiesse.obras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests de resolución job OSI → obra (sin BD). */
class BiesseOrderJobMatchTest {

    @Test
    void orderJobMatchRecord_ambiguous() {
        var match = new BiesseObrasRepository.OrderJobMatch(null, true, java.util.List.of());
        assertTrue(match.ambiguous());
        assertNull(match.order());
        assertNotNull(match.candidates());
    }

    @Test
    void orderJobMatchRecord_resolved() {
        var match =
                new BiesseObrasRepository.OrderJobMatch(
                        java.util.Map.of("orderid", 1), false, java.util.List.of());
        assertFalse(match.ambiguous());
        assertNotNull(match.order());
    }

    @Test
    void blancoBlanco_isWeakJob() throws Exception {
        Method m =
                BiesseObrasRepository.class.getDeclaredMethod(
                        "isWeakJobNameForLooseMatch", String.class, String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(null, "BLANCO BLANCO", null));
        assertFalse((Boolean) m.invoke(null, "31374 VISION PRADO CLOSET PISO 16 BLANCO 18MM", "31374"));
    }

    @Test
    void onlyExactName_matchesNbspAndAscii() throws Exception {
        Method m =
                BiesseObrasRepository.class.getDeclaredMethod(
                        "onlyExactName", List.class, String.class, String.class);
        m.setAccessible(true);
        List<Map<String, Object>> rows =
                List.of(
                        Map.of(
                                "orderid",
                                99L,
                                "ordername",
                                "S13336 DUROLAC BLANCO",
                                "bookingcode",
                                "S13336"),
                        Map.of(
                                "orderid",
                                1752L,
                                "ordername",
                                "BLANCO\u00A0BLANCO",
                                "bookingcode",
                                "BLANCO\u00A0BLANCO"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exact =
                (List<Map<String, Object>>) m.invoke(null, rows, "BLANCO BLANCO", "BLANCOBLANCO");
        assertEquals(1, exact.size());
        assertEquals(1752L, ((Number) exact.getFirst().get("orderid")).longValue());
    }

    @Test
    void compactName_stripsNbsp() throws Exception {
        Method m = BiesseObrasRepository.class.getDeclaredMethod("compactName", String.class);
        m.setAccessible(true);
        assertEquals("BLANCOBLANCO", m.invoke(null, "BLANCO\u00A0BLANCO"));
        assertEquals("BLANCOBLANCO", m.invoke(null, "BLANCO BLANCO"));
    }
}
