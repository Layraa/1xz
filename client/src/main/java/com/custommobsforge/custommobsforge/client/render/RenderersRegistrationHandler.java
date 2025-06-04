package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraftforge.client.event.EntityRenderersEvent;

public class RenderersRegistrationHandler {

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        System.out.println("RenderersRegistrationHandler: Registering AzureLib 3.0 entity renderer");

        try {
            event.registerEntityRenderer(EntityRegistry.CUSTOM_MOB.get(), CustomMobRenderer::new);
            System.out.println("RenderersRegistrationHandler: AzureLib 3.0 renderer registration successful");
        } catch (Exception e) {
            System.err.println("RenderersRegistrationHandler: Failed to register AzureLib 3.0 renderer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}