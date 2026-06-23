package com.agentpanel.application.service;

import com.agentpanel.application.entity.*;
import com.agentpanel.application.repository.*;
import com.agentpanel.auth.AuthPrincipal;
import com.agentpanel.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskKanbanServiceTest {

    @Mock private AppTaskBoardRepository boardRepository;
    @Mock private AppTaskColumnRepository columnRepository;
    @Mock private AppTaskRepository taskRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private AgentTemplateRepository templateRepository;

    @InjectMocks
    private TaskKanbanService taskKanbanService;

    private AppTaskBoard board;
    private AppTaskColumn runningColumn;

    @BeforeEach
    void setUp() {
        board = new AppTaskBoard();
        board.setId(1L);
        board.setName("默认看板");
        board.setTenantId(2L);

        runningColumn = new AppTaskColumn();
        runningColumn.setId(10L);
        runningColumn.setBoardId(1L);
        runningColumn.setStatusMapping("running");
        runningColumn.setOrderNo(1);

        var principal = new AuthPrincipal(1L, "admin", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getBoardDoesNotTriggerSync() {
        when(boardRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(board));
        when(columnRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L)).thenReturn(List.of(runningColumn));
        when(taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L)).thenReturn(List.of());

        taskKanbanService.getBoard(1L);

        verify(applicationRepository, never()).findByDeletedFalseOrderByUpdatedAtDesc();
        verify(applicationRepository, never()).findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(anyLong());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void syncApplicationTasksUsesBoardTenantNotAllTenants() {
        when(boardRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(board));
        when(columnRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L))
                .thenReturn(List.of(runningColumn));
        when(applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of());
        when(taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L)).thenReturn(List.of());

        taskKanbanService.syncApplicationTasks(1L);

        verify(applicationRepository).findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L);
        verify(applicationRepository, never()).findByDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void syncApplicationTasksMapsRunningStatusToColumn() {
        Application app = new Application();
        app.setId(100L);
        app.setName("agent-a");
        app.setStatus("running");
        app.setTenantId(2L);

        when(boardRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(board));
        when(columnRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L))
                .thenReturn(List.of(runningColumn));
        when(applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of(app));
        when(taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L)).thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskKanbanService.syncApplicationTasks(1L);

        verify(taskRepository).save(argThat(task ->
                task.getApplicationId().equals(100L) && task.getColumnId().equals(10L)));
    }

    @Test
    void syncBoardsForTenantSyncsEachBoardWithoutOwnershipCheck() {
        when(boardRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of(board));
        when(columnRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L))
                .thenReturn(List.of(runningColumn));
        when(applicationRepository.findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of());
        when(taskRepository.findByBoardIdAndDeletedFalseOrderByOrderNoAsc(1L)).thenReturn(List.of());

        taskKanbanService.syncBoardsForTenant(2L);

        verify(applicationRepository).findByDeletedFalseAndTenantIdOrderByUpdatedAtDesc(2L);
    }

    @Test
    void requireOwnedBoardRejectsCrossTenant() {
        board.setTenantId(99L);
        when(boardRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(board));

        assertThrows(BusinessException.class, () -> taskKanbanService.getBoard(1L));
    }
}
