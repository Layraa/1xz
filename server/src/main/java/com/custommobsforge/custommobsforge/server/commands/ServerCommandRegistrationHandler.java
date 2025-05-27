package com.custommobsforge.custommobsforge.server.commands;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class ServerCommandRegistrationHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        DebugCommands.register(dispatcher);

        // Регистрируем команду для спавна кастомных мобов
        dispatcher.register(
                Commands.literal("custommob")
                        .requires(source -> source.hasPermission(2)) // Требуется уровень прав 2 (оператор)
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("mobId", StringArgumentType.word())
                                        .executes(context -> spawnMob(context.getSource(), StringArgumentType.getString(context, "mobId")))
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(context -> listMobs(context.getSource()))
                        )
        );
    }

    private static int spawnMob(CommandSourceStack source, String mobId) {
        try {
            // Получаем мир сервера
            ServerLevel level = source.getLevel();

            System.out.println("!!!!!!!! ServerCommandRegistrationHandler: Spawning mob with ID: " + mobId + " !!!!!!!!");

            // Загружаем данные моба
            var mobData = MobConfigManager.loadMobConfig(mobId, level);

            if (mobData == null) {
                source.sendFailure(Component.literal("Mob with id '" + mobId + "' not found"));
                return 0;
            }

            // Создаем сущность моба
            CustomMobEntity entity = EntityRegistry.CUSTOM_MOB.get().create(level);

            if (entity != null) {
                // Получаем позицию для спавна
                Vec3 pos = source.getPosition();

                // Настраиваем моба
                entity.setPos(pos.x, pos.y, pos.z);
                entity.setMobId(mobId);
                entity.setMobData(mobData);

                // Спавним моба
                level.addFreshEntity(entity);
                entity.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                        MobSpawnType.COMMAND, null, null);

                source.sendSuccess(() -> Component.literal("Spawned mob: " + mobId), true);
                return Command.SINGLE_SUCCESS;
            } else {
                source.sendFailure(Component.literal("Failed to create mob entity"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error spawning mob: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int listMobs(CommandSourceStack source) {
        try {
            // Получаем мир сервера
            ServerLevel level = source.getLevel();

            // Получаем список доступных мобов
            List<String> mobIds = MobConfigManager.getAvailableMobIds(level);

            if (mobIds.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No custom mobs available"), false);
                return Command.SINGLE_SUCCESS;
            }

            // Строим сообщение со списком
            StringBuilder message = new StringBuilder("Available mobs (" + mobIds.size() + "):\n");
            for (String id : mobIds) {
                message.append("- ").append(id).append("\n");
            }

            source.sendSuccess(() -> Component.literal(message.toString()), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error listing mobs: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}