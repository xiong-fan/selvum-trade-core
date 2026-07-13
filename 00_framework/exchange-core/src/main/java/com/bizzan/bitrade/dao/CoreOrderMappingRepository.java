package com.bizzan.bitrade.dao;

import com.bizzan.bitrade.entity.CoreOrderMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreOrderMappingRepository extends JpaRepository<CoreOrderMapping, Long> {
    CoreOrderMapping findByOrderId(String orderId);
    CoreOrderMapping findByCoreOrderId(Long coreOrderId);
}
