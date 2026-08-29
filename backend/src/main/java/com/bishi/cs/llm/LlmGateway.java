package com.bishi.cs.llm;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class LlmGateway {
    private final AppProperties props;
    private final OllamaClient ollama;
    private final MoonshotClient moonshot;
    private final LocalEmbedding local;

    /** Process-lifetime backend so stored vectors stay the same dimension. */
    private volatile String embedBackend;

    public LlmGateway(AppProperties props, OllamaClient ollama, MoonshotClient moonshot, LocalEmbedding local) {
        this.props = props;
        this.ollama = ollama;
        this.moonshot = moonshot;
        this.local = local;
    }

    public boolean useMoonshot() {
        String provider = props.getLlm().getProvider();
        String key = props.getMoonshot().getApiKey();
        return "moonshot".equalsIgnoreCase(provider) && key != null && !key.isBlank();
    }

    public int timeoutSeconds() {
        return useMoonshot() ? props.getMoonshot().getTimeoutSeconds() : props.getOllama().getTimeoutSeconds();
    }

    /**
     * Prefer Ollama nomic-embed-text (semantic). Moonshot Embedding is usually 403.
     * Fall back to local hashed vectors if Ollama is down. Choice is cached for this process.
     */
    public synchronized String resolveEmbedBackend() {
        if (embedBackend != null) {
            return embedBackend;
        }
        String configured = props.getLlm().getEmbedProvider();
        String mode = configured == null || configured.isBlank() ? "auto" : configured.trim().toLowerCase();

        if ("local".equals(mode)) {
            embedBackend = "local";
            return embedBackend;
        }
        if ("moonshot".equals(mode) && hasMoonshotKey()) {
            try {
                moonshot.embed("ping");
                embedBackend = "moonshot";
                return embedBackend;
            } catch (Exception e) {
                System.err.println("[WARN] Moonshot Embedding 不可用，改试 Ollama: " + e.getMessage());
            }
        }
        if ("ollama".equals(mode) || "auto".equals(mode) || "moonshot".equals(mode)) {
            try {
                if (!ollama.pingEmbed()) {
                    throw new ApiException(502, "Ollama Embedding ping 失败");
                }
                embedBackend = "ollama";
                return embedBackend;
            } catch (Exception e) {
                System.err.println("[WARN] Ollama Embedding 不可用（" + e.getMessage() + "），改用本机词法向量");
            }
        }
        embedBackend = "local";
        return embedBackend;
    }

    public String activeEmbedBackend() {
        return resolveEmbedBackend();
    }

    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String backend = resolveEmbedBackend();
        if ("moonshot".equals(backend)) {
            return moonshot.embedAll(texts);
        }
        if ("ollama".equals(backend)) {
            return ollama.embedAll(texts);
        }
        return local.embedAll(texts);
    }

    public float[] embed(String text) {
        List<float[]> all = embedAll(List.of(text == null ? "" : text));
        if (all.isEmpty()) {
            throw new ApiException(502, "向量化失败");
        }
        return all.get(0);
    }

    public String complete(String system, String user, int timeoutSeconds) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        );
        if (useMoonshot()) {
            return moonshot.complete(messages, timeoutSeconds);
        }
        if ("moonshot".equalsIgnoreCase(props.getLlm().getProvider())) {
            throw new ApiException(500, "已配置使用 Moonshot，但未设置 MOONSHOT_API_KEY");
        }
        try {
            return ollama.complete(messages, timeoutSeconds);
        } catch (ApiException e) {
            if (hasMoonshotKey() && isOllamaRuntimeFailure(e)) {
                return moonshot.complete(messages, timeoutSeconds);
            }
            throw e;
        }
    }

    public void chatStream(List<Map<String, String>> messages, Consumer<String> onToken, Runnable onComplete) {
        if (useMoonshot()) {
            moonshot.chatStream(messages, onToken, onComplete);
            return;
        }
        if ("moonshot".equalsIgnoreCase(props.getLlm().getProvider())) {
            throw new ApiException(500, "已配置使用 Moonshot，但未设置 MOONSHOT_API_KEY");
        }
        try {
            ollama.chatStream(messages, onToken, onComplete);
        } catch (ApiException e) {
            if (hasMoonshotKey() && isOllamaRuntimeFailure(e)) {
                System.err.println("[WARN] Ollama 不可用，改用 Moonshot Chat: " + e.getMessage());
                moonshot.chatStream(messages, onToken, onComplete);
                return;
            }
            throw e;
        }
    }

    private boolean hasMoonshotKey() {
        String key = props.getMoonshot().getApiKey();
        return key != null && !key.isBlank();
    }

    private static boolean isOllamaRuntimeFailure(ApiException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("CUDA")
                || msg.contains("llama-server")
                || msg.contains("HTTP 500")
                || msg.contains("无法连接 Ollama");
    }
}
