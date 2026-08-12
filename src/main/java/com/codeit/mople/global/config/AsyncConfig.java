package com.codeit.mople.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@EnableRetry
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // ThreadPoolExecutor는 큐가 가득 차야 corePoolSize를 넘어 maxPoolSize까지 늘어난다.
        // 큐를 500으로 크게 잡으면 그 500개가 다 쌓일 때까지 스레드가 2개에서 안 늘어나,
        // 이미 알림이 한참 늦어진 뒤에야 maxPoolSize가 의미를 갖는 문제가 있었다.
        // 알림은 늦게 전달될수록 가치가 떨어지므로, core를 올리고 큐를 짧게 잡아
        // 스레드가 먼저 늘어나도록 한다.
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // 큐(50)와 스레드(10)가 모두 소진돼 제출이 거부될 상황이면, 기본값(AbortPolicy)처럼
        // RejectedExecutionException을 던져 작업을 통째로 유실시키는 대신 제출한 스레드가
        // 직접 실행하게 한다. 그 스레드가 잠깐 느려지는 게 알림이 조용히 사라지는 것보다 낫다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("비동기 처리 최종 실패 - method: {}", method.getName(), ex);
    }
}
