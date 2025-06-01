package com.custommobsforge.custommobsforge.server.behavior;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Упрощенный менеджер коллайдеров костей
 */
public class BoneColliderManager {
    private final Map<String, SimpleBoneCollider> activeColliders = new ConcurrentHashMap<>();
    private final CustomMobEntity entity;
    private boolean debugVisualization = false;
    private long lastVisualizationTime = 0;

    public BoneColliderManager(CustomMobEntity entity) {
        this.entity = entity;
        LogHelper.info("[BoneColliderManager] Created for entity {}", entity.getId());
    }

    /**
     * Простой коллайдер кости
     */
    public static class SimpleBoneCollider {
        public final String boneName;
        public final float damage;
        public final double radius;
        public final Set<UUID> hitTargets = new HashSet<>();
        public boolean isActive = false;
        public long activationTime = 0;

        public SimpleBoneCollider(String boneName, float damage, double radius) {
            this.boneName = boneName;
            this.damage = damage;
            this.radius = radius;
            this.activationTime = System.currentTimeMillis();
        }
    }

    /**
     * Расширенный коллайдер кости с несколькими точками проверки
     */
    public static class ExtendedBoneCollider extends SimpleBoneCollider {
        public final int checkPoints; // Количество точек проверки вдоль кости
        public final double boneLength; // Длина кости для оружия

        public ExtendedBoneCollider(String boneName, float damage, double radius, double boneLength) {
            super(boneName, damage, radius);
            this.boneLength = boneLength;
            // Чем длиннее кость, тем больше точек проверки
            this.checkPoints = Math.max(3, (int)(boneLength * 2));
        }
    }

    /**
     * Активирует коллайдер для кости
     */
    public void activateBoneCollider(String boneName, float damage, double radius) {
        // Определяем длину кости по её типу
        double boneLength = estimateBoneLength(boneName);

        ExtendedBoneCollider collider = new ExtendedBoneCollider(boneName, damage, radius, boneLength);
        collider.isActive = true;
        activeColliders.put(boneName, collider);

        LogHelper.info("[BoneColliderManager] ⚔️ Activated extended collider: bone='{}', damage={}, radius={}, length={}, points={}",
                boneName, damage, radius, boneLength, collider.checkPoints);
    }

    private final Map<String, Vec3> lastKnownBonePositions = new ConcurrentHashMap<>();

    /**
     * Оценивает длину кости по её имени
     */
    private double estimateBoneLength(String boneName) {
        String lower = boneName.toLowerCase();

        // Длинное оружие
        if (lower.contains("greatsword") || lower.contains("spear") || lower.contains("pike") || lower.contains("halberd")) {
            return 3.0; // 3 блока
        }
        // Среднее оружие
        else if (lower.contains("sword") || lower.contains("axe") || lower.contains("staff") || lower.contains("mace")) {
            return 2.0; // 2 блока
        }
        // Короткое оружие
        else if (lower.contains("dagger") || lower.contains("knife") || lower.contains("fist")) {
            return 1.0; // 1 блок
        }
        // Части тела
        else if (lower.contains("arm") || lower.contains("leg")) {
            return 1.5;
        }
        // По умолчанию
        else {
            return 1.0;
        }
    }

    /**
     * Деактивирует коллайдер кости
     */
    public void deactivateBoneCollider(String boneName) {
        SimpleBoneCollider collider = activeColliders.get(boneName);
        if (collider != null) {
            collider.isActive = false;
            LogHelper.info("[BoneColliderManager] ❄️ Deactivated collider: bone='{}'", boneName);
        }
    }

    /**
     * Деактивирует все коллайдеры
     */
    public void deactivateAllColliders() {
        for (SimpleBoneCollider collider : activeColliders.values()) {
            collider.isActive = false;
        }
        LogHelper.info("[BoneColliderManager] Deactivated all colliders");
    }

    /**
     * Включает/выключает визуализацию
     */
    public void setDebugVisualization(boolean enabled) {
        this.debugVisualization = enabled;
        LogHelper.info("[BoneColliderManager] Debug visualization: {}", enabled ? "ON" : "OFF");
    }

    /**
     * Включает детальное логирование для отладки
     */
    public void enableDetailedDebug() {
        this.debugVisualization = true;
        LogHelper.info("[BoneColliderManager] Detailed debug ENABLED");
    }

