package com.bishi.cs.knowledge;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.config.AppProperties;
import com.bishi.cs.llm.LlmGateway;
import com.bishi.cs.rag.KnowledgeRouter;
import com.bishi.cs.rag.QdrantClient;
import com.bishi.cs.rag.VectorIndex;
import com.bishi.cs.rag.VectorMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KnowledgeIndexer {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final LlmGateway llm;
    private final VectorMath math;
    private final VectorIndex vectorIndex;
    private final QdrantClient qdrant;
    private final AppProperties props;

    public KnowledgeIndexer(KnowledgeDocumentRepository documents,
                            KnowledgeChunkRepository chunks,
                            LlmGateway llm,
                            VectorMath math,
                            VectorIndex vectorIndex,
                            QdrantClient qdrant,
                            AppProperties props) {
        this.documents = documents;
        this.chunks = chunks;
        this.llm = llm;
        this.math = math;
        this.vectorIndex = vectorIndex;
        this.qdrant = qdrant;
        this.props = props;
    }

    @Transactional
    public void index(Long documentId, String text) {
        KnowledgeDocument doc = documents.findById(documentId)
                .orElseThrow(() -> new ApiException(404, "文档不存在"));
        try {
            List<String> pieces = math.chunk(text, props.getRag().getChunkSize(), props.getRag().getChunkOverlap());
            if (pieces.isEmpty()) {
                fail(doc, "文档内容为空");
                return;
            }
            chunks.deleteByDocumentId(doc.getId());
            qdrant.deleteDocument(doc.getId());
            List<float[]> vectors = llm.embedAll(pieces);
            for (int i = 0; i < pieces.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocument(doc);
                chunk.setChunkIndex(i);
                chunk.setContent(pieces.get(i));
                chunk.setEmbeddingJson(math.toJson(vectors.get(i)));
                chunk.setCreatedAt(LocalDateTime.now());
                KnowledgeChunk saved = chunks.save(chunk);
                qdrant.upsert(
                        saved.getId(),
                        doc.getId(),
                        doc.getFilename(),
                        KnowledgeRouter.normalize(doc.getCollection()),
                        saved.getContent(),
                        vectors.get(i)
                );
            }
            doc.setStatus("READY");
            doc.setErrorMessage(null);
            documents.save(doc);
            vectorIndex.reload();
        } catch (Exception e) {
            fail(doc, trimError(e.getMessage()));
        }
    }

    private void fail(KnowledgeDocument doc, String message) {
        chunks.deleteByDocumentId(doc.getId());
        qdrant.deleteDocument(doc.getId());
        doc.setStatus("FAILED");
        doc.setErrorMessage(message);
        documents.save(doc);
    }

    private static String trimError(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() > 480 ? msg.substring(0, 480) : msg;
    }
}
