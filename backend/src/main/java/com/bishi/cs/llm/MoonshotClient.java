package com.bishi.cs.llm;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.common.StreamCancelledException;
import com.bishi.cs.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class MoonshotClient {
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public MoonshotClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public List<float[]> embedAll(List<String> texts) {
        try {
            int batch = 16;
            java.util.ArrayList<float[]> all = new java.util.ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i += batch) {
                List<String> slice = texts.subList(i, Math.min(texts.size(), i + batch));
                String body = mapper.writeValueAsString(Map.of(
                        "model", props.getMoonshot().getEmbedModel(),
                        "input", slice
                ));
                HttpResponse<String> response = sendJson("/embeddings", body);
                if (response.statusCode() >= 400) {
                    throw new ApiException(502, "Moonshot Embedding 调用失败: HTTP " + response.statusCode() + " " + response.body());
                }
                JsonNode data = mapper.readTree(response.body()).path("data");
                if (!data.isArray() || data.size() != slice.size()) {
                    throw new ApiException(502, "Moonshot Embedding 返回数量与输入不一致");
                }
                float[][] ordered = new float[slice.size()][];
                int fallback = 0;
                for (JsonNode item : data) {
                    int idx = item.has("index") ? item.path("index").asInt(fallback) : fallback;
                    JsonNode arr = item.path("embedding");
                    if (idx < 0 || idx >= slice.size() || !arr.isArray()) {
                        throw new ApiException(502, "Moonshot Embedding 返回格式无效");
                    }
                    ordered[idx] = toVector(arr);
                    fallback++;
                }
                for (float[] vec : ordered) {
                    if (vec == null) {
                        throw new ApiException(502, "Moonshot Embedding 缺少向量");
                    }
                    all.add(vec);
                }
            }
            return all;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "无法连接 Moonshot Embedding: " + e.getMessage());
        }
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    private static float[] toVector(JsonNode arr) {
        float[] vector = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vector[i] = (float) arr.get(i).asDouble();
        }
        return vector;
    }

    public String complete(List<Map<String, String>> messages, int timeoutSeconds) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", props.getMoonshot().getChatModel());
            payload.put("messages", messages);
            payload.put("stream", false);
            payload.put("thinking", Map.of("type", "disabled"));
            payload.put("max_tokens", 200);
            HttpResponse<String> response = sendJson("/chat/completions", mapper.writeValueAsString(payload), timeoutSeconds);
            if (response.statusCode() >= 400) {
                throw new ApiException(502, "Moonshot 补全失败: HTTP " + response.statusCode() + " " + response.body());
            }
            String text = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new ApiException(502, "Moonshot 补全返回为空");
            }
            return text;
        } catch (ApiException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(504, "LLM 调用超时，请稍后重试");
        } catch (Exception e) {
            throw new ApiException(502, "无法连接 Moonshot Chat: " + e.getMessage());
        }
    }

    public void chatStream(List<Map<String, String>> messages, Consumer<String> onToken, Runnable onComplete) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", props.getMoonshot().getChatModel());
            payload.put("messages", messages);
            payload.put("stream", true);
            payload.put("thinking", Map.of("type", "disabled"));
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getMoonshot().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getMoonshot().getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new ApiException(502, "Moonshot Chat 调用失败: HTTP " + response.statusCode() + " " + err);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode node = mapper.readTree(data);
                    if (node.path("error").isObject() || node.path("error").isTextual()) {
                        String msg = node.path("error").isTextual()
                                ? node.path("error").asText()
                                : node.path("error").path("message").asText("未知错误");
                        throw new ApiException(502, "Moonshot 错误: " + msg);
                    }
                    JsonNode delta = node.path("choices").path(0).path("delta");
                    String token = delta.path("content").asText("");
                    if (!token.isEmpty()) {
                        onToken.accept(token);
                    }
                }
            }
            onComplete.run();
        } catch (ApiException e) {
            throw e;
        } catch (StreamCancelledException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(504, "LLM 调用超时，请稍后重试");
        } catch (Exception e) {
            throw new ApiException(502, "无法连接 Moonshot Chat: " + e.getMessage());
        }
    }

    private HttpResponse<String> sendJson(String path, String body) throws Exception {
        return sendJson(path, body, props.getMoonshot().getTimeoutSeconds());
    }

    private HttpResponse<String> sendJson(String path, String body, int timeoutSeconds) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + path))
                .timeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getMoonshot().getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        String url = props.getMoonshot().getBaseUrl();
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
