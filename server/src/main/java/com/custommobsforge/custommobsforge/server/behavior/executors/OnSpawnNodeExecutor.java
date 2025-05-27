package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Исполнитель узла события появления
 * Активируется при появлении моба в мире
 */
public class OnSpawnNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // ИСПРАВЛЕНИЕ: Проверяем глобальный флаг спавна
        boolean globalSpawnExecuted = executor.getBlackboard().getBooleanValue("global_spawn_executed", false);

        if (!globalSpawnExecuted) {
            // Выполняем логику спавна
            double delay = getParameter(node, "delay", 0.0, Double.class);
            long currentTime = System.currentTimeMillis();

            if (delay > 0) {
                Long startTime = executor.getBlackboard().getValue(nodeId + ":start_time", null);
                if (startTime == null) {
                    executor.getBlackboard().setValue(nodeId + ":start_time", currentTime);
                    return BehaviorTreeExecutor.NodeStatus.RUNNING;
                }

                long elapsedTime = currentTime - startTime;
                if (elapsedTime < (delay * 1000)) {
                    return BehaviorTreeExecutor.NodeStatus.RUNNING;
                }
            }

            // Выполняем дочерние узлы
            var children = executor.getChildNodes(node);
            if (!children.isEmpty()) {
                for (BehaviorNode child : children) {
                    executor.executeNode(child);
                }
            }

            // Помечаем как выполненный ГЛОБАЛЬНО
            executor.getBlackboard().setValue("global_spawn_executed", true);
            executor.getBlackboard().setValue(nodeId + ":executed", true);

            System.out.println("[OnSpawnNode] Spawn actions executed for entity " + entity.getId());
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Спавн уже выполнен - возвращаем FAILURE чтобы Selector шел дальше
        return BehaviorTreeExecutor.NodeStatus.FAILURE;
    }
}