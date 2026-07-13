package com.bizzan.bitrade.service;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.constant.TransactionType;
import com.bizzan.bitrade.dao.*;
import com.bizzan.bitrade.entity.*;
import com.bizzan.bitrade.matching.constant.MatchingCommandType;
import com.bizzan.bitrade.matching.dto.MatchingBalanceEvent;
import com.bizzan.bitrade.matching.dto.MatchingCommandResult;
import com.bizzan.bitrade.matching.dto.MatchingOrderEvent;
import com.bizzan.bitrade.matching.dto.MatchingTradeEvent;
import com.bizzan.bitrade.util.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

@Slf4j
@Service
public class MatchingSettlementService {
    @Autowired
    private CoreTradeLogRepository coreTradeLogRepository;
    @Autowired
    private CoreBalanceMirrorRepository balanceMirrorRepository;
    @Autowired
    private CoreSymbolMappingRepository symbolMappingRepository;
    @Autowired
    private CoreOrderMappingRepository orderMappingRepository;
    @Autowired
    private ExchangeOrderRepository exchangeOrderRepository;
    @Autowired
    private ExchangeOrderDetailRepository exchangeOrderDetailRepository;
    @Autowired
    private OrderDetailAggregationRepository orderDetailAggregationRepository;
    @Autowired
    private MemberWalletService memberWalletService;
    @Autowired
    private MemberTransactionService memberTransactionService;
    @Autowired
    private MemberTradingTransferLogRepository transferLogRepository;
    @Autowired
    private CoreAmountConverter amountConverter;

    @Transactional
    public void handleTrade(MatchingTradeEvent event) {
        if (coreTradeLogRepository.findByTradeId(event.getTradeId()) != null) {
            log.info("duplicate matching trade ignored: {}", event.getTradeId());
            return;
        }
        CoreTradeLog log = new CoreTradeLog();
        log.setTradeId(event.getTradeId());
        log.setCoreSeq(event.getCoreSeq());
        log.setEventType("TRADE");
        log.setSymbol(event.getSymbol());
        log.setCoreSymbolId(event.getCoreSymbolId());
        log.setMakerOrderId(event.getMakerOrderId());
        log.setTakerOrderId(event.getTakerOrderId());
        log.setMakerMemberId(event.getMakerMemberId());
        log.setTakerMemberId(event.getTakerMemberId());
        log.setPrice(event.getPrice());
        log.setAmount(event.getAmount());
        log.setTurnover(event.getTurnover());
        log.setMakerFee(event.getMakerFee());
        log.setTakerFee(event.getTakerFee());
        log.setCreatedTime(new Date());
        coreTradeLogRepository.saveAndFlush(log);

        mirrorOrderTrade(event.getMakerOrderId(), event.getAmount(), event.getTurnover(), event.getPrice(), event.getTimestamp(), event.getMakerFee(), event.getMakerOrderCompleted());
        mirrorOrderTrade(event.getTakerOrderId(), event.getAmount(), event.getTurnover(), event.getPrice(), event.getTimestamp(), event.getTakerFee(), event.getTakerOrderCompleted());
        mirrorTradeBalance(event);
    }

    public void handleOrderEvent(MatchingOrderEvent event) {
        ExchangeOrder order = exchangeOrderRepository.findByOrderId(event.getOrderId());
        if (order == null) {
            log.warn("order mirror event ignored, order not found: {}", JSON.toJSONString(event));
            return;
        }
        if (Boolean.TRUE.equals(event.getCompleted())) {
            order.setStatus(ExchangeOrderStatus.CANCELED);
            order.setCanceledTime(Calendar.getInstance().getTimeInMillis());
            exchangeOrderRepository.saveAndFlush(order);
        }
    }

    @Transactional
    public void handleBalance(MatchingBalanceEvent event) {
        retryWrite(() -> {
            CoreBalanceMirror mirror = balanceMirrorRepository.findByMemberIdAndCurrency(event.getMemberId(), event.getCurrency());
            if (mirror == null) {
                mirror = new CoreBalanceMirror();
                mirror.setMemberId(event.getMemberId());
                mirror.setCurrency(event.getCurrency());
            }
            mirror.setCoreCurrencyId(event.getCoreCurrencyId());
            mirror.setAvailable(event.getAvailable());
            mirror.setReserved(event.getReserved());
            mirror.setTotal(event.getTotal());
            mirror.setCoreSeq(event.getCoreSeq());
            mirror.setUpdatedTime(new Date());
            balanceMirrorRepository.saveAndFlush(mirror);
        }, "handleBalance");
    }

    @Transactional
    public void handleCommandResult(MatchingCommandResult result) {
        if (MatchingCommandType.PLACE_ORDER.name().equals(result.getCommandType())) {
            handlePlaceOrderResult(result);
            return;
        }
        if (MatchingCommandType.CANCEL_ORDER.name().equals(result.getCommandType())) {
            handleCancelOrderResult(result);
            return;
        }
        if (MatchingCommandType.DEPOSIT_TRADING.name().equals(result.getCommandType())) {
            handleDepositResult(result);
            return;
        }
        if (MatchingCommandType.WITHDRAW_TRADING.name().equals(result.getCommandType())) {
            handleWithdrawResult(result);
        }
    }

