package com.jobmate.common.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiConfiguration {

    @Bean(name = "openAiRestClient")
    public RestClient openAiRestClient(AiSettings settings) {
        int timeout;
        try {
            timeout = Math.max(1, Math.min(60, settings.timeoutSeconds()));
        } catch (IllegalArgumentException exception) {
            timeout = 30;
        }
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeout))
                        .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean(name = "aiTaskExecutor")
    public TaskExecutor aiTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("jobmate-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
