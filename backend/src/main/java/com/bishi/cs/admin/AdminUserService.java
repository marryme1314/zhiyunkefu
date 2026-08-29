package com.bishi.cs.admin;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.session.ChatMessage;
import com.bishi.cs.session.ChatMessageRepository;
import com.bishi.cs.session.ChatSession;
import com.bishi.cs.session.ChatSessionRepository;
import com.bishi.cs.session.MessageFeedbackRepository;
import com.bishi.cs.user.AdminBootstrap;
import com.bishi.cs.user.UserAccount;
import com.bishi.cs.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserService {
    private final UserAccountRepository users;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final MessageFeedbackRepository feedbacks;

    public AdminUserService(UserAccountRepository users,
                            ChatSessionRepository sessions,
                            ChatMessageRepository messages,
                            MessageFeedbackRepository feedbacks) {
        this.users = users;
        this.sessions = sessions;
        this.messages = messages;
        this.feedbacks = feedbacks;
    }

    public List<Map<String, Object>> listUsers(UserAccount operator) {
        List<UserAccount> all = users.findAllByOrderByCreatedAtDesc();
        long adminCount = users.countByRoleIgnoreCase("ADMIN");
        List<Map<String, Object>> views = new ArrayList<>();
        for (UserAccount user : all) {
            views.add(toUserView(user, operator, adminCount));
        }
        return views;
    }

    /**
     * 删除用户账号，并按依赖顺序清理：
     * 消息上的反馈 → 消息 → 会话 → 该用户留下的反馈 → 用户本身。
     */
    @Transactional
    public Map<String, Object> deleteUser(UserAccount operator, Long targetId) {
        if (targetId == null) {
            throw new ApiException(400, "用户 ID 无效");
        }
        UserAccount target = users.findById(targetId)
                .orElseThrow(() -> new ApiException(404, "用户不存在或已被删除"));

        DeleteGuard guard = evaluateDelete(operator, target, users.countByRoleIgnoreCase("ADMIN"));
        if (!guard.allowed()) {
            throw new ApiException(400, guard.reason());
        }

        List<ChatSession> userSessions = sessions.findByUserOrderByUpdatedAtDesc(target);
        int sessionCount = userSessions.size();
        int messageCount = 0;
        int feedbackCount = 0;

        for (ChatSession session : userSessions) {
            List<ChatMessage> history = messages.findBySessionOrderByCreatedAtAsc(session);
            messageCount += history.size();
            for (ChatMessage message : history) {
                long onMessage = feedbacks.countByMessage(message);
                feedbackCount += (int) onMessage;
                if (onMessage > 0) {
                    feedbacks.deleteByMessage(message);
                }
            }
            messages.deleteBySession(session);
        }
        if (!userSessions.isEmpty()) {
            sessions.deleteAll(userSessions);
        }

        long leftover = feedbacks.countByUser(target);
        if (leftover > 0) {
            feedbackCount += (int) leftover;
            feedbacks.deleteByUser(target);
        }

        users.delete(target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedUserId", targetId);
        result.put("account", accountOf(target));
        result.put("role", target.getRole());
        result.put("deletedSessions", sessionCount);
        result.put("deletedMessages", messageCount);
        result.put("deletedFeedbacks", feedbackCount);
        result.put("message", "已删除账号，并清理其会话、消息与反馈数据");
        return result;
    }

    private Map<String, Object> toUserView(UserAccount user, UserAccount operator, long adminCount) {
        long sessionCount = sessions.countByUser(user);
        long questionCount = messages.countUserQuestionsByUserId(user.getId());
        DeleteGuard guard = evaluateDelete(operator, user, adminCount);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("email", user.getEmail() == null ? "" : user.getEmail());
        view.put("phone", user.getPhone() == null ? "" : user.getPhone());
        view.put("account", accountOf(user));
        view.put("role", user.getRole() == null ? "USER" : user.getRole());
        view.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        view.put("sessionCount", sessionCount);
        view.put("questionCount", questionCount);
        view.put("builtInAdmin", isBuiltInAdmin(user));
        view.put("self", operator.getId().equals(user.getId()));
        view.put("canDelete", guard.allowed());
        view.put("deleteBlockReason", guard.allowed() ? "" : guard.reason());
        return view;
    }

    private DeleteGuard evaluateDelete(UserAccount operator, UserAccount target, long adminCount) {
        if (operator.getId().equals(target.getId())) {
            return DeleteGuard.deny("不能删除当前登录的管理员账号");
        }
        if (isBuiltInAdmin(target)) {
            return DeleteGuard.deny("系统内置管理员（" + AdminBootstrap.ADMIN_EMAIL + "）不可删除");
        }
        if (target.isAdmin() && adminCount <= 1) {
            return DeleteGuard.deny("系统中至少需保留一名管理员");
        }
        return DeleteGuard.allow();
    }

    private static boolean isBuiltInAdmin(UserAccount user) {
        return user.getEmail() != null
                && AdminBootstrap.ADMIN_EMAIL.equalsIgnoreCase(user.getEmail());
    }

    private static String accountOf(UserAccount user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return user.getPhone();
        }
        return "用户#" + user.getId();
    }

    private record DeleteGuard(boolean allowed, String reason) {
        static DeleteGuard allow() {
            return new DeleteGuard(true, "");
        }

        static DeleteGuard deny(String reason) {
            return new DeleteGuard(false, reason);
        }
    }
}
