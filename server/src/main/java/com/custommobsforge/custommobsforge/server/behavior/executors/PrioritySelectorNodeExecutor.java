package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;

import java.util.List;

/**
 * Селектор с приоритетами - проверяет ВСЕ узлы каждый тик
 * События всегда имеют приоритет над обычным поведением
 */
public class PrioritySelectorNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        List<BehaviorNode> children = executor.getChildNodes(node);

        if (children.isEmpty()) {
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        String nodeId = node.getId();
        String currentExecutingChild = executor.getBlackboard().getStringValue(nodeId + ":current_child", null);

        LogHelper.debug("[PrioritySelector] Executing with {} children, current: {}", children.size(), currentExecutingChild);

        // ФАЗА 1: Проверяем все событийные узлы (они всегда имеют приоритет)
        for (BehaviorNode child : children) {
            if (isEventNode(child)) {
                BehaviorTreeExecutor.NodeStatus eventStatus = executor.executeNode(child);

                if (eventStatus == BehaviorTreeExecutor.NodeStatus.SUCCESS) {
                    // Событие произошло - прерываем текущее действие
                    if (currentExecutingChild != null && !currentExecutingChild.equals(child.getId())) {
                        LogHelper.info("[PrioritySelector] Event {} interrupted current action {}",
                                child.getType(), currentExecutingChild);

                        // Очищаем состояние прерванного узла
                        clearNodeState(executor, currentExecutingChild);
                    }

                    executor.getBlackboard().setValue(nodeId + ":current_child", child.getId());
                    LogHelper.info("[PrioritySelector] Event {} is active", child.getType());
                    return BehaviorTreeExecutor.NodeStatus.SUCCESS;
                }

                if (eventStatus == BehaviorTreeExecutor.NodeStatus.RUNNING) {
                    // Событийный узел все еще выполняется
                    executor.getBlackboard().setValue(nodeId + ":current_child", child.getId());
                    return BehaviorTreeExecutor.NodeStatus.RUNNING;
                }
            }
        }

        // ФАЗА 2: Если нет активных событий, выполняем обычную логику селектора
        for (BehaviorNode child : children) {
            if (isEventNode(child)) continue; // События уже проверены

            BehaviorTreeExecutor.NodeStatus childStatus = executor.executeNode(child);

            switch (childStatus) {
                case RUNNING:
                    executor.getBlackboard().setValue(nodeId + ":current_child", child.getId());
                    LogHelper.debug("[PrioritySelector] Child {} is RUNNING", child.getType());
                    return BehaviorTreeExecutor.NodeStatus.RUNNING;

                case SUCCESS:
                    executor.getBlackboard().setValue(nodeId + ":current_child", child.getId());
                    LogHelper.debug("[PrioritySelector] Child {} SUCCESS", child.getType());
                    return BehaviorTreeExecutor.NodeStatus.SUCCESS;

                case FAILURE:
                    LogHelper.debug("[PrioritySelector] Child {} FAILURE, trying next", child.getType());
                    continue; // Пробуем следующий узел
            }
        }

        // Все узлы вернули FAILURE
        executor.getBlackboard().removeValue(nodeId + ":current_child");
        return BehaviorTreeExecutor.NodeStatus.FAILURE;
    }

    /**
     * Проверяет, является ли узел событийным
     */
    private boolean isEventNode(BehaviorNode node) {
        String type = node.getType().toLowerCase();
        return type.contains("onspawn") ||
                type.contains("ondamage") ||
                type.contains("ondeath") ||
                type.contains("ontimer") ||
                type.contains("oncondition");
    }

    /**
     * Очищает состояние прерванного узла
     */
    private void clearNodeState(BehaviorTreeExecutor executor, String nodeId) {
        executor.getBlackboard().removeValue(nodeId + ":current_index");
        executor.getBlackboard().removeValue(nodeId + ":completion_time");
        executor.getBlackboard().removeValue(nodeId + ":attack_phase");
        executor.getBlackboard().removeValue(nodeId + ":start_time");
        executor.getBlackboard().removeValue(nodeId + ":last_path_update");

        LogHelper.debug("[PrioritySelector] Cleared state for interrupted node: {}", nodeId);
    }
}