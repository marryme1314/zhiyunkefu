package com.bishi.cs.user;

import com.bishi.cs.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    public static final String ADMIN_EMAIL = "admin@company.com";

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminBootstrap(UserAccountRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.getAdmin().getEmail();
        String password = props.getAdmin().getPassword();
        UserAccount existing = users.findByEmail(email).orElse(null);
        if (existing == null) {
            UserAccount admin = new UserAccount();
            admin.setEmail(email);
            admin.setPhone(null);
            admin.setPasswordHash(encoder.encode(password));
            admin.setRole("ADMIN");
            admin.setCreatedAt(LocalDateTime.now());
            users.save(admin);
            if (props.getAdmin().isLogCredentials()) {
                log.info("已创建管理员账号 {} / {}", email, password);
            } else {
                log.info("已创建管理员账号 {}", email);
            }
            return;
        }
        if (props.getAdmin().isResetPassword()) {
            existing.setPasswordHash(encoder.encode(password));
            existing.setRole("ADMIN");
            users.save(existing);
            log.warn("已按 ADMIN_RESET_PASSWORD 重置管理员密码 {}", email);
            return;
        }
        log.info("管理员账号已存在 {}", email);
    }
}
