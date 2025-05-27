package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;

import java.util.List;

/**
 * Исполнитель узла последовательности (SERVER SIDE ONLY)
 */
public class SequenceNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        List<BehaviorNode> children = executor.getChildNodes(node);

        LogHelper.info("[SequenceNode] Executing node {} with {} children", node.getId(), children.size());

        if (children.isEmpty()) {
            LogHelper.info("[SequenceNode] No children, returning SUCCESS");
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        String nodeId = node.getId();

        // Получаем индекс текущего дочернего узла
        int currentIndex = executor.getBlackboard().getIntValue(nodeId + ":current_index", 0);

        LogHelper.info("[SequenceNode] Current child index: {} of {}", currentIndex, children.size());

        // Если все узлы выполнены успешно
        if (currentIndex >= children.size()) {
            LogHelper.info("[SequenceNode] All children completed successfully, resetting");
            executor.getBlackboard().removeValue(nodeId + ":current_index");
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Получаем и выполняем текущий дочерний узел
        BehaviorNode currentChild = children.get(currentIndex);
        LogHelper.info("[SequenceNode] Executing child {} of type {}", currentIndex, currentChild.getType());

        BehaviorTreeExecutor.NodeStatus childStatus = executor.executeNode(currentChild);

        LogHelper.info("[SequenceNode] Child {} returned status: {}", currentIndex, childStatus);

        switch (childStatus) {
            case RUNNING:
                LogHelper.info("[SequenceNode] Child is RUNNING, sequence returns RUNNING");
                // НЕ увеличиваем индекс при RUNNING!
                return BehaviorTreeExecutor.NodeStatus.RUNNING;

            case FAILURE:
                LogHelper.info("[SequenceNode] Child FAILURE, sequence returns FAILURE and resets");
                executor.getBlackboard().removeValue(nodeId + ":current_index");
                return BehaviorTreeExecutor.NodeStatus.FAILURE;

            case SUCCESS:
                LogHelper.info("[SequenceNode] Child SUCCESS, moving to next child");
                // Увеличиваем индекс только при SUCCESS
                currentIndex++;
                executor.getBlackboard().setValue(nodeId + ":current_index", currentIndex);

                // Если это был последний узел, последовательность выполнена
                if (currentIndex >= children.size()) {
                    LogHelper.info("[SequenceNode] Last child completed, sequence SUCCESS");
                    executor.getBlackboard().removeValue(nodeId + ":current_index");
                    return BehaviorTreeExecutor.NodeStatus.SUCCESS;
                }

                LogHelper.info("[SequenceNode] More children to execute, returning RUNNING");
                return BehaviorTreeExecutor.NodeStatus.RUNNING;

            default:
                LogHelper.warn("[SequenceNode] Unexpected child status: {}", childStatus);
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }
}