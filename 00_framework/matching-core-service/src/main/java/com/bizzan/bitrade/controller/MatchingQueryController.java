package com.bizzan.bitrade.controller;

import com.bizzan.bitrade.engine.ExchangeCoreLifecycle;
import com.bizzan.bitrade.entity.CoreOrderMapping;
import com.bizzan.bitrade.service.CoreOrderMappingService;
import com.bizzan.bitrade.util.MessageResult;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.IOrder;
import exchange.core2.core.common.L2MarketData;
import exchange.core2.core.common.UserProfile;
import exchange.core2.core.common.api.reports.SingleUserReportResult;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/matching/query")
public class MatchingQueryController {
    @Autowired
    private CoreOrderMappingService coreOrderMappingService;
    @Autowired
    private ExchangeCoreLifecycle exchangeCoreLifecycle;

    @GetMapping("/order/{orderId}")
    public MessageResult order(@PathVariable String orderId) {
        CoreOrderMapping mapping = coreOrderMappingService.findByOrderId(orderId);
        if (mapping == null) {
            return MessageResult.error("order mapping not found");
        }
        MessageResult result = MessageResult.success("success");
        result.setData(mapping);
        return result;
    }

    /**
     * 直接查询撮合引擎内存中的用户余额，不依赖 MySQL mirror。
     * MySQL mirror 写失败时兜底使用。
     */
    @GetMapping("/balance/{memberId}")
    public MessageResult balance(@PathVariable Long memberId) {
        SingleUserReportResult report = exchangeCoreLifecycle.userReport(memberId);
        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("userStatus", report.getUserStatus());
        Map<Integer, Long> accounts = new HashMap<>();
        if (report.getAccounts() != null) {
            report.getAccounts().forEachKeyValue((currencyId, balance) ->
                accounts.put(currencyId, balance));
        }
        data.put("accounts", accounts);
        data.put("queryStatus", report.getQueryExecutionStatus());
        MessageResult result = MessageResult.success("success");
        result.setData(data);
        return result;
    }

