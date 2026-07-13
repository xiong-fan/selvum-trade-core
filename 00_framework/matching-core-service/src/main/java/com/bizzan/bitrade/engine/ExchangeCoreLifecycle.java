package com.bizzan.bitrade.engine;

import com.bizzan.bitrade.dao.CoreSymbolMappingRepository;
import com.bizzan.bitrade.entity.CoreSymbolMapping;
import com.bizzan.bitrade.entity.ExchangeOrderDirection;
import com.bizzan.bitrade.entity.ExchangeOrderType;
import com.bizzan.bitrade.matching.constant.MatchingCommandType;
import com.bizzan.bitrade.matching.dto.MatchingCommand;
import com.bizzan.bitrade.service.CoreAmountConverter;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.ExchangeCore;
import exchange.core2.core.SimpleEventsProcessor;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.L2MarketData;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.SymbolType;
import exchange.core2.core.common.UserProfile;
import exchange.core2.core.common.IOrder;
import exchange.core2.core.orderbook.IOrderBook;
import exchange.core2.core.processors.MatchingEngineRouter;
import exchange.core2.core.common.api.ApiAddUser;
import exchange.core2.core.common.api.ApiAdjustUserBalance;
import exchange.core2.core.common.api.ApiCancelOrder;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPersistState;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.api.binary.BatchAddSymbolsCommand;
import exchange.core2.core.common.api.reports.SingleUserReportQuery;
import exchange.core2.core.common.api.reports.SingleUserReportResult;
import exchange.core2.core.common.config.ExchangeConfiguration;
import exchange.core2.core.common.config.InitialStateConfiguration;
import exchange.core2.core.common.config.LoggingConfiguration;
import exchange.core2.core.common.config.OrdersProcessingConfiguration;
import exchange.core2.core.common.config.PerformanceConfiguration;
import exchange.core2.core.common.config.ReportsQueriesConfiguration;
import exchange.core2.core.common.config.SerializationConfiguration;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.processors.RiskEngine;
import exchange.core2.core.processors.SymbolSpecificationProvider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

@Slf4j
@Component
public class ExchangeCoreLifecycle {
    private volatile boolean started;
    private ExchangeCore exchangeCore;
    private ExchangeApi api;

    @Autowired
    private CoreEventHandler coreEventHandler;
    @Autowired
    private CoreSymbolMappingRepository symbolMappingRepository;
    @Autowired
    private CoreAmountConverter amountConverter;
    @Value("${matching.core.journal-enabled:true}")
    private boolean journalEnabled;

    @PostConstruct
    public void startup() {
        SimpleEventsProcessor eventsProcessor = new SimpleEventsProcessor(coreEventHandler);
        SerializationConfiguration serializationCfg = journalEnabled
                ? SerializationConfiguration.DISK_JOURNALING
                : SerializationConfiguration.DEFAULT;
        InitialStateConfiguration initStateCfg;
        if (journalEnabled) {
            // 从 journal 重放历史状态，恢复之前持久化的 UserProfile、订单等数据
            initStateCfg = InitialStateConfiguration.lastKnownStateFromJournal("MY_EXCHANGE", 0, 0);
        } else {
            initStateCfg = InitialStateConfiguration.cleanStart("MY_EXCHANGE");
        }
        ExchangeConfiguration conf = ExchangeConfiguration.builder()
                .serializationCfg(serializationCfg)
                .initStateCfg(initStateCfg)
                .ordersProcessingCfg(OrdersProcessingConfiguration.DEFAULT)
                .performanceCfg(PerformanceConfiguration.DEFAULT)
                .reportsQueriesCfg(ReportsQueriesConfiguration.DEFAULT)
                .loggingCfg(LoggingConfiguration.DEFAULT)
                .build();
        exchangeCore = ExchangeCore.builder()
                .resultsConsumer(eventsProcessor)
                .exchangeConfiguration(conf)
                .build();
        exchangeCore.startup();
        api = exchangeCore.getApi();
        started = true;
        log.info("exchange-core2 engine started, journaling={}", journalEnabled);
    }

    @PreDestroy
    public void shutdown() {
        if (exchangeCore != null) {
            exchangeCore.shutdown();
        }
        started = false;
        log.info("matching-core-service stopped");
    }

    public boolean isStarted() {
        return started;
    }

