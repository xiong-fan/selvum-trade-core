package com.bizzan.bitrade.controller;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.constant.TransactionType;
import com.bizzan.bitrade.dao.CoreBalanceMirrorRepository;
import com.bizzan.bitrade.dao.CoreSymbolMappingRepository;
import com.bizzan.bitrade.entity.CoreBalanceMirror;
import com.bizzan.bitrade.entity.MemberTradingTransferLog;
import com.bizzan.bitrade.entity.MemberTransaction;
import com.bizzan.bitrade.entity.MemberWallet;
import com.bizzan.bitrade.matching.constant.MatchingCommandType;
import com.bizzan.bitrade.matching.constant.MatchingTopics;
import com.bizzan.bitrade.matching.dto.MatchingCommand;
import com.bizzan.bitrade.service.MemberTransactionService;
import com.bizzan.bitrade.service.MemberWalletService;
import com.bizzan.bitrade.dao.MemberTradingTransferLogRepository;
import com.bizzan.bitrade.util.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/wallet/trading")
public class TradingTransferController {
    @Autowired
    private MemberWalletService memberWalletService;
    @Autowired
    private MemberTransactionService memberTransactionService;
    @Autowired
    private MemberTradingTransferLogRepository transferLogRepository;
    @Autowired
    private CoreBalanceMirrorRepository balanceMirrorRepository;
    @Autowired
    private CoreSymbolMappingRepository symbolMappingRepository;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/transfer-in")
    @Transactional
    public MessageResult transferIn(Long memberId, String currency, Integer coreCurrencyId, BigDecimal amount) {
        if (memberId == null || currency == null || coreCurrencyId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return MessageResult.error("invalid transfer-in argument");
        }
        MemberWallet wallet = memberWalletService.findByCoinUnitAndMemberId(currency, memberId);
        if (wallet == null) {
            return MessageResult.error("wallet not found");
        }
        // core 整数单位 → 自然单位：wallet 存的是自然单位（1 BTC = 1），engine 用 core 单位（1 BTC = baseScaleK）
        BigDecimal naturalAmount = toNaturalAmount(coreCurrencyId, amount);
        int ret = memberWalletService.decreaseBalance(wallet.getId(), naturalAmount);
        if (ret <= 0) {
            return MessageResult.error("insufficient member wallet balance");
        }
        String commandId = "DEPOSIT_TRADING-" + memberId + "-" + currency + "-" + System.currentTimeMillis();
        saveTransferLog(commandId, "TRANSFER_TO_TRADING", memberId, currency, coreCurrencyId, naturalAmount, "SUBMITTED");
        saveTransaction(memberId, currency, naturalAmount.negate(), TransactionType.TRANSFER_TO_TRADING);
        // 确保用户已在撮合引擎中注册交易账户（重复注册幂等）
        MatchingCommand addUser = new MatchingCommand();
        addUser.setCommandId("ADD_USER-" + memberId);
        addUser.setCommandType(MatchingCommandType.ADD_USER);
        addUser.setMemberId(memberId);
        addUser.setTimestamp(System.currentTimeMillis());
        kafkaTemplate.send(MatchingTopics.MATCHING_FUNDING_COMMAND, String.valueOf(memberId), JSON.toJSONString(addUser));
        MatchingCommand command = buildFundingCommand(commandId, MatchingCommandType.DEPOSIT_TRADING, memberId, currency, coreCurrencyId, amount);
        kafkaTemplate.send(MatchingTopics.MATCHING_FUNDING_COMMAND, String.valueOf(memberId), JSON.toJSONString(command));
        MessageResult result = MessageResult.success("success");
        result.setData(commandId);
        return result;
    }

