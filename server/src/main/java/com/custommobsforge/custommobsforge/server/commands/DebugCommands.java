package com.custommobsforge.custommobsforge.server.commands;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.behavior.BoneColliderManager;
import com.custommobsforge.custommobsforge.server.util.EntityBoneHelper;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DebugCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("custommob")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("nearest")
                                        .executes(context -> debugNearestMob(context.getSource()))
                                )
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(context -> debugSpecificMob(context.getSource(),
                                                        EntityArgument.getEntity(context, "target")))
                                        )
                                )
                                .then(Commands.literal("bones")
                                        .executes(context -> debugNearestMobBones(context.getSource()))
                                )
                                .then(Commands.literal("colliders")
                                        .executes(context -> debugNearestMobColliders(context.getSource()))
                                )
                                .then(Commands.literal("test_attack")
                                        .executes(context -> testAttack(context.getSource()))
                                )

                        )
        );
    }

    private static int debugNearestMob(CommandSourceStack source) {
        Vec3 pos = source.getPosition();

        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-10, -10, -10), pos.add(10, 10, 10))
        );

        if (nearbyMobs.isEmpty()) {
            source.sendFailure(Component.literal("No custom mobs found nearby"));
            return 0;
        }

        CustomMobEntity nearestMob = nearbyMobs.get(0);
        return debugMob(source, nearestMob);
    }

    private static int debugNearestMobBones(CommandSourceStack source) {
        Vec3 pos = source.getPosition();

        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-10, -10, -10), pos.add(10, 10, 10))
        );

        if (nearbyMobs.isEmpty()) {
            source.sendFailure(Component.literal("No custom mobs found nearby"));
            return 0;
        }

        CustomMobEntity nearestMob = nearbyMobs.get(0);
        return debugMobBones(source, nearestMob);
    }

    private static int debugNearestMobColliders(CommandSourceStack source) {
        Vec3 pos = source.getPosition();

        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-10, -10, -10), pos.add(10, 10, 10))
        );

        if (nearbyMobs.isEmpty()) {
            source.sendFailure(Component.literal("No custom mobs found nearby"));
            return 0;
        }

        CustomMobEntity nearestMob = nearbyMobs.get(0);
        return debugMobColliders(source, nearestMob);
    }

    private static int debugSpecificMob(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof CustomMobEntity)) {
            source.sendFailure(Component.literal("Entity is not a custom mob"));
            return 0;
        }

        return debugMob(source, (CustomMobEntity) entity);
    }

    private static int debugMob(CommandSourceStack source, CustomMobEntity mob) {
        LogHelper.info("=== DEBUG COMMAND FOR MOB {} ===", mob.getId());

        StringBuilder debug = new StringBuilder();
        debug.append("Entity ID: ").append(mob.getId()).append("\n");
        debug.append("Mob ID: ").append(mob.getMobId() != null ? mob.getMobId() : "NULL").append("\n");
        debug.append("Has MobData: ").append(mob.getMobData() != null).append("\n");
        debug.append("Is Attacking: ").append(mob.isAttacking()).append("\n");

        if (mob.getMobData() != null) {
            debug.append("Mob Name: ").append(mob.getMobData().getName()).append("\n");
            debug.append("Has BehaviorTree: ").append(mob.getMobData().getBehaviorTree() != null).append("\n");

            if (mob.getMobData().getBehaviorTree() != null) {
                var tree = mob.getMobData().getBehaviorTree();
                debug.append("Tree Name: ").append(tree.getName()).append("\n");
                debug.append("Tree Nodes: ").append(tree.getNodes() != null ? tree.getNodes().size() : 0).append("\n");
                debug.append("Root Node: ").append(tree.getRootNode() != null ? tree.getRootNode().getType() : "NULL").append("\n");
            }
        }

        // Проверяем AI goals
        debug.append("AI Goals:\n");
        mob.goalSelector.getAvailableGoals().forEach(goal -> {
            debug.append("  - ").append(goal.getGoal().getClass().getSimpleName());
            if (goal.getGoal() instanceof BehaviorTreeExecutor) {
                BehaviorTreeExecutor executor = (BehaviorTreeExecutor) goal.getGoal();
                debug.append(" (CanUse: ").append(executor.canUse()).append(")");
            }
            debug.append("\n");
        });

        // Проверяем коллайдеры костей
        Object manager = mob.getServerBoneColliderManager();
        if (manager instanceof BoneColliderManager) {
            BoneColliderManager colliderManager = (BoneColliderManager) manager;
            debug.append("Bone Colliders: ").append(colliderManager.hasActiveColliders() ? "ACTIVE" : "INACTIVE").append("\n");
            debug.append("Active Colliders: ").append(colliderManager.getActiveColliders().size()).append("\n");
        } else {
            debug.append("Bone Colliders: NOT_INITIALIZED\n");
        }

        LogHelper.info(debug.toString());
        source.sendSuccess(() -> Component.literal(debug.toString()), false);

        return Command.SINGLE_SUCCESS;
    }

    // В DebugCommands.java
    private static int debugMobBones(CommandSourceStack source, CustomMobEntity mob) {
        LogHelper.info("=== BONE DEBUG FOR MOB {} ===", mob.getId());

        StringBuilder debug = new StringBuilder();
        debug.append("=== BONE DEBUG ===\n");
        debug.append("Entity ID: ").append(mob.getId()).append("\n");
        debug.append("Is Attacking: ").append(mob.isAttacking()).append("\n");
        debug.append("Last Bone Update: ").append(System.currentTimeMillis() - mob.getLastBoneUpdateTime()).append("ms ago\n");

        List<String> boneNames = mob.getAvailableBoneNames();
        debug.append("Available Bones (").append(boneNames.size()).append("):\n");

        if (boneNames.isEmpty()) {
            debug.append("  No bones found!\n");
        } else {
            for (String boneName : boneNames) {
                Vec3 bonePos = mob.getBoneWorldPosition(boneName);
                debug.append("  - ").append(boneName);
                if (bonePos != null) {
                    debug.append(" at (").append(String.format("%.1f", bonePos.x))
                            .append(", ").append(String.format("%.1f", bonePos.y))
                            .append(", ").append(String.format("%.1f", bonePos.z)).append(")");
                } else {
                    debug.append(" (position unknown)");
                }
                debug.append("\n");
            }
        }

        LogHelper.info(debug.toString());
        source.sendSuccess(() -> Component.literal(debug.toString()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int debugMobColliders(CommandSourceStack source, CustomMobEntity mob) {
        LogHelper.info("=== COLLIDER DEBUG FOR MOB {} ===", mob.getId());

        StringBuilder debug = new StringBuilder();
        debug.append("=== COLLIDER DEBUG ===\n");
        debug.append("Entity ID: ").append(mob.getId()).append("\n");

        Object manager = mob.getServerBoneColliderManager();
        if (manager instanceof BoneColliderManager) {
            BoneColliderManager colliderManager = (BoneColliderManager) manager;

            debug.append("Collider Manager: INITIALIZED\n");
            debug.append("Has Active Colliders: ").append(colliderManager.hasActiveColliders()).append("\n");

            var activeColliders = colliderManager.getActiveColliders();
            debug.append("Active Colliders (").append(activeColliders.size()).append("):\n");

            for (var collider : activeColliders) {
                debug.append("  - Bone: ").append(collider.boneName)
                        .append(", Damage: ").append(collider.damage)
                        .append(", Radius: ").append(collider.radius)
                        .append(", Hits: ").append(collider.hitTargets.size())
                        .append("\n");
            }
        } else {
            debug.append("Collider Manager: NOT_INITIALIZED\n");
        }

        LogHelper.info(debug.toString());
        source.sendSuccess(() -> Component.literal(debug.toString()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int testAttack(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-5, -5, -5), pos.add(5, 5, 5))
        );

        if (!nearbyMobs.isEmpty()) {
            CustomMobEntity mob = nearbyMobs.get(0);

            // Принудительно создаем коллайдер для тестирования
            BoneColliderManager manager = EntityBoneHelper.getOrCreateBoneColliderManager(mob);
            manager.activateBoneCollider("greatsword", Vec3.ZERO, Vec3.ZERO, 10.0f, 3.0);

            source.sendSuccess(() -> Component.literal("Test attack activated"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(Component.literal("No mob found"));
        return 0;
    }
}