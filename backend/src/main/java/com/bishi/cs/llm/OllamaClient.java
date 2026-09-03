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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OllamaClient {
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OllamaClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public float[] embed(String text) {
        return embed(text, props.getOllama().getTimeoutSeconds());
    }

    public boolean pingEmbed() {
        try {
            // 冷启动拉模型可能较慢，给足超时，避免误判为不可用
            embed("ping", 30);
            return true;
        } catch (Exception e) {
            System.err.println("[WARN] Ollama Embedding ping 详情: " + e.getMessage());
            return false;
        }
    }

    private float[] embed(String text, int timeoutSeconds) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", props.getOllama().getEmbedModel(),
                    "prompt", text
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getOllama().getBaseUrl()) + "/api/embeddings"))
                    .timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(502, "Ollama Embedding 调用失败: HTTP " + response.statusCode() + " " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode arr = root.path("embedding");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new ApiException(502, "Ollama Embedding 返回为空，请确认已拉取模型: " + props.getOllama().getEmbedModel());
            }
            float[] vector = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vector[i] = (float) arr.get(i).asDouble();
            }
            return vector;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "无法连接 Ollama Embedding 服务: " + e.getMessage());
        }
    }

    public List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embed(text));
        }
        return out;
    }

    public String complete(List<Map<String, String>> messages, int timeoutSeconds) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", props.getOllama().getChatModel(),
                    "messages", messages,
                    "stream", false
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getOllama().getBaseUrl()) + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(502, "Ollama 补全失败: HTTP " + response.statusCode() + " " + response.body());
            }
            String text = mapper.readTree(response.body()).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new ApiException(502, "Ollama 补全返回为空");
            }
            return text;
        } catch (ApiException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(504, "LLM 调用超时，请稍后重试");
        } catch (Exception e) {
            throw new ApiException(502, "无法连接 Ollama Chat 服务: " + e.getMessage());
        }
    }

    public void chatStream(List<Map<String, String>> messages, Consumer<String> onToken, Runnable onComplete) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", props.getOllama().getChatModel(),
                    "messages", messages,
                    "stream", true
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getOllama().getBaseUrl()) + "/api/chat"))
                    .timeout(Duration.ofSeconds(props.getOllama().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new ApiException(502, "Ollama Chat 调用失败: HTTP " + response.statusCode() + " " + err);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = mapper.readTree(line);
                    if (node.path("error").isTextual()) {
                        throw new ApiException(502, "Ollama 错误: " + node.path("error").asText());
                    }
                    String token = node.path("message").path("content").asText("");
                    if (!token.isEmpty()) {
                        onToken.accept(token);
                    }
                    if (node.path("done").asBoolean(false)) {
                        break;
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
            throw new ApiException(502, "无法连接 Ollama Chat 服务: " + e.getMessage());
        }
    }

    public List<Map<String, String>> systemAndUser(String system, String user) {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("role", "system", "content", system));
        list.add(Map.of("role", "user", "content", user));
        return list;
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