    /**
     * 查询撮合引擎中 UserProfile 完整信息（含持仓明细）。
     * 直接从 RiskEngine 内存 HashMap 读取，不走 Disruptor 环形队列，零等待。
     * 用于测试验证，可直接 curl 调用。
     */
    @GetMapping("/user-profile/{memberId}")
    public MessageResult userProfile(@PathVariable Long memberId) {
        UserProfile profile = exchangeCoreLifecycle.getUserProfileDirect(memberId);
        if (profile == null) {
            return MessageResult.error(404, "user not found or engine not started: " + memberId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", profile.uid);
        data.put("userStatus", profile.userStatus != null ? profile.userStatus.name() : null);
        data.put("adjustmentsCounter", profile.adjustmentsCounter);

        // 账户余额
        Map<Integer, Long> accounts = new HashMap<>();
        profile.accounts.forEachKeyValue((currencyId, balance) ->
            accounts.put(currencyId, balance));
        data.put("accounts", accounts);

        // 持仓明细
        if (!profile.positions.isEmpty()) {
            Map<Integer, Map<String, Object>> positions = new HashMap<>();
            profile.positions.forEachKeyValue((symbolId, pos) -> {
                Map<String, Object> posData = new HashMap<>();
                posData.put("symbol", symbolId);
                posData.put("currency", pos.currency);
                posData.put("direction", pos.direction != null ? pos.direction.name() : null);
                posData.put("openVolume", pos.openVolume);
                posData.put("openPriceSum", pos.openPriceSum);
                posData.put("profit", pos.profit);
                posData.put("pendingSellSize", pos.pendingSellSize);
                posData.put("pendingBuySize", pos.pendingBuySize);
                positions.put(symbolId, posData);
            });
            data.put("positions", positions);
        }

        MessageResult result = MessageResult.success("success");
        result.setData(data);
        return result;
    }

    /**
     * 查询交易对是否已在撮合引擎中注册。
     * 直接从 RiskEngine 内存 HashMap 读取，不走 Disruptor。
     */
    @GetMapping("/symbol/{coreSymbolId}")
    public MessageResult symbol(@PathVariable Integer coreSymbolId) {
        if (!exchangeCoreLifecycle.isStarted()) {
            return MessageResult.error(503, "engine not started");
        }
        boolean registered = exchangeCoreLifecycle.isSymbolRegistered(coreSymbolId);
        Map<String, Object> data = new HashMap<>();
        data.put("coreSymbolId", coreSymbolId);
        data.put("registered", registered);
        MessageResult result = MessageResult.success("success");
        result.setData(data);
        return result;
    }

    /**
     * 列出撮合引擎中所有已注册的交易对。
     * 直接从 RiskEngine 内存 HashMap 读取，不走 Disruptor。
     */
    @GetMapping("/symbols")
    public MessageResult symbols() {
        if (!exchangeCoreLifecycle.isStarted()) {
            return MessageResult.error(503, "engine not started");
        }
        IntObjectHashMap<CoreSymbolSpecification> specs = exchangeCoreLifecycle.getAllSymbolSpecifications();
        Map<String, Object> data = new HashMap<>();
        data.put("count", specs.size());
        Map<Integer, Map<String, Object>> symbolList = new HashMap<>();
        specs.forEachKeyValue((symbolId, spec) -> {
            Map<String, Object> specData = new HashMap<>();
            specData.put("symbolId", spec.symbolId);
            specData.put("type", spec.type.name());
            specData.put("baseCurrency", spec.baseCurrency);
            specData.put("quoteCurrency", spec.quoteCurrency);
            specData.put("baseScaleK", spec.baseScaleK);
            specData.put("quoteScaleK", spec.quoteScaleK);
            specData.put("takerFee", spec.takerFee);
            specData.put("makerFee", spec.makerFee);
            symbolList.put(symbolId, specData);
        });
        data.put("symbols", symbolList);
        MessageResult result = MessageResult.success("success");
        result.setData(data);
        return result;
    }

    /**
     * 查询订单簿 L2 快照（买卖盘口）。
     * 直接从 MatchingEngine 内存读取，不走 Disruptor。
     */
    @GetMapping("/orderbook/{coreSymbolId}")
    public MessageResult orderbook(@PathVariable Integer coreSymbolId) {
        if (!exchangeCoreLifecycle.isStarted()) {
            return MessageResult.error(503, "engine not started");
        }
        L2MarketData l2 = exchangeCoreLifecycle.getOrderBookSnapshot(coreSymbolId);
        if (l2 == null) {
            return MessageResult.error(404, "order book not found for coreSymbolId=" + coreSymbolId);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("coreSymbolId", coreSymbolId);
        data.put("timestamp", l2.timestamp);
        // 卖盘（asks）
        Map<Integer, Map<String, Object>> asks = new HashMap<>();
        for (int i = 0; i < l2.askSize; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("price", l2.askPrices[i]);
            row.put("volume", l2.askVolumes[i]);
            row.put("orders", l2.askOrders[i]);
            asks.put(i, row);
        }
        data.put("asks", asks);
        // 买盘（bids）
        Map<Integer, Map<String, Object>> bids = new HashMap<>();
        for (int i = 0; i < l2.bidSize; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("price", l2.bidPrices[i]);
            row.put("volume", l2.bidVolumes[i]);
            row.put("orders", l2.bidOrders[i]);
            bids.put(i, row);
        }
        data.put("bids", bids);
        MessageResult result = MessageResult.success("success");
        result.setData(data);
        return result;
    }

    /**
     * 查询订单簿中所有挂单明细（含 uid、orderId、买卖方向、价格、数量、已成交量）。
     * 直接从 MatchingEngine 内存读取，不走 Disruptor。
     */
    @GetMapping("/orderbook/{coreSymbolId}/orders")
    public MessageResult orderbookOrders(@PathVariable Integer coreSymbolId) {
        if (!exchangeCoreLifecycle.isStarted()) {
            return MessageResult.error(503, "engine not started");
        }
        java.util.List<IOrder> orders = exchangeCoreLifecycle.getOrderBookOrders(coreSymbolId);
        if (orders.isEmpty()) {
            return MessageResult.error(404, "order book not found for coreSymbolId=" + coreSymbolId);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("coreSymbolId", coreSymbolId);
        data.put("totalOrders", orders.size());
        java.util.List<Map<String, Object>> orderList = new java.util.ArrayList<>();
        for (IOrder o : orders) {
            Map<String, Object> row = new HashMap<>();
            row.put("orderId", o.getOrderId());
            row.put("uid", o.getUid());
            row.put("action", o.getAction() != null ? o.getAction().name() : null);
            row.put("price", o.getPrice());
            row.put("size", o.getSize());
            row.put("filled", o.getFilled());
            row.put("reserveBidPrice", o.getReserveBidPrice());
            row.put("timestamp", o.getTimestamp());
            orderList.add(row);
        }
        data.put("orders", orderList);
        MessageResult result = MessageResult.success("success");
        result.setData(data);
        return result;
    }
}
