package com.bishi.cs.rag;

import com.bishi.cs.config.AppProperties;
import com.bishi.cs.knowledge.KnowledgeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class QdrantClient {
    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private volatile Integer vectorSize;

    public QdrantClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public boolean enabled() {
        return props.getQdrant().enabled();
    }

    public boolean healthy() {
        if (!enabled()) {
            return false;
        }
        try {
            HttpResponse<String> response = send("GET", "/readyz", null);
            return response.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    public void upsert(long chunkId, long documentId, String documentName, String collection, String content, float[] vector) {
        if (!enabled()) {
            return;
        }
        ensureCollection(vector.length);
        Map<String, Object> point = point(chunkId, documentId, documentName, collection, content, vector);
        putPoints(List.of(point));
    }

    public void deleteDocument(long documentId) {
        if (!enabled()) {
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "filter", Map.of(
                            "must", List.of(Map.of(
                                    "key", "documentId",
                                    "match", Map.of("value", documentId)
                            ))
                    )
            );
            send("POST", "/collections/" + collection() + "/points/delete", mapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Qdrant 删除文档失败 documentId={}: {}", documentId, e.getMessage());
        }
    }

    public void rebuild(List<KnowledgeChunk> chunks) {
        if (!enabled() || chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            float[] first = parseVector(chunks.get(0).getEmbeddingJson());
            recreateCollection(first.length);
            List<Map<String, Object>> batch = new ArrayList<>();
            for (KnowledgeChunk chunk : chunks) {
                if (chunk.getId() == null || chunk.getDocument() == null) {
                    continue;
                }
                float[] vector = parseVector(chunk.getEmbeddingJson());
                batch.add(point(
                        chunk.getId(),
                        chunk.getDocument().getId(),
                        chunk.getDocument().getFilename(),
                        KnowledgeRouter.normalize(chunk.getDocument().getCollection()),
                        chunk.getContent(),
                        vector
                ));
                if (batch.size() >= 64) {
                    putPoints(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                putPoints(batch);
            }
            log.info("Qdrant 已重建 {} 条向量", chunks.size());
        } catch (Exception e) {
            log.warn("Qdrant 重建失败，检索将回退内存索引: {}", e.getMessage());
        }
    }

    public List<RetrievedChunk> search(float[] query, Set<String> collections, int topK, double threshold) {
        if (!enabled() || query == null || query.length == 0) {
            return List.of();
        }
        try {
            ensureCollection(query.length);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", toList(query));
            body.put("limit", Math.max(1, topK));
            body.put("score_threshold", threshold);
            body.put("with_payload", true);
            if (collections != null && !collections.isEmpty()) {
                body.put("filter", Map.of(
                        "must", List.of(Map.of(
                                "key", "collection",
                                "match", Map.of("any", collections)
                        ))
                ));
            }
            HttpResponse<String> response = send(
                    "POST",
                    "/collections/" + collection() + "/points/search",
                    mapper.writeValueAsString(body)
            );
            if (response.statusCode() >= 400) {
                log.warn("Qdrant 检索失败 HTTP {}", response.statusCode());
                return List.of();
            }
            JsonNode result = mapper.readTree(response.body()).path("result");
            List<RetrievedChunk> hits = new ArrayList<>();
            if (!result.isArray()) {
                return hits;
            }
            for (JsonNode item : result) {
                JsonNode payload = item.path("payload");
                hits.add(new RetrievedChunk(
                        payload.path("documentId").asLong(),
                        payload.path("documentName").asText(""),
                        payload.path("content").asText(""),
                        item.path("score").asDouble()
                ));
            }
            return hits;
        } catch (Exception e) {
            log.warn("Qdrant 检索异常，回退内存索引: {}", e.getMessage());
            return List.of();
        }
    }

    private void ensureCollection(int dim) {
        Integer current = vectorSize;
        if (current != null && current == dim) {
            return;
        }
        synchronized (this) {
            if (vectorSize != null && vectorSize == dim) {
                return;
            }
            try {
                HttpResponse<String> get = send("GET", "/collections/" + collection(), null);
                if (get.statusCode() == 200) {
                    int size = mapper.readTree(get.body())
                            .path("result").path("config").path("params").path("vectors").path("size").asInt(0);
                    if (size == dim) {
                        vectorSize = dim;
                        return;
                    }
                    send("DELETE", "/collections/" + collection(), null);
                }
                createCollection(dim);
                vectorSize = dim;
            } catch (Exception e) {
                log.warn("Qdrant 集合初始化失败: {}", e.getMessage());
            }
        }
    }

    private void recreateCollection(int dim) throws Exception {
        send("DELETE", "/collections/" + collection(), null);
        createCollection(dim);
        vectorSize = dim;
    }

    private void createCollection(int dim) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "vectors", Map.of(
                        "size", dim,
                        "distance", "Cosine"
                )
        ));
        HttpResponse<String> created = send("PUT", "/collections/" + collection(), body);
        if (created.statusCode() >= 400) {
            throw new IllegalStateException("创建集合失败 HTTP " + created.statusCode());
        }
    }

    private void putPoints(List<Map<String, Object>> points) {
        try {
            String body = mapper.writeValueAsString(Map.of("points", points));
            HttpResponse<String> response = send("PUT", "/collections/" + collection() + "/points?wait=true", body);
            if (response.statusCode() >= 400) {
                log.warn("Qdrant upsert 失败 HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Qdrant upsert 异常: {}", e.getMessage());
        }
    }

    private Map<String, Object> point(long chunkId, long documentId, String documentName,
                                      String collection, String content, float[] vector) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", documentId);
        payload.put("documentName", documentName == null ? "" : documentName);
        payload.put("collection", collection == null ? "GENERAL" : collection);
        payload.put("content", content == null ? "" : content);
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", chunkId);
        point.put("vector", toList(vector));
        point.put("payload", payload);
        return point;
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        String url = trimSlash(props.getQdrant().getUrl()) + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(2, props.getQdrant().getTimeoutSeconds())))
                .header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(method, publisher);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String collection() {
        String name = props.getQdrant().getCollection();
        return name == null || name.isBlank() ? "knowledge_chunks" : name;
    }

    private float[] parseVector(String json) throws Exception {
        JsonNode arr = mapper.readTree(json);
        float[] vector = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vector[i] = (float) arr.get(i).asDouble();
        }
        return vector;
    }

    private static List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
