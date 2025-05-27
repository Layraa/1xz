package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Исполнитель узла параллельного выполнения
 * Выполняет все дочерние узлы одновременно
 */
public class ParallelNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        List<BehaviorNode> children = executor.getChildNodes(node);
        if (children.isEmpty()) {
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        String nodeId = node.getId();

        // Параметры параллельного выполнения
        boolean failOnFirst = getParameter(node, "fail_on_first", false, Boolean.class);
        boolean succeedOnFirst = getParameter(node, "succeed_on_first", false, Boolean.class);

        // Отслеживаем статусы дочерних узлов
        Set<String> completedNodes = executor.getBlackboard().getValue(nodeId + ":completed", new HashSet<String>());
        Set<String> failedNodes = executor.getBlackboard().getValue(nodeId + ":failed", new HashSet<String>());
        Set<String> succeededNodes = executor.getBlackboard().getValue(nodeId + ":succeeded", new HashSet<String>());

        int runningCount = 0;
        int successCount = 0;
        int failureCount = 0;

        // Выполняем все дочерние узлы
        for (BehaviorNode child : children) {
            String childId = child.getId();

            // Пропускаем уже завершенные узлы
            if (completedNodes.contains(childId)) {
                if (succeededNodes.contains(childId)) {
                    successCount++;
                } else if (failedNodes.contains(childId)) {
                    failureCount++;
                }
                continue;
            }

            // Выполняем дочерний узел
            BehaviorTreeExecutor.NodeStatus childStatus = executor.executeNode(child);

            switch (childStatus) {
                case RUNNING:
                    runningCount++;
                    break;

                case SUCCESS:
                    successCount++;
                    completedNodes.add(childId);
                    succeededNodes.add(childId);

                    // Если нужно завершаться при первом успехе
                    if (succeedOnFirst) {
                        resetParallelState(nodeId, executor);
                        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
                    }
                    break;

                case FAILURE:
                    failureCount++;
                    completedNodes.add(childId);
                    failedNodes.add(childId);

                    // Если нужно завершаться при первой неудаче
                    if (failOnFirst) {
                        resetParallelState(nodeId, executor);
                        return BehaviorTreeExecutor.NodeStatus.FAILURE;
                    }
                    break;
            }
        }

        // Сохраняем состояние
        executor.getBlackboard().setValue(nodeId + ":completed", completedNodes);
        executor.getBlackboard().setValue(nodeId + ":failed", failedNodes);
        executor.getBlackboard().setValue(nodeId + ":succeeded", succeededNodes);

        // Проверяем завершение всех узлов
        if (runningCount == 0) {
            // Все узлы завершены
            resetParallelState(nodeId, executor);

            // Успех, если хотя бы один узел успешен и нет требования всех успехов
            if (successCount > 0 && !getParameter(node, "require_all_success", true, Boolean.class)) {
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            }

            // Успех, если все узлы успешны
            if (successCount == children.size()) {
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            }

            // Иначе неудача
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Еще есть выполняющиеся узлы
        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    private void resetParallelState(String nodeId, BehaviorTreeExecutor executor) {
        executor.getBlackboard().removeValue(nodeId + ":completed");
        executor.getBlackboard().removeValue(nodeId + ":failed");
        executor.getBlackboard().removeValue(nodeId + ":succeeded");
    }
}