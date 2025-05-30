package com.custommobsforge.custommobsforge.server.behavior;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для управления коллайдерами костей во время атак (SERVER ONLY)
 */
public class BoneColliderManager {
    private final Map<String, BoneCollider> activeBoneColliders = new ConcurrentHashMap<>();
    private final CustomMobEntity entity;

    public BoneColliderManager(CustomMobEntity entity) {
        this.entity = entity;
    }

    public static class BoneCollider {
        public final String boneName;
        public final Vec3 size;
        public final Vec3 offset;
        public final float damage;
        public final double radius;
        public final Set<UUID> hitTargets = new HashSet<>();
        public boolean isActive = false;
        public long activationTime = 0;

        public BoneCollider(String boneName, Vec3 size, Vec3 offset, float damage, double radius) {
            this.boneName = boneName;
            this.size = size;
            this.offset = offset;
            this.damage = damage;
            this.radius = radius;
        }
    }

    public void activateBoneCollider(String boneName, Vec3 size, Vec3 offset, float damage, double radius) {
        BoneCollider collider = new BoneCollider(boneName, size, offset, damage, radius);
        collider.isActive = true;
        collider.activationTime = System.currentTimeMillis();
        activeBoneColliders.put(boneName, collider);

        LogHelper.info("[BoneColliderManager] Activated collider for bone: {} (damage: {}, radius: {})",
                boneName, damage, radius);
    }

    public void deactivateBoneCollider(String boneName) {
        BoneCollider collider = activeBoneColliders.get(boneName);
        if (collider != null) {
            collider.isActive = false;
            LogHelper.info("[BoneColliderManager] Deactivated collider for bone: {}", boneName);
        }
    }

    public void deactivateAllColliders() {
        for (BoneCollider collider : activeBoneColliders.values()) {
            collider.isActive = false;
        }
        LogHelper.info("[BoneColliderManager] Deactivated all colliders");
    }

    public void checkCollisions() {
        for (BoneCollider collider : activeBoneColliders.values()) {
            if (collider.isActive) {
                checkBoneCollision(collider);
            }
        }
    }

    private void checkBoneCollision(BoneCollider collider) {
        Vec3 bonePosition = entity.getBoneWorldPosition(collider.boneName);
        if (bonePosition == null) {
            LogHelper.warn("[BoneColliderManager] Could not get position for bone: {}", collider.boneName);
            return;
        }

        // ОТЛАДКА ПОЗИЦИИ КОСТИ
        LogHelper.info("[BoneColliderManager] Checking collision for bone '{}' at position ({}, {}, {})",
                collider.boneName,
                String.format("%.2f", bonePosition.x),
                String.format("%.2f", bonePosition.y),
                String.format("%.2f", bonePosition.z));

        Vec3 colliderPosition = bonePosition.add(collider.offset);

        AABB colliderAABB = new AABB(
                colliderPosition.subtract(collider.radius, collider.radius, collider.radius),
                colliderPosition.add(collider.radius, collider.radius, collider.radius)
        );

        // ОТЛАДКА КОЛЛАЙДЕРА
        LogHelper.info("[BoneColliderManager] Collider AABB: min({}, {}, {}) max({}, {}, {})",
                String.format("%.2f", colliderAABB.minX), String.format("%.2f", colliderAABB.minY), String.format("%.2f", colliderAABB.minZ),
                String.format("%.2f", colliderAABB.maxX), String.format("%.2f", colliderAABB.maxY), String.format("%.2f", colliderAABB.maxZ));

        List<LivingEntity> targets = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                colliderAABB,
                target -> target != entity &&
                        target.isAlive() &&
                        !collider.hitTargets.contains(target.getUUID())
        );

        LogHelper.info("[BoneColliderManager] Found {} potential targets in range", targets.size());

        for (LivingEntity target : targets) {
            LogHelper.info("[BoneColliderManager] Attempting to damage target: {}", target.getName().getString());

            boolean success = target.hurt(entity.damageSources().mobAttack(entity), collider.damage);
            if (success) {
                collider.hitTargets.add(target.getUUID());

                LogHelper.info("[BoneColliderManager] 💥 SUCCESS! Bone '{}' hit {} for {} damage",
                        collider.boneName, target.getName().getString(), collider.damage);

                spawnHitEffects(colliderPosition, target.position());
            } else {
                LogHelper.warn("[BoneColliderManager] ❌ FAILED to damage target: {}", target.getName().getString());
            }
        }
    }

    private void spawnHitEffects(Vec3 weaponPos, Vec3 targetPos) {
        if (!entity.level().isClientSide) {
            ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    targetPos.x, targetPos.y + 1.0, targetPos.z,
                    8, 0.3, 0.3, 0.3, 0.1
            );

            ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CRIT,
                    weaponPos.x, weaponPos.y, weaponPos.z,
                    12, 0.4, 0.4, 0.4, 0.2
            );

            ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    weaponPos.x, weaponPos.y, weaponPos.z,
                    3, 0.5, 0.5, 0.5, 0.1
            );
        }
    }

    public void cleanup() {
        activeBoneColliders.clear();
        LogHelper.info("[BoneColliderManager] Cleaned up all colliders");
    }

    public Collection<BoneCollider> getActiveColliders() {
        return activeBoneColliders.values().stream()
                .filter(collider -> collider.isActive)
                .toList();
    }

    public boolean hasActiveColliders() {
        return activeBoneColliders.values().stream().anyMatch(collider -> collider.isActive);
    }
}