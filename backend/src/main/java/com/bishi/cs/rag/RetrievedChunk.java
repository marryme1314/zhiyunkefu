package com.bishi.cs.rag;

public record RetrievedChunk(
        Long documentId,
        String documentName,
        String content,
        double score
) {
    public String summary() {
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
    }
}
