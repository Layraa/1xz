package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.behavior.EnhancedBoneColliderManager;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Исполнитель узла атаки для Enhanced системы коллайдеров
 */
public class AttackNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        if (!executor.getBlackboard().hasValue(nodeId + ":started")) {
            return startEnhancedAttack(entity, node, executor);
        }

        return updateEnhancedAttack(entity, node, executor);
    }

    private BehaviorTreeExecutor.NodeStatus startEnhancedAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] === STARTING ENHANCED ATTACK ===");

        executor.getBlackboard().setValue(nodeId + ":started", true);

        // Получаем параметры атаки
        String animationName = getAnimationId(node);
        float duration = getParameter(node, "duration", 2.0f, Float.class);
        String bonesParam = getParameter(node, "bones", "greatsword", String.class);
        float damage = getParameter(node, "damage", 5.0f, Float.class);

        // Новые параметры для разных типов коллайдеров
        String colliderType = getParameter(node, "collider_type", "auto", String.class); // auto, sphere, capsule, box
        double primarySize = getParameter(node, "primary_size", 2.0, Double.class); // радиус или ширина
        double secondarySize = getParameter(node, "secondary_size", 3.0, Double.class); // длина или высота
        String direction = getParameter(node, "direction", "0,0,-1", String.class); // направление для капсулы

        LogHelper.info("[AttackNode] Animation: '{}', Duration: {}s, Damage: {}, Type: {}, Size: {}x{}",
                animationName, duration, damage, colliderType, primarySize, secondarySize);

        // Парсим кости
        List<String> boneNames = parseBoneNames(bonesParam);
        LogHelper.info("[AttackNode] Attack bones: {}", boneNames);

        if (boneNames.isEmpty()) {
            LogHelper.error("[AttackNode] No bones specified!");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Запускаем анимацию
        entity.setAnimation(animationName, false, 1.0f);

        // Инициализируем систему атак
        entity.forceInitializeAttackSystem();
        entity.startAttack();

        // Получаем улучшенный менеджер коллайдеров
        EnhancedBoneColliderManager colliderManager = getEnhancedBoneColliderManager(entity);
        if (colliderManager == null) {
            LogHelper.error("[AttackNode] Could not get EnhancedBoneColliderManager!");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Парсим направление
        Vec3 directionVec = parseDirection(direction);

        // Активируем коллайдеры для всех указанных костей
        for (String boneName : boneNames) {
            activateColliderForBone(colliderManager, boneName, damage, colliderType,
                    primarySize, secondarySize, directionVec);
        }

        // Включаем визуализацию для отладки
        colliderManager.setDebugVisualization(true);

        // Сохраняем данные атаки
        executor.getBlackboard().setValue(nodeId + ":start_time", System.currentTimeMillis());
        executor.getBlackboard().setValue(nodeId + ":duration", (long)(duration * 1000));
        executor.getBlackboard().setValue(nodeId + ":bones", boneNames);

        LogHelper.info("[AttackNode] Enhanced attack started successfully");
        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    private BehaviorTreeExecutor.NodeStatus updateEnhancedAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);
        long duration = executor.getBlackboard().getLongValue(nodeId + ":duration", 2000L);

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        // Получаем менеджер и проверяем коллизии
        EnhancedBoneColliderManager colliderManager = getEnhancedBoneColliderManager(entity);
        if (colliderManager != null) {
            colliderManager.checkCollisions();
        }

        // Проверяем завершение атаки
        if (elapsedTime >= duration) {
            LogHelper.info("[AttackNode] Attack completed after {}ms", elapsedTime);

            // Останавливаем атаку
            entity.stopAttack();

            // Деактивируем все коллайдеры
            if (colliderManager != null) {
                colliderManager.deactivateAllColliders();
                colliderManager.setDebugVisualization(false);
            }

            // Очищаем данные
            cleanup(executor, nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Активирует коллайдер для кости в зависимости от типа
     */
    private void activateColliderForBone(EnhancedBoneColliderManager manager, String boneName,
                                         float damage, String colliderType, double primarySize,
                                         double secondarySize, Vec3 direction) {
        switch (colliderType.toLowerCase()) {
            case "sphere":
                manager.activateSphereCollider(boneName, damage, primarySize);
                break;

            case "capsule":
                manager.activateCapsuleCollider(boneName, damage, primarySize, secondarySize, direction);
                break;

            case "auto":
            default:
                manager.activateSmartCollider(boneName, damage, primarySize, secondarySize);
                break;
        }
    }

    /**
     * Парсит направление из строки "x,y,z"
     */
    private Vec3 parseDirection(String directionStr) {
        try {
            String[] parts = directionStr.split(",");
            if (parts.length >= 3) {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                return new Vec3(x, y, z);
            }
        } catch (NumberFormatException e) {
            LogHelper.warn("[AttackNode] Failed to parse direction: {}", directionStr);
        }

        // По умолчанию - вперед
        return new Vec3(0, 0, -1);
    }

    /**
     * Получает EnhancedBoneColliderManager
     */
    private EnhancedBoneColliderManager getEnhancedBoneColliderManager(CustomMobEntity entity) {
        Object manager = entity.getServerBoneColliderManager();
        if (manager instanceof EnhancedBoneColliderManager) {
            return (EnhancedBoneColliderManager) manager;
        }

        // Создаем новый улучшенный менеджер
        EnhancedBoneColliderManager newManager = new EnhancedBoneColliderManager(entity);
        entity.setServerBoneColliderManager(newManager);
        return newManager;
    }

    /**
     * Парсит строку с костями в список
     */
    private List<String> parseBoneNames(String bonesParam) {
        List<String> boneNames = new ArrayList<>();

        if (bonesParam == null || bonesParam.trim().isEmpty()) {
            return boneNames;
        }

        String[] parts = bonesParam.split("[,;\\s]+");
        for (String part : parts) {
            String boneName = part.trim();
            if (!boneName.isEmpty()) {
                boneNames.add(boneName);
            }
        }

        return boneNames;
    }

    /**
     * Получает ID анимации
     */
    private String getAnimationId(BehaviorNode node) {
        // Приоритет 1: customParameters
        String animationId = getParameter(node, "animation", null, String.class);
        if (animationId != null && !animationId.isEmpty()) {
            return animationId;
        }

        // Приоритет 2: поле animationId
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            return node.getAnimationId();
        }

        // Fallback
        return "attack";
    }

    /**
     * Очистка
     */
    private void cleanup(BehaviorTreeExecutor executor, String nodeId) {
        executor.getBlackboard().removeValue(nodeId + ":started");
        executor.getBlackboard().removeValue(nodeId + ":start_time");
        executor.getBlackboard().removeValue(nodeId + ":duration");
        executor.getBlackboard().removeValue(nodeId + ":bones");
    }
}