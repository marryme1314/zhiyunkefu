package com.bishi.cs.rag;

import com.bishi.cs.security.AuthUser;
import com.bishi.cs.user.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final RagChatService ragChatService;
    private final AuthService authService;

    public ChatController(RagChatService ragChatService, AuthService authService) {
        this.ragChatService = ragChatService;
        this.authService = authService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody ChatRequest req,
                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        return ragChatService.stream(
                authService.requireUser(user),
                req.sessionId(),
                req.question(),
                Boolean.TRUE.equals(req.regenerate()),
                req.replaceMessageId(),
                response
        );
    }

    public record ChatRequest(
            @NotNull Long sessionId,
            @NotBlank String question,
            Boolean regenerate,
            Long replaceMessageId
    ) {
    }
}
