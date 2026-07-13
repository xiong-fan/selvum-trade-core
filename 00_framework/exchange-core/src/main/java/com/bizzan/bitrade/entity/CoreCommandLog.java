package com.bizzan.bitrade.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@Entity
@Table(name = "exchange_core_command_log",
        uniqueConstraints = @UniqueConstraint(columnNames = "commandId"))
public class CoreCommandLog implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String commandId;
    private String commandType;
    private String businessKey;
    @Lob
    private String payload;
    private String status;
    private String resultCode;
    @Column(length = 1024)
    private String errorMessage;
    private Date createdTime;
    private Date updatedTime;
}
