package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.behavior.BoneColliderManager;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Упрощенный исполнитель узла атаки - привязывает коллайдеры к костям
 */
public class AttackNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] Execute for node: {}", nodeId);

        if (!executor.getBlackboard().hasValue(nodeId + ":started")) {
            return startSimpleAttack(entity, node, executor);
        }

        return updateSimpleAttack(entity, node, executor);
    }

    private BehaviorTreeExecutor.NodeStatus startSimpleAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] === STARTING SIMPLE ATTACK ===");

        executor.getBlackboard().setValue(nodeId + ":started", true);

        // Получаем параметры
        String animationName = getAnimationId(node);
        float duration = getParameter(node, "duration", 2.0f, Float.class);
        String bonesParam = getParameter(node, "bones", "greatsword", String.class);
        float damage = getParameter(node, "damage", 5.0f, Float.class);
        double radius = getParameter(node, "radius", 2.5, Double.class);


        LogHelper.info("[AttackNode] Animation: '{}', Duration: {}s, Damage: {}, Radius: {}",
                animationName, duration, damage, radius);

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

        // Получаем менеджер коллайдеров
        BoneColliderManager colliderManager = getBoneColliderManager(entity);
        if (colliderManager == null) {
            LogHelper.error("[AttackNode] Could not get BoneColliderManager!");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Активируем коллайдеры для всех указанных костей
        for (String boneName : boneNames) {
            colliderManager.activateBoneCollider(boneName, damage, radius);
            LogHelper.info("[AttackNode] Activated collider for bone: {}", boneName);
        }

        // Включаем детальную отладку
        if (colliderManager instanceof BoneColliderManager) {
            ((BoneColliderManager) colliderManager).enableDetailedDebug();
        }

        // Сохраняем данные атаки
        executor.getBlackboard().setValue(nodeId + ":start_time", System.currentTimeMillis());
        executor.getBlackboard().setValue(nodeId + ":duration", (long)(duration * 1000));
        executor.getBlackboard().setValue(nodeId + ":bones", boneNames);

        LogHelper.info("[AttackNode] Simple attack started successfully");
        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }




    private BehaviorTreeExecutor.NodeStatus updateSimpleAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);
        long duration = executor.getBlackboard().getLongValue(nodeId + ":duration", 2000L);

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        // Получаем менеджер и проверяем коллизии
        BoneColliderManager colliderManager = getBoneColliderManager(entity);
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
            }

            // Очищаем данные
            cleanup(executor, nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Парсит строку с костями в список
     * Поддерживает: "bone1,bone2,bone3" или "bone1;bone2;bone3" или просто "bone1"
     */
    private List<String> parseBoneNames(String bonesParam) {
        List<String> boneNames = new ArrayList<>();

        if (bonesParam == null || bonesParam.trim().isEmpty()) {
            return boneNames;
        }

        // Поддерживаем разделители: запятая, точка с запятой, пробел
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
     * Получает BoneColliderManager
     */
    private BoneColliderManager getBoneColliderManager(CustomMobEntity entity) {
        Object manager = entity.getServerBoneColliderManager();
        if (manager instanceof BoneColliderManager) {
            return (BoneColliderManager) manager;
        }
        return null;
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