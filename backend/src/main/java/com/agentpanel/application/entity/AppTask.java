package com.agentpanel.application.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_task")
public class AppTask extends BaseEntity {

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "column_id", nullable = false)
    private Long columnId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false, length = 256)
    private String title;

    private String description;

    @Column(nullable = false, length = 16)
    private String priority = "normal";

    @Column(name = "order_no", nullable = false)
    private int orderNo;
}
