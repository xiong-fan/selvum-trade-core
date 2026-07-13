package com.bizzan.bitrade.ext;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 字符串映射成枚举，同时支持序数（"0"/"1"）和枚举名（"SELL"/"BUY"）。
 *
 * @author GS
 * @date 2017年12月12日
 */
public class OrdinalToEnumConverterFactory implements ConverterFactory<String, Enum<?>> {

    @Override
    public <T extends Enum<?>> Converter<String, T> getConverter(Class<T> targetType) {
        return new OrdinalToEnum<>(targetType);
    }

    private class OrdinalToEnum<T extends Enum<?>> implements Converter<String, T> {

        private Map<String, T> enumMap = new HashMap<>();

        public OrdinalToEnum(Class<T> enumType) {
            T[] enums = enumType.getEnumConstants();
            for (T e : enums) {
                // 同时支持序数和名称
                enumMap.put(String.valueOf(e.ordinal()), e);
                enumMap.put(e.name(), e);
            }
        }

        @Override
        public T convert(String source) {
            T result = enumMap.get(source);
            if (result == null) {
                // 兼容大小写不敏感
                result = enumMap.get(source.toUpperCase());
            }
            if (result == null) {
                throw new IllegalArgumentException("No element matches " + source);
            }
            return result;
        }
    }
}
