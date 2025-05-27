package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// AzureLib импорты
import mod.azure.azurelib.core.animatable.model.CoreGeoBone;
import mod.azure.azurelib.core.animation.AnimationController;
import mod.azure.azurelib.core.animation.AnimationProcessor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Исполнитель узла атаки с поддержкой bone tracking через AzureLib
 */
public class AttackNodeExecutor implements NodeExecutor {

    // Кеш для оптимизации
    private static final Map<String, BoneCache> BONE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 2000; // 2 секунды

    // Кеш данных костей
    private static class BoneCache {
        final List<CoreGeoBone> bones;
        final String cacheKey;

        BoneCache(List<CoreGeoBone> bones, String cacheKey) {
            this.bones = bones;
            this.cacheKey = cacheKey;
        }
    }

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // Получаем параметры атаки
        double damage = getParameter(node, "damage", 3.0, Double.class);
        double range = getParameter(node, "range", 3.0, Double.class);
        double angle = getParameter(node, "angle", 90.0, Double.class);

        // Параметры для bone tracking
        boolean useBoneTracking = getParameter(node, "use_bone_tracking", false, Boolean.class);
        String boneNames = getParameter(node, "bone_name", "rightArm", String.class);
        double boneRadius = getParameter(node, "bone_radius", 0.5, Double.class);

        String animationId = getAnimationId(node);

        System.out.println("[AttackNode] Executing attack - bone tracking: " + useBoneTracking +
                ", bones: " + boneNames + " for entity " + entity.getId());

