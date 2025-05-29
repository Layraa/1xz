package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Исполнитель узла атаки с поддержкой реальных костей из AzureLib
 */
public class AttackNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] Execute called for node: {} on entity {}", nodeId, entity.getId());

        if (!executor.getBlackboard().hasValue(nodeId + ":started")) {
            return startRealBoneAttack(entity, node, executor);
        }

        return updateRealBoneAttack(entity, node, executor);
    }

    /**
     * Запускает атаку с синхронизацией реальных костей
     */
    private BehaviorTreeExecutor.NodeStatus startRealBoneAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();
        String animationName = getAnimationId(node);

        LogHelper.info("[AttackNode] === STARTING REAL BONE ATTACK ===");
        LogHelper.info("[AttackNode] Animation: {}", animationName);

        // Запускаем анимацию
        entity.setAnimation(animationName, false, 1.0f);

        // КЛЮЧЕВОЕ: Включаем синхронизацию костей!
        entity.startAttack();

        // Получаем параметры атаки
        float damageStart = getParameter(node, "damage_start_percent", 40.0f, Float.class) / 100.0f;
        float damageEnd = getParameter(node, "damage_end_percent", 70.0f, Float.class) / 100.0f;
        float totalDuration = getParameter(node, "duration", 2.0f, Float.class);

        // Сохраняем данные
        executor.getBlackboard().setValue(nodeId + ":started", true);
        executor.getBlackboard().setValue(nodeId + ":start_time", System.currentTimeMillis());
        executor.getBlackboard().setValue(nodeId + ":hit_targets", new HashSet<UUID>());
        executor.getBlackboard().setValue(nodeId + ":damage_start_time", (long)(damageStart * totalDuration * 1000));
        executor.getBlackboard().setValue(nodeId + ":damage_end_time", (long)(damageEnd * totalDuration * 1000));
        executor.getBlackboard().setValue(nodeId + ":total_duration", (long)(totalDuration * 1000));

        LogHelper.info("[AttackNode] Attack configured: damage {}%-{}%, duration {}s",
                (int)(damageStart * 100), (int)(damageEnd * 100), totalDuration);

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Обновляет атаку с проверкой реальных костей
     */
    private BehaviorTreeExecutor.NodeStatus updateRealBoneAttack(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);
        long damageStartTime = executor.getBlackboard().getLongValue(nodeId + ":damage_start_time", 0L);
        long damageEndTime = executor.getBlackboard().getLongValue(nodeId + ":damage_end_time", 0L);
        long totalDuration = executor.getBlackboard().getLongValue(nodeId + ":total_duration", 2000L);
        Set<UUID> hitTargets = executor.getBlackboard().getValue(nodeId + ":hit_targets", new HashSet<UUID>());

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        // Проверяем, находимся ли в окне урона
        if (elapsedTime >= damageStartTime && elapsedTime <= damageEndTime) {
            checkRealBoneCollisions(entity, node, hitTargets);
        }

        // Проверяем завершение атаки
        if (elapsedTime >= totalDuration) {
            // Отключаем синхронизацию костей
            entity.stopAttack();

            cleanup(executor, nodeId);
            LogHelper.info("[AttackNode] Attack completed for entity {}", entity.getId());
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Проверяет коллизии с использованием РЕАЛЬНЫХ позиций костей
     */
    private void checkRealBoneCollisions(CustomMobEntity entity, BehaviorNode node, Set<UUID> hitTargets) {
        // Получаем параметры
        String weaponBone = getParameter(node, "weapon_bone", "sword", String.class);
        double damageRadius = getParameter(node, "damage_radius", 1.5, Double.class);
        double damage = getParameter(node, "damage", 10.0, Double.class);

        // Получаем РЕАЛЬНУЮ позицию кости оружия!
        Vec3 weaponPosition = entity.getBoneWorldPosition(weaponBone);

        if (weaponPosition == null) {
            LogHelper.warn("[AttackNode] Could not get position for bone: {}", weaponBone);
            return;
        }

        LogHelper.debug("[AttackNode] Weapon bone '{}' at position: ({}, {}, {})",
                weaponBone,
                String.format("%.2f", weaponPosition.x),
                String.format("%.2f", weaponPosition.y),
                String.format("%.2f", weaponPosition.z));

        // Создаем коллайдер вокруг оружия
        AABB weaponCollider = new AABB(
                weaponPosition.subtract(damageRadius, damageRadius, damageRadius),
                weaponPosition.add(damageRadius, damageRadius, damageRadius)
        );

        // Ищем цели в коллайдере
        List<LivingEntity> targets = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                weaponCollider,
                target -> target != entity && target.isAlive() && !hitTargets.contains(target.getUUID())
        );

        // Наносим урон найденным целям
        for (LivingEntity target : targets) {
            boolean success = target.hurt(entity.damageSources().mobAttack(entity), (float) damage);
            if (success) {
                hitTargets.add(target.getUUID());

                LogHelper.info("[AttackNode] 💥 REAL BONE HIT! Bone '{}' hit {} for {} damage",
                        weaponBone, target.getName().getString(), damage);

                // Эффекты попадания в точном месте
                spawnHitEffects(entity, weaponPosition, target.position());
            }
        }
    }

    /**
     * Создает эффекты попадания
     */
    private void spawnHitEffects(CustomMobEntity attacker, Vec3 weaponPos, Vec3 targetPos) {
        if (!attacker.level().isClientSide) {
            // Частицы крови в месте попадания
            ((net.minecraft.server.level.ServerLevel) attacker.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    targetPos.x, targetPos.y + 1.0, targetPos.z,
                    5, 0.2, 0.2, 0.2, 0.1
            );

            // Частицы искр от оружия
            ((net.minecraft.server.level.ServerLevel) attacker.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CRIT,
                    weaponPos.x, weaponPos.y, weaponPos.z,
                    8, 0.3, 0.3, 0.3, 0.2
            );
        }
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
        return animationId;
    }

    /**
     * Очистка ресурсов узла
     */
    private void cleanup(BehaviorTreeExecutor executor, String nodeId) {
        executor.getBlackboard().removeValue(nodeId + ":started");
        executor.getBlackboard().removeValue(nodeId + ":start_time");
        executor.getBlackboard().removeValue(nodeId + ":hit_targets");
        executor.getBlackboard().removeValue(nodeId + ":damage_start_time");
        executor.getBlackboard().removeValue(nodeId + ":damage_end_time");
        executor.getBlackboard().removeValue(nodeId + ":total_duration");
    }
}