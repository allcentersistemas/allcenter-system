package com.allcenter.modulebiesse.obras;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var match = new BiesseObrasRepository.OrderJobMatch(java.util.Map.of("orderid", 1), false, java.util.List.of());
        assertFalse(match.ambiguous());
        assertNotNull(match.order());
    }
}