        // Проверяем кулдаун
        long lastAttackTime = executor.getBlackboard().getLongValue(nodeId + ":last_attack", 0L);
        long attackCooldown = getParameter(node, "cooldown", 1000L, Long.class);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime < attackCooldown) {
            System.out.println("[AttackNode] Attack on cooldown for entity " + entity.getId());
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Выбираем режим атаки
        if (useBoneTracking) {
            return executeBoneBasedAttack(entity, node, executor, animationId, boneNames, boneRadius, damage);
        } else {
            return executeTraditionalAttack(entity, node, executor, animationId, damage, range, angle);
        }
    }

    /**
     * Атака на основе отслеживания костей
     */
    private BehaviorTreeExecutor.NodeStatus executeBoneBasedAttack(CustomMobEntity entity, BehaviorNode node,
                                                                   BehaviorTreeExecutor executor, String animationId,
                                                                   String boneNames, double boneRadius, double damage) {
        String nodeId = node.getId();
        String attackPhase = executor.getBlackboard().getStringValue(nodeId + ":attack_phase", "start");

        switch (attackPhase) {
            case "start":
                return startBoneAttack(entity, node, executor, animationId, boneNames);

            case "tracking":
                return trackBoneCollisions(entity, node, executor, boneNames, boneRadius, damage);

            default:
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }

    /**
     * Запуск bone tracking атаки
     */
    private BehaviorTreeExecutor.NodeStatus startBoneAttack(CustomMobEntity entity, BehaviorNode node,
                                                            BehaviorTreeExecutor executor, String animationId, String boneNames) {
        String nodeId = node.getId();

        // Запускаем анимацию
        if (animationId != null && !animationId.isEmpty()) {
            playAttackAnimation(entity, animationId);
        }

        // Получаем кости с кешированием
        BoneCache boneCache = getCachedBones(entity, boneNames);
        if (boneCache == null || boneCache.bones.isEmpty()) {
            System.err.println("[AttackNode] No bones found for patterns: " + boneNames);
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Сохраняем начальные позиции костей
        Map<String, Vec3> initialPositions = new HashMap<>();
        for (CoreGeoBone bone : boneCache.bones) {
            Vec3 pos = getBoneWorldPosition(entity, bone);
            initialPositions.put(bone.getName(), pos);
        }

        executor.getBlackboard().setValue(nodeId + ":bone_positions", initialPositions);
        executor.getBlackboard().setValue(nodeId + ":hit_targets", new HashSet<UUID>());
        executor.getBlackboard().setValue(nodeId + ":attack_phase", "tracking");
        executor.getBlackboard().setValue(nodeId + ":start_time", System.currentTimeMillis());
        executor.getBlackboard().setValue(nodeId + ":bone_cache", boneCache);

        System.out.println("[AttackNode] Started bone attack with " + boneCache.bones.size() + " bones");

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Отслеживание коллизий костей
     */
    private BehaviorTreeExecutor.NodeStatus trackBoneCollisions(CustomMobEntity entity, BehaviorNode node,
                                                                BehaviorTreeExecutor executor, String boneNames,
                                                                double boneRadius, double damage) {
        String nodeId = node.getId();

        BoneCache boneCache = (BoneCache) executor.getBlackboard().getValue(nodeId + ":bone_cache", null);
        if (boneCache == null) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        @SuppressWarnings("unchecked")
        Map<String, Vec3> lastBonePositions = (Map<String, Vec3>) executor.getBlackboard().getValue(nodeId + ":bone_positions", new HashMap<String, Vec3>());
        @SuppressWarnings("unchecked")
        Set<UUID> hitTargets = (Set<UUID>) executor.getBlackboard().getValue(nodeId + ":hit_targets", new HashSet<UUID>());

        Map<String, Vec3> currentBonePositions = new HashMap<>();

        // Обрабатываем каждую кость
        for (CoreGeoBone bone : boneCache.bones) {
            Vec3 currentPos = getBoneWorldPosition(entity, bone);
            Vec3 lastPos = lastBonePositions.get(bone.getName());

            currentBonePositions.put(bone.getName(), currentPos);

            if (currentPos != null && lastPos != null) {
                // Вычисляем скорость движения кости
                double boneVelocity = currentPos.distanceTo(lastPos) * 20; // блоки/сек

                // Минимальная скорость для нанесения урона
                double minVelocity = getParameter(node, "min_velocity", 0.5, Double.class);

                if (boneVelocity >= minVelocity) {
                    // Создаем путь движения кости
                    List<Vec3> bonePath = interpolateBonePath(lastPos, currentPos, 2);

                    // Проверяем коллизии вдоль пути
                    for (Vec3 pathPoint : bonePath) {
                        List<LivingEntity> targets = findTargetsAtPoint(entity, pathPoint, boneRadius);

                        for (LivingEntity target : targets) {
                            if (!hitTargets.contains(target.getUUID())) {
                                // Урон зависит от скорости кости
                                double velocityMultiplier = Math.min(boneVelocity / minVelocity, 2.5);
                                double finalDamage = damage * velocityMultiplier;

                                // Наносим урон
                                target.hurt(entity.damageSources().mobAttack(entity), (float) finalDamage);
                                hitTargets.add(target.getUUID());

                                // Эффекты попадания
                                spawnBoneHitEffects(entity, pathPoint, target, boneVelocity, bone.getName());

                                System.out.println("[AttackNode] Bone hit by '" + bone.getName() + "' on " +
                                        target.getName().getString() + " (velocity: " +
                                        String.format("%.1f", boneVelocity) + ", damage: " +
                                        String.format("%.1f", finalDamage) + ")");
                            }
                        }
                    }
                }
            }
        }

        // Сохраняем состояние
        executor.getBlackboard().setValue(nodeId + ":bone_positions", currentBonePositions);
        executor.getBlackboard().setValue(nodeId + ":hit_targets", hitTargets);

        // Проверяем завершение атаки
        if (isBoneAttackComplete(entity, executor, nodeId)) {
            cleanupBoneAttack(executor, nodeId);
            executor.getBlackboard().setValue(nodeId + ":last_attack", System.currentTimeMillis());
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Кешированное получение костей
     */
    private BoneCache getCachedBones(CustomMobEntity entity, String boneNames) {
        String cacheKey = entity.getClass().getSimpleName() + ":" + boneNames;
        long currentTime = System.currentTimeMillis();

        // Проверяем актуальность кеша
        Long cacheTime = CACHE_TIMESTAMPS.get(cacheKey);
        if (cacheTime != null && (currentTime - cacheTime) < CACHE_DURATION) {
            BoneCache cached = BONE_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        // Создаем новый кеш
        try {
            AnimationProcessor<?> processor = getAnimationProcessor(entity);
            if (processor == null) return null;

            List<String> boneNameList = parseBoneNames(boneNames);
            List<CoreGeoBone> bones = new ArrayList<>();

            for (String boneName : boneNameList) {
                List<CoreGeoBone> foundBones = findBonesWithPattern(processor, boneName);
                bones.addAll(foundBones);
            }

            BoneCache cache = new BoneCache(bones, cacheKey);

            // Сохраняем в кеш
            BONE_CACHE.put(cacheKey, cache);
            CACHE_TIMESTAMPS.put(cacheKey, currentTime);

            return cache;

        } catch (Exception e) {
            System.err.println("[AttackNode] Error creating bone cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Получение мировой позиции кости
     */
    private Vec3 getBoneWorldPosition(CustomMobEntity entity, CoreGeoBone bone) {
        try {
            // Получаем локальную позицию кости
            float localX = bone.getPosX();
            float localY = bone.getPosY();
            float localZ = bone.getPosZ();

            // Применяем поворот сущности
            double entityYaw = Math.toRadians(entity.getYRot());
            double cosYaw = Math.cos(entityYaw);
            double sinYaw = Math.sin(entityYaw);

            double worldX = localX * cosYaw - localZ * sinYaw;
            double worldZ = localX * sinYaw + localZ * cosYaw;

            // Добавляем позицию сущности
            Vec3 entityPos = entity.position();
            return entityPos.add(worldX, localY + 1.0, worldZ);

        } catch (Exception e) {
            return entity.position().add(0, 1.5, 2.0);
        }
    }

    /**
     * Получение AnimationProcessor из entity
     */
    private AnimationProcessor<?> getAnimationProcessor(CustomMobEntity entity) {
        try {
            var cache = entity.getAnimatableInstanceCache();
            if (cache == null) return null;

            var manager = cache.getManagerForId(entity.getId());
            if (manager == null) return null;

            var controllers = manager.getAnimationControllers();
            if (controllers.isEmpty()) return null;

            AnimationController<?> controller = controllers.values().iterator().next();
            if (controller == null) return null;

            // Получаем поле lastModel через рефлексию
            try {
                var field = controller.getClass().getDeclaredField("lastModel");
                field.setAccessible(true);
                Object lastModel = field.get(controller);

                if (lastModel != null) {
                    var method = lastModel.getClass().getMethod("getAnimationProcessor");
                    return (AnimationProcessor<?>) method.invoke(lastModel);
                }
            } catch (Exception e) {
                System.err.println("[AttackNode] Error accessing lastModel: " + e.getMessage());
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Парсинг имен костей
     */
    private List<String> parseBoneNames(String boneNames) {
        List<String> result = new ArrayList<>();
        if (boneNames == null || boneNames.isEmpty()) return result;

        String[] parts = boneNames.split("[,;]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Поиск костей по паттерну
     */
    private List<CoreGeoBone> findBonesWithPattern(AnimationProcessor<?> processor, String pattern) {
        List<CoreGeoBone> foundBones = new ArrayList<>();
        Collection<CoreGeoBone> allBones = processor.getRegisteredBones();

        if (pattern.contains("*")) {
            // Wildcard паттерн
            String regex = pattern.replace("*", ".*");
            for (CoreGeoBone bone : allBones) {
                if (bone.getName().matches(regex)) {
                    foundBones.add(bone);
                }
            }
        } else if (pattern.startsWith("group:")) {
            // Группа костей
            String groupName = pattern.substring(6);
            foundBones.addAll(findBoneGroup(allBones, groupName));
        } else {
            // Точное имя кости
            for (CoreGeoBone bone : allBones) {
                if (bone.getName().equals(pattern)) {
                    foundBones.add(bone);
                    break;
                }
            }
        }

        return foundBones;
    }

    /**
     * Поиск группы костей
     */
    private List<CoreGeoBone> findBoneGroup(Collection<CoreGeoBone> allBones, String groupName) {
        List<CoreGeoBone> group = new ArrayList<>();

        for (CoreGeoBone bone : allBones) {
            String name = bone.getName().toLowerCase();

            switch (groupName.toLowerCase()) {
                case "weapons":
                    if (name.contains("sword") || name.contains("weapon") || name.contains("blade")) {
                        group.add(bone);
                    }
                    break;
                case "arms":
                    if (name.contains("arm") || name.contains("hand")) {
                        group.add(bone);
                    }
                    break;
                case "right":
                    if (name.contains("right") || name.startsWith("r_")) {
                        group.add(bone);
                    }
                    break;
                case "left":
                    if (name.contains("left") || name.startsWith("l_")) {
                        group.add(bone);
                    }
                    break;
            }
        }

        return group;
    }

    /**
     * Создание пути интерполяции
     */
    private List<Vec3> interpolateBonePath(Vec3 start, Vec3 end, int steps) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            path.add(start.lerp(end, t));
        }
        return path;
    }

    /**
     * Поиск целей в точке
     */
    private List<LivingEntity> findTargetsAtPoint(CustomMobEntity entity, Vec3 point, double radius) {
        AABB searchBox = new AABB(
                point.x - radius, point.y - radius, point.z - radius,
                point.x + radius, point.y + radius, point.z + radius
        );

        return entity.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                target -> target != entity && target.isAlive()
        );
    }

    /**
     * Эффекты попадания кости
     */
    private void spawnBoneHitEffects(CustomMobEntity entity, Vec3 position, LivingEntity target, double velocity, String boneName) {
        if (!entity.level().isClientSide) {
            int particleCount = Math.min((int)(velocity * 2), 8);

            if (boneName.toLowerCase().contains("sword") || boneName.toLowerCase().contains("blade")) {
                ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                        position.x, position.y, position.z,
                        2, 0.5, 0.5, 0.5, 0.1
                );
            } else {
                ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.CRIT,
                        position.x, position.y, position.z,
                        particleCount, 0.3, 0.3, 0.3, velocity * 0.1
                );
            }
        }
    }

    /**
     * Проверка завершения bone атаки
     */
    private boolean isBoneAttackComplete(CustomMobEntity entity, BehaviorTreeExecutor executor, String nodeId) {
        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);
        long maxDuration = 4000; // 4 секунды максимум

        return (System.currentTimeMillis() - startTime) > maxDuration ||
                !entity.hasTreeAnimation;
    }

    /**
     * Очистка bone атаки
     */
    private void cleanupBoneAttack(BehaviorTreeExecutor executor, String nodeId) {
        executor.getBlackboard().removeValue(nodeId + ":bone_positions");
        executor.getBlackboard().removeValue(nodeId + ":hit_targets");
        executor.getBlackboard().removeValue(nodeId + ":attack_phase");
        executor.getBlackboard().removeValue(nodeId + ":start_time");
        executor.getBlackboard().removeValue(nodeId + ":bone_cache");
    }

    /**
     * Традиционная атака
     */
    private BehaviorTreeExecutor.NodeStatus executeTraditionalAttack(CustomMobEntity entity, BehaviorNode node,
                                                                     BehaviorTreeExecutor executor, String animationId,
                                                                     double damage, double range, double angle) {
        List<LivingEntity> targets = findTargetsInRange(entity, range, angle);

        if (targets.isEmpty()) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        if (animationId != null && !animationId.isEmpty()) {
            playAttackAnimation(entity, animationId);
        }

        for (LivingEntity target : targets) {
            target.hurt(entity.damageSources().mobAttack(entity), (float) damage);
        }

        executor.getBlackboard().setValue(node.getId() + ":last_attack", System.currentTimeMillis());
        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }

    // Вспомогательные методы
    private List<LivingEntity> findTargetsInRange(CustomMobEntity entity, double range, double angle) {
        List<LivingEntity> targets = new ArrayList<>();

        LivingEntity currentTarget = entity.getTarget();
        if (currentTarget != null && currentTarget.isAlive() &&
                entity.distanceTo(currentTarget) <= range &&
                isInAttackAngle(entity, currentTarget, angle)) {
            targets.add(currentTarget);
            return targets;
        }

        AABB searchArea = entity.getBoundingBox().inflate(range);
        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(
                Player.class,
                searchArea,
                player -> player.isAlive() && isInAttackAngle(entity, player, angle)
        );

        targets.addAll(nearbyPlayers);
        return targets;
    }

    private boolean isInAttackAngle(CustomMobEntity entity, LivingEntity target, double maxAngle) {
        if (maxAngle >= 360.0) return true;

        double lookX = Math.sin(Math.toRadians(-entity.getYRot()));
        double lookZ = Math.cos(Math.toRadians(-entity.getYRot()));

        double deltaX = target.getX() - entity.getX();
        double deltaZ = target.getZ() - entity.getZ();

        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (distance == 0) return true;

        deltaX /= distance;
        deltaZ /= distance;

        double dotProduct = lookX * deltaX + lookZ * deltaZ;
        double angleRadians = Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct)));
        double angleDegrees = Math.toDegrees(angleRadians);

        return angleDegrees <= maxAngle / 2.0;
    }

    private void playAttackAnimation(CustomMobEntity entity, String animationId) {
        if (entity.getMobData() != null && entity.getMobData().getAnimations() != null) {
            var animationMapping = entity.getMobData().getAnimations().get(animationId.toUpperCase());
            if (animationMapping != null) {
                entity.setAnimation(animationMapping.getAnimationName(),
                        animationMapping.isLoop(),
                        animationMapping.getSpeed());
            } else {
                entity.setAnimation(animationId, false, 1.0f);
            }
        } else {
            entity.setAnimation(animationId, false, 1.0f);
        }
    }

    private String getAnimationId(BehaviorNode node) {
        String animationId = getParameter(node, "animation", null, String.class);
        if (animationId == null || animationId.isEmpty()) {
            animationId = node.getAnimationId();
        }
        return animationId;
    }

    /**
     * Очистка кеша (вызывать периодически)
     */
    public static void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        CACHE_TIMESTAMPS.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > CACHE_DURATION * 3) {
                BONE_CACHE.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}