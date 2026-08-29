package com.bishi.cs.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {
    public static final String ADMIN_EMAIL = "admin@company.com";
    public static final String ADMIN_PASSWORD = "Admin123!";

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(UserAccountRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        UserAccount admin = users.findByEmail(ADMIN_EMAIL).orElseGet(UserAccount::new);
        boolean created = admin.getId() == null;
        admin.setEmail(ADMIN_EMAIL);
        admin.setPhone(null);
        admin.setPasswordHash(encoder.encode(ADMIN_PASSWORD));
        admin.setRole("ADMIN");
        if (created) {
            admin.setCreatedAt(LocalDateTime.now());
        }
        users.save(admin);
        System.out.println("[INFO] 管理员账号已就绪 " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD
                + (created ? "（新建）" : "（已重置密码）"));
    }
}
