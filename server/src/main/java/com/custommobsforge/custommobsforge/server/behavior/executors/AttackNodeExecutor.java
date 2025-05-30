package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.behavior.BoneColliderManager;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Исполнитель узла атаки с системой точных коллайдеров костей (SERVER ONLY)
 */
public class AttackNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] Execute called for node: {} on entity {}", nodeId, entity.getId());

        if (!executor.getBlackboard().hasValue(nodeId + ":started")) {
            return startAdvancedBoneAttack(entity, node, executor);
        }

        return updateAdvancedBoneAttack(entity, node, executor);
    }

    private BehaviorTreeExecutor.NodeStatus startAdvancedBoneAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] === STARTING ADVANCED BONE ATTACK ===");

        String animationName = getAnimationId(node);
        float totalDuration = getParameter(node, "duration", 2.5f, Float.class);
        String attackPhases = getParameter(node, "attack_phases", "", String.class);

        LogHelper.info("[AttackNode] Animation: '{}', Duration: {}s", animationName, totalDuration);

        List<AttackPhase> phases = parseAttackPhases(attackPhases, totalDuration, node);

        if (phases.isEmpty()) {
            LogHelper.error("[AttackNode] No attack phases configured!");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        LogHelper.info("[AttackNode] Configured {} attack phases:", phases.size());
        for (int i = 0; i < phases.size(); i++) {
            AttackPhase phase = phases.get(i);
            LogHelper.info("  Phase {}: bone '{}' active {}ms-{}ms, damage {}, radius {}",
                    i, phase.boneName, phase.startTime, phase.endTime, phase.damage, phase.radius);
        }

        entity.setAnimation(animationName, false, 1.0f);
        entity.startAttack();

        // Инициализируем менеджер коллайдеров если его нет
        BoneColliderManager colliderManager = getBoneColliderManager(entity);
        if (colliderManager == null) {
            colliderManager = new BoneColliderManager(entity);
            entity.setServerBoneColliderManager(colliderManager);
        }

        executor.getBlackboard().setValue(nodeId + ":phases", phases);
        executor.getBlackboard().setValue(nodeId + ":start_time", System.currentTimeMillis());
        executor.getBlackboard().setValue(nodeId + ":current_phase", 0);
        executor.getBlackboard().setValue(nodeId + ":total_duration", (long)(totalDuration * 1000));

        LogHelper.info("[AttackNode] Advanced attack started successfully");
        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    private BehaviorTreeExecutor.NodeStatus updateAdvancedBoneAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);
        long totalDuration = executor.getBlackboard().getLongValue(nodeId + ":total_duration", 2500L);
        List<AttackPhase> phases = executor.getBlackboard().getValue(nodeId + ":phases", new ArrayList<>());

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        // ДОБАВИМ ДЕТАЛЬНУЮ ОТЛАДКУ
        LogHelper.info("[AttackNode] UPDATE: elapsed={}ms, total={}ms, phases={}",
                elapsedTime, totalDuration, phases.size());

        BoneColliderManager colliderManager = getBoneColliderManager(entity);
        if (colliderManager != null) {

            boolean hasActivePhases = false;

            for (int i = 0; i < phases.size(); i++) {
                AttackPhase phase = phases.get(i);
                boolean shouldBeActive = elapsedTime >= phase.startTime && elapsedTime <= phase.endTime;

                // ОТЛАДКА КАЖДОЙ ФАЗЫ
                if (elapsedTime >= phase.startTime - 100 && elapsedTime <= phase.endTime + 100) {
                    LogHelper.info("[AttackNode] Phase {}: bone='{}' time={}ms-{}ms current={}ms shouldActive={} isActive={}",
                            i, phase.boneName, phase.startTime, phase.endTime, elapsedTime, shouldBeActive, phase.isActive);
                }

                if (shouldBeActive && !phase.isActive) {
                    // Проверяем позицию кости ПЕРЕД активацией
                    Vec3 bonePos = entity.getBoneWorldPosition(phase.boneName);
                    LogHelper.info("[AttackNode] ⚔️ ACTIVATING phase for bone '{}' at {}ms, bone position: {}",
                            phase.boneName, elapsedTime, bonePos);

                    activatePhase(entity, phase, colliderManager);
                    phase.isActive = true;
                    hasActivePhases = true;

                } else if (!shouldBeActive && phase.isActive) {
                    LogHelper.info("[AttackNode] 🛡️ DEACTIVATING phase for bone '{}' at {}ms",
                            phase.boneName, elapsedTime);

                    deactivatePhase(phase, colliderManager);
                    phase.isActive = false;
                } else if (shouldBeActive && phase.isActive) {
                    hasActivePhases = true;
                }
            }

            // ОТЛАДКА СОСТОЯНИЯ КОЛЛАЙДЕРОВ
            if (hasActivePhases) {
                LogHelper.info("[AttackNode] Checking collisions - active phases: {}, manager has active: {}",
                        hasActivePhases, colliderManager.hasActiveColliders());

                // Проверяем позиции костей
                for (AttackPhase phase : phases) {
                    if (phase.isActive) {
                        Vec3 bonePos = entity.getBoneWorldPosition(phase.boneName);
                        LogHelper.info("[AttackNode] Active bone '{}' position: {}", phase.boneName, bonePos);
                    }
                }
            }

            // Проверяем коллизии для всех активных коллайдеров
            colliderManager.checkCollisions();
        } else {
            LogHelper.error("[AttackNode] Collider manager is NULL!");
        }

        // Проверяем завершение атаки
        if (elapsedTime >= totalDuration) {
            LogHelper.info("[AttackNode] Attack completed after {}ms", elapsedTime);

            entity.stopAttack();
            if (colliderManager != null) {
                colliderManager.cleanup();
            }

            cleanup(executor, nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Безопасно получает BoneColliderManager из entity
     */
    private BoneColliderManager getBoneColliderManager(CustomMobEntity entity) {
        Object manager = entity.getServerBoneColliderManager();
        if (manager instanceof BoneColliderManager) {
            return (BoneColliderManager) manager;
        }
        return null;
    }

    public static class AttackPhase {
        public final String boneName;
        public final long startTime;
        public final long endTime;
        public final float damage;
        public final double radius;
        public final Vec3 offset;
        public boolean isActive = false;

        public AttackPhase(String boneName, long startTime, long endTime, float damage, double radius, Vec3 offset) {
            this.boneName = boneName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.damage = damage;
            this.radius = radius;
            this.offset = offset;
        }
    }

    /**
     * Парсинг фаз атаки из параметров
     */
    private List<AttackPhase> parseAttackPhases(String attackPhases, float totalDuration, BehaviorNode node) {
        List<AttackPhase> phases = new ArrayList<>();

        if (attackPhases.isEmpty()) {
            // Создаем дефолтную фазу из старых параметров для обратной совместимости
            String weaponBone = getParameter(node, "weapon_bone", "greatsword", String.class);
            float damage = getParameter(node, "damage", 8.0f, Float.class);
            double radius = getParameter(node, "damage_radius", 2.0, Double.class);
            float startPercent = getParameter(node, "damage_start_percent", 35.0f, Float.class);
            float endPercent = getParameter(node, "damage_end_percent", 65.0f, Float.class);

            long startTime = (long)(totalDuration * startPercent / 100.0 * 1000);
            long endTime = (long)(totalDuration * endPercent / 100.0 * 1000);

            phases.add(new AttackPhase(weaponBone, startTime, endTime, damage, radius, Vec3.ZERO));

            LogHelper.info("[AttackNode] Using legacy single-phase configuration");
            return phases;
        }

        // Новый формат: "bone1:start-end:damage:radius:offsetX,offsetY,offsetZ|bone2:start-end:damage:radius:offsetX,offsetY,offsetZ"
        String[] phaseStrings = attackPhases.split("\\|");

        LogHelper.info("[AttackNode] Parsing {} attack phases from config", phaseStrings.length);

        for (String phaseString : phaseStrings) {
            try {
                String[] parts = phaseString.split(":");
                if (parts.length >= 4) {
                    String boneName = parts[0].trim();

                    String[] timeRange = parts[1].split("-");
                    float startPercent = Float.parseFloat(timeRange[0]);
                    float endPercent = Float.parseFloat(timeRange[1]);

                    float damage = Float.parseFloat(parts[2]);
                    double radius = Double.parseDouble(parts[3]);

                    Vec3 offset = Vec3.ZERO;
                    if (parts.length >= 5 && !parts[4].isEmpty()) {
                        String[] offsetParts = parts[4].split(",");
                        if (offsetParts.length == 3) {
                            offset = new Vec3(
                                    Double.parseDouble(offsetParts[0]),
                                    Double.parseDouble(offsetParts[1]),
                                    Double.parseDouble(offsetParts[2])
                            );
                        }
                    }

                    long startTime = (long)(totalDuration * startPercent / 100.0 * 1000);
                    long endTime = (long)(totalDuration * endPercent / 100.0 * 1000);

                    phases.add(new AttackPhase(boneName, startTime, endTime, damage, radius, offset));

                    LogHelper.info("[AttackNode] Parsed phase: {} ({}%-{}%, damage {}, radius {})",
                            boneName, startPercent, endPercent, damage, radius);
                }
            } catch (Exception e) {
                LogHelper.error("[AttackNode] Error parsing phase '{}': {}", phaseString, e.getMessage());
            }
        }

        return phases;
    }

    /**
     * Активация фазы атаки
     */
    private void activatePhase(CustomMobEntity entity, AttackPhase phase, BoneColliderManager colliderManager) {
        colliderManager.activateBoneCollider(
                phase.boneName,
                new Vec3(phase.radius * 2, phase.radius * 2, phase.radius * 2),
                phase.offset,
                phase.damage,
                phase.radius
        );
    }

    /**
     * Деактивация фазы атаки
     */
    private void deactivatePhase(AttackPhase phase, BoneColliderManager colliderManager) {
        colliderManager.deactivateBoneCollider(phase.boneName);
    }

    /**
     * Получает ID анимации из узла
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

        // Приоритет 3: строка parameter
        animationId = parseFromParameterString(node.getParameter(), "animation");
        if (animationId != null) {
            return animationId;
        }

        // Fallback
        return "attack";
    }

    /**
     * Очистка ресурсов узла
     */
    private void cleanup(BehaviorTreeExecutor executor, String nodeId) {
        executor.getBlackboard().removeValue(nodeId + ":started");
        executor.getBlackboard().removeValue(nodeId + ":start_time");
        executor.getBlackboard().removeValue(nodeId + ":phases");
        executor.getBlackboard().removeValue(nodeId + ":current_phase");
        executor.getBlackboard().removeValue(nodeId + ":total_duration");
    }
}