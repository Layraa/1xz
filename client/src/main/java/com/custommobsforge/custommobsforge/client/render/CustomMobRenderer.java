package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.BonePositionSyncPacket;
import com.custommobsforge.custommobsforge.client.ClientLogHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel;
import mod.azure.azurelib.core.animatable.model.CoreGeoBone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Рендерер для кастомных мобов с синхронизацией костей
 */
public class CustomMobRenderer extends GeoEntityRenderer<CustomMobEntity> {

    public CustomMobRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CustomMobModel());
        ClientLogHelper.info("CustomMobRenderer: Created new renderer instance with bone sync support");
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {

        // ОТЛАДКА
        if (entity != null && entity.tickCount % 60 == 0) {
            ClientLogHelper.info("CustomMobRenderer: Rendering entity {}, isAttacking: {}",
                    entity.getId(), entity.isAttacking());
        }

        // Синхронизируем кости ПЕРЕД рендерингом если моб атакует
        if (entity.isAttacking()) {
            try {
                syncBonePositions(entity, partialTick);
            } catch (Exception e) {
                ClientLogHelper.error("CustomMobRenderer: Error syncing bone positions: {}", e.getMessage());
            }
        }

        // Вызываем базовый метод рендеринга
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * Синхронизирует позиции костей с сервером
     */
    private void syncBonePositions(CustomMobEntity entity, float partialTick) {
        // Синхронизируем только каждые 2 тика
        if (entity.tickCount % 2 != 0) {
            return;
        }

        if (entity.tickCount % 20 == 0) {
            ClientLogHelper.info("[BoneSync] syncBonePositions for entity {}", entity.getId());
        }

        try {
            // Получаем модель через правильный API
            ResourceLocation modelLocation = this.model.getModelResource(entity);

            // ИСПРАВЛЕНО: Используем правильный метод
            CoreBakedGeoModel bakedModel = this.model.getBakedGeoModel(modelLocation.toString());
            if (bakedModel == null) {
                ClientLogHelper.error("[BoneSync] Baked model is null for: {}", modelLocation);
                return;
            }

            if (bakedModel.getBones() == null || bakedModel.getBones().isEmpty()) {
                ClientLogHelper.error("[BoneSync] No bones in baked model");
                return;
            }

            if (entity.tickCount % 40 == 0) {
                ClientLogHelper.info("[BoneSync] Baked model has {} bones", bakedModel.getBones().size());
            }

            Map<String, Vec3> bonePositions = new HashMap<>();

            // Собираем позиции костей
            for (CoreGeoBone bone : bakedModel.getBones()) {
                collectBonePositions(entity, bone, bonePositions, partialTick);
            }

            // Также пробуем через getBone для конкретных костей
            String[] targetBones = {"greatsword", "sword", "weapon", "right_arm", "left_arm", "head", "body"};
            for (String boneName : targetBones) {
                Optional<? extends CoreGeoBone> boneOpt = bakedModel.getBone(boneName);
                if (boneOpt.isPresent() && !bonePositions.containsKey(boneName)) {
                    CoreGeoBone bone = boneOpt.get();
                    Vec3 worldPos = getBoneWorldPosition(entity, bone, partialTick);
                    if (worldPos != null) {
                        bonePositions.put(boneName, worldPos);
                    }
                }
            }

            // ОТЛАДКА
            if (entity.tickCount % 20 == 0) {
                ClientLogHelper.info("[BoneSync] Collected {} bone positions", bonePositions.size());
                for (Map.Entry<String, Vec3> entry : bonePositions.entrySet()) {
                    if (entry.getKey().contains("sword") || entry.getKey().contains("greatsword")) {
                        ClientLogHelper.info("  - {}: ({}, {}, {})",
                                entry.getKey(),
                                String.format("%.2f", entry.getValue().x),
                                String.format("%.2f", entry.getValue().y),
                                String.format("%.2f", entry.getValue().z));
                    }
                }
            }

            // Отправляем на сервер
            if (!bonePositions.isEmpty()) {
                try {
                    NetworkManager.INSTANCE.sendToServer(
                            new BonePositionSyncPacket(entity.getId(), bonePositions)
                    );

                    if (entity.tickCount % 20 == 0) {
                        ClientLogHelper.info("[BoneSync] ✅ SENT {} bone positions", bonePositions.size());
                    }
                } catch (Exception e) {
                    ClientLogHelper.error("[BoneSync] ERROR sending packet: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            ClientLogHelper.error("[BoneSync] Error in syncBonePositions: {}", e.getMessage());
        }
    }

    /**
     * Рекурсивно собирает позиции костей
     */
    private void collectBonePositions(CustomMobEntity entity, CoreGeoBone bone,
                                      Map<String, Vec3> bonePositions, float partialTick) {
        String boneName = bone.getName();

        if (isImportantBone(boneName)) {
            Vec3 worldPos = getBoneWorldPosition(entity, bone, partialTick);
            if (worldPos != null) {
                bonePositions.put(boneName, worldPos);
            }
        }

        // Рекурсивно обрабатываем дочерние кости
        if (bone.getChildBones() != null) {
            for (CoreGeoBone childBone : bone.getChildBones()) {
                collectBonePositions(entity, childBone, bonePositions, partialTick);
            }
        }
    }

    /**
     * Определяет важность кости
     */
    private boolean isImportantBone(String boneName) {
        String lower = boneName.toLowerCase();
        return lower.contains("arm") ||
                lower.contains("hand") ||
                lower.contains("sword") ||
                lower.contains("weapon") ||
                lower.contains("blade") ||
                lower.contains("staff") ||
                lower.contains("bow") ||
                lower.contains("axe") ||
                lower.contains("hammer") ||
                lower.contains("head") ||
                lower.contains("body") ||
                lower.contains("chest") ||
                lower.contains("hurtbox") ||
                lower.contains("greatsword");
    }

    /**
     * Получает мировую позицию кости с правильным API
     */
    private Vec3 getBoneWorldPosition(CustomMobEntity entity, CoreGeoBone bone, float partialTick) {
        try {
            // ПРАВИЛЬНЫЙ API: используем методы из CoreGeoBone
            float localX = bone.getPosX();
            float localY = bone.getPosY();
            float localZ = bone.getPosZ();

            float rotX = bone.getRotX();
            float rotY = bone.getRotY();
            float rotZ = bone.getRotZ();

            float scaleX = bone.getScaleX();
            float scaleY = bone.getScaleY();
            float scaleZ = bone.getScaleZ();

            float pivotX = bone.getPivotX();
            float pivotY = bone.getPivotY();
            float pivotZ = bone.getPivotZ();

            // КРИТИЧНАЯ ОТЛАДКА для greatsword
            if (bone.getName().equals("greatsword") && entity.tickCount % 5 == 0) {
                ClientLogHelper.info("[BoneAnimated] greatsword TICK {}: pos({}, {}, {}) rot({}, {}, {}) scale({}, {}, {}) pivot({}, {}, {})",
                        entity.tickCount, localX, localY, localZ, rotX, rotY, rotZ, scaleX, scaleY, scaleZ, pivotX, pivotY, pivotZ);

                // Проверяем изменения
                ClientLogHelper.info("[BoneAnimated] greatsword changes: posChanged={}, rotChanged={}, scaleChanged={}",
                        bone.hasPositionChanged(), bone.hasRotationChanged(), bone.hasScaleChanged());
            }

            // Применяем pivot (точка поворота)
            double adjustedX = localX - pivotX;
            double adjustedY = localY - pivotY;
            double adjustedZ = localZ - pivotZ;

            // Применяем масштаб
            double scaledX = adjustedX * scaleX;
            double scaledY = adjustedY * scaleY;
            double scaledZ = adjustedZ * scaleZ;

            // Применяем повороты в правильном порядке (Z, Y, X)
            if (rotX != 0 || rotY != 0 || rotZ != 0) {
                // Z поворот (roll)
                if (rotZ != 0) {
                    double boneRotZ = Math.toRadians(rotZ);
                    double cosZ = Math.cos(boneRotZ);
                    double sinZ = Math.sin(boneRotZ);
                    double tempX = scaledX * cosZ - scaledY * sinZ;
                    double tempY = scaledX * sinZ + scaledY * cosZ;
                    scaledX = tempX;
                    scaledY = tempY;
                }

                // Y поворот (yaw)
                if (rotY != 0) {
                    double boneRotY = Math.toRadians(rotY);
                    double cosY = Math.cos(boneRotY);
                    double sinY = Math.sin(boneRotY);
                    double tempX = scaledX * cosY + scaledZ * sinY;
                    double tempZ = -scaledX * sinY + scaledZ * cosY;
                    scaledX = tempX;
                    scaledZ = tempZ;
                }

                // X поворот (pitch)
                if (rotX != 0) {
                    double boneRotX = Math.toRadians(rotX);
                    double cosX = Math.cos(boneRotX);
                    double sinX = Math.sin(boneRotX);
                    double tempY = scaledY * cosX - scaledZ * sinX;
                    double tempZ = scaledY * sinX + scaledZ * cosX;
                    scaledY = tempY;
                    scaledZ = tempZ;
                }
            }

            // Возвращаем pivot обратно
            scaledX += pivotX;
            scaledY += pivotY;
            scaledZ += pivotZ;

            // Получаем позицию сущности
            Vec3 entityPos = entity.getPosition(partialTick);

            // Применяем поворот сущности
            double entityYaw = Math.toRadians(entity.getYRot() + (partialTick * (entity.getYRot() - entity.yRotO)));
            double cosEntityYaw = Math.cos(entityYaw);
            double sinEntityYaw = Math.sin(entityYaw);

            double worldX = scaledX * cosEntityYaw - scaledZ * sinEntityYaw;
            double worldZ = scaledX * sinEntityYaw + scaledZ * cosEntityYaw;

            // Финальная позиция с корректировкой высоты
            Vec3 finalPos = entityPos.add(worldX, scaledY + entity.getBbHeight() * 0.5, worldZ);

            if (bone.getName().equals("greatsword") && entity.tickCount % 5 == 0) {
                ClientLogHelper.info("[BoneAnimated] greatsword final world pos: {} (entity at {})", finalPos, entityPos);
            }

            return finalPos;

        } catch (Exception e) {
            ClientLogHelper.error("[BoneSync] Error getting bone position for '{}': {}",
                    bone.getName(), e.getMessage());

            // Fallback к виртуальной позиции
            return getVirtualBonePosition(entity, bone.getName());
        }
    }

    /**
     * Виртуальная позиция кости как fallback
     */
    private Vec3 getVirtualBonePosition(CustomMobEntity entity, String boneName) {
        Vec3 basePos = entity.position();
        Vec3 lookDir = entity.getLookAngle();
        double height = entity.getBbHeight();

        switch (boneName.toLowerCase()) {
            case "greatsword":
            case "sword":
            case "weapon":
                return basePos.add(lookDir.x * 2.0, height * 0.8, lookDir.z * 2.0);
            case "right_arm":
            case "rightarm":
                Vec3 rightDir = new Vec3(-lookDir.z, 0, lookDir.x).normalize();
                return basePos.add(rightDir.x * 0.6, height * 0.8, rightDir.z * 0.6);
            case "left_arm":
            case "leftarm":
                Vec3 leftDir = new Vec3(lookDir.z, 0, -lookDir.x).normalize();
                return basePos.add(leftDir.x * 0.6, height * 0.8, leftDir.z * 0.6);
            case "head":
                return basePos.add(0, height * 0.9, 0);
            case "body":
            case "chest":
                return basePos.add(0, height * 0.6, 0);
            default:
                return basePos.add(0, height * 0.5, 0);
        }
    }
}