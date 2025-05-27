package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;

import java.util.List;

/**
 * Исполнитель узла выбора (SERVER SIDE ONLY)
 */
public class SelectorNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        List<BehaviorNode> children = executor.getChildNodes(node);

        LogHelper.info("[SelectorNode] Executing node {} with {} children", node.getId(), children.size());

        if (children.isEmpty()) {
            LogHelper.warn("[SelectorNode] No children found for node {}", node.getId());
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        String nodeId = node.getId();

        // Получаем индекс текущего дочернего узла
        int currentIndex = executor.getBlackboard().getValue(nodeId + ":current_index", 0);

        LogHelper.info("[SelectorNode] Current child index: {} of {}", currentIndex, children.size());

        // Если все узлы завершились неудачей
        if (currentIndex >= children.size()) {
            LogHelper.info("[SelectorNode] All children failed, resetting and returning FAILURE");
            executor.getBlackboard().setValue(nodeId + ":current_index", 0);
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Получаем и выполняем текущий дочерний узел
        BehaviorNode currentChild = children.get(currentIndex);
        LogHelper.info("[SelectorNode] Executing child {} of type {}", currentIndex, currentChild.getType());

        BehaviorTreeExecutor.NodeStatus childStatus = executor.executeNode(currentChild);

        LogHelper.info("[SelectorNode] Child {} returned status: {}", currentIndex, childStatus);

        switch (childStatus) {
            case RUNNING:
                LogHelper.info("[SelectorNode] Child is RUNNING, selector returns RUNNING");
                return BehaviorTreeExecutor.NodeStatus.RUNNING;

            case SUCCESS:
                LogHelper.info("[SelectorNode] Child SUCCESS, selector returns SUCCESS and resets");
                executor.getBlackboard().setValue(nodeId + ":current_index", 0);
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;

            case FAILURE:
                LogHelper.info("[SelectorNode] Child FAILURE, moving to next child");
                currentIndex++;
                executor.getBlackboard().setValue(nodeId + ":current_index", currentIndex);

                // Если это был последний узел, селектор не выполнен
                if (currentIndex >= children.size()) {
                    LogHelper.info("[SelectorNode] Last child failed, returning FAILURE");
                    executor.getBlackboard().setValue(nodeId + ":current_index", 0);
                    return BehaviorTreeExecutor.NodeStatus.FAILURE;
                }

                LogHelper.info("[SelectorNode] Moving to next child, returning RUNNING");
                return BehaviorTreeExecutor.NodeStatus.RUNNING;

            default:
                LogHelper.warn("[SelectorNode] Unexpected child status: {}", childStatus);
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }
}