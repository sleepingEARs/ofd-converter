package com.ofd.converter.repository;

import com.ofd.converter.model.Task;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends CrudRepository<Task, String> {
    @Modifying
    @Query("DELETE FROM task WHERE created_at < :before")
    void deleteByCreatedAtBefore(@Param("before") long before);
}
