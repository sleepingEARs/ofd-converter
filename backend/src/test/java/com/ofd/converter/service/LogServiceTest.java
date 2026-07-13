package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.repository.OperationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LogServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void recordsAsyncWithoutThrowing() throws Exception {
        OperationLogRepository repo = mock(OperationLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LogService svc = new LogService(repo, jdbc, Executors.newSingleThreadExecutor());

        svc.record(OperationType.UPLOAD, "1.2.3.4", "f1", null, null, "SUCCESS", 12, null, "ua");

        Thread.sleep(200); // let async complete
        verify(repo, times(1)).save(any(OperationLog.class));
    }

    @Test
    void swallowsWriteFailure() throws Exception {
        OperationLogRepository repo = mock(OperationLogRepository.class);
        when(repo.save(any())).thenThrow(new RuntimeException("db down"));
        LogService svc = new LogService(repo, jdbc, Executors.newSingleThreadExecutor());

        svc.record(OperationType.UPLOAD, "1.2.3.4", "f1", null, null, "SUCCESS", 1, null, "ua");
        Thread.sleep(200);
        // No exception propagated — pass
        assertTrue(true);
    }
}
