package com.bizzan.bitrade.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "member_trading_transfer_log",
        uniqueConstraints = @UniqueConstraint(columnNames = "commandId"))
public class MemberTradingTransferLog implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String commandId;
    private String transferType;
    private Long memberId;
    private String currency;
    private Integer coreCurrencyId;
    private BigDecimal amount;
    private String status;
    private String resultCode;
    private String errorMessage;
    private Date createdTime;
    private Date updatedTime;
}
