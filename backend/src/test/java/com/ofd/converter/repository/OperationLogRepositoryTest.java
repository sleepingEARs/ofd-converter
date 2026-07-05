package com.ofd.converter.repository;

import com.ofd.converter.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OperationLogRepositoryTest {

    @Autowired
    OperationLogRepository repo;

    @Test
    void insertAndDeleteOlderThan() {
        OperationLog log = new OperationLog();
        log.setId("l1");
        log.setOperationType(OperationType.UPLOAD.name());
        log.setClientIp("127.0.0.1");
        log.setFileId("f1");
        log.setStatus("SUCCESS");
        log.setCreatedAt(1000L);
        repo.save(log);

        repo.deleteByCreatedAtBefore(2000L);
        assertTrue(repo.findById("l1").isEmpty());
    }
}
