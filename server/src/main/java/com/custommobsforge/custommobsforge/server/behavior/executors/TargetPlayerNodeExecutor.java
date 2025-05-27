package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;

/**
 * Узел поиска и установки цели игрока
 */
public class TargetPlayerNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        double range = getParameter(node, "range", 16.0, Double.class);
        long rememberTime = getParameter(node, "remember_time", 30000L, Long.class); // мс
        boolean requireLineOfSight = getParameter(node, "line_of_sight", false, Boolean.class);
        boolean targetHostile = getParameter(node, "target_hostile", false, Boolean.class);

        String nodeId = node.getId();
        long currentTime = System.currentTimeMillis();

        // Проверяем текущую цель
        LivingEntity currentTarget = entity.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            double distanceToTarget = entity.distanceTo(currentTarget);

            // Проверяем, не слишком ли далеко цель
            if (distanceToTarget <= range * 1.5) { // Небольшой запас чтобы не терять цель сразу
                executor.getBlackboard().setValue(nodeId + ":target_time", currentTime);
                System.out.println("[TargetPlayerNode] Keeping current target: " + currentTarget.getName().getString());
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            }
        }

        // Проверяем время последней цели
        long lastTargetTime = executor.getBlackboard().getLongValue(nodeId + ":target_time", 0L);
        if (currentTime - lastTargetTime < rememberTime && currentTarget != null) {
            // Еще помним цель, даже если она далеко
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Ищем новую цель
        Player nearestPlayer = entity.level().getNearestPlayer(
                entity.getX(), entity.getY(), entity.getZ(),
                range, false
        );

        if (nearestPlayer == null) {
            entity.setTarget(null);
            System.out.println("[TargetPlayerNode] No players found in range");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Проверяем линию обзора если нужно
        if (requireLineOfSight) {
            if (!entity.hasLineOfSight(nearestPlayer)) {
                System.out.println("[TargetPlayerNode] Player found but no line of sight");
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
            }
        }

        // Устанавливаем цель
        entity.setTarget(nearestPlayer);
        executor.getBlackboard().setValue(nodeId + ":target_time", currentTime);

        System.out.println("[TargetPlayerNode] New target set: " + nearestPlayer.getName().getString() +
                " at distance " + String.format("%.1f", entity.distanceTo(nearestPlayer)));

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}