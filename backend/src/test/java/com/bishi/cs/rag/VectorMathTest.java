package com.bishi.cs.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorMathTest {
    private final VectorMath math = new VectorMath(new ObjectMapper());

    @Test
    void identicalVectorsHaveCosineOne() {
        float[] v = {0.3f, 0.4f, 0};
        assertEquals(1.0, math.cosine(v, v), 1e-5);
    }

    @Test
    void mismatchedLengthIsZero() {
        assertEquals(0, math.cosine(new float[]{1}, new float[]{1, 0}));
    }

    @Test
    void chunkKeepsOverlap() {
        List<String> parts = math.chunk("abcdefghij", 4, 2);
        assertTrue(parts.size() >= 3);
        assertEquals("abcd", parts.get(0));
    }
}
