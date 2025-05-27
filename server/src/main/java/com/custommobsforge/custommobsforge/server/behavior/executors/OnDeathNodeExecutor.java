package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Исполнитель узла события смерти
 * Активируется при смерти моба
 */
public class OnDeathNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Этот узел должен вызываться извне при смерти моба
        // Здесь мы просто регистрируем его в blackboard для последующего использования

        String nodeId = node.getId();

        // Проверяем, активирован ли узел событием смерти
        boolean deathTriggered = executor.getBlackboard().getBooleanValue("death_triggered", false);

        if (!deathTriggered) {
            // Регистрируем узел как обработчик события смерти
            executor.getBlackboard().setValue("death_handler_node", nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Событие смерти произошло, выполняем действия
        System.out.println("[OnDeathNode] Executing death actions for entity " + entity.getId());

        // Получаем параметры
        String animationId = getParameter(node, "animation", null, String.class);
        boolean dropItems = getParameter(node, "drop_items", false, Boolean.class);
        String deathMessage = getParameter(node, "death_message", null, String.class);

        // Воспроизводим анимацию смерти если указана
        if (animationId != null && !animationId.isEmpty()) {
            if (entity.getMobData() != null && entity.getMobData().getAnimations() != null) {
                var animationMapping = entity.getMobData().getAnimations().get(animationId);
                if (animationMapping != null) {
                    entity.setAnimation(animationMapping.getAnimationName(),
                            animationMapping.isLoop(),
                            animationMapping.getSpeed());
                } else {
                    entity.setAnimation(animationId, false, 1.0f);
                }
            }
        }

        // Выполняем дочерние узлы если есть
        var children = executor.getChildNodes(node);
        if (!children.isEmpty()) {
            for (BehaviorNode child : children) {
                executor.executeNode(child);
            }
        }

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}