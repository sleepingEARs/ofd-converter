package com.ofd.converter.repository;

import com.ofd.converter.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryTest {

    @Autowired
    TaskRepository repo;

    @Test
    void saveAndFind() {
        Task t = new Task();
        t.setId("t1");
        t.setSourceFileId("f1");
        t.setSourceFilename("a.ofd");
        t.setSourceType(SourceType.OFD.name());
        t.setTargetFormat(ConvertFormat.PDF.name());
        t.setStatus(TaskStatus.PENDING.name());
        t.setCreatedAt(System.currentTimeMillis());
        t.setUpdatedAt(t.getCreatedAt());
        repo.save(t);

        Task loaded = repo.findById("t1").orElseThrow();
        assertEquals(TaskStatus.PENDING.name(), loaded.getStatus());
    }
}
