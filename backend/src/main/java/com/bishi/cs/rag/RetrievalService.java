package com.bishi.cs.rag;

import com.bishi.cs.config.AppProperties;
import com.bishi.cs.llm.LlmGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RetrievalService {
    private final VectorIndex vectorIndex;
    private final LlmGateway llm;
    private final VectorMath math;
    private final AppProperties props;

    public RetrievalService(VectorIndex vectorIndex, LlmGateway llm, VectorMath math, AppProperties props) {
        this.vectorIndex = vectorIndex;
        this.llm = llm;
        this.math = math;
        this.props = props;
    }

    public List<RetrievedChunk> retrieve(String question) {
        return retrieve(question, null);
    }

    public List<RetrievedChunk> retrieve(String question, IntentRecognizer.Intent intent) {
        List<RetrievedChunk> routed = search(question, KnowledgeRouter.preferred(intent));
        if (!routed.isEmpty()) {
            return routed;
        }
        return search(question, Set.of());
    }

    private List<RetrievedChunk> search(String question, Set<String> collections) {
        String q = question == null ? "" : question.trim();
        float[] query = llm.embed(q);
        double threshold = similarityThreshold();
        Map<String, RetrievedChunk> byKey = new LinkedHashMap<>();
        for (VectorIndex.Entry chunk : vectorIndex.all()) {
            if (!collections.isEmpty() && !collections.contains(chunk.collection())) {
                continue;
            }
            if (query.length != chunk.vector().length) {
                continue;
            }
            double score = math.cosine(query, chunk.vector());
            if (score >= threshold) {
                putBest(byKey, chunk, score);
            }
        }
        if (byKey.isEmpty() && !q.isEmpty()) {
            String needle = q.toLowerCase(Locale.ROOT);
            for (VectorIndex.Entry chunk : vectorIndex.all()) {
                if (!collections.isEmpty() && !collections.contains(chunk.collection())) {
                    continue;
                }
                String content = chunk.content() == null ? "" : chunk.content().toLowerCase(Locale.ROOT);
                if (content.contains(needle)) {
                    putBest(byKey, chunk, 0.45);
                }
            }
        }
        List<RetrievedChunk> scored = new ArrayList<>(byKey.values());
        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        int k = props.getRag().getTopK();
        if (scored.size() > k) {
            return new ArrayList<>(scored.subList(0, k));
        }
        return scored;
    }

    private double similarityThreshold() {
        double configured = props.getRag().getSimilarityThreshold();
        if ("local".equals(llm.activeEmbedBackend())) {
            return configured;
        }
        return Math.max(configured, 0.28);
    }

    private static void putBest(Map<String, RetrievedChunk> byKey, VectorIndex.Entry chunk, double score) {
        String key = chunk.documentId() + "#" + chunk.content().hashCode();
        RetrievedChunk current = byKey.get(key);
        if (current == null || score > current.score()) {
            byKey.put(key, new RetrievedChunk(
                    chunk.documentId(),
                    chunk.documentName(),
                    chunk.content(),
                    score
            ));
        }
    }
}
