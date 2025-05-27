package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Исполнитель узла отступления
 * Заставляет моба убегать от игрока
 */
public class FleeNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры отступления
        double fleeDistance = getParameter(node, "flee_distance", 8.0, Double.class);
        double triggerDistance = getParameter(node, "trigger_distance", 5.0, Double.class);
        double speed = getParameter(node, "speed", 1.2, Double.class);

        // Находим ближайшего игрока
        Player nearestPlayer = entity.level().getNearestPlayer(
                entity.getX(), entity.getY(), entity.getZ(),
                triggerDistance, false
        );

        if (nearestPlayer == null) {
            // Нет игроков поблизости, отступление не нужно
            entity.getNavigation().stop();
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        double distanceToPlayer = entity.distanceTo(nearestPlayer);

        // Если игрок достаточно далеко, прекращаем бегство
        if (distanceToPlayer >= fleeDistance) {
            entity.getNavigation().stop();
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Вычисляем направление бегства (противоположное игроку)
        Vec3 entityPos = entity.position();
        Vec3 playerPos = nearestPlayer.position();

        Vec3 fleeDirection = entityPos.subtract(playerPos).normalize();

        // Находим точку для бегства
        Vec3 fleeTarget = entityPos.add(fleeDirection.scale(fleeDistance));
        BlockPos fleePos = new BlockPos((int)fleeTarget.x, (int)fleeTarget.y, (int)fleeTarget.z);

        // Проверяем, нужно ли обновить путь
        String nodeId = node.getId();
        long lastPathUpdate = executor.getBlackboard().getValue(nodeId + ":last_path_update", 0L);
        long currentTime = System.currentTimeMillis();

        PathNavigation navigation = entity.getNavigation();

        // Обновляем путь каждые 300 мс для быстрой реакции
        if (currentTime - lastPathUpdate > 300) {
            boolean pathSet = navigation.moveTo(fleePos.getX(), fleePos.getY(), fleePos.getZ(), speed);
            executor.getBlackboard().setValue(nodeId + ":last_path_update", currentTime);

            if (!pathSet) {
                // Если не можем найти путь, пробуем альтернативные направления
                for (int angle = 45; angle <= 315; angle += 45) {
                    double radians = Math.toRadians(angle);
                    Vec3 altDirection = new Vec3(
                            Math.cos(radians),
                            0,
                            Math.sin(radians)
                    );

                    Vec3 altTarget = entityPos.add(altDirection.scale(fleeDistance / 2));
                    BlockPos altPos = new BlockPos((int)altTarget.x, (int)altTarget.y, (int)altTarget.z);

                    if (navigation.moveTo(altPos.getX(), altPos.getY(), altPos.getZ(), speed)) {
                        break;
                    }
                }
            }
        }

        // Проверяем состояние навигации
        if (navigation.isInProgress()) {
            return BehaviorTreeExecutor.NodeStatus.RUNNING;
        } else {
            // Путь завершен, проверяем расстояние
            if (distanceToPlayer >= fleeDistance) {
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            } else {
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }
        }
    }
}