    private void handlePlaceOrderResult(MatchingCommandResult result) {
        ExchangeOrder order = exchangeOrderRepository.findByOrderId(result.getOrderId());
        if (order == null) {
            return;
        }
        if (result.isSuccess()) {
            if (order.getStatus() == ExchangeOrderStatus.SUBMITTED) {
                order.setStatus(ExchangeOrderStatus.ACCEPTED);
                exchangeOrderRepository.saveAndFlush(order);
            }
            return;
        }
        order.setStatus(ExchangeOrderStatus.REJECTED);
        order.setCanceledTime(Calendar.getInstance().getTimeInMillis());
        exchangeOrderRepository.saveAndFlush(order);
    }

    private void handleCancelOrderResult(MatchingCommandResult result) {
        if (result.isSuccess()) {
            return;
        }
        log.warn("cancel order rejected by matching core: {}", JSON.toJSONString(result));
    }

    private void handleDepositResult(MatchingCommandResult result) {
        MemberTradingTransferLog transfer = transferLogRepository.findByCommandId(result.getCommandId());
        if (transfer == null || "SETTLED".equals(transfer.getStatus()) || "REFUNDED".equals(transfer.getStatus())) {
            return;
        }
        transfer.setResultCode(result.getResultCode());
        transfer.setUpdatedTime(new Date());
        if (result.isSuccess()) {
            transfer.setStatus("SETTLED");
            transferLogRepository.saveAndFlush(transfer);
            return;
        }
        MemberWallet wallet = memberWalletService.findByCoinUnitAndMemberId(transfer.getCurrency(), transfer.getMemberId());
        memberWalletService.increaseBalance(wallet.getId(), transfer.getAmount());
        MemberTransaction transaction = new MemberTransaction();
        transaction.setMemberId(transfer.getMemberId());
        transaction.setSymbol(transfer.getCurrency());
        transaction.setAmount(transfer.getAmount());
        transaction.setType(TransactionType.TRANSFER_FROM_TRADING);
        transaction.setFee(BigDecimal.ZERO);
        transaction.setRealFee("0");
        transaction.setDiscountFee("0");
        transaction.setCreateTime(new Date());
        memberTransactionService.save(transaction);
        transfer.setStatus("REFUNDED");
        transfer.setErrorMessage(result.getMessage());
        transferLogRepository.saveAndFlush(transfer);
    }

    private void handleWithdrawResult(MatchingCommandResult result) {
        MemberTradingTransferLog transfer = transferLogRepository.findByCommandId(result.getCommandId());
        if (transfer == null || "SETTLED".equals(transfer.getStatus())) {
            return;
        }
        transfer.setResultCode(result.getResultCode());
        transfer.setUpdatedTime(new Date());
        if (!result.isSuccess()) {
            transfer.setStatus("FAILED");
            transfer.setErrorMessage(result.getMessage());
            transferLogRepository.saveAndFlush(transfer);
            return;
        }
        MemberWallet wallet = memberWalletService.findByCoinUnitAndMemberId(transfer.getCurrency(), transfer.getMemberId());
        memberWalletService.increaseBalance(wallet.getId(), transfer.getAmount());
        MemberTransaction transaction = new MemberTransaction();
        transaction.setMemberId(transfer.getMemberId());
        transaction.setSymbol(transfer.getCurrency());
        transaction.setAmount(transfer.getAmount());
        transaction.setType(TransactionType.TRANSFER_FROM_TRADING);
        transaction.setFee(BigDecimal.ZERO);
        transaction.setRealFee("0");
        transaction.setDiscountFee("0");
        transaction.setCreateTime(new Date());
        memberTransactionService.save(transaction);
        transfer.setStatus("SETTLED");
        transferLogRepository.saveAndFlush(transfer);
    }

