package com.bizzan.bitrade.matching.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MatchingBalanceEvent implements Serializable {
    private Long coreSeq;
    private Long memberId;
    private String currency;
    private Integer coreCurrencyId;
    private BigDecimal available;
    private BigDecimal reserved;
    private BigDecimal total;
    private Long timestamp;
}
