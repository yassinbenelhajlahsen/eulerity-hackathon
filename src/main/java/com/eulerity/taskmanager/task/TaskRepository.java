package com.eulerity.taskmanager.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    long countByPriorityAndStatusIn(Priority priority, Collection<Status> statuses);

    Optional<Task> findFirstByPriorityAndStatusInOrderByCreatedAtAsc(
            Priority priority, Collection<Status> statuses);
}
