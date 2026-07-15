package com.ofd.converter.repository;

import com.ofd.converter.model.OperationLog;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends CrudRepository<OperationLog, String> {
    @Modifying
    @Query("DELETE FROM operation_log WHERE created_at < :before")
    void deleteByCreatedAtBefore(@Param("before") long before);
}
