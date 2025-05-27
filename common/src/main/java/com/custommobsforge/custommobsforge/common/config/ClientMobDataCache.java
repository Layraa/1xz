package com.custommobsforge.custommobsforge.common.config;

import com.custommobsforge.custommobsforge.common.data.MobData;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Кэш данных мобов на клиенте
 */
public class ClientMobDataCache {
    private static final Map<String, MobData> mobDataCache = new ConcurrentHashMap<>();

    /**
     * Кэширует данные моба
     */
    public static void cacheMobData(MobData mobData) {
        if (mobData != null && mobData.getId() != null) {
            mobDataCache.put(mobData.getId(), mobData);
            System.out.println("[ClientMobDataCache] Cached mob data: " + mobData.getId());
        }
    }

    /**
     * Получает данные моба из кэша
     */
    public static MobData getMobData(String mobId) {
        return mobDataCache.get(mobId);
    }

    /**
     * Проверяет наличие данных в кэше
     */
    public static boolean hasMobData(String mobId) {
        return mobDataCache.containsKey(mobId);
    }

    /**
     * Очищает кэш
     */
    public static void clearCache() {
        mobDataCache.clear();
        System.out.println("[ClientMobDataCache] Cache cleared");
    }

    /**
     * Получает все закэшированные данные
     */
    public static Map<String, MobData> getAllCachedData() {
        return new ConcurrentHashMap<>(mobDataCache);
    }
}