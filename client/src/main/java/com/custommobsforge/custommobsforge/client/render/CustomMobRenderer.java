package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.BonePositionSyncPacket;
import com.custommobsforge.custommobsforge.client.ClientLogHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import mod.azure.azurelib.rewrite.render.entity.AzEntityRenderer;
import mod.azure.azurelib.rewrite.render.entity.AzEntityRendererConfig;
import mod.azure.azurelib.rewrite.animation.impl.AzEntityAnimator;
import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.model.AzBone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Рендерер для кастомных мобов с системой синхронизации костей под AzureLib 3.0
 */
public class CustomMobRenderer extends AzEntityRenderer<CustomMobEntity> {

    // Хранилище позиций костей для каждой энтити
    private final Map<Integer, Map<String, Vec3>> entityBonePositions = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSyncTime = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSeenTime = new ConcurrentHashMap<>();

    // Настройки синхронизации
    private static final long SYNC_INTERVAL = 50; // Синхронизация каждые 50мс
    private static final long CLEANUP_INTERVAL = 5000; // Очистка через 5 секунд

    // Список важных костей для синхронизации
    private static final String[] IMPORTANT_BONES = {
            "greatsword", "sword", "weapon", "blade", "axe", "hammer", "staff", "bow",
            "right_arm", "left_arm", "rightarm", "leftarm",
            "right_hand", "left_hand", "righthand", "lefthand",
            "head", "body", "chest", "torso",
            "hurtbox", "hitbox", "damage_area"
    };

    public CustomMobRenderer(EntityRendererProvider.Context renderManager) {
        super(createConfig(), renderManager);
        ClientLogHelper.info("CustomMobRenderer: Created with AzureLib 3.0 bone sync support");
    }

