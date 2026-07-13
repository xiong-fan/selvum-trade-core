package com.bizzan.bitrade.consumer;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.matching.constant.MatchingTopics;
import com.bizzan.bitrade.matching.dto.MatchingBalanceEvent;
import com.bizzan.bitrade.matching.dto.MatchingCommandResult;
import com.bizzan.bitrade.matching.dto.MatchingOrderEvent;
import com.bizzan.bitrade.matching.dto.MatchingTradeEvent;
import com.bizzan.bitrade.service.MatchingSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MatchingSettlementConsumer {
    @Autowired
    private MatchingSettlementService settlementService;

    @KafkaListener(topics = MatchingTopics.MATCHING_TRADE_EVENT, containerFactory = "kafkaListenerContainerFactory")
    public void onTrade(List<ConsumerRecord<String, String>> records) {
        for (ConsumerRecord<String, String> record : records) {
            settlementService.handleTrade(JSON.parseObject(record.value(), MatchingTradeEvent.class));
        }
    }

    @KafkaListener(topics = MatchingTopics.MATCHING_ORDER_EVENT, containerFactory = "kafkaListenerContainerFactory")
    public void onOrder(List<ConsumerRecord<String, String>> records) {
        for (ConsumerRecord<String, String> record : records) {
            settlementService.handleOrderEvent(JSON.parseObject(record.value(), MatchingOrderEvent.class));
        }
    }

    @KafkaListener(topics = MatchingTopics.MATCHING_BALANCE_EVENT, containerFactory = "kafkaListenerContainerFactory")
    public void onBalance(List<ConsumerRecord<String, String>> records) {
        for (ConsumerRecord<String, String> record : records) {
            settlementService.handleBalance(JSON.parseObject(record.value(), MatchingBalanceEvent.class));
        }
    }

    @KafkaListener(topics = MatchingTopics.MATCHING_COMMAND_RESULT, containerFactory = "kafkaListenerContainerFactory")
    public void onCommandResult(List<ConsumerRecord<String, String>> records) {
        for (ConsumerRecord<String, String> record : records) {
            settlementService.handleCommandResult(JSON.parseObject(record.value(), MatchingCommandResult.class));
        }
    }
}
