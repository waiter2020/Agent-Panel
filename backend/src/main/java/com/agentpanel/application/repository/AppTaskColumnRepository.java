package com.agentpanel.application.repository;

import com.agentpanel.application.entity.AppTaskColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppTaskColumnRepository extends JpaRepository<AppTaskColumn, Long> {
    List<AppTaskColumn> findByBoardIdAndDeletedFalseOrderByOrderNoAsc(Long boardId);
}
