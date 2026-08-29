package com.bishi.cs.rag;

import com.bishi.cs.session.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PromptBuilder {
    public static final String SYSTEM_PROMPT = """
            你是企业智能客服助手。你必须严格依据【知识库检索结果】回答用户问题。
            规则：
            1. 不要编造知识库中不存在的政策、价格、流程或承诺。
            2. 如果检索结果不足以回答，明确说明资料不足，并建议联系人工客服。
            3. 回答简洁、礼貌，使用中文。
            4. 不要输出与问题无关的内容。
            """;

    private PromptBuilder() {
    }

    public static String buildUserPrompt(String question, List<RetrievedChunk> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("【知识库检索结果】\n");
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk hit = hits.get(i);
            sb.append("[").append(i + 1).append("] 来源文档: ").append(hit.documentName())
                    .append("（相似度 ").append(String.format("%.3f", hit.score())).append("）\n")
                    .append(hit.content()).append("\n\n");
        }
        sb.append("【用户问题】\n").append(question).append("\n");
        sb.append("请仅根据以上检索结果作答。");
        return sb.toString();
    }

    public static List<Map<String, String>> withHistory(List<ChatMessage> history, int maxRounds) {
        List<ChatMessage> filtered = history.stream()
                .filter(m -> "USER".equals(m.getRole()) || "ASSISTANT".equals(m.getRole()))
                .toList();
        int keep = maxRounds * 2;
        if (filtered.size() > keep) {
            filtered = filtered.subList(filtered.size() - keep, filtered.size());
        }
        List<Map<String, String>> out = new ArrayList<>();
        out.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ChatMessage m : filtered) {
            String role = "USER".equals(m.getRole()) ? "user" : "assistant";
            out.add(Map.of("role", role, "content", m.getContent()));
        }
        return out;
    }
}
