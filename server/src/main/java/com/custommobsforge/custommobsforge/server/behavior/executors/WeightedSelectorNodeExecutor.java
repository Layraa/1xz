package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;
import java.util.Random;

/**
 * Исполнитель узла взвешенного выбора
 * Случайно выбирает один из дочерних узлов на основе весов
 */
public class WeightedSelectorNodeExecutor implements NodeExecutor {

    private static final Random random = new Random();

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        List<BehaviorNode> children = executor.getChildNodes(node);
        if (children.isEmpty()) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        String nodeId = node.getId();

        // Проверяем, выбран ли уже узел для выполнения
        String selectedNodeId = executor.getBlackboard().getStringValue(nodeId + ":selected_node", null);

        if (selectedNodeId != null) {
            // Ищем выбранный узел и выполняем его
            BehaviorNode selectedNode = children.stream()
                    .filter(child -> child.getId().equals(selectedNodeId))
                    .findFirst()
                    .orElse(null);

            if (selectedNode != null) {
                BehaviorTreeExecutor.NodeStatus status = executor.executeNode(selectedNode);

                // Если узел завершился (не RUNNING), очищаем выбор
                if (status != BehaviorTreeExecutor.NodeStatus.RUNNING) {
                    executor.getBlackboard().removeValue(nodeId + ":selected_node");
                }

                return status;
            }
        }

        // Выбираем новый узел на основе весов
        BehaviorNode selectedNode = selectNodeByWeight(children, node);

        if (selectedNode == null) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Сохраняем выбранный узел
        executor.getBlackboard().setValue(nodeId + ":selected_node", selectedNode.getId());

        System.out.println("[WeightedSelector] Selected node: " + selectedNode.getDescription());

        // Выполняем выбранный узел
        return executor.executeNode(selectedNode);
    }

    /**
     * Выбирает узел на основе весов
     */
    private BehaviorNode selectNodeByWeight(List<BehaviorNode> children, BehaviorNode parentNode) {
        // Получаем веса из параметров узлов или используем равные веса
        double[] weights = new double[children.size()];
        double totalWeight = 0.0;

        for (int i = 0; i < children.size(); i++) {
            BehaviorNode child = children.get(i);

            // Пробуем получить вес из параметров дочернего узла
            double weight = getParameter(child, "weight", 1.0, Double.class);

            // Если вес не найден в дочернем узле, пробуем получить из родительского
            if (weight == 1.0) {
                weight = getParameter(parentNode, "weight_" + i, 1.0, Double.class);
            }

            weights[i] = Math.max(0.1, weight); // Минимальный вес 0.1
            totalWeight += weights[i];
        }

        if (totalWeight <= 0) {
            // Если все веса нулевые, выбираем случайно
            return children.get(random.nextInt(children.size()));
        }

        // Выбираем узел на основе весов
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;

        for (int i = 0; i < children.size(); i++) {
            currentWeight += weights[i];
            if (randomValue <= currentWeight) {
                return children.get(i);
            }
        }

        // Fallback - последний узел
        return children.get(children.size() - 1);
    }
}