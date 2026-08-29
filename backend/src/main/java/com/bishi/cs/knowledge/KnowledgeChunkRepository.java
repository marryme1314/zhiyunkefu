package com.bishi.cs.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    void deleteByDocumentId(Long documentId);

    @Query("select c from KnowledgeChunk c join fetch c.document d where d.status = 'READY'")
    List<KnowledgeChunk> findAllReady();
}
