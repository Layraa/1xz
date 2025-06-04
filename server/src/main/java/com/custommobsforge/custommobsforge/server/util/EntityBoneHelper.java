package com.custommobsforge.custommobsforge.server.util;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.bone.BonePositionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.entity.AzEntityRenderer;
import mod.azure.azurelib.rewrite.animation.impl.AzEntityAnimator;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Утилитарный класс для работы с костями энтити под AzureLib 3.0
 * Обеспечивает получение позиций костей как на клиенте, так и на сервере
 */
public class EntityBoneHelper {

    // Кэш позиций костей для каждой энтити
    private static final Map<Integer, Map<String, Vec3>> CLIENT_BONE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> CACHE_UPDATE_TIME = new ConcurrentHashMap<>();

    // Настройки кэширования
    private static final long CACHE_LIFETIME = 100; // 100мс
    private static final long CLEANUP_INTERVAL = 5000; // 5 секунд

    // Список важных костей для поиска
    private static final String[] WEAPON_BONE_PATTERNS = {
            "greatsword", "sword", "weapon", "blade", "axe", "hammer", "staff", "bow",
            "katana", "dagger", "spear", "lance", "club", "mace"
    };

    private static final String[] LIMB_BONE_PATTERNS = {
            "right_arm", "left_arm", "rightarm", "leftarm",
            "right_hand", "left_hand", "righthand", "lefthand",
            "right_leg", "left_leg", "rightleg", "leftleg"
    };

    private static final String[] BODY_BONE_PATTERNS = {
            "head", "neck", "body", "chest", "torso", "spine", "pelvis", "hip"
    };

    private static final String[] ATTACK_BONE_PATTERNS = {
            "hurtbox", "hitbox", "damage_area", "attack_point", "collision"
    };

    /**
     * Получает позицию кости энтити в мировых координатах
     */
    public static Vec3 getBoneWorldPosition(Entity entity, String boneName) {
        return getBoneWorldPosition(entity, boneName, 0.0f);
    }

    /**
     * Получает позицию кости энтити в мировых координатах с учетом partialTick
     */
    public static Vec3 getBoneWorldPosition(Entity entity, String boneName, float partialTick) {
        if (entity == null || boneName == null) {
            LogHelper.warn("[EntityBoneHelper] Null entity or bone name");
            return Vec3.ZERO;
        }

        // На клиенте пытаемся получить через рендерер
        if (entity.level().isClientSide) {
            Vec3 clientPos = getClientBonePosition(entity, boneName, partialTick);
            if (clientPos != null) {
                return clientPos;
            }
        }

        // На сервере используем кэш позиций или аппроксимацию
        return getServerBonePosition(entity, boneName);
    }

    /**
     * Получает позицию кости на клиенте через рендерер
     */
    @OnlyIn(Dist.CLIENT)
    private static Vec3 getClientBonePosition(Entity entity, String boneName, float partialTick) {
        try {
            // Проверяем кэш
            Integer entityId = entity.getId();
            Map<String, Vec3> cachedPositions = CLIENT_BONE_CACHE.get(entityId);
            Long lastUpdate = CACHE_UPDATE_TIME.get(entityId);
            long currentTime = System.currentTimeMillis();

            if (cachedPositions != null && lastUpdate != null &&
                    (currentTime - lastUpdate) < CACHE_LIFETIME &&
                    cachedPositions.containsKey(boneName)) {
                return cachedPositions.get(boneName);
            }

            // Получаем рендерер
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof AzEntityRenderer)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            AzEntityRenderer<Entity> azRenderer = (AzEntityRenderer<Entity>) renderer;

            // Получаем аниматор
            AzEntityAnimator<Entity> animator = azRenderer.getAnimator();
            if (animator == null || animator.context() == null || animator.context().boneCache() == null) {
                LogHelper.debug("[EntityBoneHelper] No animator or bone cache available for entity {}", entityId);
                return null;
            }

            // Получаем модель
            AzBakedModel model = animator.context().boneCache().getBakedModel();
            if (model == null || model.getBonesByName().isEmpty()) {
                LogHelper.debug("[EntityBoneHelper] No baked model available for entity {}", entityId);
                return null;
            }

            // Ищем кость
            AzBone bone = findBone(model, boneName);
            if (bone == null) {
                LogHelper.debug("[EntityBoneHelper] Bone '{}' not found in model for entity {}", boneName, entityId);
                return null;
            }

            // Получаем мировую позицию кости
            Vector3d worldPos = bone.getWorldPosition();
            if (worldPos == null) {
                LogHelper.debug("[EntityBoneHelper] Could not get world position for bone '{}' on entity {}", boneName, entityId);
                return null;
            }

            Vec3 result = new Vec3(worldPos.x, worldPos.y, worldPos.z);

            // Обновляем кэш
            cachedPositions = CLIENT_BONE_CACHE.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());
            cachedPositions.put(boneName, result);
            CACHE_UPDATE_TIME.put(entityId, currentTime);