    /**
     * Основной метод проверки коллизий с улучшенной отладкой
     */
    public void checkCollisions() {
        long currentTime = System.currentTimeMillis();
        boolean foundCollisions = false;

        for (SimpleBoneCollider collider : activeColliders.values()) {
            if (!collider.isActive) continue;

            // Получаем текущую позицию кости
            Vec3 bonePosition = entity.getBoneWorldPosition(collider.boneName);
            if (bonePosition == null) {
                LogHelper.warn("[BoneColliderManager] No position for bone: {}", collider.boneName);
                continue;
            }

            // ОТЛАДКА: Сравниваем с позицией моба
            Vec3 entityPos = entity.position();
            double distanceFromEntity = bonePosition.distanceTo(entityPos);

            if (distanceFromEntity > 10) {
                LogHelper.error("[BoneColliderManager] ⚠️ BONE TOO FAR FROM ENTITY!");
                LogHelper.error("  Entity at: ({}, {}, {})",
                        String.format("%.1f", entityPos.x),
                        String.format("%.1f", entityPos.y),
                        String.format("%.1f", entityPos.z));
                LogHelper.error("  Bone at: ({}, {}, {})",
                        String.format("%.1f", bonePosition.x),
                        String.format("%.1f", bonePosition.y),
                        String.format("%.1f", bonePosition.z));
                LogHelper.error("  Distance: {} blocks", String.format("%.1f", distanceFromEntity));

                // Используем fallback позицию
                bonePosition = entity.position().add(entity.getLookAngle().scale(2)).add(0, entity.getBbHeight() * 0.8, 0);
                LogHelper.error("  Using fallback position: ({}, {}, {})",
                        String.format("%.1f", bonePosition.x),
                        String.format("%.1f", bonePosition.y),
                        String.format("%.1f", bonePosition.z));
            }

            // Улучшенная визуализация
            if (debugVisualization && currentTime - lastVisualizationTime > 50) {
                visualizeColliderDetailed(bonePosition, collider, entityPos);
            }

            // Проверяем коллизии
            if (checkBoneCollision(collider, bonePosition)) {
                foundCollisions = true;
            }
        }

        if (debugVisualization && currentTime - lastVisualizationTime > 50) {
            lastVisualizationTime = currentTime;
        }
    }

