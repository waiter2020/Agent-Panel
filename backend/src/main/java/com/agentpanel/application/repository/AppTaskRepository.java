package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AppTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppTaskRepository extends JpaRepository<AppTask, Long> {
    List<AppTask> findByBoardIdAndDeletedFalseOrderByOrderNoAsc(Long boardId);
    List<AppTask> findByColumnIdAndDeletedFalseOrderByOrderNoAsc(Long columnId);
}
