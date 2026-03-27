package com.exam_bank.exam_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);
    private static final String CACHE_NAMESPACE = "v6::";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .build();
        RedisSerializationContext.SerializationPair<Object> pair = RedisSerializationContext.SerializationPair
                .fromSerializer(serializer);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(pair)
                .prefixCacheNameWith(CACHE_NAMESPACE)
                .entryTtl(Duration.ofMinutes(5));

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        perCacheConfig.put("publicExams", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        perCacheConfig.put("publicExamDetail", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        perCacheConfig.put("managedExams", defaultConfig.entryTtl(Duration.ofSeconds(45)));
        perCacheConfig.put("managedExamDetail", defaultConfig.entryTtl(Duration.ofSeconds(45)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Ignoring cache GET error for cache={} key={}. Entry will be evicted.",
                        cache == null ? "unknown" : cache.getName(), key, exception);
                if (cache != null) {
                    cache.evict(key);
                }
            }
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return cacheErrorHandler();
    }
}
