package com.codeit.mople.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("AsyncConfig 테스트")
class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    @DisplayName("getAsyncExecutor는 큐/스레드가 소진돼도 작업을 버리지 않는 CallerRunsPolicy를 사용한다")
    void getAsyncExecutor는_CallerRunsPolicy를_거부_정책으로_사용한다() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.getAsyncExecutor();

        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
            .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

        executor.shutdown();
    }

    @Test
    @DisplayName("풀과 큐가 모두 가득 차면 작업이 버려지지 않고 제출한 스레드가 직접 실행한다")
    void 풀이_포화되면_제출한_스레드가_직접_작업을_실행한다() throws InterruptedException {
        // given - 프로덕션과 동일한 CallerRunsPolicy를 쓰되, 즉시 포화시키기 위해 풀 크기만 최소화
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        CountDownLatch blockFirstTask = new CountDownLatch(1);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);

        // 유일한 워커 스레드를 점유시켜 풀을 포화 상태로 만든다
        executor.execute(() -> {
            firstTaskStarted.countDown();
            try {
                blockFirstTask.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(firstTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicReference<Thread> executedOnThread = new AtomicReference<>();
        Thread submittingThread = Thread.currentThread();

        // when - 풀이 이미 가득 찬 상태에서 두 번째 작업 제출 (큐 용량도 0이라 즉시 거부 대상)
        executor.execute(() -> executedOnThread.set(Thread.currentThread()));

        // then - 예외 없이, 제출한 스레드가 그 자리에서 동기 실행했다 (별도 워커 스레드가 아님)
        assertThat(executedOnThread.get()).isEqualTo(submittingThread);

        blockFirstTask.countDown();
        executor.shutdown();
    }
}
