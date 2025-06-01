package com.custommobsforge.custommobsforge.server.util;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.BoneColliderManager;

/**
 * Хелпер для работы с костями сущностей на сервере
 */
public class EntityBoneHelper {

    /**
     * Безопасно получает или создает BoneColliderManager для сущности
     */
    public static BoneColliderManager getOrCreateBoneColliderManager(CustomMobEntity entity) {
        Object manager = entity.getServerBoneColliderManager();

        if (manager instanceof BoneColliderManager) {
            return (BoneColliderManager) manager;
        }

        // Создаем новый менеджер
        BoneColliderManager newManager = new BoneColliderManager(entity);
        entity.setServerBoneColliderManager(newManager);

        LogHelper.info("[EntityBoneHelper] Created new BoneColliderManager for entity {}", entity.getId());
        return newManager;
    }

    /**
     * Проверяет, есть ли у сущности активные коллайдеры костей
     */
    public static boolean hasActiveColliders(CustomMobEntity entity) {
        Object manager = entity.getServerBoneColliderManager();

        if (manager instanceof BoneColliderManager) {
            return ((BoneColliderManager) manager).hasActiveColliders();
        }

        return false;
    }

    /**
     * Очищает все коллайдеры у сущности
     */
    public static void cleanupColliders(CustomMobEntity entity) {
        Object manager = entity.getServerBoneColliderManager();

        if (manager instanceof BoneColliderManager) {
            ((BoneColliderManager) manager).cleanup();
            LogHelper.info("[EntityBoneHelper] Cleaned up colliders for entity {}", entity.getId());
        }
    }
}