package com.ofd.converter.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    private final ExecutorService conversionExecutor;
    private final ExecutorService logExecutor;

    public ThreadPoolConfig() {
        this.conversionExecutor = new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "ofd-converter"); t.setDaemon(true); return t; });
        this.logExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ofd-log-writer"); t.setDaemon(true); return t;
        });
    }

    @Bean(name = "conversionExecutor")
    public ExecutorService conversionExecutor() {
        return conversionExecutor;
    }

    @Bean(name = "logExecutor")
    public ExecutorService logExecutor() {
        return logExecutor;
    }

    @PreDestroy
    public void shutdown() {
        shutdownQuietly(logExecutor, "logExecutor");
        shutdownQuietly(conversionExecutor, "conversionExecutor");
    }

    private void shutdownQuietly(ExecutorService exec, String name) {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
