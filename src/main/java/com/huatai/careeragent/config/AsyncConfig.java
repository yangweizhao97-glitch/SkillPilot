package com.huatai.careeragent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "careerTaskExecutor")
    public Executor careerTaskExecutor(
            @Value("${career-agent.agent.task-thread-pool-size:4}") int poolSize
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("career-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "interviewStreamExecutor")
    public Executor interviewStreamExecutor(
            @Value("${career-agent.interview.stream-thread-pool-size:4}") int poolSize
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("interview-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "tutorStreamExecutor")
    public Executor tutorStreamExecutor(
            @Value("${career-agent.tutor.stream-thread-pool-size:4}") int poolSize
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("tutor-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "careerTaskStreamExecutor")
    public ExecutorService careerTaskStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "careerAgentStreamExecutor")
    public ExecutorService careerAgentStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
