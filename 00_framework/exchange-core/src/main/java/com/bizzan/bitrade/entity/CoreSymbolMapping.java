package com.bizzan.bitrade.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@Entity
@Table(name = "exchange_core_symbol_mapping",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "symbol"),
                @UniqueConstraint(columnNames = "core_symbol_id")
        })
public class CoreSymbolMapping implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    @Column(name = "core_symbol_id")
    private Integer coreSymbolId;
    @Column(name = "base_symbol")
    private String baseSymbol;
    @Column(name = "quote_symbol")
    private String quoteSymbol;
    @Column(name = "base_currency_id")
    private Integer baseCurrencyId;
    @Column(name = "quote_currency_id")
    private Integer quoteCurrencyId;
    @Column(name = "base_scale")
    private Integer baseScale;
    @Column(name = "quote_scale")
    private Integer quoteScale;
    @Column(name = "base_scale_k")
    private Long baseScaleK;
    @Column(name = "quote_scale_k")
    private Long quoteScaleK;
    private Long makerFee;
    private Long takerFee;
    private Integer status;
    private Date createdTime;
    private Date updatedTime;
}
