package com.sit.tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient; // haven't added before

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:500}")
    private int queueCapacity;

    @Value("${app.async.thread-name-prefix:sit-telegram-async-}")
    private String threadNamePrefix;

    @Bean(name = "telegramAsyncExecutor")
    public Executor telegramAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}