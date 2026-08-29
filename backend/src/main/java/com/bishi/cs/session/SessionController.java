package com.bishi.cs.session;

import com.bishi.cs.common.ApiResponse;
import com.bishi.cs.security.AuthUser;
import com.bishi.cs.user.AuthService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final SessionService sessionService;
    private final AuthService authService;

    public SessionController(SessionService sessionService, AuthService authService) {
        this.sessionService = sessionService;
        this.authService = authService;
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(sessionService.create(authService.requireUser(user)));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(sessionService.list(authService.requireUser(user)));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(sessionService.detail(authService.requireUser(user), id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        sessionService.delete(authService.requireUser(user), id);
        return ApiResponse.ok();
    }

    @PostMapping("/messages/{id}/feedback")
    public ApiResponse<Void> feedback(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long id,
                                      @RequestBody FeedbackRequest req) {
        sessionService.feedback(authService.requireUser(user), id, req.type(), req.comment());
        return ApiResponse.ok();
    }

    public record FeedbackRequest(@NotBlank String type, String comment) {
    }
}
