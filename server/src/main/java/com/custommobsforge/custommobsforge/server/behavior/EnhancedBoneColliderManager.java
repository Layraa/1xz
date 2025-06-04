package com.custommobsforge.custommobsforge.server.behavior;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Улучшенный менеджер коллайдеров костей с поддержкой различных форм
 */
public class EnhancedBoneColliderManager {

    public enum ColliderType {
        SPHERE,    // Сферический коллайдер (по умолчанию)
        CAPSULE,   // Капсула (для мечей, копий)
        BOX,       // Прямоугольный (для щитов, молотов)
        LINE       // Линейный (для тонких клинков)
    }

    /**
     * Базовый коллайдер кости
     */
    public static abstract class BoneCollider {
        public final String boneName;
        public final float damage;
        public final ColliderType type;
        public final Set<UUID> hitTargets = new HashSet<>();
        public boolean isActive = false;
        public long activationTime = 0;
        public Vec3 lastPosition;
        public int framesSinceLastHit = 0;

        public BoneCollider(String boneName, float damage, ColliderType type) {
            this.boneName = boneName;
            this.damage = damage;
            this.type = type;
            this.activationTime = System.currentTimeMillis();
        }

        public abstract boolean checkCollision(Vec3 bonePosition, LivingEntity target);
        public abstract void visualize(ServerLevel level, Vec3 bonePosition);
        public abstract AABB getSearchArea(Vec3 bonePosition);
    }

    /**
     * Сферический коллайдер
     */
    public static class SphereCollider extends BoneCollider {
        public final double radius;

        public SphereCollider(String boneName, float damage, double radius) {
            super(boneName, damage, ColliderType.SPHERE);
            this.radius = radius;
        }

        @Override
        public boolean checkCollision(Vec3 bonePosition, LivingEntity target) {
            double distance = target.position().distanceTo(bonePosition);
            return distance <= radius + target.getBbWidth() / 2;
        }

        @Override
        public void visualize(ServerLevel level, Vec3 bonePosition) {
            // Создаем частицы по окружности
            int particles = 12;
            for (int i = 0; i < particles; i++) {
                double angle = 2 * Math.PI * i / particles;
                double x = bonePosition.x + radius * Math.cos(angle);
                double z = bonePosition.z + radius * Math.sin(angle);
                level.sendParticles(ParticleTypes.FLAME, x, bonePosition.y, z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, x, bonePosition.y + radius, z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, x, bonePosition.y - radius, z, 1, 0, 0, 0, 0);
            }
        }

        @Override
        public AABB getSearchArea(Vec3 bonePosition) {
            return new AABB(
                    bonePosition.subtract(radius, radius, radius),
                    bonePosition.add(radius, radius, radius)
            );
        }
    }

    /**
     * Капсульный коллайдер (для мечей)
     */
    public static class CapsuleCollider extends BoneCollider {
        public final double radius;
        public final double length;
        public final Vec3 direction;

        public CapsuleCollider(String boneName, float damage, double radius, double length, Vec3 direction) {
            super(boneName, damage, ColliderType.CAPSULE);
            this.radius = radius;
            this.length = length;
            this.direction = direction.normalize();
        }

        @Override
        public boolean checkCollision(Vec3 bonePosition, LivingEntity target) {
            Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);

            // Вычисляем ближайшую точку на линии капсулы
            Vec3 lineStart = bonePosition;
            Vec3 lineEnd = bonePosition.add(direction.scale(length));

            Vec3 closestPoint = getClosestPointOnLine(lineStart, lineEnd, targetPos);
            double distance = targetPos.distanceTo(closestPoint);

            return distance <= radius + target.getBbWidth() / 2;
        }

        private Vec3 getClosestPointOnLine(Vec3 lineStart, Vec3 lineEnd, Vec3 point) {
            Vec3 lineDir = lineEnd.subtract(lineStart);
            double lineLength = lineDir.length();
            if (lineLength == 0) return lineStart;

            lineDir = lineDir.normalize();
            Vec3 toPoint = point.subtract(lineStart);
            double projection = toPoint.dot(lineDir);

            // Ограничиваем проекцию границами линии
            projection = Math.max(0, Math.min(lineLength, projection));

            return lineStart.add(lineDir.scale(projection));
        }

