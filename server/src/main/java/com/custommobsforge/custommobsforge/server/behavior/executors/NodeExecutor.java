package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Интерфейс для исполнителей узлов дерева поведения (SERVER SIDE ONLY)
 */
public interface NodeExecutor {

    /**
     * Выполняет узел и возвращает статус
     */
    BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor);

    /**
     * Универсальный парсер параметров узла
     */
    default <T> T getParameter(BehaviorNode node, String key, T defaultValue, Class<T> type) {
        // Сначала пробуем customParameters
        Object value = node.getCustomParameter(key);

        if (value == null && node.getParameter() != null) {
            // Fallback к parameter строке
            value = parseFromParameterString(node.getParameter(), key);
        }

        if (value == null) {
            return defaultValue;
        }

        // Конвертируем тип
        return convertValue(value, defaultValue, type);
    }

    /**
     * Парсит параметр из строки parameter
     */
    default String parseFromParameterString(String parameterString, String key) {
        if (parameterString == null || parameterString.isEmpty()) {
            return null;
        }

        // Формат: "key1=value1;key2=value2"
        String[] pairs = parameterString.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].trim().equals(key)) {
                return keyValue[1].trim();
            }
        }

        return null;
    }

    /**
     * Конвертирует значение в нужный тип
     */
    @SuppressWarnings("unchecked")
    default <T> T convertValue(Object value, T defaultValue, Class<T> type) {
        if (value == null) {
            return defaultValue;
        }

        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        String stringValue = value.toString();

        try {
            if (type == String.class) {
                return (T) stringValue;
            } else if (type == Double.class || type == double.class) {
                return (T) Double.valueOf(stringValue);
            } else if (type == Float.class || type == float.class) {
                return (T) Float.valueOf(stringValue);
            } else if (type == Integer.class || type == int.class) {
                return (T) Integer.valueOf(stringValue);
            } else if (type == Boolean.class || type == boolean.class) {
                return (T) Boolean.valueOf(stringValue);
            } else if (type == Long.class || type == long.class) {
                return (T) Long.valueOf(stringValue);
            }
        } catch (NumberFormatException | ClassCastException e) {
            // Если конвертация не удалась, возвращаем значение по умолчанию
        }

        return defaultValue;
    }
}