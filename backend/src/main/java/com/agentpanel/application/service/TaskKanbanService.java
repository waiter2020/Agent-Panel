package com.agentpanel.application.service;

import com.agentpanel.application.dto.TaskBoardDto;
import com.agentpanel.application.dto.TaskColumnDto;
import com.agentpanel.application.dto.TaskItemDto;
import com.agentpanel.application.entity.*;
import com.agentpanel.application.repository.*;
import com.agentpanel.auth.SecurityUtils;
import com.agentpanel.common.BusinessException;
import com.agentpanel.common.TenantAccessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskKanbanService {

    private final AppTaskBoardRepository boardRepository;
    private final AppTaskColumnRepository columnRepository;
    private final AppTaskRepository taskRepository;
    private final ApplicationRepository applicationRepository;
    private final AgentTemplateRepository templateRepository;

    public List<TaskBoardDto> listBoards() {
        List<AppTaskBoard> boards = SecurityUtils.isSuperAdmin()
                ? boardRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                : boardRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(
                        SecurityUtils.getCurrentTenantId());
        return boards.stream()
                .map(b -> {
                    TaskBoardDto dto = new TaskBoardDto();
                    dto.setId(b.getId());
                    dto.setName(b.getName());
                    dto.setScopeType(b.getScopeType());
                    dto.setScopeRef(b.getScopeRef());
                    return dto;
                }).toList();
    }

    public TaskBoardDto getBoard(Long boardId) {
        AppTaskBoard board = requireOwnedBoard(boardId);
        TaskBoardDto dto = new TaskBoardDto();
        dto.setId(board.getId());
        dto.setName(board.getName());
        dto.setScopeType(board.getScopeType());
        dto.setScopeRef(board.getScopeRef());
        List<AppTaskColumn> columns = columnRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(boardId);
        List<AppTask> tasks = taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(boardId);
        Map<Long, List<AppTask>> byColumn = tasks.stream().collect(Collectors.groupingBy(AppTask::getColumnId));
        dto.setColumns(columns.stream().map(col -> toColumnDto(col, byColumn.getOrDefault(col.getId(), List.of()))).toList());
        return dto;
    }

    @Transactional
    public void syncApplicationTasks(Long boardId) {
        AppTaskBoard board = requireOwnedBoard(boardId);
        syncBoard(board);
    }

    @Transactional
    public void syncBoardsForTenant(Long tenantId) {
        boardRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(tenantId)
                .forEach(this::syncBoard);
    }

    @Transactional
    public void syncAllBoards() {
        boardRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                .forEach(this::syncBoard);
    }

    private void syncBoard(AppTaskBoard board) {
        Long boardId = board.getId();
        List<AppTaskColumn> columns = columnRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(boardId);
        Map<String, Long> statusToColumn = columns.stream()
                .filter(c -> c.getStatusMapping() != null && !c.getStatusMapping().isBlank())
                .collect(Collectors.toMap(AppTaskColumn::getStatusMapping, AppTaskColumn::getId, (a, b) -> a));

        List<Application> apps = applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(
                board.getTenantId());

        java.util.Set<Long> activeAppIds = apps.stream().map(Application::getId).collect(Collectors.toSet());

        List<AppTask> existing = taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(boardId);
        for (AppTask orphan : existing) {
            if (orphan.getApplicationId() != null && !activeAppIds.contains(orphan.getApplicationId())) {
                orphan.setDeleted(true);
                taskRepository.save(orphan);
            }
        }

        existing = taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(boardId);
        Map<Long, AppTask> byAppId = existing.stream()
                .filter(t -> t.getApplicationId() != null)
                .collect(Collectors.toMap(AppTask::getApplicationId, t -> t, (a, b) -> a));

        int order = existing.size();
        for (Application app : apps) {
            AppTask task = byAppId.get(app.getId());
            Long targetColumnId = statusToColumn.getOrDefault(app.getStatus(), columns.isEmpty() ? null : columns.get(0).getId());
            if (targetColumnId == null) {
                continue;
            }
            if (task == null) {
                task = new AppTask();
                task.setBoardId(boardId);
                task.setApplicationId(app.getId());
                task.setTitle(app.getName());
                task.setDescription("自动同步自应用状态");
                task.setOrderNo(order++);
            } else {
                task.setTitle(app.getName());
            }
            task.setColumnId(targetColumnId);
            taskRepository.save(task);
        }
    }

    @Transactional
    public TaskItemDto moveTask(Long taskId, Long columnId, Integer orderNo) {
        AppTask task = taskRepository.findById(taskId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException("任务不存在"));
        requireOwnedBoard(task.getBoardId());
        columnRepository.findById(columnId)
                .filter(c -> !c.isDeleted() && c.getBoardId().equals(task.getBoardId()))
                .orElseThrow(() -> new BusinessException("目标列不存在"));
        task.setColumnId(columnId);
        if (orderNo != null) {
            task.setOrderNo(orderNo);
        }
        AppTask saved = taskRepository.save(task);
        return toTaskItemDto(saved);
    }

    private TaskColumnDto toColumnDto(AppTaskColumn col, List<AppTask> tasks) {
        TaskColumnDto dto = new TaskColumnDto();
        dto.setId(col.getId());
        dto.setName(col.getName());
        dto.setStatusMapping(col.getStatusMapping());
        dto.setOrderNo(col.getOrderNo());
        dto.setColor(col.getColor());
        dto.setTasks(tasks.stream().map(this::toTaskItemDto).toList());
        return dto;
    }

    private TaskItemDto toTaskItemDto(AppTask task) {
        TaskItemDto dto = new TaskItemDto();
        dto.setId(task.getId());
        dto.setColumnId(task.getColumnId());
        dto.setApplicationId(task.getApplicationId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setPriority(task.getPriority());
        dto.setOrderNo(task.getOrderNo());
        if (task.getApplicationId() != null) {
            applicationRepository.findById(task.getApplicationId()).ifPresent(app -> {
                dto.setAppName(app.getName());
                dto.setAppStatus(app.getStatus());
                templateRepository.findById(app.getTemplateId()).ifPresent(t -> dto.setTemplateCode(t.getCode()));
            });
        }
        return dto;
    }

    private AppTaskBoard requireOwnedBoard(Long boardId) {
        AppTaskBoard board = boardRepository.findByIdAndDeletedFalse(boardId)
                .orElseThrow(() -> new BusinessException("看板不存在"));
        TenantAccessHelper.requireOwnedTenant(board.getTenantId(), "看板不存在");
        return board;
    }
}
