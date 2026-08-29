package com.codeit.mople.global.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// order를 낮춰 캐시 어드바이스를 트랜잭션 바깥에 고정함
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
@Configuration
@RequiredArgsConstructor
public class RedisConfig implements CachingConfigurer {

  private final RedisCacheErrorHandler cacheErrorHandler;

  // 캐시별 설정이 없을 때의 기본 만료 시간
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

  // 재시도 모두 실패했을 때 옛 값이 남는 상한 시간
  private static final Duration FOLLOW_COUNT_TTL = Duration.ofMinutes(10);

  // 고정 데이터라 짧게 잡으면 회차마다 TMDB를 다시 부르게 됨
  private static final Duration TMDB_GENRES_TTL = Duration.ofDays(7);

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    //Redis의 Key는 일반 String으로 저장
    template.setKeySerializer(new StringRedisSerializer());

    //Redis의 Value는 JSON 형태로 저장(객체 저장을 위함)
    template.setValueSerializer(jsonSerializer());

    //Hash 자료구조를 사용할 경우를 대비한 직렬화 설정
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(jsonSerializer());

    return template;
  }

  //Spring Cache를 Redis로 사용하기 위한 CacheManager 빈 등록
  @Bean
  public CacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      RedisNamespaceProperties properties) {

    // 기존 로직에 namespace만 추가
    RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
        .serializeKeysWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(jsonSerializer()))
        .entryTtl(DEFAULT_TTL)
        .prefixCacheNameWith(properties.cacheKeyPrefix());

    // followCont 값 타입을 고정하지 않으면 long이 Integer로 되돌아와 캐시 히트에서 터짐 그래서 long으로 고정
    RedisCacheConfiguration followCountConfiguration = defaultConfiguration
        .entryTtl(FOLLOW_COUNT_TTL)
        .disableCachingNullValues()
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new GenericToStringSerializer<>(Long.class)));

    // tmdb
    RedisCacheConfiguration tmdbGenresConfiguration = defaultConfiguration
        .entryTtl(TMDB_GENRES_TTL)
        .disableCachingNullValues();

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfiguration)
        .withCacheConfiguration(CacheNames.FOLLOW_COUNT, followCountConfiguration)
        .withCacheConfiguration(CacheNames.TMDB_GENRES, tmdbGenresConfiguration)
        // 바깥 트랜잭션이 있는 호출에서만 개입함, 캐시 쓰기/삭제를 그 커밋 이후로 미룸
        .transactionAware()
        // 히트/미스를 Actuator cache 메트릭으로 노출
        .enableStatistics()
        .build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return cacheErrorHandler;
  }

  private GenericJackson2JsonRedisSerializer jsonSerializer() {
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
    serializer.configure(mapper -> mapper.registerModule(new JavaTimeModule()));
    return serializer;
  }
}