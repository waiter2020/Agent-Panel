package com.agentpanel.application.entity;

import com.agentpanel.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_task_column")
public class AppTaskColumn extends BaseEntity {

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "status_mapping", length = 32)
    private String statusMapping;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(length = 16)
    private String color;
}
