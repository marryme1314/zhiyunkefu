package com.bishi.cs.rag;

import com.bishi.cs.llm.LlmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class IntentRecognizer {
    public enum Intent {
        PRODUCT_INQUIRY("产品咨询"),
        AFTER_SALES("售后问题"),
        CHITCHAT("闲聊"),
        COMPLAINT("投诉"),
        HANDOFF("转人工"),
        OUT_OF_SCOPE("超出范围"),
        KNOWLEDGE_QA("知识问答");

        private final String label;

        Intent(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final String SYSTEM = """
            你是企业客服意图分类器。只输出 JSON：{"intent":"CODE"}
            CODE 必须是下列之一：
            PRODUCT_INQUIRY 产品价格/套餐/功能咨询
            AFTER_SALES 退款退货换货、账号登录、物流发票、维修
            COMPLAINT 明确不满、投诉、维权、欺诈指控（比对售后：售后要解决方案，投诉强调情绪与问责）
            HANDOFF 明确要求转人工/找真人客服
            CHITCHAT 打招呼、你是谁、谢谢再见
            OUT_OF_SCOPE 天气股市彩票写诗等与本企业产品无关
            KNOWLEDGE_QA 其他知识库问答
            """;

    private final LlmGateway llm;
    private final ObjectMapper mapper;

    public IntentRecognizer(LlmGateway llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Intent classify(String question) {
        Intent fromModel = classifyWithModel(question);
        if (fromModel != null) {
            return fromModel;
        }
        return classifyByRules(question);
    }

    public Intent classifyByRules(String question) {
        String q = question == null ? "" : question.replaceAll("\\s+", "").toLowerCase();
        if (q.isEmpty()) {
            return Intent.KNOWLEDGE_QA;
        }
        if (isHandoff(q)) {
            return Intent.HANDOFF;
        }
        if (isComplaint(q)) {
            return Intent.COMPLAINT;
        }
        if (isChitchat(q)) {
            return Intent.CHITCHAT;
        }
        if (isOutOfScope(q)) {
            return Intent.OUT_OF_SCOPE;
        }
        if (isAfterSales(q)) {
            return Intent.AFTER_SALES;
        }
        if (isProductInquiry(q)) {
            return Intent.PRODUCT_INQUIRY;
        }
        return Intent.KNOWLEDGE_QA;
    }

    private Intent classifyWithModel(String question) {
        if (llm == null || question == null || question.isBlank()) {
            return null;
        }
        try {
            String raw = llm.complete(SYSTEM, "用户问题：" + question.trim(), 8);
            JsonNode node = LlmJson.tree(mapper, raw);
            String code = node.path("intent").asText("").trim().toUpperCase();
            if (code.isEmpty()) {
                return null;
            }
            return Intent.valueOf(code);
        } catch (Exception e) {
            return null;
        }
    }

    public String chitchatReply() {
        return "您好，我是企业智能客服。您可以询问套餐价格、账号问题、退款与售后等政策，我会依据知识库为您解答。";
    }

    public String handoffReply() {
        return "好的，已记录您的转人工请求。工作时间 9:00-18:00 会有客服跟进；您也可继续描述问题，我先根据知识库尽力协助。当前为演示环境，不会真实接通电话坐席。";
    }

    public String complaintFallback() {
        return "非常抱歉给您带来不便。我已将问题标记为投诉类诉求。请尽量补充订单号、发生时间与具体问题，便于尽快核实处理；也可直接说「转人工」。";
    }

    private boolean isChitchat(String q) {
        return q.matches("^(你好|您好|在吗|在么|hi|hello|谢谢|感谢|再见|拜拜|早上好|下午好|晚上好)([!！?？。.~～])*")
                || q.contains("你是谁")
                || q.contains("你叫什么")
                || q.contains("你是人工智能")
                || q.contains("你是机器人")
                || q.equals("哈哈")
                || q.equals("呵呵");
    }

    private boolean isHandoff(String q) {
        return q.contains("转人工")
                || q.contains("人工客服")
                || q.contains("找人工")
                || q.equals("人工")
                || q.contains("转接客服");
    }

    private boolean isComplaint(String q) {
        return q.contains("投诉")
                || q.contains("举报")
                || q.contains("差评")
                || q.contains("骗人")
                || q.contains("欺诈")
                || q.contains("太差了")
                || q.contains("垃圾服务")
                || q.contains("投诉你们")
                || q.contains("维权")
                || q.contains("曝光");
    }

    private boolean isAfterSales(String q) {
        return q.contains("退款")
                || q.contains("退货")
                || q.contains("换货")
                || q.contains("售后")
                || q.contains("维修")
                || q.contains("发票")
                || q.contains("物流")
                || q.contains("发货")
                || q.contains("坏了")
                || q.contains("不能用")
                || q.contains("无法登录")
                || q.contains("账号被盗");
    }

    private boolean isProductInquiry(String q) {
        return q.contains("价格")
                || q.contains("多少钱")
                || q.contains("套餐")
                || q.contains("专业版")
                || q.contains("企业版")
                || q.contains("功能")
                || q.contains("怎么用")
                || q.contains("如何使用")
                || q.contains("开通")
                || q.contains("购买")
                || q.contains("收费")
                || q.contains("免费试用");
    }

    private boolean isOutOfScope(String q) {
        return q.contains("股市")
                || q.contains("股票")
                || q.contains("彩票")
                || q.contains("天气预报")
                || q.contains("今天天气")
                || q.contains("星期几")
                || q.contains("几号")
                || q.contains("几点")
                || q.contains("世界杯")
                || q.contains("写诗")
                || q.contains("写代码作业");
    }
}
