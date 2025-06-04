package com.custommobsforge.custommobsforge.client;

import com.custommobsforge.custommobsforge.client.cache.MobDataCache;
import com.custommobsforge.custommobsforge.client.commands.CommandRegistrationHandler;
import com.custommobsforge.custommobsforge.client.gui.MobCreatorGUI;
import com.custommobsforge.custommobsforge.client.render.RenderersRegistrationHandler;
import com.custommobsforge.custommobsforge.common.CommonCustomMobsForge;
import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import javafx.application.Platform;

@Mod("custommobsforge_client")
@Mod.EventBusSubscriber(modid = CommonCustomMobsForge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CustomMobsForgeClient {
    public static final String MOD_ID = "custommobsforge_client";
    private static boolean isGuiLaunched = false;

    public CustomMobsForgeClient() {
        System.out.println("CustomMobsForgeClient: Initializing client side mod with bone sync support");

        // Регистрация обработчиков событий
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        // ИЗМЕНЕНО: Зарегистрируем явно событие рендерера entity - это самое важное
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerEntityRenderers);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CommandRegistrationHandler.class);

        // Инициализация JavaFX
        try {
            Platform.startup(() -> {});
            Platform.setImplicitExit(false);
        } catch (Exception e) {
            System.err.println("Failed to initialize JavaFX: " + e.getMessage());
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Инициализация клиентского кэша
        MobDataCache.init();
        System.out.println("CustomMobsForgeClient: Client cache initialized with bone sync support");
    }

    // ДОБАВЛЕНО: Явная регистрация рендереров для сущностей
    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        System.out.println("CustomMobsForgeClient: Registering entity renderers with bone sync");
        RenderersRegistrationHandler.registerEntityRenderers(event);
    }

    // Оставляем существующий обработчик как запасной вариант
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        System.out.println("CustomMobsForgeClient: Static renderer registration with bone sync");
        RenderersRegistrationHandler.registerEntityRenderers(event);
    }
}