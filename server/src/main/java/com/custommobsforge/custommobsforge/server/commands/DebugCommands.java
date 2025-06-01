package com.custommobsforge.custommobsforge.server.commands;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.behavior.BoneColliderManager;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
                                .then(Commands.literal("visualize")
                                        .then(Commands.literal("on")
                                                .executes(context -> toggleVisualization(context.getSource(), true))
                                        )
                                        .then(Commands.literal("off")
                                                .executes(context -> toggleVisualization(context.getSource(), false))
                                        )
                                )
                                .then(Commands.literal("test_simple_attack")
                                        .executes(context -> testSimpleAttack(context.getSource()))
                                )
                                .then(Commands.literal("test_multi_bone")
                                        .executes(context -> testMultiBoneAttack(context.getSource()))
                                )
                                .then(Commands.literal("stop_attack")
                                        .executes(context -> stopAttack(context.getSource()))
                                )
                                .then(Commands.literal("show_bone_pos")
                                        .executes(context -> showBonePositions(context.getSource()))
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
        debug.append("=== MOB DEBUG INFO ===\n");
        debug.append("Entity ID: ").append(mob.getId()).append("\n");
        debug.append("Mob ID: ").append(mob.getMobId() != null ? mob.getMobId() : "NULL").append("\n");
        debug.append("Has MobData: ").append(mob.getMobData() != null).append("\n");
        debug.append("Is Attacking: ").append(mob.isAttacking()).append("\n");
        debug.append("Attack System Ready: ").append(mob.isAttackSystemReady()).append("\n");

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

        // Проверяем систему коллайдеров
        Object manager = mob.getServerBoneColliderManager();
        if (manager instanceof BoneColliderManager) {
            BoneColliderManager colliderManager = (BoneColliderManager) manager;
            debug.append("Collider Manager: ACTIVE\n");
            debug.append("Has Active Colliders: ").append(colliderManager.hasActiveColliders()).append("\n");
            debug.append("Debug Visualization: ").append(colliderManager.isDebugVisualizationEnabled()).append("\n");
            debug.append("Active Colliders: ").append(colliderManager.getActiveColliders().size()).append("\n");
        } else {
            debug.append("Collider Manager: NOT_INITIALIZED\n");
        }

        LogHelper.info(debug.toString());
        source.sendSuccess(() -> Component.literal(debug.toString()), false);

        return Command.SINGLE_SUCCESS;
    }

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
            debug.append("Debug Visualization: ").append(colliderManager.isDebugVisualizationEnabled()).append("\n");

            var activeColliders = colliderManager.getActiveColliders();
            debug.append("Active Colliders (").append(activeColliders.size()).append("):\n");

            for (var collider : activeColliders) {
                debug.append("  - Bone: ").append(collider.boneName)
                        .append(", Damage: ").append(collider.damage)
                        .append(", Radius: ").append(collider.radius)
                        .append(", Hits: ").append(collider.hitTargets.size())
                        .append(", Active: ").append(collider.isActive)
                        .append("\n");
            }
        } else {
            debug.append("Collider Manager: NOT_INITIALIZED\n");
        }

        LogHelper.info(debug.toString());
        source.sendSuccess(() -> Component.literal(debug.toString()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int toggleVisualization(CommandSourceStack source, boolean enabled) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-10, -10, -10), pos.add(10, 10, 10))
        );

        int count = 0;
        for (CustomMobEntity mob : nearbyMobs) {
            Object manager = mob.getServerBoneColliderManager();
            if (manager instanceof BoneColliderManager) {
                BoneColliderManager colliderManager = (BoneColliderManager) manager;
                colliderManager.setDebugVisualization(enabled);
                count++;
            }
        }

        final String status = enabled ? "enabled" : "disabled";
        final int finalCount = count;

        source.sendSuccess(() -> Component.literal(
                "Collider visualization " + status + " for " + finalCount + " mobs"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int testSimpleAttack(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-5, -5, -5), pos.add(5, 5, 5))
        );

        if (!nearbyMobs.isEmpty()) {
            CustomMobEntity mob = nearbyMobs.get(0);

            LogHelper.info("=== SIMPLE ATTACK TEST ===");

            // Инициализируем систему
            mob.forceInitializeAttackSystem();
            mob.startAttack();

            // Получаем менеджер
            Object managerObj = mob.getServerBoneColliderManager();
            if (managerObj instanceof BoneColliderManager) {
                BoneColliderManager manager = (BoneColliderManager) managerObj;
                manager.setDebugVisualization(true);

                // Активируем коллайдер для одной кости
                manager.activateBoneCollider("greatsword", 8.0f, 2.0);

                LogHelper.info("Simple attack activated for entity {}", mob.getId());
                source.sendSuccess(() -> Component.literal(
                        "✅ Simple attack started - greatsword bone active!"), false);
            } else {
                source.sendFailure(Component.literal("❌ Could not initialize attack system"));
            }

            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(Component.literal("❌ No mob found nearby"));
        return 0;
    }

    private static int testMultiBoneAttack(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-5, -5, -5), pos.add(5, 5, 5))
        );

        if (!nearbyMobs.isEmpty()) {
            CustomMobEntity mob = nearbyMobs.get(0);

            LogHelper.info("=== MULTI-BONE ATTACK TEST ===");

            // Инициализируем систему
            mob.forceInitializeAttackSystem();
            mob.startAttack();

            // Получаем менеджер
            Object managerObj = mob.getServerBoneColliderManager();
            if (managerObj instanceof BoneColliderManager) {
                BoneColliderManager manager = (BoneColliderManager) managerObj;
                manager.setDebugVisualization(true);

                // Активируем коллайдеры для нескольких костей
                manager.activateBoneCollider("greatsword", 8.0f, 2.5);
                manager.activateBoneCollider("right_arm", 4.0f, 1.5);
                manager.activateBoneCollider("left_arm", 4.0f, 1.5);
                manager.activateBoneCollider("sword", 6.0f, 2.0);

                LogHelper.info("Multi-bone attack activated for entity {}", mob.getId());
                source.sendSuccess(() -> Component.literal(
                        "✅ Multi-bone attack started! Bones: greatsword, right_arm, left_arm, sword"), false);
            } else {
                source.sendFailure(Component.literal("❌ Could not initialize attack system"));
            }

            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(Component.literal("❌ No mob found nearby"));
        return 0;
    }

    private static int stopAttack(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-5, -5, -5), pos.add(5, 5, 5))
        );

        if (!nearbyMobs.isEmpty()) {
            CustomMobEntity mob = nearbyMobs.get(0);

            LogHelper.info("=== STOPPING ATTACK ===");

            // Останавливаем атаку
            mob.stopAttack();

            // Деактивируем все коллайдеры
            Object managerObj = mob.getServerBoneColliderManager();
            if (managerObj instanceof BoneColliderManager) {
                BoneColliderManager manager = (BoneColliderManager) managerObj;
                manager.deactivateAllColliders();
                manager.setDebugVisualization(false);

                LogHelper.info("Attack stopped for entity {}", mob.getId());
                source.sendSuccess(() -> Component.literal("🛑 Attack stopped and colliders deactivated"), false);
            } else {
                source.sendSuccess(() -> Component.literal("🛑 Attack stopped"), false);
            }

            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(Component.literal("❌ No mob found nearby"));
        return 0;
    }

    private static int showBonePositions(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-5, -5, -5), pos.add(5, 5, 5))
        );

        if (!nearbyMobs.isEmpty()) {
            CustomMobEntity mob = nearbyMobs.get(0);

            LogHelper.info("=== BONE POSITIONS DEBUG ===");

            // Показываем позиции важных костей
            String[] importantBones = {"greatsword", "sword", "right_arm", "left_arm", "head", "body"};

            for (String boneName : importantBones) {
                Vec3 bonePos = mob.getBoneWorldPosition(boneName);
                if (bonePos != null) {
                    // Спавним частицу в позиции кости
                    if (mob.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                bonePos.x, bonePos.y, bonePos.z, 10, 0.2, 0.2, 0.2, 0.1);
                    }

                    source.sendSystemMessage(Component.literal(
                            String.format("§e%s: §f(%.2f, %.2f, %.2f)",
                                    boneName, bonePos.x, bonePos.y, bonePos.z)
                    ));

                    LogHelper.info("Bone '{}' at ({}, {}, {})", boneName,
                            String.format("%.2f", bonePos.x),
                            String.format("%.2f", bonePos.y),
                            String.format("%.2f", bonePos.z));
                } else {
                    source.sendSystemMessage(Component.literal("§c" + boneName + ": NOT FOUND"));
                }
            }

            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(Component.literal("❌ No mob found nearby"));
        return 0;
    }
}