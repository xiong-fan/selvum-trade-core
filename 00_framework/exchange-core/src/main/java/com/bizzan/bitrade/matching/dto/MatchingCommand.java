package com.bizzan.bitrade.matching.dto;

import com.bizzan.bitrade.entity.ExchangeOrderDirection;
import com.bizzan.bitrade.entity.ExchangeOrderType;
import com.bizzan.bitrade.matching.constant.MatchingCommandType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MatchingCommand implements Serializable {
    private String commandId;
    private MatchingCommandType commandType;
    private String businessKey;
    private Long memberId;
    private String symbol;
    private String orderId;
    private Long coreOrderId;
    private Integer coreSymbolId;
    private Integer coreCurrencyId;
    private Long coreTransactionId;
    private ExchangeOrderDirection direction;
    private ExchangeOrderType orderType;
    private BigDecimal price;
    private BigDecimal amount;
    private String currency;
    private BigDecimal funds;
    private Long timestamp;
}
