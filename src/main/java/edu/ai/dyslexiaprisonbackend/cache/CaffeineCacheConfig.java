package edu.ai.dyslexiaprisonbackend.cache;


import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class CaffeineCacheConfig {


    @Bean
    public com.github.benmanes.caffeine.cache.Cache<@NonNull String, Boolean>
    tokenBlacklistCache() {

        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .recordStats()
                .build();
    }
}
