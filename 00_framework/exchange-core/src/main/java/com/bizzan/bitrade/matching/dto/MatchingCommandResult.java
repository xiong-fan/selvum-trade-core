package com.bizzan.bitrade.matching.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class MatchingCommandResult implements Serializable {
    private String commandId;
    private String commandType;
    private String businessKey;
    private boolean success;
    private String resultCode;
    private String message;
    private Long coreSeq;
    private Long memberId;
    private String symbol;
    private String orderId;
    private Long coreOrderId;
    private Integer coreCurrencyId;
    private String currency;
    private java.math.BigDecimal funds;
    private Long timestamp;
}
