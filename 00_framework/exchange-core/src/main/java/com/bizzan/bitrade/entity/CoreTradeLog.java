package com.bizzan.bitrade.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "exchange_core_trade_log",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "tradeId")
        })
public class CoreTradeLog implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tradeId;
    private Long coreSeq;
    private String eventType;
    private String symbol;
    private Integer coreSymbolId;
    private String makerOrderId;
    private String takerOrderId;
    private Long makerMemberId;
    private Long takerMemberId;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal turnover;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
    private Date createdTime;
}