    public OrderCommand submit(MatchingCommand command) {
        if (!started) {
            throw new IllegalStateException("matching core is not started");
        }
        try {
            Object coreCommand = toCoreCommand(command);
            Future<?> future;
            if (coreCommand instanceof BatchAddSymbolsCommand) {
                future = api.submitBinaryDataAsync((BatchAddSymbolsCommand) coreCommand);
            } else {
                future = api.submitCommandAsyncFullResponse((ApiCommand) coreCommand);
            }
            Object result = future.get();
            log.info("exchange-core2 command submitted: commandId={}, type={}, result={}",
                    command.getCommandId(), command.getCommandType(), result);
            return result instanceof OrderCommand ? (OrderCommand) result : null;
        } catch (Exception e) {
            throw new IllegalStateException("exchange-core2 command failed: " + command.getCommandId(), e);
        }
    }

    public void persistState(long dumpId) {
        try {
            api.submitCommandAsync(ApiPersistState.builder().dumpId(dumpId).build()).get();
        } catch (Exception e) {
            throw new IllegalStateException("persist exchange-core2 state failed", e);
        }
    }

    public SingleUserReportResult userReport(long memberId) {
        try {
            return api.processReport(new SingleUserReportQuery(memberId), 0).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("exchange-core2 user report timeout for memberId={}", memberId);
            return SingleUserReportResult.createFromRiskEngineNotFound(memberId);
        } catch (Exception e) {
            throw new IllegalStateException("query exchange-core2 user report failed: " + memberId, e);
        }
    }

    /**
     * 直接查询 UserProfile，不走 Disruptor 环形队列，零等待。
     * 从 RiskEngine 的 UserProfileService 内存 HashMap 直接读取。
     *
     * @param memberId 用户 ID
     * @return UserProfile（可能为 null 表示不存在或未找到对应 shard）
     */
    public UserProfile getUserProfileDirect(long memberId) {
        if (!started || exchangeCore == null) {
            return null;
        }
        for (RiskEngine riskEngine : exchangeCore.getRiskEngines().values()) {
            if (riskEngine.uidForThisHandler(memberId)) {
                return riskEngine.getUserProfileService().getUserProfile(memberId);
            }
        }
        return null;
    }

