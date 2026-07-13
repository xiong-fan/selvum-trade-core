package com.bizzan.bitrade.service;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.dao.CoreCommandLogRepository;
import com.bizzan.bitrade.engine.ExchangeCoreLifecycle;
import com.bizzan.bitrade.entity.CoreCommandLog;
import com.bizzan.bitrade.matching.dto.MatchingCommand;
import com.bizzan.bitrade.matching.dto.MatchingBalanceEvent;
import com.bizzan.bitrade.matching.constant.MatchingCommandType;
import com.bizzan.bitrade.publisher.ExchangeCoreEventPublisher;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.api.reports.SingleUserReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class ExchangeCoreCommandService {
    @Autowired
    private CoreCommandLogRepository commandLogRepository;
    @Autowired
    private ExchangeCoreLifecycle exchangeCoreLifecycle;
    @Autowired
    private ExchangeCoreEventPublisher eventPublisher;

    @Transactional
    public void handle(MatchingCommand command) {
        CoreCommandLog existed = commandLogRepository.findByCommandId(command.getCommandId());
        if (existed != null && "SUCCESS".equals(existed.getStatus()) && !isEngineBootstrapCommand(command)) {
            eventPublisher.publishCommandResult(command, true, "DUPLICATED", "command already processed");
            return;
        }
        CoreCommandLog log = existed == null ? new CoreCommandLog() : existed;
        log.setCommandId(command.getCommandId());
        log.setCommandType(command.getCommandType() == null ? null : command.getCommandType().name());
        log.setBusinessKey(command.getBusinessKey());
        log.setPayload(JSON.toJSONString(command));
        log.setStatus("PROCESSING");
        log.setUpdatedTime(new Date());
        if (log.getCreatedTime() == null) {
            log.setCreatedTime(new Date());
        }
        commandLogRepository.saveAndFlush(log);

        try {
            OrderCommand response = exchangeCoreLifecycle.submit(command);
            boolean success = isSuccess(response);
            log.setStatus(success ? "SUCCESS" : "FAILED");
            log.setResultCode(response == null || response.resultCode == null ? "OK" : response.resultCode.name());
            log.setErrorMessage(success ? null : log.getResultCode());
            log.setUpdatedTime(new Date());
            commandLogRepository.saveAndFlush(log);
            eventPublisher.publishCommandResult(command, success, log.getResultCode(), success ? "accepted" : "rejected by exchange-core");
            if (success) {
                publishBalanceMirrorIfFunding(command, response);
            }
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setResultCode("ERROR");
            log.setErrorMessage(e.getMessage());
            log.setUpdatedTime(new Date());
            commandLogRepository.saveAndFlush(log);
            eventPublisher.publishCommandResult(command, false, "ERROR", e.getMessage());
            throw e;
        }
    }

    private boolean isSuccess(OrderCommand response) {
        if (response == null || response.resultCode == null) {
            return true;
        }
        return response.resultCode == CommandResultCode.SUCCESS || response.resultCode == CommandResultCode.ACCEPTED;
    }

    private boolean isEngineBootstrapCommand(MatchingCommand command) {
        return command.getCommandType() == MatchingCommandType.ADD_USER
                || command.getCommandType() == MatchingCommandType.ADD_SYMBOL;
    }

    private void publishBalanceMirrorIfFunding(MatchingCommand command, OrderCommand response) {
        if (command.getCommandType() != MatchingCommandType.DEPOSIT_TRADING
                && command.getCommandType() != MatchingCommandType.WITHDRAW_TRADING) {
            return;
        }
        if (response == null || response.resultCode == null || !response.resultCode.name().equals("SUCCESS")) {
            return;
        }
        SingleUserReportResult report = exchangeCoreLifecycle.userReport(command.getMemberId());
        long available = 0L;
        if (report.getAccounts() != null && command.getCoreCurrencyId() != null) {
            available = report.getAccounts().get(command.getCoreCurrencyId());
        }
        MatchingBalanceEvent event = new MatchingBalanceEvent();
        event.setCoreSeq(response.timestamp);
        event.setMemberId(command.getMemberId());
        event.setCurrency(command.getCurrency());
        event.setCoreCurrencyId(command.getCoreCurrencyId());
        event.setAvailable(java.math.BigDecimal.valueOf(available));
        event.setReserved(java.math.BigDecimal.ZERO);
        event.setTotal(java.math.BigDecimal.valueOf(available));
        event.setTimestamp(System.currentTimeMillis());
        eventPublisher.publishBalanceEvent(event);
    }
}
