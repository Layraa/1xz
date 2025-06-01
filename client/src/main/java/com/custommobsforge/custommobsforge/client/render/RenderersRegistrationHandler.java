package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Обработчик регистрации рендереров
 */
public class RenderersRegistrationHandler {

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ДОБАВЛЕНО: Подробное логирование
        System.out.println("RenderersRegistrationHandler: Registering entity renderer for CustomMobEntity");

        // Регистрируем рендерер для кастомных мобов
        try {
            event.registerEntityRenderer(EntityRegistry.CUSTOM_MOB.get(), CustomMobRenderer::new);
            System.out.println("RenderersRegistrationHandler: Renderer registration successful");
        } catch (Exception e) {
            System.err.println("RenderersRegistrationHandler: Failed to register entity renderer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}