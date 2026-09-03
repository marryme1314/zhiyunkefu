package com.bishi.cs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@Order(0)
public class ProdSafetyValidator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProdSafetyValidator.class);
    private final AppProperties props;

    public ProdSafetyValidator(AppProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.length() < 32 || looksDefault(secret)) {
            throw new IllegalStateException("生产环境必须设置至少 32 位的 JWT_SECRET，且不能使用示例值");
        }
        String password = props.getAdmin().getPassword();
        if (password == null || password.isBlank() || "Admin123!".equals(password)) {
            throw new IllegalStateException("生产环境必须设置 ADMIN_PASSWORD，且不能使用默认口令 Admin123!");
        }
        if ("moonshot".equalsIgnoreCase(props.getLlm().getProvider())
                && (props.getMoonshot().getApiKey() == null || props.getMoonshot().getApiKey().isBlank())) {
            throw new IllegalStateException("LLM_PROVIDER=moonshot 时必须设置 MOONSHOT_API_KEY");
        }
        log.info("生产安全检查通过");
    }

    private static boolean looksDefault(String secret) {
        String lower = secret.toLowerCase().trim();
        return lower.startsWith("please-change")
                || lower.startsWith("replace-this")
                || lower.equals("change-me")
                || lower.contains("example-secret");
    }
}
