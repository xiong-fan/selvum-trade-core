package com.bizzan.bitrade.matching.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MatchingTradeEvent implements Serializable {
    private String tradeId;
    private Long coreSeq;
    private Integer coreSymbolId;
    private String symbol;
    private String takerAction;
    private String makerOrderId;
    private String takerOrderId;
    private Long makerCoreOrderId;
    private Long takerCoreOrderId;
    private Long makerMemberId;
    private Long takerMemberId;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal turnover;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
    private Boolean makerOrderCompleted;
    private Boolean takerOrderCompleted;
    private Long timestamp;
}
