package com.bizzan.bitrade.dao;

import com.bizzan.bitrade.entity.CoreTradeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreTradeLogRepository extends JpaRepository<CoreTradeLog, Long> {
    CoreTradeLog findByTradeId(String tradeId);
    CoreTradeLog findByCoreSeqAndEventType(Long coreSeq, String eventType);
}
