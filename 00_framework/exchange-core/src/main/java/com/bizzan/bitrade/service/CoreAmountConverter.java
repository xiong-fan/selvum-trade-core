package com.bizzan.bitrade.service;

import com.bizzan.bitrade.entity.CoreSymbolMapping;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CoreAmountConverter {
    private static int scaleOrDefault(Integer scale) {
        return scale == null ? 8 : scale;
    }

    private static long scaleKOrDefault(Long scaleK) {
        return scaleK == null ? 1L : scaleK;
    }

    public long toCoreSize(CoreSymbolMapping mapping, BigDecimal amount) {
        BigDecimal baseUnit = BigDecimal.TEN.pow(scaleOrDefault(mapping.getBaseScale()));
        long value = amount.multiply(baseUnit)
                .divide(BigDecimal.valueOf(scaleKOrDefault(mapping.getBaseScaleK())), 0, RoundingMode.DOWN)
                .longValue();
        if (value <= 0) {
            throw new IllegalArgumentException("core size must be greater than zero");
        }
        return value;
    }

    public long toCorePrice(CoreSymbolMapping mapping, BigDecimal price) {
        BigDecimal baseUnit = BigDecimal.TEN.pow(scaleOrDefault(mapping.getBaseScale()));
        long value = price.multiply(BigDecimal.valueOf(scaleKOrDefault(mapping.getBaseScaleK())))
                .divide(baseUnit, 0, RoundingMode.DOWN)
                .longValue();
        if (value <= 0) {
            throw new IllegalArgumentException("core price must be greater than zero");
        }
        return value;
    }

    public BigDecimal toBusinessAmount(CoreSymbolMapping mapping, long size) {
        int baseScale = scaleOrDefault(mapping.getBaseScale());
        BigDecimal baseUnit = BigDecimal.TEN.pow(baseScale);
        return BigDecimal.valueOf(size)
                .multiply(BigDecimal.valueOf(scaleKOrDefault(mapping.getBaseScaleK())))
                .divide(baseUnit, baseScale, RoundingMode.DOWN);
    }

    public BigDecimal toBusinessPrice(CoreSymbolMapping mapping, long price) {
        int baseScale = scaleOrDefault(mapping.getBaseScale());
        BigDecimal baseUnit = BigDecimal.TEN.pow(baseScale);
        return BigDecimal.valueOf(price)
                .multiply(baseUnit)
                .divide(BigDecimal.valueOf(scaleKOrDefault(mapping.getBaseScaleK())), baseScale, RoundingMode.DOWN);
    }

    public BigDecimal toBusinessTurnover(CoreSymbolMapping mapping, long price, long size) {
        return toBusinessPrice(mapping, price).multiply(toBusinessAmount(mapping, size));
    }
}
