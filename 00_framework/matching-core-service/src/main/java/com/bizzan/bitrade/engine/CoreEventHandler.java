package com.bizzan.bitrade.engine;

import com.alibaba.fastjson.JSON;
import com.bizzan.bitrade.dao.CoreOrderMappingRepository;
import com.bizzan.bitrade.dao.CoreSymbolMappingRepository;
import com.bizzan.bitrade.entity.CoreOrderMapping;
import com.bizzan.bitrade.entity.CoreSymbolMapping;
import com.bizzan.bitrade.matching.constant.MatchingTopics;
import com.bizzan.bitrade.matching.dto.MatchingBalanceEvent;
import com.bizzan.bitrade.matching.dto.MatchingOrderEvent;
import com.bizzan.bitrade.matching.dto.MatchingTradeEvent;
import com.bizzan.bitrade.service.CoreAmountConverter;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Component
public class CoreEventHandler implements IEventsHandler {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private CoreSymbolMappingRepository symbolMappingRepository;
    @Autowired
    private CoreOrderMappingRepository orderMappingRepository;
    @Autowired
    private CoreAmountConverter amountConverter;
    @Autowired
    private ExchangeCoreLifecycle exchangeCoreLifecycle;

    @Override
    public void commandResult(ApiCommandResult commandResult) {
        kafkaTemplate.send(MatchingTopics.MATCHING_STATE_EVENT, eventKey(commandResult.command), JSON.toJSONString(commandResult));
    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        CoreSymbolMapping symbol = symbolMappingRepository.findByCoreSymbolId(tradeEvent.symbol);
        if (symbol == null) {
            log.error("symbol mapping not found for coreSymbolId={}", tradeEvent.symbol);
            return;
        }
        for (Trade trade : tradeEvent.trades) {
            MatchingTradeEvent event = new MatchingTradeEvent();
            event.setCoreSeq(tradeEvent.timestamp);
            event.setCoreSymbolId(tradeEvent.symbol);
            event.setSymbol(symbol.getSymbol());
            event.setTakerAction(tradeEvent.takerAction == null ? null : tradeEvent.takerAction.name());
            event.setMakerCoreOrderId(trade.makerOrderId);
            event.setTakerCoreOrderId(tradeEvent.takerOrderId);
            CoreOrderMapping maker = orderMappingRepository.findByCoreOrderId(trade.makerOrderId);
            CoreOrderMapping taker = orderMappingRepository.findByCoreOrderId(tradeEvent.takerOrderId);
            event.setMakerOrderId(maker == null ? String.valueOf(trade.makerOrderId) : maker.getOrderId());
            event.setTakerOrderId(taker == null ? String.valueOf(tradeEvent.takerOrderId) : taker.getOrderId());
            event.setMakerMemberId(trade.makerUid);
            event.setTakerMemberId(tradeEvent.takerUid);
            event.setPrice(amountConverter.toBusinessPrice(symbol, trade.price));
            event.setAmount(amountConverter.toBusinessAmount(symbol, trade.volume));
            event.setTurnover(amountConverter.toBusinessTurnover(symbol, trade.price, trade.volume));
            event.setMakerFee(BigDecimal.ZERO);
            event.setTakerFee(BigDecimal.ZERO);
            event.setMakerOrderCompleted(trade.makerOrderCompleted);
            event.setTakerOrderCompleted(tradeEvent.takeOrderCompleted);
            event.setTimestamp(tradeEvent.timestamp);
            event.setTradeId(tradeEvent.symbol + "-" + tradeEvent.takerOrderId + "-" + trade.makerOrderId + "-" + tradeEvent.timestamp + "-" + trade.volume);
            kafkaTemplate.send(MatchingTopics.MATCHING_TRADE_EVENT, symbol.getSymbol(), JSON.toJSONString(event));
            if (tradeEvent.takerAction == OrderAction.BID) {
                publishBalanceSnapshot(trade.makerUid, symbol.getBaseSymbol(), symbol.getBaseCurrencyId(), tradeEvent.timestamp);
                publishBalanceSnapshot(tradeEvent.takerUid, symbol.getQuoteSymbol(), symbol.getQuoteCurrencyId(), tradeEvent.timestamp);
            } else {
                publishBalanceSnapshot(trade.makerUid, symbol.getQuoteSymbol(), symbol.getQuoteCurrencyId(), tradeEvent.timestamp);
                publishBalanceSnapshot(tradeEvent.takerUid, symbol.getBaseSymbol(), symbol.getBaseCurrencyId(), tradeEvent.timestamp);
            }
        }
    }

