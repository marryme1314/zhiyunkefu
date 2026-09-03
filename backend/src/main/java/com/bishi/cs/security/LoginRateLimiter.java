package com.bishi.cs.security;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimiter {
    private final AppProperties props;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final ConcurrentHashMap<String, Window> local = new ConcurrentHashMap<>();

    public LoginRateLimiter(AppProperties props, ObjectProvider<StringRedisTemplate> redis) {
        this.props = props;
        this.redis = redis;
    }

    public void guard(String ip) {
        if (failures(ip) >= props.getSecurity().getLoginMaxFailures()) {
            throw new ApiException(429, "登录失败次数过多，请稍后再试");
        }
    }

    public void success(String ip) {
        clear(ip);
    }

    public void failure(String ip) {
        addFailure(ip);
    }

    private int failures(String ip) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            String raw = template.opsForValue().get(key(ip));
            return raw == null ? 0 : Integer.parseInt(raw);
        }
        Window window = local.get(ip);
        if (window == null || window.expired()) {
            return 0;
        }
        return window.count.get();
    }

    private void addFailure(String ip) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            String k = key(ip);
            Long n = template.opsForValue().increment(k);
            if (n != null && n == 1L) {
                template.expire(k, Duration.ofMinutes(props.getSecurity().getLoginWindowMinutes()));
            }
            return;
        }
        local.compute(ip, (ignored, current) -> {
            if (current == null || current.expired()) {
                return new Window(ttlMs());
            }
            current.count.incrementAndGet();
            return current;
        });
    }

    private void clear(String ip) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            template.delete(key(ip));
            return;
        }
        local.remove(ip);
    }

    private String key(String ip) {
        return "login:fail:" + ip;
    }

    private long ttlMs() {
        return Duration.ofMinutes(props.getSecurity().getLoginWindowMinutes()).toMillis();
    }

    private static final class Window {
        private final AtomicInteger count = new AtomicInteger(1);
        private final long expiresAt;

        private Window(long ttlMs) {
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
