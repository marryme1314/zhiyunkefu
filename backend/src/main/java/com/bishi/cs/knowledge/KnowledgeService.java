package com.bishi.cs.knowledge;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.llm.LlmGateway;
import com.bishi.cs.rag.KnowledgeRouter;
import com.bishi.cs.rag.VectorIndex;
import com.bishi.cs.rag.VectorMath;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class KnowledgeService {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeIndexer indexer;
    private final DocumentParser parser;
    private final LlmGateway llm;
    private final VectorMath math;
    private final VectorIndex vectorIndex;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);

    public KnowledgeService(KnowledgeDocumentRepository documents,
                            KnowledgeChunkRepository chunks,
                            KnowledgeIndexer indexer,
                            DocumentParser parser,
                            LlmGateway llm,
                            VectorMath math,
                            VectorIndex vectorIndex) {
        this.documents = documents;
        this.chunks = chunks;
        this.indexer = indexer;
        this.parser = parser;
        this.llm = llm;
        this.math = math;
        this.vectorIndex = vectorIndex;
    }

    public List<Map<String, Object>> list() {
        return documents.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    public String activeEmbedName() {
        return llm.activeEmbedBackend();
    }

    public Map<String, Object> upload(MultipartFile file, String collection) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "请选择要上传的文件");
        }
        String name = file.getOriginalFilename() == null ? "未命名.txt" : file.getOriginalFilename();
        String type = parser.detectType(name);
        String text = parser.extract(file, type);
        KnowledgeDocument doc = createProcessing(name, type, collection);
        Long id = doc.getId();
        workers.submit(() -> {
            try {
                indexer.index(id, text);
            } catch (Exception e) {
                System.err.println("[WARN] 文档向量化失败 id=" + id + " " + e.getMessage());
            }
        });
        return toView(doc);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument doc = documents.findById(id).orElseThrow(() -> new ApiException(404, "文档不存在"));
        chunks.deleteByDocumentId(doc.getId());
        documents.delete(doc);
        vectorIndex.reload();
    }

    public void importSeedIfEmpty() {
        if (documents.count() > 0) {
            return;
        }
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:seed/*");
            for (Resource resource : resources) {
                if (!resource.isReadable() || resource.getFilename() == null) {
                    continue;
                }
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                KnowledgeDocument doc = createProcessing(resource.getFilename(), parser.detectType(resource.getFilename()), null);
                indexer.index(doc.getId(), text);
            }
        } catch (Exception e) {
            throw new ApiException(500, "初始化知识库失败: " + e.getMessage());
        }
    }

    @Transactional
    public void reembedIfDimensionMismatch() {
        try {
            List<KnowledgeChunk> all = chunks.findAllReady();
            if (all.isEmpty()) {
                return;
            }
            float[] probe = llm.embed("维度探测");
            float[] first = math.fromJson(all.get(0).getEmbeddingJson());
            if (first.length == probe.length) {
                return;
            }
            for (KnowledgeChunk chunk : all) {
                chunk.setEmbeddingJson(math.toJson(llm.embed(chunk.getContent())));
                chunks.save(chunk);
            }
        } finally {
            vectorIndex.reload();
        }
    }

    private KnowledgeDocument createProcessing(String filename, String type, String collection) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFilename(filename);
        doc.setContentType(type);
        if (collection == null || collection.isBlank() || "AUTO".equalsIgnoreCase(collection)) {
            doc.setCollection(KnowledgeRouter.infer(filename));
        } else {
            doc.setCollection(KnowledgeRouter.normalize(collection));
        }
        doc.setStatus("PROCESSING");
        doc.setCreatedAt(LocalDateTime.now());
        return documents.saveAndFlush(doc);
    }

    private Map<String, Object> toView(KnowledgeDocument doc) {
        return Map.of(
                "id", doc.getId(),
                "filename", doc.getFilename(),
                "contentType", doc.getContentType(),
                "collection", KnowledgeRouter.normalize(doc.getCollection()),
                "collectionLabel", KnowledgeRouter.label(doc.getCollection()),
                "status", doc.getStatus(),
                "errorMessage", doc.getErrorMessage() == null ? "" : doc.getErrorMessage(),
                "createdAt", doc.getCreatedAt().toString()
        );
    }
}
