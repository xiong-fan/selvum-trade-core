package com.bizzan.bitrade.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@Entity
@Table(name = "exchange_core_order_mapping",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "order_id"),
                @UniqueConstraint(columnNames = "core_order_id")
        })
public class CoreOrderMapping implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id")
    private String orderId;
    @Column(name = "core_order_id")
    private Long coreOrderId;
    @Column(name = "member_id")
    private Long memberId;
    private String symbol;
    @Column(name = "core_symbol_id")
    private Integer coreSymbolId;
    private ExchangeOrderDirection direction;
    private ExchangeOrderType type;
    private ExchangeOrderStatus status;
    private Date createdTime;
    private Date updatedTime;
}