    /**
     * Детальная визуализация для отладки
     */
    private void visualizeColliderDetailed(Vec3 bonePos, SimpleBoneCollider collider, Vec3 entityPos) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // 1. Позиция энтити - зелёные частицы
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                entityPos.x, entityPos.y + entity.getBbHeight() / 2, entityPos.z,
                10, 0.2, 0.2, 0.2, 0.0);

        // 2. Позиция кости - синие частицы
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                bonePos.x, bonePos.y, bonePos.z,
                10, 0.1, 0.1, 0.1, 0.0);

        // 3. Линия от энтити к кости - жёлтые частицы
        int lineParticles = 10;
        for (int i = 0; i <= lineParticles; i++) {
            double t = (double)i / lineParticles;
            Vec3 linePoint = entityPos.lerp(bonePos, t);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    linePoint.x, linePoint.y, linePoint.z,
                    1, 0, 0, 0, 0);
        }

        // 4. Сфера коллайдера
        visualizeCollider(bonePos, collider.radius, collider.boneName);

        // 5. Сообщение в чат админам каждую секунду
        if (System.currentTimeMillis() % 1000 < 100) {
            for (Player player : serverLevel.getPlayers(p -> p.hasPermissions(2) && p.distanceTo(entity) < 20)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        String.format("§e[DEBUG] Entity: (%.1f, %.1f, %.1f) | Bone: (%.1f, %.1f, %.1f) | Dist: %.1f",
                                entityPos.x, entityPos.y, entityPos.z,
                                bonePos.x, bonePos.y, bonePos.z,
                                entityPos.distanceTo(bonePos))
                ));
            }
        }
    }

    /**
     * Проверяет коллизию для одной кости
     */
    private boolean checkBoneCollision(SimpleBoneCollider collider, Vec3 bonePosition) {
        // ДОБАВЛЕНО: Специальная отладка для оружия
        if (collider.boneName.equals("greatsword") || collider.boneName.contains("sword")) {
            LogHelper.info("[SERVER] Greatsword collision check at: ({}, {}, {})",
                    String.format("%.2f", bonePosition.x),
                    String.format("%.2f", bonePosition.y),
                    String.format("%.2f", bonePosition.z));

            // Дополнительная отладка - время с начала атаки
            long timeSinceActivation = System.currentTimeMillis() - collider.activationTime;
            LogHelper.info("[SERVER] Time since activation: {}ms", timeSinceActivation);
        }

        // Оригинальный лог
        LogHelper.info("[BoneColliderManager] Checking bone '{}' at ({}, {}, {})",
                collider.boneName,
                String.format("%.1f", bonePosition.x),
                String.format("%.1f", bonePosition.y),
                String.format("%.1f", bonePosition.z));

        // Создаем расширенную область поиска для длинного оружия
        double searchRadius = collider.radius;
        if (collider instanceof ExtendedBoneCollider) {
            ExtendedBoneCollider extCollider = (ExtendedBoneCollider) collider;
            searchRadius = collider.radius + extCollider.boneLength;
        }

        AABB searchArea = new AABB(
                bonePosition.subtract(searchRadius, searchRadius, searchRadius),
                bonePosition.add(searchRadius, searchRadius, searchRadius)
        );

        // Ищем потенциальные цели
        List<LivingEntity> targets = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                target -> target != entity &&
                        target.isAlive() &&
                        !collider.hitTargets.contains(target.getUUID())
        );

        LogHelper.info("[BoneColliderManager] Found {} potential targets in area", targets.size());

        // ДОБАВЛЕНО: Логируем границы области поиска
        if (!targets.isEmpty() || collider.boneName.contains("sword")) {
            LogHelper.info("[BoneColliderManager] Search area: from ({}, {}, {}) to ({}, {}, {})",
                    String.format("%.2f", searchArea.minX),
                    String.format("%.2f", searchArea.minY),
                    String.format("%.2f", searchArea.minZ),
                    String.format("%.2f", searchArea.maxX),
                    String.format("%.2f", searchArea.maxY),
                    String.format("%.2f", searchArea.maxZ)
            );
        }

        // Если это расширенный коллайдер, проверяем несколько точек
        if (collider instanceof ExtendedBoneCollider) {
            ExtendedBoneCollider extCollider = (ExtendedBoneCollider) collider;

            // ИСПРАВЛЕНИЕ: Получаем вторую позицию кости через некоторое время
            // чтобы вычислить реальное направление движения
            Vec3 previousPosition = lastKnownBonePositions.get(collider.boneName + "_prev");
            Vec3 boneDirection;

            if (previousPosition != null && !previousPosition.equals(bonePosition)) {
                // Используем реальное направление движения кости
                boneDirection = bonePosition.subtract(previousPosition).normalize();

                // Сохраняем текущую позицию как предыдущую для следующего кадра
                lastKnownBonePositions.put(collider.boneName + "_prev", bonePosition);
            } else {
                // Fallback на направление взгляда
                boneDirection = entity.getLookAngle();
                lastKnownBonePositions.put(collider.boneName + "_prev", bonePosition);
            }

            // Проверяем коллизии вдоль всей длины кости
            boolean hitSomeone = false;
            for (int i = 0; i <= extCollider.checkPoints; i++) {
                double t = (double)i / extCollider.checkPoints;

                // ИСПРАВЛЕНИЕ: Точки проверки от базовой позиции НАЗАД по направлению кости
                // Это покроет всю длину меча от рукояти до кончика
                Vec3 checkPoint = bonePosition.subtract(boneDirection.scale(extCollider.boneLength * t));

                // Отладка
                if (collider.boneName.contains("sword") && (i == 0 || i == extCollider.checkPoints)) {
                    LogHelper.info("[BoneColliderManager] Check point {}/{} at ({}, {}, {})",
                            i, extCollider.checkPoints,
                            String.format("%.1f", checkPoint.x),
                            String.format("%.1f", checkPoint.y),
                            String.format("%.1f", checkPoint.z));
                }

                // Проверяем коллизию в этой точке
                if (checkCollisionAtPoint(collider, checkPoint, targets)) {
                    hitSomeone = true;
                    // НЕ прерываем цикл - проверяем все точки!
                }
            }

            // ДОПОЛНИТЕЛЬНО: Проверяем промежуточные точки между позициями
            if (previousPosition != null && previousPosition.distanceTo(bonePosition) > 0.5) {
                // Интерполируем между предыдущей и текущей позицией
                for (int i = 1; i < 4; i++) {
                    double t = i / 4.0;
                    Vec3 interpolatedPos = previousPosition.lerp(bonePosition, t);

                    // Проверяем вдоль длины меча в интерполированной позиции
                    for (int j = 0; j <= 3; j++) {
                        double s = j / 3.0;
                        Vec3 checkPoint = interpolatedPos.subtract(boneDirection.scale(extCollider.boneLength * s));

                        if (checkCollisionAtPoint(collider, checkPoint, targets)) {
                            hitSomeone = true;
                        }
                    }
                }
            }

            return hitSomeone;
        } else {
            // Обычная проверка для простого коллайдера
            return checkCollisionAtPoint(collider, bonePosition, targets);
        }
    }

    /**
     * Проверяет коллизию в конкретной точке
     */
    private boolean checkCollisionAtPoint(SimpleBoneCollider collider, Vec3 checkPoint, List<LivingEntity> targets) {
        boolean hitSomeone = false;

        for (LivingEntity target : targets) {
            if (collider.hitTargets.contains(target.getUUID())) {
                continue; // Уже ударили эту цель
            }

            double distance = target.position().distanceTo(checkPoint);

            LogHelper.info("[BoneColliderManager] Target '{}' at ({}, {}, {}) - distance: {} (radius: {})",
                    target.getName().getString(),
                    String.format("%.2f", target.position().x),
                    String.format("%.2f", target.position().y),
                    String.format("%.2f", target.position().z),
                    String.format("%.2f", distance),
                    collider.radius);

            if (distance <= collider.radius + 0.5) { // Добавляем небольшой допуск
                boolean success = target.hurt(entity.damageSources().mobAttack(entity), collider.damage);

                if (success) {
                    collider.hitTargets.add(target.getUUID());
                    hitSomeone = true;

                    LogHelper.info("[BoneColliderManager] 💥 HIT! Bone '{}' damaged '{}' for {} damage at distance {}",
                            collider.boneName, target.getName().getString(), collider.damage,
                            String.format("%.2f", distance));

                    spawnHitEffects(checkPoint, target.position());
                } else {
                    LogHelper.warn("[BoneColliderManager] ❌ Failed to damage '{}'", target.getName().getString());
                }
            }
        }

        return hitSomeone;
    }

    /**
     * Улучшенная визуализация коллайдера
     */
    private void visualizeCollider(Vec3 center, double radius, String boneName) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // 1. Центральная точка - яркая синяя частица
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                center.x, center.y, center.z, 3, 0.1, 0.1, 0.1, 0.0);

        // 2. Границы коллайдера - красные частицы по кругу
        int circleParticles = 12;
        for (int i = 0; i < circleParticles; i++) {
            double angle = 2 * Math.PI * i / circleParticles;

            // Горизонтальный круг на уровне центра
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    x, center.y, z, 1, 0, 0, 0, 0);

            // Вертикальные круги
            double y1 = center.y + radius * 0.5;
            double y2 = center.y - radius * 0.5;
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    x, y1, z, 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    x, y2, z, 1, 0, 0, 0, 0);
        }

        // 3. Стрелка направления - показывает куда направлена кость
        Vec3 forward = entity.getLookAngle();

        // Для расширенного коллайдера показываем всю длину
        if (activeColliders.get(boneName) instanceof ExtendedBoneCollider extCollider) {
            for (int i = 0; i <= 5; i++) {
                double t = i / 5.0;
                Vec3 arrowPoint = center.add(forward.scale(extCollider.boneLength * t));
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        arrowPoint.x, arrowPoint.y, arrowPoint.z, 1, 0, 0, 0, 0);
            }
        }

        // 4. Информация в чат - только админам и реже
        for (Player player : serverLevel.getPlayers(p -> p.hasPermissions(2) && p.distanceTo(entity) < 15)) {
            if (System.currentTimeMillis() % 2000 < 100) { // Каждые 2 секунды
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        String.format("§c[%s] §f(%.2f, %.2f, %.2f) §7R:%.1f",
                                boneName, center.x, center.y, center.z, radius)
                ));
            }
        }
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
        LogHelper.info("[BoneColliderManager] Cleanup completed");
    }

    /**
     * Проверки состояния
     */
    public boolean hasActiveColliders() {
        return activeColliders.values().stream().anyMatch(c -> c.isActive);
    }

    public Collection<SimpleBoneCollider> getActiveColliders() {
        return activeColliders.values().stream().filter(c -> c.isActive).toList();
    }

    public boolean isDebugVisualizationEnabled() {
        return debugVisualization;
    }
}