    /**
     * 直接查询交易对是否已在撮合引擎中注册。
     * 从 RiskEngine 的 SymbolSpecificationProvider 内存 HashMap 直接读取，不走 Disruptor。
     *
     * @param coreSymbolId 交易对 ID
     * @return true 表示已注册
     */
    public boolean isSymbolRegistered(int coreSymbolId) {
        if (!started || exchangeCore == null) {
            return false;
        }
        for (RiskEngine riskEngine : exchangeCore.getRiskEngines().values()) {
            if (riskEngine.getSymbolSpecificationProvider().getSymbolSpecification(coreSymbolId) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取已在撮合引擎中注册的所有交易对。
     * 从第一组 RiskEngine 的 SymbolSpecificationProvider 读取，不走 Disruptor。
     * 注意：所有 shard 的 SymbolSpecificationProvider 数据一致（符号注册时全 shard 同步）。
     *
     * @return coreSymbolId → CoreSymbolSpecification 映射，可能为空 Map
     */
    public IntObjectHashMap<CoreSymbolSpecification> getAllSymbolSpecifications() {
        if (!started || exchangeCore == null) {
            return new IntObjectHashMap<>();
        }
        for (RiskEngine riskEngine : exchangeCore.getRiskEngines().values()) {
            SymbolSpecificationProvider provider = riskEngine.getSymbolSpecificationProvider();
            IntObjectHashMap<CoreSymbolSpecification> specs = provider.getSymbolSpecs();
            if (!specs.isEmpty()) {
                return specs;
            }
        }
        return new IntObjectHashMap<>();
    }

    /**
     * 直接查询订单簿 L2 快照，不走 Disruptor。
     *
     * @param coreSymbolId 交易对 ID
     * @return L2MarketData（包含买卖盘价格、数量、订单数），未找到返回 null
     */
    public L2MarketData getOrderBookSnapshot(int coreSymbolId) {
        if (!started || exchangeCore == null) {
            return null;
        }
        for (MatchingEngineRouter me : exchangeCore.getMatchingEngines().values()) {
            IOrderBook ob = me.getOrderBooks().get(coreSymbolId);
            if (ob != null) {
                return ob.getL2MarketDataSnapshot();
            }
        }
        return null;
    }

    /**
     * 获取订单簿中所有挂单明细（含 uid、orderId 等），不走 Disruptor。
     */
    public List<IOrder> getOrderBookOrders(int coreSymbolId) {
        if (!started || exchangeCore == null) {
            return java.util.Collections.emptyList();
        }
        for (MatchingEngineRouter me : exchangeCore.getMatchingEngines().values()) {
            IOrderBook ob = me.getOrderBooks().get(coreSymbolId);
            if (ob != null) {
                List<IOrder> orders = new ArrayList<>();
                ob.askOrdersStream(false).forEach(orders::add);
                ob.bidOrdersStream(false).forEach(orders::add);
                return orders;
            }
        }
        return java.util.Collections.emptyList();
    }

    private Object toCoreCommand(MatchingCommand command) {
        MatchingCommandType type = command.getCommandType();
        if (type == MatchingCommandType.ADD_USER) {
            return ApiAddUser.builder().uid(command.getMemberId()).build();
        }
        if (type == MatchingCommandType.ADD_SYMBOL) {
            CoreSymbolMapping mapping = requiredSymbolMapping(command);
            CoreSymbolSpecification spec = CoreSymbolSpecification.builder()
                    .symbolId(mapping.getCoreSymbolId())
                    .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                    .baseCurrency(mapping.getBaseCurrencyId() == null ? 0 : mapping.getBaseCurrencyId())
                    .quoteCurrency(mapping.getQuoteCurrencyId() == null ? 0 : mapping.getQuoteCurrencyId())
                    .baseScaleK(mapping.getBaseScaleK() == null ? 1L : mapping.getBaseScaleK())
                    .quoteScaleK(mapping.getQuoteScaleK() == null ? 1L : mapping.getQuoteScaleK())
                    .makerFee(mapping.getMakerFee() == null ? 0L : mapping.getMakerFee())
                    .takerFee(mapping.getTakerFee() == null ? 0L : mapping.getTakerFee())
                    .build();
            return new BatchAddSymbolsCommand(spec);
        }
        if (type == MatchingCommandType.DEPOSIT_TRADING || type == MatchingCommandType.WITHDRAW_TRADING) {
            if (command.getCoreCurrencyId() == null) {
                throw new IllegalArgumentException("coreCurrencyId is required for funding command");
            }
            if (command.getFunds() == null) {
                throw new IllegalArgumentException("funds is required for funding command");
            }
            long amount = command.getFunds().longValue();
            if (type == MatchingCommandType.WITHDRAW_TRADING && amount > 0) {
                amount = -amount;
            }
            return ApiAdjustUserBalance.builder()
                    .uid(command.getMemberId())
                    .currency(command.getCoreCurrencyId())
                    .amount(amount)
                    .transactionId(command.getCoreTransactionId() == null ? System.currentTimeMillis() : command.getCoreTransactionId())
                    .build();
        }
        if (type == MatchingCommandType.CANCEL_ORDER) {
            CoreSymbolMapping mapping = requiredSymbolMapping(command);
            return ApiCancelOrder.builder()
                    .uid(command.getMemberId())
                    .orderId(command.getCoreOrderId())
                    .symbol(mapping.getCoreSymbolId())
                    .build();
        }
        if (type == MatchingCommandType.PLACE_ORDER) {
            CoreSymbolMapping mapping = requiredSymbolMapping(command);
            long corePrice = amountConverter.toCorePrice(mapping, command.getPrice());
            return ApiPlaceOrder.builder()
                    .uid(command.getMemberId())
                    .orderId(command.getCoreOrderId())
                    .price(corePrice)
                    .reservePrice(corePrice)
                    .size(amountConverter.toCoreSize(mapping, command.getAmount()))
                    .action(command.getDirection() == ExchangeOrderDirection.BUY ? OrderAction.BID : OrderAction.ASK)
                    .orderType(command.getOrderType() == ExchangeOrderType.MARKET_PRICE ? OrderType.IOC : OrderType.GTC)
                    .symbol(mapping.getCoreSymbolId())
                    .build();
        }
        if (type == MatchingCommandType.PERSIST_STATE) {
            return ApiPersistState.builder().dumpId(System.currentTimeMillis()).build();
        }
        throw new IllegalArgumentException("unsupported matching command type: " + type);
    }

    private CoreSymbolMapping requiredSymbolMapping(MatchingCommand command) {
        CoreSymbolMapping mapping = command.getSymbol() == null ? null : symbolMappingRepository.findBySymbol(command.getSymbol());
        if (mapping == null && command.getCoreSymbolId() != null) {
            mapping = symbolMappingRepository.findByCoreSymbolId(command.getCoreSymbolId());
        }
        if (mapping == null) {
            throw new IllegalArgumentException("exchange_core_symbol_mapping not found for symbol=" + command.getSymbol());
        }
        return mapping;
    }
}