    /**
     * ИСПРАВЛЕНИЕ: Реализуем абстрактный метод из EntityRenderer
     */
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CustomMobEntity entity) {
        if (entity.getMobData() != null && entity.getMobData().getTexturePath() != null) {
            return pathToResourceLocation(entity.getMobData().getTexturePath());
        }
        return new ResourceLocation("custommobsforge", "textures/entity/custom_mob.png");
    }

    /**
     * Создает конфигурацию рендерера
     */
    private static AzEntityRendererConfig<CustomMobEntity> createConfig() {
        return AzEntityRendererConfig.<CustomMobEntity>builder(
                        entity -> entity.getMobData() != null && entity.getMobData().getModelPath() != null
                                ? pathToResourceLocation(entity.getMobData().getModelPath())
                                : new ResourceLocation("custommobsforge", "geo/custom_mob.geo.json"),
                        entity -> entity.getMobData() != null && entity.getMobData().getTexturePath() != null
                                ? pathToResourceLocation(entity.getMobData().getTexturePath())
                                : new ResourceLocation("custommobsforge", "textures/entity/custom_mob.png")
                )
                .setAnimatorProvider(() -> new CustomMobAnimator())
                .setShadowRadius(0.5f)
                .setDeathMaxRotation(90.0f)
                .build();
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        // Отладка
        if (entity.tickCount % 60 == 0) {
            ClientLogHelper.debug("CustomMobRenderer: Rendering entity {}, isAttacking: {}",
                    entity.getId(), entity.isAttacking());
        }

        // Обновляем время последнего рендера
        lastSeenTime.put(entity.getId(), System.currentTimeMillis());

        // Периодическая очистка старых данных
        if (entity.tickCount % 100 == 0) {
            cleanupOldData();
        }

        // Очищаем старые позиции перед новым рендерингом
        if (entity.isAttacking()) {
            entityBonePositions.remove(entity.getId());
        }

        // ВАЖНО: Захватываем позиции костей ДО рендеринга
        if (entity.isAttacking()) {
            captureBonePositions(entity, partialTick);
        }

        // Выполняем рендеринг
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        // После рендеринга отправляем собранные позиции на сервер
        if (entity.isAttacking()) {
            sendBonePositionsToServer(entity);
        }
    }

    /**
     * Захватывает позиции костей через аниматор
     */
    private void captureBonePositions(CustomMobEntity entity, float partialTick) {
        try {
            // Получаем аниматор
            AzEntityAnimator<CustomMobEntity> animator = this.getAnimator();
            if (animator == null) {
                ClientLogHelper.warn("[BoneSync] No animator available for entity {}", entity.getId());
                return;
            }

            // Получаем модель
            AzBakedModel model = animator.context().boneCache().getBakedModel();
            if (model == null || model.getBonesByName().isEmpty()) {
                ClientLogHelper.warn("[BoneSync] No model available for entity {}", entity.getId());
                return;
            }

            Map<String, Vec3> positions = entityBonePositions.computeIfAbsent(
                    entity.getId(), k -> new HashMap<>()
            );

            // Проходим по всем костям модели
            for (AzBone bone : model.getBonesByName().values()) {
                String boneName = bone.getName();

                if (isImportantBone(boneName)) {
                    try {
                        // Получаем мировую позицию кости
                        Vector3d worldPos = bone.getWorldPosition();

                        if (worldPos != null) {
                            Vec3 finalPos = new Vec3(worldPos.x, worldPos.y, worldPos.z);
                            positions.put(boneName, finalPos);

                            // Отладка для важных костей
                            if (shouldDebugBone(boneName) && entity.tickCount % 20 == 0) {
                                ClientLogHelper.debug("[BoneSync] Captured bone '{}' at world position: ({}, {}, {})",
                                        boneName,
                                        String.format("%.2f", finalPos.x),
                                        String.format("%.2f", finalPos.y),
                                        String.format("%.2f", finalPos.z));
                            }
                        }
                    } catch (Exception e) {
                        ClientLogHelper.error("[BoneSync] Error getting position for bone {}: {}",
                                boneName, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            ClientLogHelper.error("[BoneSync] Error capturing bone positions: {}", e.getMessage());
        }
    }

    /**
     * Проверяет, является ли кость важной для синхронизации
     */
    private boolean isImportantBone(String boneName) {
        if (boneName == null) return false;

        String lower = boneName.toLowerCase();
        for (String important : IMPORTANT_BONES) {
            if (lower.contains(important)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, нужно ли выводить отладку для этой кости
     */
    private boolean shouldDebugBone(String boneName) {
        String lower = boneName.toLowerCase();
        return lower.contains("greatsword") || lower.contains("sword") ||
                lower.contains("weapon") || lower.contains("hurtbox");
    }

    /**
     * Отправляет позиции костей на сервер
     */
    private void sendBonePositionsToServer(CustomMobEntity entity) {
        long currentTime = System.currentTimeMillis();
        Long lastSync = lastSyncTime.get(entity.getId());

        // Ограничиваем частоту отправки
        if (lastSync != null && currentTime - lastSync < SYNC_INTERVAL) {
            return;
        }

        Map<String, Vec3> positions = entityBonePositions.get(entity.getId());
        if (positions != null && !positions.isEmpty()) {
            // Отправляем пакет
            NetworkManager.INSTANCE.sendToServer(
                    new BonePositionSyncPacket(entity.getId(), new HashMap<>(positions))
            );

            lastSyncTime.put(entity.getId(), currentTime);

            ClientLogHelper.debug("[BoneSync] Sent {} bone positions to server for entity {}",
                    positions.size(), entity.getId());
        }
    }

    /**
     * Очищает данные для энтити, которые давно не рендерились
     */
    private void cleanupOldData() {
        long currentTime = System.currentTimeMillis();
        var iterator = lastSeenTime.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (currentTime - entry.getValue() > CLEANUP_INTERVAL) {
                int entityId = entry.getKey();
                entityBonePositions.remove(entityId);
                lastSyncTime.remove(entityId);
                iterator.remove();

                ClientLogHelper.debug("[BoneSync] Cleaned up data for entity {}", entityId);
            }
        }
    }

    /**
     * Преобразует путь в ResourceLocation
     */
    private static ResourceLocation pathToResourceLocation(String path) {
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