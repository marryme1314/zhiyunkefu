package com.bishi.cs.llm;

import com.bishi.cs.rag.VectorMath;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalEmbeddingTest {
    private final LocalEmbedding embedding = new LocalEmbedding();
    private final VectorMath math = new VectorMath(new ObjectMapper());

    @Test
    void dimensionIsFixed() {
        assertEquals(LocalEmbedding.DIM, embedding.embed("退款政策").length);
    }

    @Test
    void similarChineseScoresHigherThanUnrelated() {
        float[] refund = embedding.embed("7天无理由退款怎么办理");
        float[] policy = embedding.embed("订阅后7天内可申请无理由退款，需提供订单号");
        float[] weather = embedding.embed("今天上海会不会下雨");
        assertTrue(math.cosine(refund, policy) > math.cosine(refund, weather));
    }
}
