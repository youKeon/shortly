package com.io.bitly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * Kafka Producer를 비동기로 처리하기 위한 스레드 풀 설정
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 표준 테스트: 최대 500 VU
        // 리디렉션 90% (약 450 VU)
        executor.setCorePoolSize(200);          // 충분한 코어 스레드
        executor.setMaxPoolSize(500);           // 피크 시 최대 스레드
        executor.setQueueCapacity(1000);        // 대기 큐 크기
        executor.setThreadNamePrefix("async-"); // 디버깅용
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        return executor;
    }
}

