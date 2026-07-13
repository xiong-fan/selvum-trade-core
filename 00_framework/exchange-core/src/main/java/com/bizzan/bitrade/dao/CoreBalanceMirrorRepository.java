package com.bizzan.bitrade.dao;

import com.bizzan.bitrade.entity.CoreBalanceMirror;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreBalanceMirrorRepository extends JpaRepository<CoreBalanceMirror, Long> {
    CoreBalanceMirror findByMemberIdAndCurrency(Long memberId, String currency);
}
