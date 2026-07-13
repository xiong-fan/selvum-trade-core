package com.bizzan.bitrade.consumer;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.matching.constant.MatchingTopics;
import com.bizzan.bitrade.matching.dto.MatchingCommand;
import com.bizzan.bitrade.service.ExchangeCoreCommandService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ExchangeCoreCommandConsumer {
    @Autowired
    private ExchangeCoreCommandService commandService;

    @KafkaListener(topics = MatchingTopics.MATCHING_COMMAND, containerFactory = "kafkaListenerContainerFactory")
    public void onCommand(List<ConsumerRecord<String, String>> records) {
        for (ConsumerRecord<String, String> record : records) {
            log.info("matching command received topic={}, key={}, value={}", record.topic(), record.key(), record.value());
            commandService.handle(JSON.parseObject(record.value(), MatchingCommand.class));
        }
    }
}
