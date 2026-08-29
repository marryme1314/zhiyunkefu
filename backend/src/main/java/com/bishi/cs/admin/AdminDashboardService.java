package com.bishi.cs.admin;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.rag.IntentRecognizer;
import com.bishi.cs.session.ChatMessage;
import com.bishi.cs.session.ChatMessageRepository;
import com.bishi.cs.session.ChatSession;
import com.bishi.cs.session.ChatSessionRepository;
import com.bishi.cs.session.MessageFeedbackRepository;
import com.bishi.cs.user.UserAccount;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final MessageFeedbackRepository feedbacks;

    public AdminDashboardService(ChatSessionRepository sessions,
                                 ChatMessageRepository messages,
                                 MessageFeedbackRepository feedbacks) {
        this.sessions = sessions;
        this.messages = messages;
        this.feedbacks = feedbacks;
    }

    public Map<String, Object> overview(int days) {
        int window = Math.max(7, Math.min(days, 60));
        LocalDateTime start = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionCount", sessions.count());
        data.put("questionCount", messages.countAllUserQuestions());
        data.put("todayQuestionCount", messages.countUserQuestionsSinceGlobal(LocalDate.now().atStartOfDay()));
        data.put("feedbackCount", feedbacks.count());
        data.put("dailyQuestions", dailyQuestions(window, start));
        data.put("feedbackStats", feedbackStats());
        data.put("intentStats", intentStats());
        return data;
    }

    public List<Map<String, Object>> listSessions() {
        List<ChatSession> all = sessions.findAllWithUserOrderByUpdatedAtDesc();
        List<Map<String, Object>> views = new ArrayList<>();
        for (ChatSession session : all) {
            UserAccount user = session.getUser();
            List<ChatMessage> history = messages.findBySessionOrderByCreatedAtAsc(session);
            long userTurns = history.stream().filter(m -> "USER".equals(m.getRole())).count();
            String lastIntent = "";
            String lastIntentLabel = "";
            for (int i = history.size() - 1; i >= 0; i--) {
                ChatMessage msg = history.get(i);
                if ("USER".equals(msg.getRole()) && msg.getIntent() != null && !msg.getIntent().isBlank()) {
                    lastIntent = msg.getIntent();
                    lastIntentLabel = intentLabel(lastIntent);
                    break;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", session.getId());
            row.put("title", session.getTitle());
            row.put("createdAt", session.getCreatedAt().toString());
            row.put("updatedAt", session.getUpdatedAt().toString());
            row.put("messageCount", history.size());
            row.put("questionCount", userTurns);
            row.put("lastIntent", lastIntent);
            row.put("lastIntentLabel", lastIntentLabel);
            row.put("userId", user.getId());
            row.put("userEmail", user.getEmail() == null ? "" : user.getEmail());
            row.put("userPhone", user.getPhone() == null ? "" : user.getPhone());
            row.put("userRole", user.getRole() == null ? "USER" : user.getRole());
            views.add(row);
        }
        return views;
    }

    public Map<String, Object> sessionDetail(Long sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
        UserAccount user = session.getUser();
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatMessage message : messages.findBySessionOrderByCreatedAtAsc(session)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", message.getId());
            row.put("role", message.getRole());
            row.put("content", message.getContent());
            row.put("createdAt", message.getCreatedAt().toString());
            row.put("intent", message.getIntent() == null ? "" : message.getIntent());
            row.put("intentLabel", intentLabel(message.getIntent()));
            items.add(row);
        }
        Map<String, Object> sessionView = new LinkedHashMap<>();
        sessionView.put("id", session.getId());
        sessionView.put("title", session.getTitle());
        sessionView.put("createdAt", session.getCreatedAt().toString());
        sessionView.put("updatedAt", session.getUpdatedAt().toString());
        sessionView.put("userEmail", user.getEmail() == null ? "" : user.getEmail());
        sessionView.put("userPhone", user.getPhone() == null ? "" : user.getPhone());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", sessionView);
        data.put("messages", items);
        return data;
    }

    private List<Map<String, Object>> dailyQuestions(int window, LocalDateTime start) {
        Map<String, Long> counted = new HashMap<>();
        for (Object[] row : messages.dailyUserQuestionCounts(start)) {
            String day = String.valueOf(row[0]);
            long cnt = ((Number) row[1]).longValue();
            counted.put(day, cnt);
        }
        List<Map<String, Object>> series = new ArrayList<>();
        LocalDate cursor = LocalDate.now().minusDays(window - 1L);
        LocalDate end = LocalDate.now();
        while (!cursor.isAfter(end)) {
            String key = cursor.toString();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("count", counted.getOrDefault(key, 0L));
            series.add(point);
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    private Map<String, Object> feedbackStats() {
        long like = 0;
        long dislike = 0;
        for (Object[] row : feedbacks.countByType()) {
            String type = String.valueOf(row[0]);
            long cnt = ((Number) row[1]).longValue();
            if ("LIKE".equals(type)) {
                like = cnt;
            } else if ("DISLIKE".equals(type)) {
                dislike = cnt;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("like", like);
        stats.put("dislike", dislike);
        stats.put("total", like + dislike);
        return stats;
    }

    private List<Map<String, Object>> intentStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : messages.countByIntent()) {
            String code = String.valueOf(row[0]);
            long cnt = ((Number) row[1]).longValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intent", "UNKNOWN".equals(code) ? "" : code);
            item.put("label", "UNKNOWN".equals(code) ? "未标注" : intentLabel(code));
            item.put("count", cnt);
            list.add(item);
        }
        list.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return list;
    }

    private static String intentLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return IntentRecognizer.Intent.valueOf(code).label();
        } catch (Exception e) {
            return code;
        }
    }
}
