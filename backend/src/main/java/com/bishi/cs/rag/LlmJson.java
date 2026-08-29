package com.bishi.cs.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class LlmJson {
    private LlmJson() {
    }

    static String extract(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.lastIndexOf("```");
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        int obj = text.indexOf('{');
        int arr = text.indexOf('[');
        int start = -1;
        if (obj >= 0 && (arr < 0 || obj < arr)) {
            start = obj;
        } else if (arr >= 0) {
            start = arr;
        }
        if (start < 0) {
            return text;
        }
        return text.substring(start).trim();
    }

    static JsonNode tree(ObjectMapper mapper, String raw) throws Exception {
        return mapper.readTree(extract(raw));
    }
}