        @Override
        public void visualize(ServerLevel level, Vec3 bonePosition) {
            Vec3 endPos = bonePosition.add(direction.scale(length));

            // Рисуем линию капсулы
            int segments = (int)(length * 2);
            for (int i = 0; i <= segments; i++) {
                double t = (double)i / segments;
                Vec3 point = bonePosition.lerp(endPos, t);

                // Рисуем кольцо в каждой точке
                int ringParticles = 8;
                for (int j = 0; j < ringParticles; j++) {
                    double angle = 2 * Math.PI * j / ringParticles;
                    Vec3 perpendicular = findPerpendicular(direction);
                    Vec3 offset = perpendicular.scale(radius * Math.cos(angle))
                            .add(direction.cross(perpendicular).scale(radius * Math.sin(angle)));

                    Vec3 particlePos = point.add(offset);
                    level.sendParticles(ParticleTypes.ENCHANT,
                            particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0);
                }
            }
        }

        private Vec3 findPerpendicular(Vec3 vector) {
            if (Math.abs(vector.x) < 0.9) {
                return new Vec3(1, 0, 0).cross(vector).normalize();
            } else {
                return new Vec3(0, 1, 0).cross(vector).normalize();
            }
        }

        @Override
        public AABB getSearchArea(Vec3 bonePosition) {
            Vec3 endPos = bonePosition.add(direction.scale(length));
            double margin = radius + 1.0;

            return new AABB(
                    Math.min(bonePosition.x, endPos.x) - margin,
                    Math.min(bonePosition.y, endPos.y) - margin,
                    Math.min(bonePosition.z, endPos.z) - margin,
                    Math.max(bonePosition.x, endPos.x) + margin,
                    Math.max(bonePosition.y, endPos.y) + margin,
                    Math.max(bonePosition.z, endPos.z) + margin
            );
        }
    }

    // Основные поля менеджера
    private final Map<String, BoneCollider> activeColliders = new ConcurrentHashMap<>();
    private final CustomMobEntity entity;
    private boolean debugVisualization = false;
    private long lastVisualizationTime = 0;

    public EnhancedBoneColliderManager(CustomMobEntity entity) {
        this.entity = entity;
        LogHelper.info("[EnhancedBoneColliderManager] Created for entity {}", entity.getId());
    }

    /**
     * Активирует сферический коллайдер
     */
    public void activateSphereCollider(String boneName, float damage, double radius) {
        SphereCollider collider = new SphereCollider(boneName, damage, radius);
        collider.isActive = true;
        activeColliders.put(boneName, collider);

        LogHelper.info("[EnhancedBoneColliderManager] ⚔️ Activated sphere collider: bone='{}', damage={}, radius={}",
                boneName, damage, radius);
    }

    /**
     * Активирует капсульный коллайдер (для мечей)
     */
    public void activateCapsuleCollider(String boneName, float damage, double radius, double length, Vec3 direction) {
        CapsuleCollider collider = new CapsuleCollider(boneName, damage, radius, length, direction);
        collider.isActive = true;
        activeColliders.put(boneName, collider);

        LogHelper.info("[EnhancedBoneColliderManager] ⚔️ Activated capsule collider: bone='{}', damage={}, radius={}, length={}",
                boneName, damage, radius, length);
    }

    /**
     * Активирует коллайдер автоматически определяя тип по имени кости
     */
    public void activateSmartCollider(String boneName, float damage, double primarySize, double secondarySize) {
        String lowerName = boneName.toLowerCase();

        if (lowerName.contains("sword") || lowerName.contains("blade") || lowerName.contains("spear")) {
            // Капсула для мечей
            Vec3 direction = new Vec3(0, 0, -1); // Направление вперед
            activateCapsuleCollider(boneName, damage, primarySize, secondarySize, direction);
        } else if (lowerName.contains("hammer") || lowerName.contains("axe") || lowerName.contains("mace")) {
            // Сфера для тяжелого оружия
            activateSphereCollider(boneName, damage, primarySize);
        } else {
            // По умолчанию сфера
            activateSphereCollider(boneName, damage, primarySize);
        }
    }

    /**
     * Деактивирует коллайдер кости
     */
    public void deactivateBoneCollider(String boneName) {
        BoneCollider collider = activeColliders.get(boneName);
        if (collider != null) {
            collider.isActive = false;
            LogHelper.info("[EnhancedBoneColliderManager] ❄️ Deactivated collider: bone='{}'", boneName);
        }
    }

    /**
     * Деактивирует все коллайдеры
     */
    public void deactivateAllColliders() {
        for (BoneCollider collider : activeColliders.values()) {
            collider.isActive = false;
        }
        LogHelper.info("[EnhancedBoneColliderManager] Deactivated all colliders");
    }

    /**
     * Включает/выключает визуализацию
     */
    public void setDebugVisualization(boolean enabled) {
        this.debugVisualization = enabled;
        LogHelper.info("[EnhancedBoneColliderManager] Debug visualization: {}", enabled ? "ON" : "OFF");
    }

    /**
     * Основной метод проверки коллизий
     */
    public void checkCollisions() {
        long currentTime = System.currentTimeMillis();
        boolean foundCollisions = false;

        for (BoneCollider collider : activeColliders.values()) {
            if (!collider.isActive) continue;

            // Получаем текущую позицию кости
            Vec3 bonePosition = entity.getBoneWorldPosition(collider.boneName);
            if (bonePosition == null) {
                LogHelper.warn("[EnhancedBoneColliderManager] No position for bone: {}", collider.boneName);
                continue;
            }

            // Обновляем счетчик кадров
            collider.framesSinceLastHit++;

            // Визуализация
            if (debugVisualization && currentTime - lastVisualizationTime > 50) {
                collider.visualize((ServerLevel) entity.level(), bonePosition);
            }

            // Проверяем коллизии
            if (checkBoneCollision(collider, bonePosition)) {
                foundCollisions = true;
                collider.framesSinceLastHit = 0;
            }

            // Сохраняем последнюю позицию
            collider.lastPosition = bonePosition;
        }

        if (debugVisualization && currentTime - lastVisualizationTime > 50) {
            lastVisualizationTime = currentTime;
        }
    }

    /**
     * Проверяет коллизию для одной кости
     */
    private boolean checkBoneCollision(BoneCollider collider, Vec3 bonePosition) {
        LogHelper.debug("[EnhancedBoneColliderManager] Checking {} collider '{}' at ({}, {}, {})",
                collider.type, collider.boneName,
                String.format("%.1f", bonePosition.x),
                String.format("%.1f", bonePosition.y),
                String.format("%.1f", bonePosition.z));

        AABB searchArea = collider.getSearchArea(bonePosition);

        // Ищем потенциальные цели
        List<LivingEntity> targets = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                target -> target != entity &&
                        target.isAlive() &&
                        !collider.hitTargets.contains(target.getUUID()) &&
                        collider.framesSinceLastHit > 3 // Защита от повторных ударов
        );

        boolean hitSomeone = false;

        for (LivingEntity target : targets) {
            if (collider.checkCollision(bonePosition, target)) {
                boolean success = target.hurt(entity.damageSources().mobAttack(entity), collider.damage);

                if (success) {
                    collider.hitTargets.add(target.getUUID());
                    hitSomeone = true;

                    LogHelper.info("[EnhancedBoneColliderManager] 💥 {} HIT! Bone '{}' damaged '{}' for {} damage",
                            collider.type, collider.boneName, target.getName().getString(), collider.damage);

                    spawnHitEffects(bonePosition, target.position());
                } else {
                    LogHelper.warn("[EnhancedBoneColliderManager] ❌ Failed to damage '{}'",
                            target.getName().getString());
                }
            }
        }

        return hitSomeone;
    }

    /**
     * Эффекты попадания
     */
    private void spawnHitEffects(Vec3 weaponPos, Vec3 targetPos) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            // Частицы урона
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    targetPos.x, targetPos.y + 1.0, targetPos.z, 5, 0.2, 0.2, 0.2, 0.1);

            // Частицы крита
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    weaponPos.x, weaponPos.y, weaponPos.z, 8, 0.3, 0.3, 0.3, 0.2);
        }
    }

    /**
     * Очистка всех коллайдеров
     */
    public void cleanup() {
        activeColliders.clear();
        LogHelper.info("[EnhancedBoneColliderManager] Cleanup completed");
    }

    /**
     * Проверки состояния
     */
    public boolean hasActiveColliders() {
        return activeColliders.values().stream().anyMatch(c -> c.isActive);
    }

    public Collection<BoneCollider> getActiveColliders() {
        return activeColliders.values().stream().filter(c -> c.isActive).toList();
    }

    public boolean isDebugVisualizationEnabled() {
        return debugVisualization;
    }
}