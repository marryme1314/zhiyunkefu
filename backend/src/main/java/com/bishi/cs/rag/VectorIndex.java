package com.bishi.cs.rag;

import com.bishi.cs.knowledge.KnowledgeChunk;
import com.bishi.cs.knowledge.KnowledgeChunkRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VectorIndex {
    public record Entry(Long documentId, String documentName, String collection, String content, float[] vector) {
    }

    private final KnowledgeChunkRepository chunks;
    private final VectorMath math;
    private volatile List<Entry> snapshot = List.of();

    public VectorIndex(KnowledgeChunkRepository chunks, VectorMath math) {
        this.chunks = chunks;
        this.math = math;
    }

    public synchronized void reload() {
        List<Entry> next = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks.findAllReady()) {
            next.add(new Entry(
                    chunk.getDocument().getId(),
                    chunk.getDocument().getFilename(),
                    KnowledgeRouter.normalize(chunk.getDocument().getCollection()),
                    chunk.getContent(),
                    math.fromJson(chunk.getEmbeddingJson())
            ));
        }
        snapshot = List.copyOf(next);
    }

    public List<Entry> all() {
        return snapshot;
    }
}
