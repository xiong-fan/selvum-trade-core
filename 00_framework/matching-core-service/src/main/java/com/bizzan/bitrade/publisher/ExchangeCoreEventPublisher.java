package com.bizzan.bitrade.publisher;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.matching.constant.MatchingTopics;
import com.bizzan.bitrade.matching.dto.MatchingCommand;
import com.bizzan.bitrade.matching.dto.MatchingCommandResult;
import com.bizzan.bitrade.matching.dto.MatchingBalanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExchangeCoreEventPublisher {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void publishCommandResult(MatchingCommand command, boolean success, String code, String message) {
        MatchingCommandResult result = new MatchingCommandResult();
        result.setCommandId(command.getCommandId());
        result.setCommandType(command.getCommandType() == null ? null : command.getCommandType().name());
        result.setBusinessKey(command.getBusinessKey());
        result.setMemberId(command.getMemberId());
        result.setSymbol(command.getSymbol());
        result.setOrderId(command.getOrderId());
        result.setCoreOrderId(command.getCoreOrderId());
        result.setCoreCurrencyId(command.getCoreCurrencyId());
        result.setCurrency(command.getCurrency());
        result.setFunds(command.getFunds());
        result.setSuccess(success);
        result.setResultCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        kafkaTemplate.send(MatchingTopics.MATCHING_COMMAND_RESULT, command.getBusinessKey(), JSON.toJSONString(result));
    }

    public void publishBalanceEvent(MatchingBalanceEvent event) {
        kafkaTemplate.send(MatchingTopics.MATCHING_BALANCE_EVENT, String.valueOf(event.getMemberId()), JSON.toJSONString(event));
    }
}
