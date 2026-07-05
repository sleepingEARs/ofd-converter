package com.ofd.converter.repository;

import com.ofd.converter.model.Task;
import org.springframework.data.repository.CrudRepository;

public interface TaskRepository extends CrudRepository<Task, String> {}
