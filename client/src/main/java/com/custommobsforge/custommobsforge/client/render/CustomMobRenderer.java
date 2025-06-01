package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.BonePositionSyncPacket;
import com.custommobsforge.custommobsforge.client.ClientLogHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import mod.azure.azurelib.cache.object.GeoBone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * Рендерер для кастомных мобов с синхронизацией костей
 */
public class CustomMobRenderer extends GeoEntityRenderer<CustomMobEntity> {

    // Хранилище позиций костей для каждой энтити
    private final Map<Integer, Map<String, Vec3>> entityBonePositions = new HashMap<>();
    private final Map<Integer, Long> lastSyncTime = new HashMap<>();
    private final Map<Integer, Long> lastSeenTime = new HashMap<>();
    private static final long SYNC_INTERVAL = 50; // Синхронизация каждые 50мс
    private static final long CLEANUP_INTERVAL = 5000; // Очистка через 5 секунд

    public CustomMobRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CustomMobModel());
        ClientLogHelper.info("CustomMobRenderer: Created new renderer instance with bone sync support");
    }

    @Override
    public void renderRecursively(PoseStack poseStack, CustomMobEntity animatable,
                                  GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {

        if (!isReRender && animatable.isAttacking() && isImportantBone(bone.getName())) {
            try {
                // ВАЖНО: Нужно учесть позицию самой энтити!
                Vec3 entityPos = animatable.getPosition(partialTick);

                // Получаем матрицу из PoseStack
                Matrix4f currentPoseMatrix = poseStack.last().pose();

                // Точка в центре кости
                Vector4f boneCenter = new Vector4f(0, 0, 0, 1);
                currentPoseMatrix.transform(boneCenter);

                // ИСПРАВЛЕНИЕ: Добавляем позицию энтити к локальным координатам!
                Vec3 worldPos = new Vec3(
                        entityPos.x + boneCenter.x(),
                        entityPos.y + boneCenter.y(),
                        entityPos.z + boneCenter.z()
                );

                // Сохраняем позицию
                Map<String, Vec3> positions = entityBonePositions.computeIfAbsent(
                        animatable.getId(),
                        k -> new HashMap<>()
                );
                positions.put(bone.getName(), worldPos);

                // Отладка
                if (bone.getName().equals("greatsword") && animatable.tickCount % 20 == 0) {
                    ClientLogHelper.info("[RenderSync] Entity at: ({}, {}, {})",
                            String.format("%.2f", entityPos.x),
                            String.format("%.2f", entityPos.y),
                            String.format("%.2f", entityPos.z)
                    );
                    ClientLogHelper.info("[RenderSync] Bone local: ({}, {}, {})",
                            String.format("%.2f", boneCenter.x()),
                            String.format("%.2f", boneCenter.y()),
                            String.format("%.2f", boneCenter.z())
                    );
                    ClientLogHelper.info("[RenderSync] Bone WORLD: ({}, {}, {})",
                            String.format("%.2f", worldPos.x),
                            String.format("%.2f", worldPos.y),
                            String.format("%.2f", worldPos.z)
                    );
                }

            } catch (Exception e) {
                ClientLogHelper.error("[RenderSync] Error capturing bone position: {}", e.getMessage());
            }
        }

        super.renderRecursively(poseStack, animatable, bone, renderType,
                bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        // Отладка
        if (entity != null && entity.tickCount % 60 == 0) {
            ClientLogHelper.info("CustomMobRenderer: Rendering entity {}, isAttacking: {}",
                    entity.getId(), entity.isAttacking());
        }

        // Обновляем время последнего рендера
        lastSeenTime.put(entity.getId(), System.currentTimeMillis());

        // Периодически очищаем старые данные
        if (entity.tickCount % 100 == 0) {
            cleanupOldData();
        }

        // Очищаем старые позиции перед новым рендерингом
        if (entity.isAttacking()) {
            entityBonePositions.remove(entity.getId());
        }

        // Выполняем рендеринг (во время которого соберём позиции костей)
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        // После рендеринга отправляем собранные позиции на сервер
        if (entity.isAttacking()) {
            sendBonePositionsToServer(entity);
        }
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

            ClientLogHelper.info("[RenderSync] Sent {} bone positions to server for entity {}",
                    positions.size(), entity.getId());
        }
    }

    /**
     * Определяет важность кости для синхронизации
     */
    private boolean isImportantBone(String boneName) {
        if (boneName == null) return false;

        String lower = boneName.toLowerCase();
        return lower.contains("greatsword") ||
                lower.contains("sword") ||
                lower.contains("weapon") ||
                lower.contains("blade") ||
                lower.contains("arm") ||
                lower.contains("hand") ||
                lower.contains("head") ||
                lower.contains("body") ||
                lower.contains("chest") ||
                lower.contains("staff") ||
                lower.contains("bow") ||
                lower.contains("axe") ||
                lower.contains("hammer") ||
                lower.contains("mace") ||
                lower.contains("spear") ||
                lower.contains("hurtbox") ||
                lower.contains("hitbox");
    }

    /**
     * Очищает данные для энтити, которые давно не рендерились
     */
    private void cleanupOldData() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> iterator = lastSeenTime.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > CLEANUP_INTERVAL) {
                int entityId = entry.getKey();
                entityBonePositions.remove(entityId);
                lastSyncTime.remove(entityId);
                iterator.remove();

                ClientLogHelper.debug("[RenderSync] Cleaned up data for entity {}", entityId);
            }
        }
    }
}