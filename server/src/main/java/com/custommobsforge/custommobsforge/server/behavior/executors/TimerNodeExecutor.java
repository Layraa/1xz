package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;

/**
 * Исполнитель узла таймера
 * Ждет заданное время, затем выполняет дочерние узлы
 */
public class TimerNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // Получаем параметры таймера
        double duration = getParameter(node, "duration", 1.0, Double.class);
        boolean repeat = getParameter(node, "repeat", false, Boolean.class);

        long durationMs = (long) (duration * 1000);
        long currentTime = System.currentTimeMillis();

        // Проверяем состояние таймера
        Long startTime = executor.getBlackboard().getValue(nodeId + ":start_time", null);
        Boolean timerCompleted = executor.getBlackboard().getValue(nodeId + ":timer_completed", false);
        Boolean childCompleted = executor.getBlackboard().getValue(nodeId + ":child_completed", false);

        // Инициализация таймера
        if (startTime == null) {
            executor.getBlackboard().setValue(nodeId + ":start_time", currentTime);
            executor.getBlackboard().setValue(nodeId + ":timer_completed", false);
            executor.getBlackboard().setValue(nodeId + ":child_completed", false);
            LogHelper.info("[TimerNode] Started timer for {} seconds", duration);
            return BehaviorTreeExecutor.NodeStatus.RUNNING;
        }

        // Проверяем время
        long elapsedTime = currentTime - startTime;

        if (elapsedTime < durationMs && !timerCompleted) {
            // Таймер еще идет
            return BehaviorTreeExecutor.NodeStatus.RUNNING;
        }

        // Время истекло
        if (!timerCompleted) {
            executor.getBlackboard().setValue(nodeId + ":timer_completed", true);
            LogHelper.info("[TimerNode] Timer completed after {} seconds", duration);
        }

        // Выполняем дочерние узлы
        var children = executor.getChildNodes(node);
        if (!children.isEmpty() && !childCompleted) {
            BehaviorTreeExecutor.NodeStatus childStatus = executor.executeNode(children.get(0));

            if (childStatus == BehaviorTreeExecutor.NodeStatus.RUNNING) {
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }

            // Дочерний узел завершен
            executor.getBlackboard().setValue(nodeId + ":child_completed", true);

            if (repeat) {
                // Сброс для повтора
                executor.getBlackboard().removeValue(nodeId + ":start_time");
                executor.getBlackboard().removeValue(nodeId + ":timer_completed");
                executor.getBlackboard().removeValue(nodeId + ":child_completed");
                LogHelper.info("[TimerNode] Restarting timer for repeat");
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }
        }

        // Полная очистка при завершении
        executor.getBlackboard().removeValue(nodeId + ":start_time");
        executor.getBlackboard().removeValue(nodeId + ":timer_completed");
        executor.getBlackboard().removeValue(nodeId + ":child_completed");
        LogHelper.info("[TimerNode] Timer and children completed");
        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}