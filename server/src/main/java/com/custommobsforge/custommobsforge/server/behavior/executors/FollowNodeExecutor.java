package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * Исполнитель узла следования за игроком
 */
public class FollowNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры следования
        double followDistance = getParameter(node, "distance", 5.0, Double.class);
        double speed = getParameter(node, "speed", 1.0, Double.class);
        double stopDistance = getParameter(node, "stop_distance", 2.0, Double.class);

        // Сначала проверяем текущую цель моба
        LivingEntity target = entity.getTarget();

        // Если нет цели, ищем ближайшего игрока
        if (target == null || !target.isAlive()) {
            target = entity.level().getNearestPlayer(
                    entity.getX(), entity.getY(), entity.getZ(),
                    followDistance, false
            );
        }

        if (target == null) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Вычисляем расстояние до цели
        double distanceToTarget = entity.distanceTo(target);

        // Если уже достаточно близко, останавливаемся
        if (distanceToTarget <= stopDistance) {
            entity.getNavigation().stop();
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Если цель слишком далеко, прекращаем следование
        if (distanceToTarget > followDistance) {
            entity.getNavigation().stop();
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        String nodeId = node.getId();
        PathNavigation navigation = entity.getNavigation();

        // Оптимизация: обновляем путь только если цель существенно сместилась
        Vec3 lastTargetPos = executor.getBlackboard().getValue(nodeId + ":last_target_pos", null);
        Vec3 currentTargetPos = target.position();
        long lastPathUpdate = executor.getBlackboard().getLongValue(nodeId + ":last_path_update", 0L);
        long currentTime = System.currentTimeMillis();

        boolean shouldUpdatePath = false;

        if (lastTargetPos == null) {
            shouldUpdatePath = true;
        } else if (currentTargetPos.distanceTo(lastTargetPos) > 2.0) {
            shouldUpdatePath = true; // Цель сместилась на 2+ блока
        } else if (currentTime - lastPathUpdate > 2000) {
            shouldUpdatePath = true; // Принудительное обновление каждые 2 секунды
        }

        if (shouldUpdatePath) {
            boolean pathSet = navigation.moveTo(target, speed);
            executor.getBlackboard().setValue(nodeId + ":last_path_update", currentTime);
            executor.getBlackboard().setValue(nodeId + ":last_target_pos", currentTargetPos);

            if (!pathSet) {
                System.out.println("[FollowNode] Failed to set path to target");
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
            }
        }

        // Проверяем, движется ли моб
        if (navigation.isInProgress()) {
            return BehaviorTreeExecutor.NodeStatus.RUNNING;
        } else {
            // Путь завершен или прерван
            if (distanceToTarget <= stopDistance) {
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            } else {
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }
        }
    }
}