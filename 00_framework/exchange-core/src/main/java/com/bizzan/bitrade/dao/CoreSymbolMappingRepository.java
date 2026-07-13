package com.bizzan.bitrade.dao;

import com.bizzan.bitrade.entity.CoreSymbolMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoreSymbolMappingRepository extends JpaRepository<CoreSymbolMapping, Long> {
    CoreSymbolMapping findBySymbol(String symbol);
    CoreSymbolMapping findByCoreSymbolId(Integer coreSymbolId);

    @Query(value = "SELECT base_scale_k FROM exchange_core_symbol_mapping WHERE base_currency_id = :currencyId LIMIT 1", nativeQuery = true)
    Long findBaseScaleKByCurrencyId(@Param("currencyId") Integer currencyId);

    @Query(value = "SELECT quote_scale_k FROM exchange_core_symbol_mapping WHERE quote_currency_id = :currencyId LIMIT 1", nativeQuery = true)
    Long findQuoteScaleKByCurrencyId(@Param("currencyId") Integer currencyId);
}
