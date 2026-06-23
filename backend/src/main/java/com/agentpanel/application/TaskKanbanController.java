package com.agentpanel.application;

import com.agentpanel.application.dto.MoveTaskRequest;
import com.agentpanel.application.dto.TaskBoardDto;
import com.agentpanel.application.service.TaskKanbanService;
import com.agentpanel.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TaskKanbanController {

    private final TaskKanbanService taskKanbanService;

    @GetMapping("/api/kanban/boards")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<List<TaskBoardDto>> listBoards() {
        return ApiResponse.ok(taskKanbanService.listBoards());
    }

    @GetMapping("/api/kanban/boards/{boardId}")
    @PreAuthorize("hasAuthority('app:read')")
    public ApiResponse<TaskBoardDto> getBoard(@PathVariable Long boardId) {
        return ApiResponse.ok(taskKanbanService.getBoard(boardId));
    }

    @PostMapping("/api/kanban/boards/{boardId}/sync")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<TaskBoardDto> syncBoard(@PathVariable Long boardId) {
        taskKanbanService.syncApplicationTasks(boardId);
        return ApiResponse.ok(taskKanbanService.getBoard(boardId));
    }

    @PostMapping("/api/kanban/tasks/{taskId}/move")
    @PreAuthorize("hasAuthority('app:write')")
    public ApiResponse<?> moveTask(@PathVariable Long taskId, @RequestBody MoveTaskRequest request) {
        return ApiResponse.ok(taskKanbanService.moveTask(taskId, request.getColumnId(), request.getOrderNo()));
    }
}
