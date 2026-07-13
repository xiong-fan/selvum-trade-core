package com.bizzan.bitrade.dao;

import com.bizzan.bitrade.entity.MemberTradingTransferLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberTradingTransferLogRepository extends JpaRepository<MemberTradingTransferLog, Long> {
    MemberTradingTransferLog findByCommandId(String commandId);
}
