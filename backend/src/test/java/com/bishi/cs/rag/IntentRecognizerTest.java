package com.bishi.cs.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentRecognizerTest {
    private final IntentRecognizer recognizer = new IntentRecognizer(null, new ObjectMapper());

    @Test
    void rulesDistinguishComplaintAndAfterSales() {
        assertEquals(IntentRecognizer.Intent.COMPLAINT, recognizer.classifyByRules("我要投诉你们服务太差了"));
        assertEquals(IntentRecognizer.Intent.AFTER_SALES, recognizer.classifyByRules("订单怎么申请退款"));
    }

    @Test
    void rulesCoverHandoffChitchatAndOutOfScope() {
        assertEquals(IntentRecognizer.Intent.HANDOFF, recognizer.classifyByRules("转人工"));
        assertEquals(IntentRecognizer.Intent.CHITCHAT, recognizer.classifyByRules("你好"));
        assertEquals(IntentRecognizer.Intent.OUT_OF_SCOPE, recognizer.classifyByRules("今天天气怎么样"));
    }

    @Test
    void classifyFallsBackToRulesWithoutLlm() {
        assertEquals(IntentRecognizer.Intent.PRODUCT_INQUIRY, recognizer.classify("专业版多少钱"));
    }
}
