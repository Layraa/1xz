package com.custommobsforge.custommobsforge.server.commands;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.behavior.EnhancedBoneColliderManager;
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

class EnhancedDebugCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("custommob")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("nearest")
                                        .executes(context -> debugNearestMob(context.getSource()))
                                )
                                .then(Commands.literal("bones")
                                        .executes(context -> debugNearestMobBones(context.getSource()))
                                )
                                .then(Commands.literal("colliders")
                                        .executes(context -> debugNearestMobColliders(context.getSource()))
                                )
                                .then(Commands.literal("test_sphere_attack")
                                        .executes(context -> testSphereAttack(context.getSource()))
                                )
                                .then(Commands.literal("test_capsule_attack")
                                        .executes(context -> testCapsuleAttack(context.getSource()))
                                )
                                .then(Commands.literal("test_smart_attack")
                                        .executes(context -> testSmartAttack(context.getSource()))
                                )
                                .then(Commands.literal("stop_attack")
                                        .executes(context -> stopAttack(context.getSource()))
                                )
                                .then(Commands.literal("visualize")
                                        .then(Commands.literal("on")
                                                .executes(context -> toggleVisualization(context.getSource(), true))
                                        )
                                        .then(Commands.literal("off")
                                                .executes(context -> toggleVisualization(context.getSource(), false))
                                        )
                                )
                        )
        );
    }

    private static int debugNearestMob(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No custom mobs found nearby"));
            return 0;
        }

        StringBuilder debug = new StringBuilder();
        debug.append("=== ENHANCED MOB DEBUG ===\n");
        debug.append("Entity ID: ").append(mob.getId()).append("\n");
        debug.append("Mob ID: ").append(mob.getMobId() != null ? mob.getMobId() : "NULL").append("\n");
        debug.append("Has MobData: ").append(mob.getMobData() != null).append("\n");
        debug.append("Is Attacking: ").append(mob.isAttacking()).append("\n");

        // Проверяем систему коллайдеров
        Object manager = mob.getServerBoneColliderManager();
        if (manager instanceof EnhancedBoneColliderManager enhancedManager) {
            debug.append("Enhanced Collider Manager: ACTIVE\n");
            debug.append("Has Active Colliders: ").append(enhancedManager.hasActiveColliders()).append("\n");
            debug.append("Debug Visualization: ").append(enhancedManager.isDebugVisualizationEnabled()).append("\n");
            debug.append("Active Colliders: ").append(enhancedManager.getActiveColliders().size()).append("\n");

            for (var collider : enhancedManager.getActiveColliders()) {
                debug.append("  - ").append(collider.type).append(" '").append(collider.boneName)
                        .append("' (damage: ").append(collider.damage).append(")\n");
            }
        } else {
            debug.append("Enhanced Collider Manager: NOT_INITIALIZED\n");
        }

        source.sendSuccess(() -> Component.literal(debug.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int testSphereAttack(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No mob found nearby"));
            return 0;
        }

        LogHelper.info("=== SPHERE ATTACK TEST ===");
        mob.forceInitializeAttackSystem();
        mob.startAttack();

        Object managerObj = mob.getServerBoneColliderManager();
        if (managerObj instanceof EnhancedBoneColliderManager manager) {
            manager.setDebugVisualization(true);
            manager.activateSphereCollider("greatsword", 8.0f, 2.5);

            LogHelper.info("Sphere attack activated for entity {}", mob.getId());
            source.sendSuccess(() -> Component.literal("✅ Sphere attack started!"), false);
        } else {
            source.sendFailure(Component.literal("❌ Could not initialize enhanced attack system"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int testCapsuleAttack(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No mob found nearby"));
            return 0;
        }

        LogHelper.info("=== CAPSULE ATTACK TEST ===");
        mob.forceInitializeAttackSystem();
        mob.startAttack();

        Object managerObj = mob.getServerBoneColliderManager();
        if (managerObj instanceof EnhancedBoneColliderManager manager) {
            manager.setDebugVisualization(true);
            Vec3 swordDirection = new Vec3(0, 0, -1); // Направление вперед
            manager.activateCapsuleCollider("greatsword", 10.0f, 0.5, 3.0, swordDirection);

            LogHelper.info("Capsule attack activated for entity {}", mob.getId());
            source.sendSuccess(() -> Component.literal("✅ Capsule attack started - 3 block sword!"), false);
        } else {
            source.sendFailure(Component.literal("❌ Could not initialize enhanced attack system"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int testSmartAttack(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No mob found nearby"));
            return 0;
        }

        LogHelper.info("=== SMART ATTACK TEST ===");
        mob.forceInitializeAttackSystem();
        mob.startAttack();

        Object managerObj = mob.getServerBoneColliderManager();
        if (managerObj instanceof EnhancedBoneColliderManager manager) {
            manager.setDebugVisualization(true);

            // Умная система сама определит тип коллайдера по имени кости
            manager.activateSmartCollider("greatsword", 12.0f, 0.8, 4.0); // capsule для меча
            manager.activateSmartCollider("right_arm", 6.0f, 1.5, 0.0);    // sphere для руки
            manager.activateSmartCollider("hammer", 15.0f, 2.0, 0.0);      // sphere для молота

            LogHelper.info("Smart attack activated for entity {}", mob.getId());
            source.sendSuccess(() -> Component.literal("✅ Smart multi-collider attack started!"), false);
        } else {
            source.sendFailure(Component.literal("❌ Could not initialize enhanced attack system"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int stopAttack(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No mob found nearby"));
            return 0;
        }

        LogHelper.info("=== STOPPING ENHANCED ATTACK ===");
        mob.stopAttack();

        Object managerObj = mob.getServerBoneColliderManager();
        if (managerObj instanceof EnhancedBoneColliderManager manager) {
            manager.deactivateAllColliders();
            manager.setDebugVisualization(false);
        }

        source.sendSuccess(() -> Component.literal("🛑 Enhanced attack stopped"), false);
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
            if (manager instanceof EnhancedBoneColliderManager enhancedManager) {
                enhancedManager.setDebugVisualization(enabled);
                count++;
            }
        }

        final String status = enabled ? "enabled" : "disabled";
        int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                "Enhanced collider visualization " + status + " for " + finalCount + " mobs"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugNearestMobBones(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No custom mobs found nearby"));
            return 0;
        }

        StringBuilder debug = new StringBuilder();
        debug.append("=== BONE DEBUG ===\n");
        debug.append("Entity ID: ").append(mob.getId()).append("\n");
        debug.append("Is Attacking: ").append(mob.isAttacking()).append("\n");
        debug.append("Last Bone Update: ").append(System.currentTimeMillis() - mob.getLastBoneUpdateTime()).append("ms ago\n");

        List<String> boneNames = mob.getAvailableBoneNames();
        debug.append("Available Bones (").append(boneNames.size()).append("):\n");

        for (String boneName : boneNames) {
            Vec3 bonePos = mob.getBoneWorldPosition(boneName);
            debug.append("  - ").append(boneName);
            if (bonePos != null) {
                debug.append(" at (").append(String.format("%.1f", bonePos.x))
                        .append(", ").append(String.format("%.1f", bonePos.y))
                        .append(", ").append(String.format("%.1f", bonePos.z)).append(")");

                // Спавним частицы в позиции кости
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                            bonePos.x, bonePos.y, bonePos.z, 3, 0.1, 0.1, 0.1, 0.0);
                }
            } else {
                debug.append(" (position unknown)");
            }
            debug.append("\n");
        }

        source.sendSuccess(() -> Component.literal(debug.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugNearestMobColliders(CommandSourceStack source) {
        CustomMobEntity mob = findNearestMob(source);
        if (mob == null) {
            source.sendFailure(Component.literal("No custom mobs found nearby"));
            return 0;
        }

        StringBuilder debug = new StringBuilder();
        debug.append("=== ENHANCED COLLIDER DEBUG ===\n");
        debug.append("Entity ID: ").append(mob.getId()).append("\n");

        Object manager = mob.getServerBoneColliderManager();
        if (manager instanceof EnhancedBoneColliderManager enhancedManager) {
            debug.append("Enhanced Manager: INITIALIZED\n");
            debug.append("Has Active Colliders: ").append(enhancedManager.hasActiveColliders()).append("\n");
            debug.append("Debug Visualization: ").append(enhancedManager.isDebugVisualizationEnabled()).append("\n");

            var activeColliders = enhancedManager.getActiveColliders();
            debug.append("Active Enhanced Colliders (").append(activeColliders.size()).append("):\n");

            for (var collider : activeColliders) {
                debug.append("  - Type: ").append(collider.type)
                        .append(", Bone: ").append(collider.boneName)
                        .append(", Damage: ").append(collider.damage)
                        .append(", Hits: ").append(collider.hitTargets.size())
                        .append(", Active: ").append(collider.isActive);

                if (collider instanceof EnhancedBoneColliderManager.SphereCollider sphere) {
                    debug.append(", Radius: ").append(sphere.radius);
                } else if (collider instanceof EnhancedBoneColliderManager.CapsuleCollider capsule) {
                    debug.append(", Radius: ").append(capsule.radius)
                            .append(", Length: ").append(capsule.length);
                }
                debug.append("\n");
            }
        } else {
            debug.append("Enhanced Manager: NOT_INITIALIZED\n");
        }

        source.sendSuccess(() -> Component.literal(debug.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static CustomMobEntity findNearestMob(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<CustomMobEntity> nearbyMobs = source.getLevel().getEntitiesOfClass(
                CustomMobEntity.class,
                new AABB(pos.add(-10, -10, -10), pos.add(10, 10, 10))
        );

        return nearbyMobs.isEmpty() ? null : nearbyMobs.get(0);
    }
}