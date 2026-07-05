package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.repository.TaskRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    @Test
    void createSetsPending() {
        TaskRepository repo = mock(TaskRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TaskService svc = new TaskService(repo);

        Task t = svc.create("f1", "a.ofd", SourceType.OFD, ConvertFormat.PDF, null);

        assertEquals(TaskStatus.PENDING.name(), t.getStatus());
        verify(repo).save(any());
    }

    @Test
    void markDoneSetsFields() {
        Task t = new Task();
        t.setId("t1");
        t.setStatus(TaskStatus.PROCESSING.name());
        t.setCreatedAt(1L);
        TaskRepository repo = mock(TaskRepository.class);
        when(repo.findById("t1")).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TaskService svc = new TaskService(repo);

        svc.markDone("t1", "/o/p", "a.pdf", 123L, "single");

        assertEquals(TaskStatus.DONE.name(), t.getStatus());
        assertEquals("a.pdf", t.getOutputFilename());
    }
}