    @PostMapping("/transfer-out")
    @Transactional
    public MessageResult transferOut(Long memberId, String currency, Integer coreCurrencyId, BigDecimal amount) {
        if (memberId == null || currency == null || coreCurrencyId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return MessageResult.error("invalid transfer-out argument");
        }
        String commandId = "WITHDRAW_TRADING-" + memberId + "-" + currency + "-" + System.currentTimeMillis();
        BigDecimal naturalAmount = toNaturalAmount(coreCurrencyId, amount);
        saveTransferLog(commandId, "TRANSFER_FROM_TRADING", memberId, currency, coreCurrencyId, naturalAmount, "SUBMITTED");
        // 确保用户已在撮合引擎中注册交易账户（重复注册幂等）
        MatchingCommand addUser = new MatchingCommand();
        addUser.setCommandId("ADD_USER-" + memberId);
        addUser.setCommandType(MatchingCommandType.ADD_USER);
        addUser.setMemberId(memberId);
        addUser.setTimestamp(System.currentTimeMillis());
        kafkaTemplate.send(MatchingTopics.MATCHING_FUNDING_COMMAND, String.valueOf(memberId), JSON.toJSONString(addUser));
        MatchingCommand command = buildFundingCommand(commandId, MatchingCommandType.WITHDRAW_TRADING, memberId, currency, coreCurrencyId, amount);
        kafkaTemplate.send(MatchingTopics.MATCHING_FUNDING_COMMAND, String.valueOf(memberId), JSON.toJSONString(command));
        MessageResult result = MessageResult.success("success");
        result.setData(commandId);
        return result;
    }

    @GetMapping("/balance")
    public MessageResult balance(Long memberId, String currency) {
        if (memberId == null || currency == null) {
            return MessageResult.error("invalid balance argument");
        }
        CoreBalanceMirror mirror = balanceMirrorRepository.findByMemberIdAndCurrency(memberId, currency);
        // MySQL mirror 不可用时，兜底直接查撮合引擎内存
        if (mirror == null) {
            try {
                String url = "http://SERVICE-MATCHING-CORE/matching/query/balance/" + memberId;
                return restTemplate.getForEntity(url, MessageResult.class).getBody();
            } catch (Exception e) {
                log.warn("fallback to engine balance failed for memberId={}", memberId, e);
            }
        }
        MessageResult result = MessageResult.success("success");
        result.setData(mirror);
        return result;
    }

    private MatchingCommand buildFundingCommand(String commandId, MatchingCommandType type, Long memberId, String currency, Integer coreCurrencyId, BigDecimal amount) {
        MatchingCommand command = new MatchingCommand();
        command.setCommandId(commandId);
        command.setCommandType(type);
        command.setBusinessKey(commandId);
        command.setMemberId(memberId);
        command.setCurrency(currency);
        command.setCoreCurrencyId(coreCurrencyId);
        command.setFunds(amount);
        command.setCoreTransactionId(System.currentTimeMillis());
        command.setTimestamp(System.currentTimeMillis());
        return command;
    }

    private void saveTransferLog(String commandId, String type, Long memberId, String currency, Integer coreCurrencyId, BigDecimal amount, String status) {
        MemberTradingTransferLog log = new MemberTradingTransferLog();
        log.setCommandId(commandId);
        log.setTransferType(type);
        log.setMemberId(memberId);
        log.setCurrency(currency);
        log.setCoreCurrencyId(coreCurrencyId);
        log.setAmount(amount);
        log.setStatus(status);
        log.setCreatedTime(new Date());
        log.setUpdatedTime(new Date());
        transferLogRepository.saveAndFlush(log);
    }

    private void saveTransaction(Long memberId, String currency, BigDecimal amount, TransactionType type) {
        MemberTransaction transaction = new MemberTransaction();
        transaction.setMemberId(memberId);
        transaction.setSymbol(currency);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setFee(BigDecimal.ZERO);
        transaction.setRealFee("0");
        transaction.setDiscountFee("0");
        transaction.setCreateTime(new Date());
        memberTransactionService.save(transaction);
    }

    /**
     * 将撮合引擎 core 整数单位转换为钱包自然单位
     * wallet 存的是自然单位（1 BTC = 1.0），engine 用的是 core 单位（1 BTC = baseScaleK）
     */
    private BigDecimal toNaturalAmount(Integer coreCurrencyId, BigDecimal coreAmount) {
        Long scaleK = symbolMappingRepository.findBaseScaleKByCurrencyId(coreCurrencyId);
        if (scaleK == null) {
            scaleK = symbolMappingRepository.findQuoteScaleKByCurrencyId(coreCurrencyId);
        }
        if (scaleK != null && scaleK > 0) {
            return coreAmount.divide(BigDecimal.valueOf(scaleK), 8, RoundingMode.HALF_UP);
        }
        return coreAmount;
    }
}
