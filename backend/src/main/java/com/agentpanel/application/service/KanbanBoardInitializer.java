package com.agentpanel.application.service;

import com.agentpanel.application.entity.AppTaskBoard;
import com.agentpanel.application.entity.AppTaskColumn;
import com.agentpanel.application.repository.AppTaskBoardRepository;
import com.agentpanel.application.repository.AppTaskColumnRepository;
import com.agentpanel.system.repository.SysTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KanbanBoardInitializer {

    private final SysTenantRepository tenantRepository;
    private final AppTaskBoardRepository boardRepository;
    private final AppTaskColumnRepository columnRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureAllTenantsHaveBoards() {
        tenantRepository.findAll().forEach(tenant -> ensureDefaultBoard(tenant.getId()));
    }

    @Transactional
    public void ensureDefaultBoard(Long tenantId) {
        if (!boardRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(tenantId).isEmpty()) {
            return;
        }
        AppTaskBoard board = new AppTaskBoard();
        board.setName("应用运维看板");
        board.setScopeType("platform");
        board.setTenantId(tenantId);
        board = boardRepository.save(board);
        createColumn(board.getId(), "待部署", "created", 1, "blue");
        createColumn(board.getId(), "部署中", "deploying", 2, "processing");
        createColumn(board.getId(), "运行中", "running", 3, "green");
        createColumn(board.getId(), "已停止", "stopped", 4, "default");
        createColumn(board.getId(), "异常", "error", 5, "red");
    }

    private void createColumn(Long boardId, String name, String statusMapping, int orderNo, String color) {
        AppTaskColumn column = new AppTaskColumn();
        column.setBoardId(boardId);
        column.setName(name);
        column.setStatusMapping(statusMapping);
        column.setOrderNo(orderNo);
        column.setColor(color);
        columnRepository.save(column);
    }
}
