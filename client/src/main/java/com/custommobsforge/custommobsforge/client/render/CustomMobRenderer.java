package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.BonePositionSyncPacket;
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

/**
 * Рендерер для кастомных мобов с синхронизацией костей
 */
public class CustomMobRenderer extends GeoEntityRenderer<CustomMobEntity> {

    public CustomMobRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CustomMobModel());
        System.out.println("CustomMobRenderer: Created new renderer instance with bone sync support");
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {

        // Расширенное логирование для отладки
        if (entity != null) {
            ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

            // Логируем только каждые 60 тиков чтобы не спамить
            if (entity.tickCount % 60 == 0) {
                System.out.println("CustomMobRenderer: Rendering entity " + entity.getId() +
                        " of type " + (entityId != null ? entityId.toString() : "unknown") +
                        ", mobId: " + entity.getMobId() +
                        ", hasData: " + (entity.getMobData() != null ? "yes" : "no") +
                        ", isAttacking: " + entity.isAttacking());

                if (entity.getMobData() != null) {
                    System.out.println("  -> model: " + entity.getMobData().getModelPath());
                    System.out.println("  -> texture: " + entity.getMobData().getTexturePath());
                    System.out.println("  -> animation: " + entity.getMobData().getAnimationFilePath());
                }
            }
        }

        // Синхронизируем кости ПЕРЕД рендерингом если моб атакует
        if (entity.isAttacking()) {
            try {
                syncBonePositions(entity, partialTick);
            } catch (Exception e) {
                System.err.println("CustomMobRenderer: Error syncing bone positions: " + e.getMessage());
                // Не выводим stack trace каждый тик - только сообщение
            }
        }

        // Вызываем базовый метод рендеринга
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * Синхронизирует позиции костей с сервером
     */
    private void syncBonePositions(CustomMobEntity entity, float partialTick) {
        // Синхронизируем только каждые 2 тика для оптимизации
        if (entity.tickCount % 2 != 0) {
            return;
        }

        try {
            // Получаем модель
            CoreBakedGeoModel model = this.model.getBakedModel(this.model.getModelResource(entity));
            if (model == null || model.getBones() == null) {
                return;
            }

            Map<String, Vec3> bonePositions = new HashMap<>();

            // Собираем позиции всех важных костей
            for (CoreGeoBone bone : model.getBones()) {
                collectBonePositions(entity, bone, bonePositions, partialTick);
            }

            // Отправляем на сервер если есть что отправлять
            if (!bonePositions.isEmpty()) {
                NetworkManager.INSTANCE.sendToServer(
                        new BonePositionSyncPacket(entity.getId(), bonePositions)
                );

                // Отладочный вывод (раз в секунду)
                if (entity.tickCount % 40 == 0) { // Каждые 2 секунды
                    System.out.println("[BoneSync] Sent " + bonePositions.size() +
                            " bone positions for entity " + entity.getId());

                    // Показываем какие кости синхронизируем
                    System.out.println("[BoneSync] Synced bones: " + String.join(", ", bonePositions.keySet()));
                }
            }

        } catch (Exception e) {
            if (entity.tickCount % 60 == 0) { // Раз в 3 секунды
                System.err.println("[BoneSync] Error in syncBonePositions: " + e.getMessage());
            }
        }
    }

    /**
     * Рекурсивно собирает позиции всех костей
     */
    private void collectBonePositions(CustomMobEntity entity, CoreGeoBone bone,
                                      Map<String, Vec3> bonePositions, float partialTick) {

        String boneName = bone.getName();

        // Фильтруем только важные кости для атак
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
     * Определяет, важна ли кость для синхронизации
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
     * Вычисляет мировую позицию кости
     */
    private Vec3 getBoneWorldPosition(CustomMobEntity entity, CoreGeoBone bone, float partialTick) {
        try {
            // Получаем локальную позицию кости
            float localX = bone.getPosX();
            float localY = bone.getPosY();
            float localZ = bone.getPosZ();

            // Получаем поворот кости
            float rotY = bone.getRotY();

            // Получаем масштаб кости
            float scaleX = bone.getScaleX();
            float scaleY = bone.getScaleY();
            float scaleZ = bone.getScaleZ();

            // Применяем масштаб
            double scaledX = localX * scaleX;
            double scaledY = localY * scaleY;
            double scaledZ = localZ * scaleZ;

            // Применяем поворот кости (упрощенный)
            if (rotY != 0) {
                double boneYaw = Math.toRadians(rotY);
                double tempX = scaledX * Math.cos(boneYaw) - scaledZ * Math.sin(boneYaw);
                double tempZ = scaledX * Math.sin(boneYaw) + scaledZ * Math.cos(boneYaw);
                scaledX = tempX;
                scaledZ = tempZ;
            }

            // Интерполируем позицию сущности для плавности
            Vec3 entityPos = entity.getPosition(partialTick);

            // Применяем поворот сущности
            double entityYaw = Math.toRadians(entity.getYRot() + (partialTick * (entity.getYRot() - entity.yRotO)));
            double cosYaw = Math.cos(entityYaw);
            double sinYaw = Math.sin(entityYaw);

            double worldX = scaledX * cosYaw - scaledZ * sinYaw;
            double worldZ = scaledX * sinYaw + scaledZ * cosYaw;

            // Добавляем позицию сущности и корректируем высоту
            return entityPos.add(worldX, scaledY + entity.getBbHeight() * 0.5, worldZ);

        } catch (Exception e) {
            // Логируем ошибку только изредка
            if (entity.tickCount % 100 == 0) {
                System.err.println("[BoneSync] Error getting bone world position for '" + bone.getName() + "': " + e.getMessage());
            }

            // Fallback позиция
            Vec3 lookDirection = entity.getLookAngle();
            return entity.position().add(
                    lookDirection.x * 1.5,
                    entity.getBbHeight() * 0.75,
                    lookDirection.z * 1.5
            );
        }
    }
}