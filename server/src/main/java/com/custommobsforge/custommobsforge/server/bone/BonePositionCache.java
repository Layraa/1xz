package com.custommobsforge.custommobsforge.server.bone;

import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш позиций костей на серверной стороне
 * Хранит позиции костей, синхронизированные с клиента
 */
public class BonePositionCache {

    // Хранилище позиций костей: entityId -> boneName -> position
    private static final Map<Integer, Map<String, Vec3>> BONE_POSITIONS = new ConcurrentHashMap<>();

    // Время последнего обновления для каждой энтити
    private static final Map<Integer, Long> LAST_UPDATE_TIME = new ConcurrentHashMap<>();

    // Настройки кэша
    private static final long CACHE_LIFETIME = 1000; // 1 секунда
    private static final long CLEANUP_INTERVAL = 10000; // 10 секунд
    private static long lastCleanup = 0;

    /**
     * Обновляет позицию кости для энтити
     */
    public static void updateBonePosition(int entityId, String boneName, Vec3 position) {
        if (boneName == null || position == null) {
            return;
        }

        Map<String, Vec3> entityBones = BONE_POSITIONS.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());
        entityBones.put(boneName, position);
        LAST_UPDATE_TIME.put(entityId, System.currentTimeMillis());

        LogHelper.debug("[BonePositionCache] Updated bone '{}' for entity {} to position ({}, {}, {})",
                boneName, entityId,
                String.format("%.2f", position.x),
                String.format("%.2f", position.y),
                String.format("%.2f", position.z));

        // Периодическая очистка
        performPeriodicCleanup();
    }

    /**
     * Обновляет множество позиций костей для энтити
     */
    public static void updateBonePositions(int entityId, Map<String, Vec3> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        Map<String, Vec3> entityBones = BONE_POSITIONS.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());
        entityBones.putAll(positions);
        LAST_UPDATE_TIME.put(entityId, System.currentTimeMillis());

        LogHelper.debug("[BonePositionCache] Updated {} bones for entity {}", positions.size(), entityId);

        performPeriodicCleanup();
    }

    /**
     * Получает позицию кости для энтити
     */
    public static Vec3 getBonePosition(int entityId, String boneName) {
        if (boneName == null) {
            return null;
        }

        // Проверяем актуальность данных
        Long lastUpdate = LAST_UPDATE_TIME.get(entityId);
        if (lastUpdate == null || (System.currentTimeMillis() - lastUpdate) > CACHE_LIFETIME) {
            LogHelper.debug("[BonePositionCache] Cache expired for entity {}", entityId);
            return null;
        }

        Map<String, Vec3> entityBones = BONE_POSITIONS.get(entityId);
        if (entityBones == null) {
            return null;
        }

        Vec3 position = entityBones.get(boneName);
        if (position != null) {
            LogHelper.debug("[BonePositionCache] Retrieved cached bone '{}' for entity {} at ({}, {}, {})",
                    boneName, entityId,
                    String.format("%.2f", position.x),
                    String.format("%.2f", position.y),
                    String.format("%.2f", position.z));
        }

        return position;
    }

    /**
     * Получает все позиции костей для энтити
     */
    public static Map<String, Vec3> getAllBonePositions(int entityId) {
        // Проверяем актуальность данных
        Long lastUpdate = LAST_UPDATE_TIME.get(entityId);
        if (lastUpdate == null || (System.currentTimeMillis() - lastUpdate) > CACHE_LIFETIME) {
            return null;
        }

        Map<String, Vec3> entityBones = BONE_POSITIONS.get(entityId);
        return entityBones != null ? new ConcurrentHashMap<>(entityBones) : null;
    }

    /**
     * Проверяет, есть ли данные для энтити
     */
    public static boolean hasDataForEntity(int entityId) {
        Long lastUpdate = LAST_UPDATE_TIME.get(entityId);
        return lastUpdate != null && (System.currentTimeMillis() - lastUpdate) <= CACHE_LIFETIME;
    }

    /**
     * Удаляет все данные для энтити
     */
    public static void removeEntity(int entityId) {
        BONE_POSITIONS.remove(entityId);
        LAST_UPDATE_TIME.remove(entityId);
        LogHelper.debug("[BonePositionCache] Removed all data for entity {}", entityId);
    }

    /**
     * Очищает все данные
     */
    public static void clear() {
        BONE_POSITIONS.clear();
        LAST_UPDATE_TIME.clear();
        LogHelper.info("[BonePositionCache] Cleared all cached bone positions");
    }

    /**
     * Получает статистику кэша
     */
    public static CacheStats getStats() {
        int totalEntities = BONE_POSITIONS.size();
        int totalBones = BONE_POSITIONS.values().stream()
                .mapToInt(Map::size)
                .sum();

        long currentTime = System.currentTimeMillis();
        int activeEntities = (int) LAST_UPDATE_TIME.values().stream()
                .filter(time -> (currentTime - time) <= CACHE_LIFETIME)
                .count();

        return new CacheStats(totalEntities, totalBones, activeEntities);
    }

    /**
     * Выполняет периодическую очистку устаревших данных
     */
    private static void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup < CLEANUP_INTERVAL) {
            return;
        }

        int removedEntities = 0;
        var iterator = LAST_UPDATE_TIME.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_LIFETIME * 2) { // Удаляем данные старше 2 секунд
                int entityId = entry.getKey();
                BONE_POSITIONS.remove(entityId);
                iterator.remove();
                removedEntities++;
            }
        }

        if (removedEntities > 0) {
            LogHelper.debug("[BonePositionCache] Cleaned up {} expired entities", removedEntities);
        }

        lastCleanup = currentTime;
    }

    /**
     * Статистика кэша
     */
    public static class CacheStats {
        public final int totalEntities;
        public final int totalBones;
        public final int activeEntities;

        public CacheStats(int totalEntities, int totalBones, int activeEntities) {
            this.totalEntities = totalEntities;
            this.totalBones = totalBones;
            this.activeEntities = activeEntities;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{entities=%d, bones=%d, active=%d}",
                    totalEntities, totalBones, activeEntities);
        }
    }
}