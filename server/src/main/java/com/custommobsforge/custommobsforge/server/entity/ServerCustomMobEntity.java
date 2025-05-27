package com.custommobsforge.custommobsforge.server.entity;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Серверное расширение CustomMobEntity с поддержкой деревьев поведения
 */
public class ServerCustomMobEntity {

    public static void enhanceEntity(CustomMobEntity entity) {
        if (entity.level().isClientSide) return;

        // Добавляем серверную функциональность
        if (entity.getMobData() != null && entity.getMobData().getBehaviorTree() != null) {
            BehaviorTreeExecutor executor = new BehaviorTreeExecutor(entity, entity.getMobData().getBehaviorTree());
            entity.goalSelector.addGoal(1, executor);

            System.out.println("[ServerCustomMobEntity] Enhanced entity " + entity.getId() + " with behavior tree");
        }
    }
}