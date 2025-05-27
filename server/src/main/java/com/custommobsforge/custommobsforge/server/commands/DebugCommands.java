package com.custommobsforge.custommobsforge.server.commands;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
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

        LogHelper.info(debug.toString());
        source.sendSuccess(() -> Component.literal(debug.toString()), false);

        return Command.SINGLE_SUCCESS;
    }
}