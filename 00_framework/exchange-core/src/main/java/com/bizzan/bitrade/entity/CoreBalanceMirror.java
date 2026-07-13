package com.bizzan.bitrade.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "exchange_core_balance_mirror",
        uniqueConstraints = @UniqueConstraint(columnNames = {"memberId", "currency"}))
public class CoreBalanceMirror implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long memberId;
    private String currency;
    private Integer coreCurrencyId;
    private BigDecimal available;
    private BigDecimal reserved;
    private BigDecimal total;
    private Long coreSeq;
    private Date updatedTime;
}
