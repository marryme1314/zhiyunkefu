package com.bishi.cs.session;

import com.bishi.cs.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/feedbacks")
public class FeedbackAdminController {
    private final MessageFeedbackRepository feedbacks;
    private final ChatMessageRepository messages;

    public FeedbackAdminController(MessageFeedbackRepository feedbacks, ChatMessageRepository messages) {
        this.feedbacks = feedbacks;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<MessageFeedback> items = feedbacks.findAllDetailed();
        List<Map<String, Object>> views = new ArrayList<>();
        for (MessageFeedback fb : items) {
            ChatMessage answer = fb.getMessage();
            String question = findPreviousUserQuestion(answer);
            Map<String, Object> row = new HashMap<>();
            row.put("id", fb.getId());
            row.put("type", fb.getType());
            row.put("comment", fb.getComment() == null ? "" : fb.getComment());
            row.put("createdAt", fb.getCreatedAt().toString());
            row.put("userEmail", fb.getUser().getEmail() == null ? "" : fb.getUser().getEmail());
            row.put("userPhone", fb.getUser().getPhone() == null ? "" : fb.getUser().getPhone());
            row.put("sessionId", answer.getSession().getId());
            row.put("sessionTitle", answer.getSession().getTitle());
            row.put("question", question);
            row.put("answer", truncate(answer.getContent(), 240));
            views.add(row);
        }
        return ApiResponse.ok(views);
    }

    private String findPreviousUserQuestion(ChatMessage answer) {
        List<ChatMessage> history = messages.findBySessionOrderByCreatedAtAsc(answer.getSession());
        String question = "";
        for (ChatMessage msg : history) {
            if (msg.getId().equals(answer.getId())) {
                break;
            }
            if ("USER".equals(msg.getRole())) {
                question = msg.getContent();
            }
        }
        return truncate(question, 160);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
