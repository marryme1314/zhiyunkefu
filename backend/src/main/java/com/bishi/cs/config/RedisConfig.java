package com.bishi.cs.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisConfig {
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(AppProperties props) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                props.getRedis().getHost(),
                props.getRedis().getPort()
        );
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
