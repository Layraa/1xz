package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// AzureLib импорты
import mod.azure.azurelib.core.animatable.model.CoreGeoBone;
import mod.azure.azurelib.core.animation.AnimationController;

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
        double boneRadius = getParameter(node, "bone_radius", 1.0, Double.class);

        String animationId = getAnimationId(node);

        LogHelper.info("[AttackNode] === ATTACK EXECUTION START ===");
        LogHelper.info("[AttackNode] Entity: {}", entity.getId());
        LogHelper.info("[AttackNode] Bone tracking: {}", useBoneTracking);
        LogHelper.info("[AttackNode] Bone names: '{}'", boneNames);
        LogHelper.info("[AttackNode] Animation: '{}'", animationId);
        LogHelper.info("[AttackNode] Damage: {}", damage);

        // Проверяем кулдаун
        long lastAttackTime = executor.getBlackboard().getLongValue(nodeId + ":last_attack", 0L);
        long attackCooldown = getParameter(node, "cooldown", 1000L, Long.class);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime < attackCooldown) {
            LogHelper.debug("[AttackNode] Attack on cooldown for entity {}", entity.getId());
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

        LogHelper.debug("[AttackNode] Bone attack phase: {}", attackPhase);

        switch (attackPhase) {
            case "start":
                return startBoneAttack(entity, node, executor, animationId, boneNames);

            case "preparation":
                return prepareBoneTracking(entity, node, executor, boneNames);

            case "tracking":
                return trackBoneCollisions(entity, node, executor, boneNames, boneRadius, damage);

            default:
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }

    /**
     * Запуск bone tracking атаки с задержкой
     */
    private BehaviorTreeExecutor.NodeStatus startBoneAttack(CustomMobEntity entity, BehaviorNode node,
                                                            BehaviorTreeExecutor executor, String animationId, String boneNames) {
        String nodeId = node.getId();

        LogHelper.info("[AttackNode] === STARTING BONE ATTACK ===");

        // Запускаем анимацию
        if (animationId != null && !animationId.isEmpty()) {
            playAttackAnimation(entity, animationId);
            LogHelper.info("[AttackNode] ✅ Started attack animation: {}", animationId);
        } else {
            LogHelper.warn("[AttackNode] No animation specified for attack!");
        }

        // Устанавливаем время начала и переходим в фазу подготовки
        executor.getBlackboard().setValue(nodeId + ":start_time", System.currentTimeMillis());
        executor.getBlackboard().setValue(nodeId + ":attack_phase", "preparation");

        LogHelper.info("[AttackNode] Phase changed to: PREPARATION");
        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Подготовка к отслеживанию костей с принудительной инициализацией
     */
    private BehaviorTreeExecutor.NodeStatus prepareBoneTracking(CustomMobEntity entity, BehaviorNode node,
                                                                BehaviorTreeExecutor executor, String boneNames) {
        String nodeId = node.getId();
        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);

        long preparationDelay = getParameter(node, "preparation_delay", 1000L, Long.class);
        long maxWaitTime = getParameter(node, "max_wait_time", 5000L, Long.class); // Увеличил до 5 секунд

        long elapsedTime = System.currentTimeMillis() - startTime;

        LogHelper.debug("[AttackNode] PREPARATION phase - elapsed: {}ms, needed: {}ms", elapsedTime, preparationDelay);

        // Сначала ждем базовую задержку
        if (elapsedTime < preparationDelay) {
            return BehaviorTreeExecutor.NodeStatus.RUNNING;
        }

        // Принудительно обновляем кости
        if (elapsedTime == preparationDelay || (elapsedTime % 500 == 0)) { // Каждые 500мс
            LogHelper.info("[AttackNode] Forcing bone update...");
            entity.forceUpdateBones();
        }

        LogHelper.info("[AttackNode] === CHECKING BONE AVAILABILITY ===");

        // Проверяем доступность костей
        List<CoreGeoBone> allBones = getAllBonesFromModel(entity);
        if (allBones.isEmpty()) {
            if (elapsedTime < maxWaitTime) {
                LogHelper.warn("[AttackNode] Bones not available yet, waiting... ({}ms elapsed)", elapsedTime);
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            } else {
                LogHelper.error("[AttackNode] ❌ No bones available after {}ms, falling back to traditional attack", elapsedTime);
                return executeTraditionalAttackFallback(entity, node, executor);
            }
        }

        LogHelper.info("[AttackNode] ✅ Found {} bones, proceeding with bone tracking", allBones.size());

        // Остальная логика остается такой же...
        BoneCache boneCache = getCachedBones(entity, boneNames);
        if (boneCache == null || boneCache.bones.isEmpty()) {
            LogHelper.error("[AttackNode] ❌ No bones found for pattern: '{}'", boneNames);

            LogHelper.error("[AttackNode] Available bones ({}):", allBones.size());
            for (CoreGeoBone bone : allBones) {
                LogHelper.error("  - '{}'", bone.getName());
            }

            return executeTraditionalAttackFallback(entity, node, executor);
        }

        if (elapsedTime == preparationDelay) {
            LogHelper.info("[AttackNode] Force initializing model...");
            entity.forceInitializeModel();
        }

        // Сохраняем начальные позиции костей
        Map<String, Vec3> initialPositions = new HashMap<>();
        for (CoreGeoBone bone : boneCache.bones) {
            Vec3 pos = getBoneWorldPosition(entity, bone);
            if (pos != null) {
                initialPositions.put(bone.getName(), pos);
                LogHelper.info("[AttackNode] Initial bone position for '{}': ({}, {}, {})",
                        bone.getName(), String.format("%.2f", pos.x), String.format("%.2f", pos.y), String.format("%.2f", pos.z));
            }
        }

        executor.getBlackboard().setValue(nodeId + ":bone_positions", initialPositions);
        executor.getBlackboard().setValue(nodeId + ":hit_targets", new HashSet<UUID>());
        executor.getBlackboard().setValue(nodeId + ":attack_phase", "tracking");
        executor.getBlackboard().setValue(nodeId + ":bone_cache", boneCache);

        LogHelper.info("[AttackNode] ✅ Started bone tracking with {} bones", boneCache.bones.size());
        LogHelper.info("[AttackNode] Phase changed to: TRACKING");

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Fallback к традиционной атаке если кости недоступны
     */
    private BehaviorTreeExecutor.NodeStatus executeTraditionalAttackFallback(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        LogHelper.info("[AttackNode] === EXECUTING TRADITIONAL ATTACK FALLBACK ===");

        // Очищаем состояние bone tracking
        String nodeId = node.getId();
        executor.getBlackboard().removeValue(nodeId + ":attack_phase");
        executor.getBlackboard().removeValue(nodeId + ":start_time");

        // Получаем параметры для традиционной атаки
        double damage = getParameter(node, "damage", 3.0, Double.class);
        double range = getParameter(node, "range", 3.0, Double.class);
        double angle = getParameter(node, "angle", 90.0, Double.class);
        String animationId = getAnimationId(node);

        // Выполняем традиционную атаку
        return executeTraditionalAttack(entity, node, executor, animationId, damage, range, angle);
    }

    /**
     * ПРАВИЛЬНОЕ получение костей из BakedGeoModel
     */
    /**
     * ПРАВИЛЬНОЕ получение костей из AnimationProcessor
     */
    /**
     * ПРАВИЛЬНОЕ получение костей через Renderer (как в вики AzureLib)
     */
    private List<CoreGeoBone> getAllBonesFromModel(CustomMobEntity entity) {
        List<CoreGeoBone> allBones = new ArrayList<>();

        try {
            // Способ 1: Получаем через renderer на клиенте (если доступен)
            if (entity.level().isClientSide) {
                try {
                    // Пытаемся получить рендерер сущности
                    var entityRenderDispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
                    var renderer = entityRenderDispatcher.getRenderer(entity);

                    LogHelper.debug("[AttackNode] Found renderer: {}", renderer.getClass().getSimpleName());

                    // Ищем методы для получения костей
                    if (renderer instanceof mod.azure.azurelib.renderer.GeoEntityRenderer) {
                        LogHelper.debug("[AttackNode] Renderer is GeoEntityRenderer");

                        // Пытаемся получить model или context
                        var fields = renderer.getClass().getDeclaredFields();
                        for (var field : fields) {
                            field.setAccessible(true);
                            Object fieldValue = field.get(renderer);

                            LogHelper.debug("[AttackNode] Renderer field '{}': {}", field.getName(),
                                    fieldValue != null ? fieldValue.getClass().getSimpleName() : "null");

                            // Ищем BoneCache или BakedGeoModel
                            if (fieldValue != null) {
                                if (field.getName().toLowerCase().contains("cache") ||
                                        field.getName().toLowerCase().contains("model")) {

                                    try {
                                        // Пытаемся получить getBakedModel
                                        var getBakedModelMethod = fieldValue.getClass().getMethod("getBakedModel");
                                        Object bakedModel = getBakedModelMethod.invoke(fieldValue);

                                        if (bakedModel instanceof mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) {
                                            mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel coreBakedModel =
                                                    (mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) bakedModel;

                                            List<? extends CoreGeoBone> bones = coreBakedModel.getBones();
                                            if (bones != null && !bones.isEmpty()) {
                                                for (CoreGeoBone bone : bones) {
                                                    collectAllBones(bone, allBones);
                                                }

                                                LogHelper.info("[AttackNode] ✅ Found {} bones from renderer field '{}'!",
                                                        allBones.size(), field.getName());
                                                return allBones;
                                            }
                                        }
                                    } catch (Exception e) {
                                        LogHelper.debug("[AttackNode] Field '{}' doesn't have getBakedModel: {}",
                                                field.getName(), e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LogHelper.debug("[AttackNode] Client-side renderer approach failed: {}", e.getMessage());
                }
            }

            // Способ 2: Серверная версия - ищем через AnimationController
            var cache = entity.getAnimatableInstanceCache();
            if (cache == null) {
                LogHelper.error("[AttackNode] AnimatableInstanceCache is null");
                return allBones;
            }

            var manager = cache.getManagerForId(entity.getId());
            if (manager == null) {
                LogHelper.error("[AttackNode] AnimationManager is null");
                return allBones;
            }

            var controllers = manager.getAnimationControllers();
            if (controllers.isEmpty()) {
                LogHelper.error("[AttackNode] No animation controllers");
                return allBones;
            }

            AnimationController<?> controller = controllers.values().iterator().next();
            if (controller == null) {
                LogHelper.error("[AttackNode] Animation controller is null");
                return allBones;
            }

            LogHelper.debug("[AttackNode] === DEEP CONTROLLER INSPECTION ===");
            LogHelper.debug("[AttackNode] Controller class: {}", controller.getClass().getName());

            // Инспектируем ВСЕ поля контроллера
            inspectObjectForBones(controller, "controller", allBones, 0);

            if (!allBones.isEmpty()) {
                LogHelper.info("[AttackNode] ✅ Found {} bones through deep inspection!", allBones.size());
                return allBones;
            }

            LogHelper.error("[AttackNode] Could not find bones through any method");

        } catch (Exception e) {
            LogHelper.error("[AttackNode] Error getting bones from model: {}", e.getMessage());
            e.printStackTrace();
        }

        return allBones;
    }

    /**
     * Глубокая инспекция объекта для поиска костей
     */
    private void inspectObjectForBones(Object obj, String objName, List<CoreGeoBone> allBones, int depth) {
        if (obj == null || depth > 3) return; // Ограничиваем глубину

        try {
            Class<?> clazz = obj.getClass();
            LogHelper.debug("[AttackNode] Inspecting {} ({})", objName, clazz.getSimpleName());

            // Проверяем поля
            var fields = clazz.getDeclaredFields();
            for (var field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);

                    if (fieldValue == null) continue;

                    LogHelper.debug("[AttackNode] Field '{}.{}': {}", objName, field.getName(),
                            fieldValue.getClass().getSimpleName());

                    // Прямая проверка на BakedGeoModel
                    if (fieldValue instanceof mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) {
                        mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel bakedModel =
                                (mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) fieldValue;

                        List<? extends CoreGeoBone> bones = bakedModel.getBones();
                        if (bones != null && !bones.isEmpty()) {
                            for (CoreGeoBone bone : bones) {
                                collectAllBones(bone, allBones);
                            }
                            LogHelper.info("[AttackNode] ✅ Found BakedGeoModel in field '{}.{}'!", objName, field.getName());
                            return;
                        }
                    }

                    // Проверяем на AnimationProcessor
                    if (fieldValue instanceof mod.azure.azurelib.core.animation.AnimationProcessor) {
                        mod.azure.azurelib.core.animation.AnimationProcessor<?> processor =
                                (mod.azure.azurelib.core.animation.AnimationProcessor<?>) fieldValue;

                        Collection<CoreGeoBone> bones = processor.getRegisteredBones();
                        if (bones != null && !bones.isEmpty()) {
                            allBones.addAll(bones);
                            LogHelper.info("[AttackNode] ✅ Found AnimationProcessor in field '{}.{}'!", objName, field.getName());
                            return;
                        }
                    }

                    // Проверяем методы объекта
                    if (field.getName().toLowerCase().contains("model") ||
                            field.getName().toLowerCase().contains("cache") ||
                            field.getName().toLowerCase().contains("context")) {

                        try {
                            var getBakedModelMethod = fieldValue.getClass().getMethod("getBakedModel");
                            Object bakedModel = getBakedModelMethod.invoke(fieldValue);

                            if (bakedModel instanceof mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) {
                                mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel coreBakedModel =
                                        (mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) bakedModel;

                                List<? extends CoreGeoBone> bones = coreBakedModel.getBones();
                                if (bones != null && !bones.isEmpty()) {
                                    for (CoreGeoBone bone : bones) {
                                        collectAllBones(bone, allBones);
                                    }
                                    LogHelper.info("[AttackNode] ✅ Found BakedGeoModel via method in field '{}.{}'!",
                                            objName, field.getName());
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            // Не все объекты имеют getBakedModel
                        }

                        // Углубляемся в поиск
                        if (depth < 2) {
                            inspectObjectForBones(fieldValue, objName + "." + field.getName(), allBones, depth + 1);
                            if (!allBones.isEmpty()) return;
                        }
                    }

                } catch (Exception e) {
                    // Игнорируем ошибки доступа к полям
                }
            }

            // Проверяем методы
            var methods = clazz.getDeclaredMethods();
            for (var method : methods) {
                String methodName = method.getName().toLowerCase();

                if ((methodName.contains("bone") || methodName.contains("model") || methodName.contains("cache")) &&
                        method.getParameterCount() == 0) {

                    try {
                        method.setAccessible(true);
                        Object result = method.invoke(obj);

                        if (result != null) {
                            LogHelper.debug("[AttackNode] Method '{}.{}()': {}", objName, method.getName(),
                                    result.getClass().getSimpleName());

                            if (result instanceof mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) {
                                mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel bakedModel =
                                        (mod.azure.azurelib.core.animatable.model.CoreBakedGeoModel) result;

                                List<? extends CoreGeoBone> bones = bakedModel.getBones();
                                if (bones != null && !bones.isEmpty()) {
                                    for (CoreGeoBone bone : bones) {
                                        collectAllBones(bone, allBones);
                                    }
                                    LogHelper.info("[AttackNode] ✅ Found BakedGeoModel via method '{}.{}'!",
                                            objName, method.getName());
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки вызова методов
                    }
                }
            }

        } catch (Exception e) {
            LogHelper.debug("[AttackNode] Error inspecting {}: {}", objName, e.getMessage());
        }
    }

    /**
     * Получение AnimationProcessor напрямую из контроллера
     */
    private mod.azure.azurelib.core.animation.AnimationProcessor<?> getAnimationProcessorDirect(AnimationController<?> controller) {
        try {
            LogHelper.debug("[AttackNode] Searching for AnimationProcessor in controller fields...");

            // Ищем поле processor в контроллере
            var fields = controller.getClass().getDeclaredFields();
            for (var field : fields) {
                field.setAccessible(true);
                Object fieldValue = field.get(controller);

                if (fieldValue instanceof mod.azure.azurelib.core.animation.AnimationProcessor) {
                    LogHelper.info("[AttackNode] ✅ Found AnimationProcessor in field: {}", field.getName());
                    return (mod.azure.azurelib.core.animation.AnimationProcessor<?>) fieldValue;
                }

                LogHelper.debug("[AttackNode] Field '{}': {}", field.getName(),
                        fieldValue != null ? fieldValue.getClass().getSimpleName() : "null");
            }

            // Если не нашли напрямую, ищем через методы
            try {
                var method = controller.getClass().getMethod("getAnimationProcessor");
                Object processor = method.invoke(controller);
                if (processor instanceof mod.azure.azurelib.core.animation.AnimationProcessor) {
                    LogHelper.info("[AttackNode] ✅ Found AnimationProcessor via getAnimationProcessor() method");
                    return (mod.azure.azurelib.core.animation.AnimationProcessor<?>) processor;
                }
            } catch (Exception e) {
                LogHelper.debug("[AttackNode] No getAnimationProcessor() method: {}", e.getMessage());
            }

            // Последняя попытка - через родительские классы
            Class<?> clazz = controller.getClass();
            while (clazz != null) {
                LogHelper.debug("[AttackNode] Checking class: {}", clazz.getSimpleName());

                var fields2 = clazz.getDeclaredFields();
                for (var field : fields2) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(controller);

                    if (fieldValue instanceof mod.azure.azurelib.core.animation.AnimationProcessor) {
                        LogHelper.info("[AttackNode] ✅ Found AnimationProcessor in parent class field: {}", field.getName());
                        return (mod.azure.azurelib.core.animation.AnimationProcessor<?>) fieldValue;
                    }
                }

                clazz = clazz.getSuperclass();
            }

            LogHelper.error("[AttackNode] Could not find AnimationProcessor in controller");
            return null;

        } catch (Exception e) {
            LogHelper.error("[AttackNode] Error getting AnimationProcessor: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Рекурсивно собирает все кости включая дочерние
     */
    private void collectAllBones(CoreGeoBone bone, List<CoreGeoBone> allBones) {
        allBones.add(bone);
        LogHelper.debug("[AttackNode] Added bone: '{}'", bone.getName());

        // Рекурсивно добавляем дочерние кости
        List<? extends CoreGeoBone> children = bone.getChildBones();
        if (children != null) {
            for (CoreGeoBone child : children) {
                collectAllBones(child, allBones);
            }
        }
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
            LogHelper.error("[AttackNode] ❌ BoneCache is null during tracking!");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        @SuppressWarnings("unchecked")
        Map<String, Vec3> lastBonePositions = (Map<String, Vec3>) executor.getBlackboard().getValue(nodeId + ":bone_positions", new HashMap<String, Vec3>());
        @SuppressWarnings("unchecked")
        Set<UUID> hitTargets = (Set<UUID>) executor.getBlackboard().getValue(nodeId + ":hit_targets", new HashSet<UUID>());

        Map<String, Vec3> currentBonePositions = new HashMap<>();
        boolean anyBoneMoving = false;
        double minVelocity = getParameter(node, "min_velocity", 0.05, Double.class); // Низкий порог
        int hitCount = 0;

        LogHelper.debug("[AttackNode] === BONE TRACKING TICK ===");
        LogHelper.debug("[AttackNode] Tracking {} bones", boneCache.bones.size());

        // Найдем всех потенциальных целей в радиусе
        List<LivingEntity> allNearbyTargets = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(10), // Большая область поиска
                target -> target != entity && target.isAlive()
        );

        LogHelper.debug("[AttackNode] Found {} potential targets nearby", allNearbyTargets.size());

        // Обрабатываем каждую кость
        for (CoreGeoBone bone : boneCache.bones) {
            String boneName = bone.getName();
            Vec3 currentPos = getBoneWorldPosition(entity, bone);
            Vec3 lastPos = lastBonePositions.get(boneName);

            currentBonePositions.put(boneName, currentPos);

            if (currentPos != null && lastPos != null) {
                // Вычисляем скорость движения кости
                double distance = currentPos.distanceTo(lastPos);
                double boneVelocity = distance * 20; // блоки/сек (20 TPS)

                LogHelper.debug("[AttackNode] Bone '{}': distance={}, velocity={} b/s",
                        boneName, String.format("%.4f", distance), String.format("%.3f", boneVelocity));

                if (boneVelocity >= minVelocity) {
                    anyBoneMoving = true;

                    LogHelper.info("[AttackNode] ⚡ Bone '{}' is ATTACKING! (velocity: {} >= {})",
                            boneName, String.format("%.3f", boneVelocity), String.format("%.3f", minVelocity));

                    // Создаем путь движения кости с большим количеством точек
                    List<Vec3> bonePath = interpolateBonePath(lastPos, currentPos, 5);

                    // Проверяем коллизии вдоль пути
                    for (int i = 0; i < bonePath.size(); i++) {
                        Vec3 pathPoint = bonePath.get(i);

                        // Ищем цели в текущей точке пути
                        for (LivingEntity target : allNearbyTargets) {
                            if (hitTargets.contains(target.getUUID())) {
                                continue; // Уже ударили
                            }

                            double distanceToTarget = target.position().distanceTo(pathPoint);

                            if (distanceToTarget <= boneRadius) {
                                // ПОПАДАНИЕ!
                                LogHelper.info("[AttackNode] 💥 *** HIT DETECTED ***");
                                LogHelper.info("  Bone: {}", boneName);
                                LogHelper.info("  Target: {}", target.getName().getString());
                                LogHelper.info("  Distance: {}", String.format("%.3f", distanceToTarget));
                                LogHelper.info("  Velocity: {}", String.format("%.3f", boneVelocity));

                                // Урон зависит от скорости кости
                                double velocityMultiplier = Math.min(boneVelocity / minVelocity, 3.0);
                                double finalDamage = damage * velocityMultiplier;

                                LogHelper.info("  Velocity multiplier: {}", String.format("%.3f", velocityMultiplier));
                                LogHelper.info("  Final damage: {}", String.format("%.3f", finalDamage));

                                // Наносим урон
                                boolean hurtSuccess = target.hurt(entity.damageSources().mobAttack(entity), (float) finalDamage);
                                hitTargets.add(target.getUUID());
                                hitCount++;

                                LogHelper.info("  Hurt success: {}", hurtSuccess);

                                // Эффекты попадания
                                spawnBoneHitEffects(entity, pathPoint, target, boneVelocity, boneName);

                                // Дополнительные эффекты для игрока
                                if (target instanceof Player) {
                                    Player player = (Player) target;

                                    // Отбрасывание
                                    Vec3 knockback = currentPos.subtract(lastPos).normalize().scale(0.3);
                                    player.setDeltaMovement(player.getDeltaMovement().add(knockback));

                                    LogHelper.info("  Applied knockback to player: {}", knockback);
                                }
                            }
                        }
                    }
                } else {
                    LogHelper.debug("[AttackNode] Bone '{}' too slow (velocity: {} < {})",
                            boneName, String.format("%.3f", boneVelocity), String.format("%.3f", minVelocity));
                }
            } else {
                if (lastPos == null) {
                    LogHelper.debug("[AttackNode] No previous position for bone '{}'", boneName);
                }
                if (currentPos == null) {
                    LogHelper.warn("[AttackNode] Could not get current position for bone '{}'", boneName);
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

            LogHelper.info("[AttackNode] === ATTACK COMPLETE ===");
            LogHelper.info("Total targets hit: {}", hitTargets.size());

            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        LogHelper.debug("[AttackNode] Attack still running, any bone moving: {}, hits this tick: {}", anyBoneMoving, hitCount);
        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Простая система поиска костей (ИСПРАВЛЕННАЯ)
     */
    private List<CoreGeoBone> findBonesWithPattern(List<CoreGeoBone> allBones, String pattern) {
        List<CoreGeoBone> foundBones = new ArrayList<>();

        LogHelper.info("[AttackNode] Searching for bones with pattern: '{}'", pattern);
        LogHelper.info("[AttackNode] Available bones ({}): {}", allBones.size(),
                allBones.stream().map(CoreGeoBone::getName).collect(Collectors.toList()));

        if (pattern.contains(",")) {
            // Множественные имена: "sword1,sword2,rightArm"
            String[] boneNames = pattern.split(",");
            LogHelper.debug("[AttackNode] Searching for multiple bone names: {}", Arrays.toString(boneNames));

            for (String boneName : boneNames) {
                boneName = boneName.trim();
                for (CoreGeoBone bone : allBones) {
                    if (bone.getName().equals(boneName) || bone.getName().equalsIgnoreCase(boneName)) {
                        foundBones.add(bone);
                        LogHelper.info("[AttackNode] ✅ Found bone: '{}'", bone.getName());
                    }
                }
            }
        } else if (pattern.contains("*")) {
            // Wildcard: "*sword*", "right*"
            String regex = pattern.replace("*", ".*").toLowerCase();
            LogHelper.debug("[AttackNode] Using wildcard pattern: {}", regex);

            for (CoreGeoBone bone : allBones) {
                if (bone.getName().toLowerCase().matches(regex)) {
                    foundBones.add(bone);
                    LogHelper.info("[AttackNode] ✅ Found bone by wildcard: '{}'", bone.getName());
                }
            }
        } else {
            // Точное имя: "rightArm"
            LogHelper.debug("[AttackNode] Searching for exact bone name: '{}'", pattern);

            for (CoreGeoBone bone : allBones) {
                if (bone.getName().equals(pattern) || bone.getName().equalsIgnoreCase(pattern)) {
                    foundBones.add(bone);
                    LogHelper.info("[AttackNode] ✅ Found exact bone: '{}'", bone.getName());
                    break;
                }
            }
        }

        if (foundBones.isEmpty()) {
            LogHelper.error("[AttackNode] ❌ No bones found for pattern '{}'. Available bones:", pattern);
            for (CoreGeoBone bone : allBones) {
                LogHelper.error("  - '{}'", bone.getName());
            }
        } else {
            LogHelper.info("[AttackNode] Found {} bones for pattern '{}'", foundBones.size(), pattern);
        }

        return foundBones;
    }

    /**
     * Получение костей (ИСПРАВЛЕННАЯ ВЕРСИЯ)
     */
    private BoneCache getCachedBones(CustomMobEntity entity, String boneNames) {
        String cacheKey = entity.getClass().getSimpleName() + ":" + boneNames;
        long currentTime = System.currentTimeMillis();

        // Проверяем актуальность кеша
        Long cacheTime = CACHE_TIMESTAMPS.get(cacheKey);
        if (cacheTime != null && (currentTime - cacheTime) < CACHE_DURATION) {
            BoneCache cached = BONE_CACHE.get(cacheKey);
            if (cached != null) {
                LogHelper.debug("[AttackNode] Using cached bones for: {}", boneNames);
                return cached;
            }
        }

        // Создаем новый кеш
        try {
            // ИСПРАВЛЕНИЕ: Получаем кости из BakedGeoModel
            List<CoreGeoBone> allBones = getAllBonesFromModel(entity);
            if (allBones.isEmpty()) {
                LogHelper.error("[AttackNode] No bones found in model");
                return null;
            }

            List<String> boneNameList = parseBoneNames(boneNames);
            List<CoreGeoBone> bones = new ArrayList<>();

            for (String boneName : boneNameList) {
                List<CoreGeoBone> foundBones = findBonesWithPattern(allBones, boneName);
                bones.addAll(foundBones);
            }

            BoneCache cache = new BoneCache(bones, cacheKey);

            // Сохраняем в кеш
            BONE_CACHE.put(cacheKey, cache);
            CACHE_TIMESTAMPS.put(cacheKey, currentTime);

            LogHelper.info("[AttackNode] Created bone cache with {} bones", bones.size());
            return cache;

        } catch (Exception e) {
            LogHelper.error("[AttackNode] Error creating bone cache: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Получение мировой позиции кости
     */
    private Vec3 getBoneWorldPosition(CustomMobEntity entity, CoreGeoBone bone) {
        try {
            if (bone == null) {
                LogHelper.error("[AttackNode] Bone is null!");
                return entity.position().add(0, 1.5, 0);
            }

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

            // Применяем поворот сущности
            double entityYaw = Math.toRadians(entity.getYRot());
            double cosYaw = Math.cos(entityYaw);
            double sinYaw = Math.sin(entityYaw);

            // Учитываем поворот и масштаб кости
            double effectiveX = localX * scaleX;
            double effectiveZ = localZ * scaleZ;

            // Применяем поворот кости по Y (важно для атак)
            if (rotY != 0) {
                double boneYaw = Math.toRadians(rotY);
                double boneCos = Math.cos(boneYaw);
                double boneSin = Math.sin(boneYaw);
                double tempX = effectiveX * boneCos - effectiveZ * boneSin;
                double tempZ = effectiveX * boneSin + effectiveZ * boneCos;
                effectiveX = tempX;
                effectiveZ = tempZ;
            }

            // Применяем поворот сущности
            double worldX = effectiveX * cosYaw - effectiveZ * sinYaw;
            double worldZ = effectiveX * sinYaw + effectiveZ * cosYaw;

            // Добавляем позицию сущности
            Vec3 entityPos = entity.position();
            double entityHeight = entity.getBbHeight();

            // Корректируем высоту относительно центра сущности
            Vec3 boneWorldPos = entityPos.add(worldX, (localY * scaleY) + (entityHeight * 0.5), worldZ);

            return boneWorldPos;

        } catch (Exception e) {
            LogHelper.error("[AttackNode] ❌ Error getting bone world position for '{}': {}",
                    (bone != null ? bone.getName() : "null"), e.getMessage());

            // Fallback позиция перед сущностью
            Vec3 lookDirection = entity.getLookAngle();
            return entity.position().add(
                    lookDirection.x * 1.5,
                    entity.getBbHeight() * 0.75,
                    lookDirection.z * 1.5
            );
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
     * Эффекты попадания кости
     */
    private void spawnBoneHitEffects(CustomMobEntity entity, Vec3 position, LivingEntity target, double velocity, String boneName) {
        if (!entity.level().isClientSide) {
            int particleCount = Math.min((int)(velocity * 5), 15);

            ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CRIT,
                    position.x, position.y, position.z,
                    particleCount, 0.3, 0.3, 0.3, velocity * 0.1
            );
        }
    }

    /**
     * Проверка завершения bone атаки
     */
    private boolean isBoneAttackComplete(CustomMobEntity entity, BehaviorTreeExecutor executor, String nodeId) {
        long startTime = executor.getBlackboard().getLongValue(nodeId + ":start_time", 0L);
        long maxDuration = 5000; // 5 секунд максимум

        long elapsedTime = System.currentTimeMillis() - startTime;
        boolean timeExpired = elapsedTime > maxDuration;
        boolean animationFinished = !entity.hasTreeAnimation;

        LogHelper.debug("[AttackNode] Checking completion: elapsed={}ms, timeExpired={}, animationFinished={}",
                elapsedTime, timeExpired, animationFinished);

        return timeExpired || animationFinished;
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

        LogHelper.debug("[AttackNode] Cleaned up bone attack state");
    }

    /**
     * Традиционная атака (без отслеживания костей)
     */
    private BehaviorTreeExecutor.NodeStatus executeTraditionalAttack(CustomMobEntity entity, BehaviorNode node,
                                                                     BehaviorTreeExecutor executor, String animationId,
                                                                     double damage, double range, double angle) {
        LogHelper.info("[AttackNode] Executing traditional attack");

        List<LivingEntity> targets = findTargetsInRange(entity, range, angle);

        if (targets.isEmpty()) {
            LogHelper.debug("[AttackNode] No targets found in range");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        if (animationId != null && !animationId.isEmpty()) {
            playAttackAnimation(entity, animationId);
            LogHelper.info("[AttackNode] Started attack animation: {}", animationId);
        }

        LogHelper.info("[AttackNode] Attacking {} targets", targets.size());
        for (LivingEntity target : targets) {
            boolean success = target.hurt(entity.damageSources().mobAttack(entity), (float) damage);
            LogHelper.info("[AttackNode] Hit {}: {} damage, success: {}",
                    target.getName().getString(), damage, success);
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
                LogHelper.debug("[AttackNode] Using animation mapping: {} -> {}",
                        animationId, animationMapping.getAnimationName());
            } else {
                entity.setAnimation(animationId, false, 1.0f);
                LogHelper.debug("[AttackNode] Using direct animation: {}", animationId);
            }
        } else {
            entity.setAnimation(animationId, false, 1.0f);
            LogHelper.debug("[AttackNode] Using fallback animation: {}", animationId);
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