    private void mirrorOrderTrade(String orderId, BigDecimal amount, BigDecimal turnover, BigDecimal price, Long timestamp, BigDecimal fee, Boolean completed) {
        ExchangeOrder order = exchangeOrderRepository.findByOrderId(orderId);
        if (order == null) {
            log.warn("trade mirror skipped, order not found: {}", orderId);
            return;
        }
        ExchangeOrderDetail detail = new ExchangeOrderDetail();
        detail.setOrderId(orderId);
        detail.setPrice(price);
        detail.setAmount(amount);
        detail.setTurnover(turnover);
        detail.setFee(fee == null ? BigDecimal.ZERO : fee);
        detail.setTime(timestamp == null ? Calendar.getInstance().getTimeInMillis() : timestamp);
        exchangeOrderDetailRepository.save(detail);

        OrderDetailAggregation aggregation = new OrderDetailAggregation();
        aggregation.setType(OrderTypeEnum.EXCHANGE);
        aggregation.setOrderId(orderId);
        aggregation.setMemberId(order.getMemberId());
        aggregation.setTime(detail.getTime());
        aggregation.setAmount(amount.doubleValue());
        aggregation.setFee(detail.getFee().doubleValue());
        aggregation.setUnit(order.getDirection() == ExchangeOrderDirection.BUY ? order.getBaseSymbol() : order.getCoinSymbol());
        aggregation.setDirection(order.getDirection());
        orderDetailAggregationRepository.save(aggregation);

        order.setTradedAmount(order.getTradedAmount().add(amount));
        order.setTurnover(order.getTurnover().add(turnover));
        if (Boolean.TRUE.equals(completed) || isOrderFilled(order)) {
            order.setStatus(ExchangeOrderStatus.COMPLETED);
            order.setCompletedTime(Calendar.getInstance().getTimeInMillis());
        } else {
            order.setStatus(ExchangeOrderStatus.PARTIALLY_TRADED);
        }
        exchangeOrderRepository.saveAndFlush(order);
    }

    private void mirrorTradeBalance(MatchingTradeEvent event) {
        CoreSymbolMapping mapping = symbolMappingRepository.findBySymbol(event.getSymbol());
        if (mapping == null) {
            log.warn("trade balance mirror skipped, symbol mapping not found: {}", event.getSymbol());
            return;
        }
        long size = amountConverter.toCoreSize(mapping, event.getAmount());
        BigDecimal baseDelta = BigDecimal.valueOf(size * (mapping.getBaseScaleK() == null ? 1L : mapping.getBaseScaleK()));
        BigDecimal quoteDelta = BigDecimal.valueOf(size
                * amountConverter.toCorePrice(mapping, event.getPrice())
                * (mapping.getQuoteScaleK() == null ? 1L : mapping.getQuoteScaleK()));

        if ("BID".equals(event.getTakerAction())) {
            incrementBalance(event.getMakerMemberId(), mapping.getQuoteSymbol(), mapping.getQuoteCurrencyId(), quoteDelta, event.getCoreSeq());
            incrementBalance(event.getTakerMemberId(), mapping.getBaseSymbol(), mapping.getBaseCurrencyId(), baseDelta, event.getCoreSeq());
        } else {
            incrementBalance(event.getMakerMemberId(), mapping.getBaseSymbol(), mapping.getBaseCurrencyId(), baseDelta, event.getCoreSeq());
            incrementBalance(event.getTakerMemberId(), mapping.getQuoteSymbol(), mapping.getQuoteCurrencyId(), quoteDelta, event.getCoreSeq());
        }
    }

    private void incrementBalance(Long memberId, String currency, Integer coreCurrencyId, BigDecimal delta, Long coreSeq) {
        if (memberId == null || currency == null || delta == null) {
            return;
        }
        CoreBalanceMirror mirror = balanceMirrorRepository.findByMemberIdAndCurrency(memberId, currency);
        if (mirror == null) {
            mirror = new CoreBalanceMirror();
            mirror.setMemberId(memberId);
            mirror.setCurrency(currency);
            mirror.setAvailable(BigDecimal.ZERO);
            mirror.setReserved(BigDecimal.ZERO);
            mirror.setTotal(BigDecimal.ZERO);
        }
        mirror.setCoreCurrencyId(coreCurrencyId);
        mirror.setAvailable(nullToZero(mirror.getAvailable()).add(delta));
        mirror.setReserved(nullToZero(mirror.getReserved()));
        mirror.setTotal(nullToZero(mirror.getTotal()).add(delta));
        mirror.setCoreSeq(coreSeq);
        mirror.setUpdatedTime(new Date());
        balanceMirrorRepository.saveAndFlush(mirror);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isOrderFilled(ExchangeOrder order) {
        if (order.getType() == ExchangeOrderType.MARKET_PRICE && order.getDirection() == ExchangeOrderDirection.BUY) {
            return order.getAmount().compareTo(order.getTurnover()) <= 0;
        }
        return order.getAmount().compareTo(order.getTradedAmount()) <= 0;
    }

    /**
     * MySQL 瞬时故障重试（最多 3 次，间隔 100ms/200ms/400ms）。
     * 如果最终失败，异常继续向上抛到 Kafka consumer，消息不提交 offset，等恢复后重新消费。
     */
    private void retryWrite(Runnable task, String label) {
        int maxRetries = 3;
        long delay = 100;
        for (int i = 0; i < maxRetries; i++) {
            try {
                task.run();
                return;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("[{}] MySQL write failed after {} retries, will retry via Kafka replay", label, maxRetries, e);
                    throw e;
                }
                log.warn("[{}] MySQL write retry {}/{}: {}", label, i + 1, maxRetries, e.getMessage());
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                delay *= 2;
            }
        }
    }
}
