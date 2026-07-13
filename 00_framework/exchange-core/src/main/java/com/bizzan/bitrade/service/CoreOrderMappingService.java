package com.bizzan.bitrade.service;

import com.bizzan.bitrade.dao.CoreOrderMappingRepository;
import com.bizzan.bitrade.dao.CoreSymbolMappingRepository;
import com.bizzan.bitrade.entity.CoreOrderMapping;
import com.bizzan.bitrade.entity.CoreSymbolMapping;
import com.bizzan.bitrade.entity.ExchangeOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class CoreOrderMappingService {
    @Autowired
    private CoreOrderMappingRepository repository;
    @Autowired
    private CoreSymbolMappingRepository symbolMappingRepository;

    @Transactional
    public CoreOrderMapping createIfAbsent(ExchangeOrder order) {
        CoreOrderMapping existed = repository.findByOrderId(order.getOrderId());
        if (existed != null) {
            return existed;
        }
        CoreOrderMapping mapping = new CoreOrderMapping();
        mapping.setOrderId(order.getOrderId());
        mapping.setCoreOrderId(nextCoreOrderId());
        mapping.setMemberId(order.getMemberId());
        mapping.setSymbol(order.getSymbol());
        CoreSymbolMapping symbolMapping = symbolMappingRepository.findBySymbol(order.getSymbol());
        if (symbolMapping != null) {
            mapping.setCoreSymbolId(symbolMapping.getCoreSymbolId());
        }
        mapping.setDirection(order.getDirection());
        mapping.setType(order.getType());
        mapping.setStatus(order.getStatus());
        mapping.setCreatedTime(new Date());
        mapping.setUpdatedTime(new Date());
        return repository.saveAndFlush(mapping);
    }

    public CoreOrderMapping findByOrderId(String orderId) {
        return repository.findByOrderId(orderId);
    }

    private long nextCoreOrderId() {
        long millis = System.currentTimeMillis();
        long tail = Math.abs(System.nanoTime() % 1000000L);
        return millis * 1000000L + tail;
    }
}