    private void publishBalanceSnapshot(long memberId, String currency, Integer coreCurrencyId, long coreSeq) {
        long available = 0L;
        UserProfile profile = exchangeCoreLifecycle.getUserProfileDirect(memberId);
        if (profile != null && profile.accounts != null && coreCurrencyId != null) {
            available = profile.accounts.get(coreCurrencyId);
        }
        MatchingBalanceEvent event = new MatchingBalanceEvent();
        event.setCoreSeq(coreSeq);
        event.setMemberId(memberId);
        event.setCurrency(currency);
        event.setCoreCurrencyId(coreCurrencyId);
        event.setAvailable(BigDecimal.valueOf(available));
        event.setReserved(BigDecimal.ZERO);
        event.setTotal(BigDecimal.valueOf(available));
        event.setTimestamp(new Date().getTime());
        kafkaTemplate.send(MatchingTopics.MATCHING_BALANCE_EVENT, String.valueOf(memberId), JSON.toJSONString(event));
    }

    @Override
    public void rejectEvent(RejectEvent rejectEvent) {
        kafkaTemplate.send(MatchingTopics.MATCHING_REJECT_EVENT, String.valueOf(rejectEvent.symbol), JSON.toJSONString(rejectEvent));
    }

    @Override
    public void reduceEvent(ReduceEvent reduceEvent) {
        CoreSymbolMapping symbol = symbolMappingRepository.findByCoreSymbolId(reduceEvent.symbol);
        MatchingOrderEvent event = new MatchingOrderEvent();
        event.setCoreSeq(reduceEvent.timestamp);
        event.setCoreSymbolId(reduceEvent.symbol);
        event.setSymbol(symbol == null ? String.valueOf(reduceEvent.symbol) : symbol.getSymbol());
        event.setCoreOrderId(reduceEvent.orderId);
        CoreOrderMapping mapping = orderMappingRepository.findByCoreOrderId(reduceEvent.orderId);
        event.setOrderId(mapping == null ? String.valueOf(reduceEvent.orderId) : mapping.getOrderId());
        event.setMemberId(reduceEvent.uid);
        event.setEventType("REDUCE");
        if (symbol != null) {
            event.setTradedAmount(amountConverter.toBusinessAmount(symbol, reduceEvent.reducedVolume));
            event.setTurnover(amountConverter.toBusinessTurnover(symbol, reduceEvent.price, reduceEvent.reducedVolume));
        }
        event.setCompleted(reduceEvent.orderCompleted);
        event.setTimestamp(reduceEvent.timestamp);
        kafkaTemplate.send(MatchingTopics.MATCHING_ORDER_EVENT, event.getSymbol(), JSON.toJSONString(event));
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        CoreSymbolMapping symbol = symbolMappingRepository.findByCoreSymbolId(orderBook.symbol);
        kafkaTemplate.send(MatchingTopics.MATCHING_ORDERBOOK_EVENT,
                symbol == null ? String.valueOf(orderBook.symbol) : symbol.getSymbol(),
                JSON.toJSONString(orderBook));
    }

    private String eventKey(ApiCommand command) {
        return command == null ? "unknown" : command.getClass().getSimpleName();
    }
}
