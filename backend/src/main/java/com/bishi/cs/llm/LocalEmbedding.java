package com.bishi.cs.llm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本机词法向量，不依赖月之暗面 Embedding（该接口对多数 Key 未开放）也不依赖 Ollama。
 * 客服 FAQ 文档短、中文关键词重叠高，足够支撑题目要求的检索。
 */
@Component
public class LocalEmbedding {
    public static final int DIM = 384;

    public List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embed(text));
        }
        return out;
    }

    public float[] embed(String text) {
        float[] vector = new float[DIM];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String s = text.toLowerCase();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            add(vector, hash(c));
            if (i + 1 < s.length()) {
                add(vector, hash(c * 31 + s.charAt(i + 1)));
            }
            if (i + 2 < s.length()) {
                add(vector, hash(c * 31 * 31 + s.charAt(i + 1) * 31 + s.charAt(i + 2)));
            }
        }
        normalize(vector);
        return vector;
    }

    private static int hash(int value) {
        int h = value * 16777619;
        h ^= (h >>> 16);
        return Math.floorMod(h, DIM);
    }

    private static void add(float[] vector, int index) {
        vector[index] += 1f;
    }

    private static void normalize(float[] vector) {
        double n = 0;
        for (float v : vector) {
            n += v * v;
        }
        if (n == 0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(n));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
