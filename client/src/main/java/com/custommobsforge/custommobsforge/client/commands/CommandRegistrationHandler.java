package com.custommobsforge.custommobsforge.client.commands;

import com.custommobsforge.custommobsforge.client.gui.MobCreatorGUI;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.SpawnMobPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import javafx.application.Platform;

public class CommandRegistrationHandler {
    private static boolean isGuiLaunched = false;

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Регистрируем команду для открытия GUI
        dispatcher.register(
                Commands.literal("custommobgui")
                        .executes(CommandRegistrationHandler::executeOpenGUI)
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("mobId", StringArgumentType.word())
                                        .executes(CommandRegistrationHandler::executeSpawnMob)))
        );
    }

    private static int executeOpenGUI(CommandContext<CommandSourceStack> context) {
        try {
            Platform.setImplicitExit(false);
            if (!isGuiLaunched) {
                new Thread(() -> {
                    MobCreatorGUI.launchGUI();
                    isGuiLaunched = true;
                }).start();
            } else {
                Platform.runLater(() -> {
                    if (MobCreatorGUI.getPrimaryStage() != null) {
                        MobCreatorGUI.getPrimaryStage().show();
                        MobCreatorGUI.getPrimaryStage().toFront();
                    }
                });
            }
            context.getSource().sendSuccess(() -> Component.literal("Opening Custom Mob GUI..."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open GUI: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeSpawnMob(CommandContext<CommandSourceStack> context) {
        try {
            String mobId = StringArgumentType.getString(context, "mobId");

            // Получаем текущую позицию игрока
            Minecraft mc = Minecraft.getInstance();
            Vec3 pos = mc.player.position();

            // Отправляем пакет на сервер для спавна моба
            NetworkManager.INSTANCE.sendToServer(new SpawnMobPacket(mobId, pos.x, pos.y, pos.z));

            context.getSource().sendSuccess(() -> Component.literal("Spawning mob: " + mobId), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to spawn mob: " + e.getMessage()));
            return 0;
        }
    }
}