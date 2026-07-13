package com.bizzan.bitrade.dao;

import com.bizzan.bitrade.entity.CoreCommandLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreCommandLogRepository extends JpaRepository<CoreCommandLog, Long> {
    CoreCommandLog findByCommandId(String commandId);
}
