package com.bishi.cs.rag;

import com.bishi.cs.llm.LlmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class FollowUpSuggester {
    private static final String SYSTEM = """
            你是企业客服。根据用户问题和知识库摘要，给出 3 个用户可能接着问的短问题。
            只输出 JSON：{"suggestions":["问题1","问题2","问题3"]}
            要求：中文、不超过 22 字、不要回答问题本身、不要重复原问。
            """;

    private final LlmGateway llm;
    private final ObjectMapper mapper;

    public FollowUpSuggester(LlmGateway llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public List<String> suggest(String question, List<RetrievedChunk> hits, String answer) {
        List<String> fromModel = suggestWithModel(question, hits, answer);
        if (!fromModel.isEmpty()) {
            return fromModel;
        }
        return suggestByHeuristic(question, hits);
    }

    public List<String> suggestByHeuristic(String question, List<RetrievedChunk> hits) {
        Set<String> out = new LinkedHashSet<>();
        String q = question == null ? "" : question;
        if (!q.contains("退款") && !q.contains("退货")) {
            out.add("订阅多久可以退款？");
        }
        if (!q.contains("专业版") && !q.contains("价格")) {
            out.add("专业版和企业版有什么区别？");
        }
        if (!q.contains("密码") && !q.contains("登录")) {
            out.add("忘记密码怎么找回？");
        }
        if (hits != null) {
            for (RetrievedChunk hit : hits) {
                String name = hit.documentName() == null ? "" : hit.documentName();
                if (name.contains("产品") || name.contains("介绍")) {
                    out.add("如何开通并开始使用？");
                }
                if (name.contains("退换") || name.contains("政策")) {
                    out.add("退货需要什么凭证？");
                }
                if (name.contains("FAQ") || name.contains("常见")) {
                    out.add("账号被盗了怎么办？");
                }
            }
        }
        List<String> list = new ArrayList<>();
        for (String item : out) {
            if (item.equals(q.trim())) {
                continue;
            }
            list.add(item);
            if (list.size() == 3) {
                break;
            }
        }
        return list;
    }

    private List<String> suggestWithModel(String question, List<RetrievedChunk> hits, String answer) {
        if (llm == null) {
            return List.of();
        }
        try {
            StringBuilder ctx = new StringBuilder();
            ctx.append("用户问题：").append(question == null ? "" : question).append('\n');
            if (hits != null) {
                for (RetrievedChunk hit : hits) {
                    ctx.append("资料《").append(hit.documentName()).append("》：")
                            .append(hit.summary()).append('\n');
                }
            }
            if (answer != null && !answer.isBlank()) {
                ctx.append("客服已答摘要：").append(answer, 0, Math.min(answer.length(), 180));
            }
            String raw = llm.complete(SYSTEM, ctx.toString(), 4);
            JsonNode arr = LlmJson.tree(mapper, raw).path("suggestions");
            if (!arr.isArray()) {
                return List.of();
            }
            List<String> list = new ArrayList<>();
            for (JsonNode n : arr) {
                String s = n.asText("").trim();
                if (s.length() >= 4 && s.length() <= 30 && !list.contains(s)) {
                    list.add(s);
                }
                if (list.size() == 3) {
                    break;
                }
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }
}
