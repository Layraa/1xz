package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import mod.azure.azurelib.rewrite.animation.AzAnimator;
import mod.azure.azurelib.rewrite.animation.controller.AzAnimationController;
import mod.azure.azurelib.rewrite.animation.controller.AzAnimationControllerContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Аниматор для кастомных мобов под AzureLib 3.0
 */
public class CustomMobAnimator extends AzAnimator<CustomMobEntity> {

    @Override
    public void registerControllers(AzAnimationControllerContainer<CustomMobEntity> controllers) {
        controllers.add(
                AzAnimationController.builder(this, "main_controller")
                        .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(CustomMobEntity entity) {
        if (entity.getMobData() != null && entity.getMobData().getAnimationFilePath() != null) {
            return pathToResourceLocation(entity.getMobData().getAnimationFilePath());
        }

        // Возвращаем анимацию по умолчанию
        return new ResourceLocation("custommobsforge", "animations/custom_mob.animation.json");
    }

    @Override
    public void setCustomAnimations(CustomMobEntity entity, float partialTicks) {
        super.setCustomAnimations(entity, partialTicks);

        // Здесь можно добавить кастомные анимации костей в коде
        // Например, движение головы за игроком:

        var boneCache = this.context().boneCache();
        var headBone = boneCache.getBakedModel().getBone("head");

        if (headBone.isPresent()) {
            // Пример поворота головы к ближайшему игроку
            var player = entity.level().getNearestPlayer(
                    entity.getX(), entity.getY(), entity.getZ(), 10.0, false
            );

            if (player != null) {
                double deltaX = player.getX() - entity.getX();
                double deltaZ = player.getZ() - entity.getZ();
                double deltaY = player.getY() + player.getEyeHeight() - (entity.getY() + entity.getEyeHeight());

                double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                float yaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
                float pitch = (float)(Math.atan2(deltaY, distance) * 180.0 / Math.PI);

                // Ограничиваем углы поворота
                yaw = Math.max(-45.0F, Math.min(45.0F, yaw - entity.getYRot()));
                pitch = Math.max(-25.0F, Math.min(25.0F, pitch));

                // Применяем поворот
                var bone = headBone.get();
                bone.setRotY((float)Math.toRadians(yaw));
                bone.setRotX((float)Math.toRadians(-pitch));
            }
        }
    }

    /**
     * Преобразует путь в ResourceLocation
     */
    private ResourceLocation pathToResourceLocation(String path) {
        if (path.startsWith("assets/")) {
            path = path.substring(7);
            int firstSlash = path.indexOf('/');
            if (firstSlash != -1) {
                String modid = path.substring(0, firstSlash);
                String resourcePath = path.substring(firstSlash + 1);
                return new ResourceLocation(modid, resourcePath);
            }
        } else if (path.contains(":")) {
            String[] parts = path.split(":", 2);
            if (parts.length == 2) {
                return new ResourceLocation(parts[0], parts[1]);
            }
        }

        return new ResourceLocation("custommobsforge", path);
    }
}