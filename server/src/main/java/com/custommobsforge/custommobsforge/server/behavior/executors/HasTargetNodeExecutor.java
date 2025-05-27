package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Узел проверки наличия живой цели
 */
public class HasTargetNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        double maxDistance = getParameter(node, "max_distance", 20.0, Double.class);
        boolean requireLineOfSight = getParameter(node, "line_of_sight", false, Boolean.class);
        boolean clearIfInvalid = getParameter(node, "clear_if_invalid", true, Boolean.class);

        // Проверяем текущую цель
        LivingEntity target = entity.getTarget();

        if (target == null) {
            System.out.println("[HasTargetNode] No target set");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        if (!target.isAlive()) {
            if (clearIfInvalid) {
                entity.setTarget(null);
            }
            System.out.println("[HasTargetNode] Target is dead");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Проверяем расстояние
        double distance = entity.distanceTo(target);
        if (distance > maxDistance) {
            if (clearIfInvalid) {
                entity.setTarget(null);
            }
            System.out.println("[HasTargetNode] Target too far: " + String.format("%.1f", distance));
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Проверяем линию обзора если нужно
        if (requireLineOfSight && !entity.hasLineOfSight(target)) {
            System.out.println("[HasTargetNode] No line of sight to target");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        System.out.println("[HasTargetNode] Valid target: " + target.getName().getString() +
                " at distance " + String.format("%.1f", distance));
        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}