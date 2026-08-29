package com.bishi.cs.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRouterTest {
    @Test
    void infersCollectionFromFilename() {
        assertEquals(KnowledgeRouter.PRODUCT, KnowledgeRouter.infer("公司产品介绍.txt"));
        assertEquals(KnowledgeRouter.AFTER_SALES, KnowledgeRouter.infer("退换货政策.txt"));
        assertEquals(KnowledgeRouter.FAQ, KnowledgeRouter.infer("常见问题FAQ.md"));
    }

    @Test
    void productIntentPrefersProductAndFaq() {
        var set = KnowledgeRouter.preferred(IntentRecognizer.Intent.PRODUCT_INQUIRY);
        assertTrue(set.contains(KnowledgeRouter.PRODUCT));
        assertTrue(set.contains(KnowledgeRouter.FAQ));
    }
}
