package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Узел проверки здоровья для фазовых переходов
 */
public class HealthCheckNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        double healthPercent = getParameter(node, "health_percent", 50.0, Double.class);
        String comparison = getParameter(node, "comparison", "less_than", String.class);
        String target = getParameter(node, "target", "self", String.class); // self или target

        double currentHealth;
        double maxHealth;

        // Определяем чье здоровье проверяем
        if ("target".equals(target)) {
            var targetEntity = entity.getTarget();
            if (targetEntity == null) {
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
            }
            currentHealth = targetEntity.getHealth();
            maxHealth = targetEntity.getMaxHealth();
        } else {
            currentHealth = entity.getHealth();
            maxHealth = entity.getMaxHealth();
        }

        double currentPercent = (currentHealth / maxHealth) * 100.0;

        boolean condition = false;
        switch (comparison.toLowerCase()) {
            case "less_than":
            case "below":
                condition = currentPercent < healthPercent;
                break;
            case "greater_than":
            case "above":
                condition = currentPercent > healthPercent;
                break;
            case "equals":
            case "equal":
                condition = Math.abs(currentPercent - healthPercent) < 5.0; // 5% допуск
                break;
        }

        if (condition) {
            System.out.println("[HealthCheckNode] Health condition met: " +
                    String.format("%.1f%% %s %.1f%%", currentPercent, comparison, healthPercent));
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        } else {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }
}