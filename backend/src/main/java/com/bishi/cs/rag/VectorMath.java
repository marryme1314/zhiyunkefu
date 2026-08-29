package com.bishi.cs.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VectorMath {
    private final ObjectMapper mapper;

    public VectorMath(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(float[] vector) {
        try {
            return mapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public float[] fromJson(String json) {
        try {
            return mapper.readValue(json, float[].class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /**
     * 按字符切分，保留 overlap，适合中文客服文档。
     */
    public List<String> chunk(String text, int size, int overlap) {
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < normalized.length()) {
            int end = Math.min(normalized.length(), i + size);
            String piece = normalized.substring(i, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            i = Math.max(i + size - overlap, i + 1);
        }
        return chunks;
    }
}
