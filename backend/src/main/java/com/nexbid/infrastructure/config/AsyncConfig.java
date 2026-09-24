package com.nexbid.infrastructure.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * EN: Turns on @Async for work that follows a commit but must not hold up the request — notifications.
 * VI: Bật @Async cho việc diễn ra sau commit nhưng không được giữ chân request — thông báo.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * EN: Explicit and bounded. Left alone, @Async fell back to a new thread per task, because the
     *     WebSocket broker's own executors made Boot's default step aside. Four threads leave most of
     *     the ten DB connections to requests.
     * VI: Khai rõ và có giới hạn. Để mặc định, @Async rơi về kiểu mỗi việc một luồng mới, vì các executor
     *     riêng của WebSocket broker khiến executor mặc định của Boot lùi lại. Bốn luồng chừa phần lớn trong
     *     mười kết nối DB cho request.
     */
    @Bean
    public ThreadPoolTaskExecutor afterCommitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("after-commit-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10_000);
        // EN: Finish what is queued on shutdown rather than drop it. / VI: Làm nốt việc đang chờ khi tắt thay vì bỏ.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return afterCommitExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error(
                "After-commit task {} failed; the change it followed still stands", method.getName(), ex);
    }
}
