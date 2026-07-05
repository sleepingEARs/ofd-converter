package com.ofd.converter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {
    @Bean(name = "conversionExecutor")
    public ExecutorService conversionExecutor() {
        return new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "ofd-converter"); t.setDaemon(true); return t; });
    }

    @Bean(name = "logExecutor")
    public ExecutorService logExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ofd-log-writer"); t.setDaemon(true); return t;
        });
    }
}
