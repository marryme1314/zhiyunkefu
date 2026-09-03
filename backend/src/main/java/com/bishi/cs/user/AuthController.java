package com.bishi.cs.user;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.common.ApiResponse;
import com.bishi.cs.security.AuthUser;
import com.bishi.cs.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/auth/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody AuthService.RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody AuthService.LoginRequest req,
                                                  HttpServletRequest request) {
        String ip = clientIp(request);
        loginRateLimiter.guard(ip);
        try {
            ApiResponse<Map<String, Object>> ok = ApiResponse.ok(authService.login(req));
            loginRateLimiter.success(ip);
            return ok;
        } catch (ApiException e) {
            if (e.getStatus() == 400) {
                loginRateLimiter.failure(ip);
            }
            throw e;
        }
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(authService.profile(authService.requireUser(user)));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
