package com.bishi.cs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Jwt jwt = new Jwt();
    private final Llm llm = new Llm();
    private final Ollama ollama = new Ollama();
    private final Moonshot moonshot = new Moonshot();
    private final Rag rag = new Rag();

    public Jwt getJwt() {
        return jwt;
    }

    public Llm getLlm() {
        return llm;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public Moonshot getMoonshot() {
        return moonshot;
    }

    public Rag getRag() {
        return rag;
    }

    public static class Llm {
        private String provider = "ollama";
        private String embedProvider = "auto";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEmbedProvider() {
            return embedProvider;
        }

        public void setEmbedProvider(String embedProvider) {
            this.embedProvider = embedProvider;
        }
    }

    public static class Jwt {
        private String secret;
        private long expireHours = 72;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpireHours() {
            return expireHours;
        }

        public void setExpireHours(long expireHours) {
            this.expireHours = expireHours;
        }
    }

    public static class Ollama {
        private String baseUrl;
        private String chatModel;
        private String embedModel;
        private int timeoutSeconds = 120;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbedModel() {
            return embedModel;
        }

        public void setEmbedModel(String embedModel) {
            this.embedModel = embedModel;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Moonshot {
        private String apiKey = "";
        private String baseUrl = "https://api.moonshot.cn/v1";
        private String chatModel = "kimi-k2.6";
        private String embedModel = "moonshot-v3-embedding";
        private int timeoutSeconds = 120;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbedModel() {
            return embedModel;
        }

        public void setEmbedModel(String embedModel) {
            this.embedModel = embedModel;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Rag {
        private int chunkSize = 400;
        private int chunkOverlap = 80;
        private int topK = 4;
        private double similarityThreshold = 0.32;
        private int historyRounds = 3;
        private int maxQuestionLength = 500;
        private int dailyQuestionLimit = 100;
        private String emptyRetrievalReply;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getHistoryRounds() {
            return historyRounds;
        }

        public void setHistoryRounds(int historyRounds) {
            this.historyRounds = historyRounds;
        }

        public int getMaxQuestionLength() {
            return maxQuestionLength;
        }

        public void setMaxQuestionLength(int maxQuestionLength) {
            this.maxQuestionLength = maxQuestionLength;
        }

        public int getDailyQuestionLimit() {
            return dailyQuestionLimit;
        }

        public void setDailyQuestionLimit(int dailyQuestionLimit) {
            this.dailyQuestionLimit = dailyQuestionLimit;
        }

        public String getEmptyRetrievalReply() {
            return emptyRetrievalReply;
        }

        public void setEmptyRetrievalReply(String emptyRetrievalReply) {
            this.emptyRetrievalReply = emptyRetrievalReply;
        }
    }
}
