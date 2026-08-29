package com.bishi.cs.user;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.security.AuthUser;
import com.bishi.cs.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        String email = blankToNull(req.email());
        String phone = blankToNull(req.phone());
        if (email == null && phone == null) {
            throw new ApiException(400, "请填写邮箱或手机号");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new ApiException(400, "密码至少 6 位");
        }
        if (email != null && users.findByEmail(email).isPresent()) {
            throw new ApiException(400, "邮箱已被注册");
        }
        if (phone != null && users.findByPhone(phone).isPresent()) {
            throw new ApiException(400, "手机号已被注册");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        users.save(user);
        return tokenPayload(user);
    }

    public Map<String, Object> login(LoginRequest req) {
        String account = req.account() == null ? "" : req.account().trim();
        UserAccount user = users.findByEmail(account)
                .or(() -> users.findByPhone(account))
                .orElseThrow(() -> new ApiException(400, "账号或密码错误"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(400, "账号或密码错误");
        }
        return tokenPayload(user);
    }

    public UserAccount requireUser(AuthUser authUser) {
        return users.findById(authUser.id()).orElseThrow(() -> new ApiException(401, "用户不存在"));
    }

    public Map<String, Object> profile(UserAccount user) {
        String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone(),
                "role", role
        );
    }

    private Map<String, Object> tokenPayload(UserAccount user) {
        return Map.of(
                "token", jwtService.createToken(user.getId()),
                "user", profile(user)
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record RegisterRequest(String email, String phone, @NotBlank String password) {
    }

    public record LoginRequest(@NotBlank String account, @NotBlank String password) {
    }
}
