package com.bishi.cs.rag;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.common.StreamCancelledException;
import com.bishi.cs.config.AppProperties;
import com.bishi.cs.llm.LlmGateway;
import com.bishi.cs.session.ChatMessage;
import com.bishi.cs.session.ChatMessageRepository;
import com.bishi.cs.session.ChatSession;
import com.bishi.cs.session.SessionService;
import com.bishi.cs.user.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RagChatService {
    private final SessionService sessions;
    private final ChatMessageRepository messages;
    private final RetrievalService retrieval;
    private final IntentRecognizer intents;
    private final FollowUpSuggester followUps;
    private final LlmGateway llm;
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public RagChatService(SessionService sessions,
                          ChatMessageRepository messages,
                          RetrievalService retrieval,
                          IntentRecognizer intents,
                          FollowUpSuggester followUps,
                          LlmGateway llm,
                          AppProperties props,
                          ObjectMapper mapper) {
        this.sessions = sessions;
        this.messages = messages;
        this.retrieval = retrieval;
        this.intents = intents;
        this.followUps = followUps;
        this.llm = llm;
        this.props = props;
        this.mapper = mapper;
    }

    public SseEmitter stream(UserAccount user,
                             Long sessionId,
                             String question,
                             boolean regenerate,
                             Long replaceMessageId,
                             HttpServletResponse response) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new ApiException(400, "问题不能为空");
        }
        if (q.length() > props.getRag().getMaxQuestionLength()) {
            throw new ApiException(400, "单次提问不能超过 " + props.getRag().getMaxQuestionLength() + " 字");
        }
        ChatSession session = sessions.requireOwned(user, sessionId);

        if (!regenerate) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            long used = messages.countUserQuestionsSince(user.getId(), startOfDay);
            if (used >= props.getRag().getDailyQuestionLimit()) {
                throw new ApiException(429, "今日提问次数已达上限");
            }
        }

        if (replaceMessageId != null) {
            sessions.deleteOwnedMessage(user, replaceMessageId);
        }

        IntentRecognizer.Intent intent = intents.classify(q);

        List<ChatMessage> historyBefore;
        if (regenerate) {
            List<ChatMessage> all = sessions.history(session);
            historyBefore = withoutTrailingUser(all);
            sessions.updateLastUserIntent(session, intent.name());
        } else {
            historyBefore = sessions.history(session);
            sessions.saveMessage(session, "USER", q, null, intent.name());
        }

        SseEmitter emitter = new SseEmitter(llm.timeoutSeconds() * 1000L);
        AtomicBoolean alive = new AtomicBoolean(true);
        emitter.onCompletion(() -> alive.set(false));
        emitter.onTimeout(() -> alive.set(false));
        emitter.onError(e -> alive.set(false));

        SecurityContext context = SecurityContextHolder.getContext();
        executor.submit(DelegatingSecurityContextRunnable.create(
                () -> runStream(emitter, response, session, historyBefore, q, intent, alive),
                context
        ));
        return emitter;
    }

    private List<ChatMessage> withoutTrailingUser(List<ChatMessage> all) {
        if (all.isEmpty()) {
            return all;
        }
        List<ChatMessage> copy = new ArrayList<>(all);
        int last = copy.size() - 1;
        if ("USER".equals(copy.get(last).getRole())) {
            copy.remove(last);
        }
        return copy;
    }

    private void runStream(SseEmitter emitter, HttpServletResponse response, ChatSession session,
                           List<ChatMessage> historyBefore, String question,
                           IntentRecognizer.Intent intent, AtomicBoolean alive) {
        try {
            if (intent == IntentRecognizer.Intent.CHITCHAT) {
                finishFixed(emitter, response, session, intent, intents.chitchatReply(), List.of(), List.of(), question, alive);
                return;
            }

            if (intent == IntentRecognizer.Intent.HANDOFF) {
                finishFixed(emitter, response, session, intent, intents.handoffReply(), List.of(), List.of(), question, alive);
                return;
            }

            if (intent == IntentRecognizer.Intent.OUT_OF_SCOPE) {
                finishFixed(emitter, response, session, intent, props.getRag().getEmptyRetrievalReply(), List.of(), List.of(), question, alive);
                return;
            }

            List<RetrievedChunk> hits = retrieval.retrieve(question, intent);
            List<Map<String, Object>> sources = hits.stream()
                    .map(h -> Map.<String, Object>of(
                            "documentName", h.documentName(),
                            "summary", h.summary(),
                            "score", h.score()
                    ))
                    .toList();
            send(emitter, response, "meta", meta(intent, sources), alive);

            if (hits.isEmpty()) {
                String fallback = intent == IntentRecognizer.Intent.COMPLAINT
                        ? intents.complaintFallback()
                        : props.getRag().getEmptyRetrievalReply();
                finishFixed(emitter, response, session, intent, fallback, sources, hits, question, alive);
                return;
            }

            List<Map<String, String>> llmMessages = PromptBuilder.withHistory(historyBefore, props.getRag().getHistoryRounds());
            String prompt = PromptBuilder.buildUserPrompt(question, hits);
            if (intent == IntentRecognizer.Intent.COMPLAINT) {
                prompt = "【用户意图：投诉】请先表达歉意与共情，再严格依据资料答复。\n" + prompt;
            } else if (intent == IntentRecognizer.Intent.AFTER_SALES) {
                prompt = "【用户意图：售后问题】请优先给出可执行的售后处理步骤。\n" + prompt;
            } else if (intent == IntentRecognizer.Intent.PRODUCT_INQUIRY) {
                prompt = "【用户意图：产品咨询】请清晰说明套餐/功能/价格相关要点。\n" + prompt;
            }
            llmMessages.add(Map.of("role", "user", "content", prompt));

            StringBuilder full = new StringBuilder();
            try {
                llm.chatStream(llmMessages, token -> {
                    if (!alive.get()) {
                        throw new StreamCancelledException();
                    }
                    full.append(token);
                    send(emitter, response, "token", Map.of("text", token), alive);
                }, () -> {
                });
            } catch (StreamCancelledException cancelled) {
                persistInterrupted(emitter, response, session, full, sources, alive);
                return;
            }

            if (!alive.get()) {
                persistInterrupted(emitter, response, session, full, sources, alive);
                return;
            }

            List<String> suggestions = followUps.suggest(question, hits, full.toString());
            ChatMessage saved = sessions.saveMessage(session, "ASSISTANT", full.toString(), payloadJson(sources, suggestions));
            send(emitter, response, "done", donePayload(saved.getId(), false, suggestions), alive);
            safeComplete(emitter);
        } catch (StreamCancelledException cancelled) {
            safeComplete(emitter);
        } catch (ApiException e) {
            try {
                send(emitter, response, "error", Map.of("message", e.getMessage()), alive);
            } catch (Exception ignored) {
                // client gone
            }
            safeComplete(emitter);
        } catch (Exception e) {
            try {
                send(emitter, response, "error", Map.of("message", "生成失败: " + e.getMessage()), alive);
            } catch (Exception ignored) {
                // client gone
            }
            safeComplete(emitter);
        }
    }

    private Map<String, Object> meta(IntentRecognizer.Intent intent, List<Map<String, Object>> sources) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("intent", intent.name());
        meta.put("intentLabel", intent.label());
        meta.put("sources", sources);
        return meta;
    }

    private Map<String, Object> donePayload(long messageId, boolean interrupted, List<String> suggestions) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("messageId", messageId);
        done.put("interrupted", interrupted);
        done.put("suggestions", suggestions == null ? List.of() : suggestions);
        return done;
    }

    private String payloadJson(List<Map<String, Object>> sources, List<String> suggestions) throws Exception {
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("sources", sources == null ? List.of() : sources);
        wrap.put("suggestions", suggestions == null ? List.of() : suggestions);
        return mapper.writeValueAsString(wrap);
    }

    private void finishFixed(SseEmitter emitter, HttpServletResponse response, ChatSession session,
                             IntentRecognizer.Intent intent, String reply,
                             List<Map<String, Object>> sources, List<RetrievedChunk> hits, String question,
                             AtomicBoolean alive) throws Exception {
        List<String> suggestions = followUps.suggest(question, hits, reply);
        send(emitter, response, "meta", meta(intent, sources), alive);
        send(emitter, response, "token", Map.of("text", reply), alive);
        ChatMessage saved = sessions.saveMessage(session, "ASSISTANT", reply, payloadJson(sources, suggestions));
        send(emitter, response, "done", donePayload(saved.getId(), false, suggestions), alive);
        safeComplete(emitter);
    }

    private void persistInterrupted(SseEmitter emitter, HttpServletResponse response, ChatSession session,
                                    StringBuilder full, List<Map<String, Object>> sources, AtomicBoolean alive) {
        try {
            if (full.length() > 0) {
                String text = full + "\n\n（已中断）";
                ChatMessage saved = sessions.saveMessage(session, "ASSISTANT", text, payloadJson(sources, List.of()));
                if (alive.get()) {
                    send(emitter, response, "token", Map.of("text", "\n\n（已中断）"), alive);
                    send(emitter, response, "done", donePayload(saved.getId(), true, List.of()), alive);
                }
            } else if (alive.get()) {
                send(emitter, response, "error", Map.of("message", "已停止生成"), alive);
            }
        } catch (Exception ignored) {
            // ignore
        }
        safeComplete(emitter);
    }

    private void send(SseEmitter emitter, HttpServletResponse response, String event, Object data, AtomicBoolean alive) {
        if (!alive.get()) {
            throw new StreamCancelledException();
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(mapper.writeValueAsString(data)));
            response.flushBuffer();
        } catch (Exception e) {
            alive.set(false);
            throw new StreamCancelledException();
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already completed
        }
    }
}
