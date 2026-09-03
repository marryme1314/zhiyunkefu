package com.bishi.cs.llm;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleEmbedClient {
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleEmbedClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean configured() {
        String key = props.getEmbed().getApiKey();
        return key != null && !key.isBlank();
    }

    public boolean ping() {
        try {
            embedAll(List.of("ping"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<float[]> embedAll(List<String> texts) {
        try {
            int batch = 16;
            List<float[]> all = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i += batch) {
                List<String> slice = texts.subList(i, Math.min(texts.size(), i + batch));
                String body = mapper.writeValueAsString(Map.of(
                        "model", props.getEmbed().getModel(),
                        "input", slice
                ));
                HttpResponse<String> response = send(body);
                if (response.statusCode() >= 400) {
                    throw new ApiException(502, "Embedding API 调用失败: HTTP " + response.statusCode());
                }
                JsonNode data = mapper.readTree(response.body()).path("data");
                if (!data.isArray() || data.size() != slice.size()) {
                    throw new ApiException(502, "Embedding API 返回数量与输入不一致");
                }
                float[][] ordered = new float[slice.size()][];
                int fallback = 0;
                for (JsonNode item : data) {
                    int idx = item.has("index") ? item.path("index").asInt(fallback) : fallback;
                    JsonNode arr = item.path("embedding");
                    if (idx < 0 || idx >= slice.size() || !arr.isArray()) {
                        throw new ApiException(502, "Embedding API 返回格式无效");
                    }
                    ordered[idx] = toVector(arr);
                    fallback++;
                }
                for (float[] vec : ordered) {
                    if (vec == null) {
                        throw new ApiException(502, "Embedding API 缺少向量");
                    }
                    all.add(vec);
                }
            }
            return all;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "无法连接 Embedding API: " + e.getMessage());
        }
    }

    private HttpResponse<String> send(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(props.getEmbed().getBaseUrl()) + "/embeddings"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getEmbed().getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static float[] toVector(JsonNode arr) {
        float[] vector = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vector[i] = (float) arr.get(i).asDouble();
        }
        return vector;
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
