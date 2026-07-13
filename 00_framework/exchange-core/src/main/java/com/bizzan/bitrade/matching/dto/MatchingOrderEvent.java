package com.bizzan.bitrade.matching.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MatchingOrderEvent implements Serializable {
    private Long coreSeq;
    private Integer coreSymbolId;
    private String symbol;
    private String orderId;
    private Long coreOrderId;
    private Long memberId;
    private String eventType;
    private BigDecimal tradedAmount;
    private BigDecimal turnover;
    private Boolean completed;
    private Long timestamp;
}
