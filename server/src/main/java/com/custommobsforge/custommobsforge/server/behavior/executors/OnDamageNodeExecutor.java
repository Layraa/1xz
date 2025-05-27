package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Исполнитель узла события получения урона
 * Активируется когда моб получает урон
 */
public class OnDamageNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // Получаем параметры
        double minDamage = getParameter(node, "minDamage", 0.0, Double.class);
        if (minDamage == 0.0) {
            minDamage = getParameter(node, "min_damage", 0.0, Double.class);
        }

        boolean playerOnly = getParameter(node, "player_only", false, Boolean.class);

        // Проверяем, произошло ли событие урона
        boolean damageTriggered = executor.getBlackboard().getBooleanValue("damage_triggered", false);

        if (!damageTriggered) {
            // Регистрируем узел как обработчик события урона
            executor.getBlackboard().setValue("damage_handler_node", nodeId);
            return BehaviorTreeExecutor.NodeStatus.FAILURE; // ИЗМЕНЕНИЕ: возвращаем FAILURE чтобы селектор шел дальше
        }

        // Событие урона произошло - проверяем условия
        double lastDamageAmount = executor.getBlackboard().getDoubleValue("last_damage_amount", 0.0);
        boolean lastDamageFromPlayer = executor.getBlackboard().getBooleanValue("last_damage_from_player", false);

        if (lastDamageAmount < minDamage) {
            executor.getBlackboard().setValue("damage_triggered", false); // Сбрасываем
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        if (playerOnly && !lastDamageFromPlayer) {
            executor.getBlackboard().setValue("damage_triggered", false); // Сбрасываем
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Выполняем действия при получении урона
        System.out.println("[OnDamageNode] Executing damage response for entity " + entity.getId() +
                " (damage: " + lastDamageAmount + ")");

        // Выполняем дочерние узлы
        var children = executor.getChildNodes(node);
        if (!children.isEmpty()) {
            for (BehaviorNode child : children) {
                executor.executeNode(child);
            }
        }

        // Сбрасываем флаг события урона
        executor.getBlackboard().setValue("damage_triggered", false);

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}