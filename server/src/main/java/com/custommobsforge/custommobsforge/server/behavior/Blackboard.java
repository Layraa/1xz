package com.custommobsforge.custommobsforge.server.behavior;

import com.custommobsforge.custommobsforge.server.util.LogHelper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Хранилище данных для выполнения дерева поведения
 */
public class Blackboard {
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    /**
     * Устанавливает значение
     */
    public void setValue(String key, Object value) {
        data.put(key, value);
        LogHelper.info("[Blackboard] Set '{}' = {}", key, value);
    }

    /**
     * Получает значение с типизацией
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String key, T defaultValue) {
        Object value = data.get(key);
        LogHelper.info("[Blackboard] Get '{}' = {} (default: {})", key, value, defaultValue);

        if (value == null) {
            return defaultValue;
        }

        // Если defaultValue null, просто возвращаем значение как есть
        if (defaultValue == null) {
            try {
                return (T) value;
            } catch (ClassCastException e) {
                LogHelper.error("[Blackboard] Cast error for key '{}': {} to {}",
                        key, value.getClass(), "unknown type");
                return null;
            }
        }

        // Проверяем совместимость типов
        if (defaultValue.getClass().isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        LogHelper.warn("[Blackboard] Type mismatch for key '{}': expected {}, got {}",
                key, defaultValue.getClass(), value.getClass());
        return defaultValue;
    }

    /**
     * Получает значение как строку
     */
    public String getStringValue(String key, String defaultValue) {
        Object value = data.get(key);
        LogHelper.debug("[Blackboard] GetString '{}' = {} (default: {})", key, value, defaultValue);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Получает значение как число
     */
    public double getDoubleValue(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Получает значение как boolean
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * Удаляет значение
     */
    public void removeValue(String key) {
        data.remove(key);
        LogHelper.info("[Blackboard] Remove '{}'", key);
    }

    /**
     * Очищает все данные
     */
    public void clear() {
        data.clear();
        LogHelper.info("[Blackboard] Cleared all data");
    }

    /**
     * Проверяет наличие ключа
     */
    public boolean hasValue(String key) {
        return data.containsKey(key);
    }

    /**
     * Получает значение как long
     */
    public long getLongValue(String key, long defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Получает значение как int
     */
    public int getIntValue(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
