package com.custommobsforge.custommobsforge.server;

import com.custommobsforge.custommobsforge.server.commands.ServerCommandRegistrationHandler;
import com.custommobsforge.custommobsforge.server.event.MobSpawnEventHandler;
import com.custommobsforge.custommobsforge.server.event.ServerSaveHandler;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("custommobsforge_server")
public class ServerCustomMobsForge {
    public static final String MOD_ID = "custommobsforge_server";

    public ServerCustomMobsForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем события жизненного цикла
        modEventBus.addListener(this::serverSetup);

        // Регистрируем обработчики событий
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerSaveHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerCommandRegistrationHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MobSpawnEventHandler.class);
    }

    private void serverSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Инициализация серверных компонентов
            System.out.println("ServerCustomMobsForge: Server components initialized");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Инициализация компонентов при запуске сервера
        System.out.println("ServerCustomMobsForge: Server starting");
    }
}