package com.bishi.cs.session;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.rag.IntentRecognizer;
import com.bishi.cs.user.UserAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SessionService {
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final MessageFeedbackRepository feedbacks;
    private final ObjectMapper mapper;

    public SessionService(ChatSessionRepository sessions,
                          ChatMessageRepository messages,
                          MessageFeedbackRepository feedbacks,
                          ObjectMapper mapper) {
        this.sessions = sessions;
        this.messages = messages;
        this.feedbacks = feedbacks;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> create(UserAccount user) {
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle("新会话");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessions.save(session);
        return toSessionView(session);
    }

    public List<Map<String, Object>> list(UserAccount user) {
        return sessions.findByUserOrderByUpdatedAtDesc(user).stream().map(this::toSessionView).toList();
    }

    public Map<String, Object> detail(UserAccount user, Long sessionId) {
        ChatSession session = requireOwned(user, sessionId);
        List<Map<String, Object>> items = messages.findBySessionOrderByCreatedAtAsc(session).stream()
                .map(this::toMessageView)
                .toList();
        return Map.of(
                "session", toSessionView(session),
                "messages", items
        );
    }

    public ChatSession requireOwned(UserAccount user, Long sessionId) {
        return sessions.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
    }

    @Transactional
    public void delete(UserAccount user, Long sessionId) {
        ChatSession session = requireOwned(user, sessionId);
        List<ChatMessage> items = messages.findBySessionOrderByCreatedAtAsc(session);
        for (ChatMessage message : items) {
            feedbacks.deleteByMessage(message);
        }
        messages.deleteBySession(session);
        sessions.delete(session);
    }

    @Transactional
    public void deleteOwnedMessage(UserAccount user, Long messageId) {
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> new ApiException(404, "消息不存在"));
        if (!message.getSession().getUser().getId().equals(user.getId())) {
            throw new ApiException(403, "无权删除该消息");
        }
        feedbacks.deleteByMessage(message);
        messages.delete(message);
    }

    @Transactional
    public ChatMessage saveMessage(ChatSession session, String role, String content, String sourcesJson) {
        return saveMessage(session, role, content, sourcesJson, null);
    }

    @Transactional
    public ChatMessage saveMessage(ChatSession session, String role, String content, String sourcesJson, String intent) {
        ChatMessage msg = new ChatMessage();
        msg.setSession(session);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSourcesJson(sourcesJson);
        msg.setIntent(intent);
        msg.setCreatedAt(LocalDateTime.now());
        messages.save(msg);
        session.setUpdatedAt(LocalDateTime.now());
        if ("USER".equals(role) && "新会话".equals(session.getTitle())) {
            String title = content.replaceAll("\\s+", " ").trim();
            session.setTitle(title.length() > 20 ? title.substring(0, 20) : title);
        }
        sessions.save(session);
        return msg;
    }

    @Transactional
    public void updateLastUserIntent(ChatSession session, String intent) {
        List<ChatMessage> history = messages.findBySessionOrderByCreatedAtAsc(session);
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if ("USER".equals(msg.getRole())) {
                msg.setIntent(intent);
                messages.save(msg);
                return;
            }
        }
    }

    @Transactional
    public void feedback(UserAccount user, Long messageId, String type, String comment) {
        if (!"LIKE".equals(type) && !"DISLIKE".equals(type)) {
            throw new ApiException(400, "反馈类型只能是 LIKE 或 DISLIKE");
        }
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> new ApiException(404, "消息不存在"));
        if (!message.getSession().getUser().getId().equals(user.getId())) {
            throw new ApiException(403, "无权评价该消息");
        }
        if (!"ASSISTANT".equals(message.getRole())) {
            throw new ApiException(400, "只能对 AI 回答反馈");
        }
        MessageFeedback fb = feedbacks.findByMessageAndUser(message, user).orElseGet(MessageFeedback::new);
        fb.setMessage(message);
        fb.setUser(user);
        fb.setType(type);
        fb.setComment(comment);
        fb.setCreatedAt(LocalDateTime.now());
        feedbacks.save(fb);
    }

    public List<ChatMessage> history(ChatSession session) {
        return messages.findBySessionOrderByCreatedAtAsc(session);
    }

    private Map<String, Object> toSessionView(ChatSession session) {
        return Map.of(
                "id", session.getId(),
                "title", session.getTitle(),
                "createdAt", session.getCreatedAt().toString(),
                "updatedAt", session.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> toMessageView(ChatMessage message) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", message.getId());
        view.put("role", message.getRole());
        view.put("content", message.getContent());
        view.put("sources", parseSources(message.getSourcesJson()));
        view.put("suggestions", parseSuggestions(message.getSourcesJson()));
        view.put("createdAt", message.getCreatedAt().toString());
        view.put("intent", message.getIntent() == null ? "" : message.getIntent());
        view.put("intentLabel", intentLabel(message.getIntent()));
        return view;
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

    private List<Map<String, Object>> parseSources(String json) {
        JsonNode root = payloadRoot(json);
        if (root == null) {
            return List.of();
        }
        try {
            JsonNode sources = root.isArray() ? root : root.path("sources");
            if (!sources.isArray()) {
                return List.of();
            }
            return mapper.convertValue(sources, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseSuggestions(String json) {
        JsonNode root = payloadRoot(json);
        if (root == null || !root.isObject()) {
            return List.of();
        }
        try {
            JsonNode arr = root.path("suggestions");
            if (!arr.isArray()) {
                return List.of();
            }
            return mapper.convertValue(arr, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private JsonNode payloadRoot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