            // Периодическая очистка кэша
            if (entityId % 100 == 0) { // Очищаем каждую 100-ю энтити
                cleanupCache();
            }

            LogHelper.debug("[EntityBoneHelper] Got bone '{}' position: ({}, {}, {}) for entity {}",
                    boneName,
                    String.format("%.2f", result.x),
                    String.format("%.2f", result.y),
                    String.format("%.2f", result.z),
                    entityId);

            return result;

        } catch (Exception e) {
            LogHelper.error("[EntityBoneHelper] Error getting client bone position for '{}': {}", boneName, e.getMessage());
            return null;
        }
    }

    /**
     * Получает позицию кости на сервере
     */
    private static Vec3 getServerBonePosition(Entity entity, String boneName) {
        // Для CustomMobEntity пытаемся использовать кэш синхронизированных позиций
        if (entity instanceof CustomMobEntity) {
            CustomMobEntity customMob = (CustomMobEntity) entity;
            Vec3 cachedPos = BonePositionCache.getBonePosition(customMob.getId(), boneName);
            if (cachedPos != null) {
                LogHelper.debug("[EntityBoneHelper] Using cached server bone position for '{}': ({}, {}, {})",
                        boneName,
                        String.format("%.2f", cachedPos.x),
                        String.format("%.2f", cachedPos.y),
                        String.format("%.2f", cachedPos.z));
                return cachedPos;
            }
        }

        // Fallback: аппроксимация позиции кости на основе позиции энтити
        return approximateBonePosition(entity, boneName);
    }

    /**
     * Аппроксимирует позицию кости для серверной стороны
     */
    private static Vec3 approximateBonePosition(Entity entity, String boneName) {
        Vec3 entityPos = entity.position();
        String lowerBoneName = boneName.toLowerCase();

        // Высота энтити
        double entityHeight = entity.getBbHeight();
        double entityWidth = entity.getBbWidth();

        // Смещения относительно центра энтити
        double offsetX = 0;
        double offsetY = 0;
        double offsetZ = 0;

        // Определяем смещения на основе имени кости
        if (isWeaponBone(lowerBoneName)) {
            // Оружие обычно в руке
            offsetX = entityWidth * 0.5 * (lowerBoneName.contains("left") ? -1 : 1);
            offsetY = entityHeight * 0.6; // Примерно на уровне рук
            offsetZ = entityWidth * 0.3; // Чуть впереди
        } else if (isHeadBone(lowerBoneName)) {
            offsetY = entityHeight * 0.85; // Голова в верхней части
        } else if (isArmBone(lowerBoneName)) {
            offsetX = entityWidth * 0.6 * (lowerBoneName.contains("left") ? -1 : 1);
            offsetY = entityHeight * 0.6;
        } else if (isLegBone(lowerBoneName)) {
            offsetX = entityWidth * 0.3 * (lowerBoneName.contains("left") ? -1 : 1);
            offsetY = entityHeight * 0.3;
        } else if (isBodyBone(lowerBoneName)) {
            offsetY = entityHeight * 0.5; // Центр тела
        } else if (isAttackBone(lowerBoneName)) {
            // Зона атаки обычно впереди энтити
            offsetZ = entityWidth * 0.8;
            offsetY = entityHeight * 0.5;
        }

        // Учитываем поворот энтити для X и Z смещений
        float yaw = (float) Math.toRadians(entity.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double finalX = entityPos.x + (offsetX * cos - offsetZ * sin);
        double finalY = entityPos.y + offsetY;
        double finalZ = entityPos.z + (offsetX * sin + offsetZ * cos);

        Vec3 result = new Vec3(finalX, finalY, finalZ);

        LogHelper.debug("[EntityBoneHelper] Approximated bone '{}' position: ({}, {}, {}) for entity {}",
                boneName,
                String.format("%.2f", result.x),
                String.format("%.2f", result.y),
                String.format("%.2f", result.z),
                entity.getId());

        return result;
    }

    /**
     * Ищет кость в модели по имени (с поддержкой нечеткого поиска)
     */
    private static AzBone findBone(AzBakedModel model, String boneName) {
        Map<String, AzBone> bones = model.getBonesByName();

        // Точное совпадение
        AzBone exactMatch = bones.get(boneName);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Нечеткий поиск
        String lowerBoneName = boneName.toLowerCase();

        for (Map.Entry<String, AzBone> entry : bones.entrySet()) {
            String modelBoneName = entry.getKey().toLowerCase();

            // Проверяем точное совпадение в нижнем регистре
            if (modelBoneName.equals(lowerBoneName)) {
                return entry.getValue();
            }

            // Проверяем содержание
            if (modelBoneName.contains(lowerBoneName) || lowerBoneName.contains(modelBoneName)) {
                return entry.getValue();
            }
        }

        // Если не найдена, пытаемся найти похожую по паттернам
        return findSimilarBone(bones, boneName);
    }

    /**
     * Ищет похожую кость по паттернам
     */
    private static AzBone findSimilarBone(Map<String, AzBone> bones, String targetBoneName) {
        String lowerTarget = targetBoneName.toLowerCase();

        for (String pattern : WEAPON_BONE_PATTERNS) {
            if (lowerTarget.contains(pattern)) {
                for (Map.Entry<String, AzBone> entry : bones.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(pattern)) {
                        return entry.getValue();
                    }
                }
            }
        }

        for (String pattern : LIMB_BONE_PATTERNS) {
            if (lowerTarget.contains(pattern)) {
                for (Map.Entry<String, AzBone> entry : bones.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(pattern)) {
                        return entry.getValue();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Получает все доступные кости энтити
     */
    @OnlyIn(Dist.CLIENT)
    public static Set<String> getAvailableBones(Entity entity) {
        if (entity == null || !entity.level().isClientSide) {
            return Collections.emptySet();
        }

        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof AzEntityRenderer)) {
                return Collections.emptySet();
            }

            @SuppressWarnings("unchecked")
            AzEntityRenderer<Entity> azRenderer = (AzEntityRenderer<Entity>) renderer;
            AzEntityAnimator<Entity> animator = azRenderer.getAnimator();

            if (animator == null || animator.context() == null || animator.context().boneCache() == null) {
                return Collections.emptySet();
            }

            AzBakedModel model = animator.context().boneCache().getBakedModel();
            if (model == null) {
                return Collections.emptySet();
            }

            return new HashSet<>(model.getBonesByName().keySet());

        } catch (Exception e) {
            LogHelper.error("[EntityBoneHelper] Error getting available bones: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Проверяет, является ли кость костью оружия
     */
    public static boolean isWeaponBone(String boneName) {
        if (boneName == null) return false;
        String lower = boneName.toLowerCase();
        for (String pattern : WEAPON_BONE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, является ли кость костью головы
     */
    public static boolean isHeadBone(String boneName) {
        if (boneName == null) return false;
        String lower = boneName.toLowerCase();
        return lower.contains("head") || lower.contains("neck");
    }

    /**
     * Проверяет, является ли кость костью руки
     */
    public static boolean isArmBone(String boneName) {
        if (boneName == null) return false;
        String lower = boneName.toLowerCase();
        return lower.contains("arm") || lower.contains("hand");
    }

    /**
     * Проверяет, является ли кость костью ноги
     */
    public static boolean isLegBone(String boneName) {
        if (boneName == null) return false;
        String lower = boneName.toLowerCase();
        return lower.contains("leg") || lower.contains("foot");
    }

    /**
     * Проверяет, является ли кость костью тела
     */
    public static boolean isBodyBone(String boneName) {
        if (boneName == null) return false;
        String lower = boneName.toLowerCase();
        for (String pattern : BODY_BONE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, является ли кость костью атаки/коллизии
     */
    public static boolean isAttackBone(String boneName) {
        if (boneName == null) return false;
        String lower = boneName.toLowerCase();
        for (String pattern : ATTACK_BONE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Очищает устаревшие записи из кэша
     */
    @OnlyIn(Dist.CLIENT)
    private static void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        var iterator = CACHE_UPDATE_TIME.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (currentTime - entry.getValue() > CLEANUP_INTERVAL) {
                CLIENT_BONE_CACHE.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    /**
     * Принудительно очищает кэш для энтити
     */
    public static void clearCache(Entity entity) {
        if (entity != null) {
            CLIENT_BONE_CACHE.remove(entity.getId());
            CACHE_UPDATE_TIME.remove(entity.getId());
        }
    }

    /**
     * Получает информацию о расстоянии между двумя костями
     */
    public static double getBoneDistance(Entity entity1, String bone1, Entity entity2, String bone2) {
        Vec3 pos1 = getBoneWorldPosition(entity1, bone1);
        Vec3 pos2 = getBoneWorldPosition(entity2, bone2);

        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE;
        }

        return pos1.distanceTo(pos2);
    }

    /**
     * Логирует все доступные кости энтити (для отладки)
     */
    @OnlyIn(Dist.CLIENT)
    public static void debugLogAvailableBones(Entity entity) {
        Set<String> bones = getAvailableBones(entity);
        LogHelper.info("[EntityBoneHelper] Available bones for entity {} ({}): {}",
                entity.getId(), entity.getClass().getSimpleName(), bones);
    }
}