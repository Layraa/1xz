package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.animation.AnimationDurationCache;
import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber
public class MobSpawnEventHandler {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        // Инициализируем менеджер конфигураций при первом событии спавна
        if (event.getLevel() instanceof ServerLevel && !event.getLevel().isClientSide) {
            if (event.getEntity() instanceof LivingEntity) {
                MobConfigManager.init((ServerLevel) event.getLevel());
            }
        }

        // Настраиваем кастомного моба при появлении в мире
        if (event.getEntity() instanceof CustomMobEntity && !event.getLevel().isClientSide) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();
            ServerLevel level = (ServerLevel) event.getLevel();

            LogHelper.info("=== MOB SPAWN DEBUG ===");
            LogHelper.info("Entity ID: {}", entity.getId());
            LogHelper.info("Mob ID: {}", entity.getMobId());
            LogHelper.info("Has MobData: {}", (entity.getMobData() != null));

            String mobId = entity.getMobId();
            if (mobId == null || mobId.isEmpty()) {
                LogHelper.error("❌ ERROR: Entity has no mob ID!");
                return;
            }

            // НОВОЕ: Всегда загружаем данные моба заново
            LogHelper.info("Loading mob data for ID: {}", mobId);
            MobData mobData = MobConfigManager.loadMobConfig(mobId, level);
            if (mobData == null) {
                LogHelper.error("❌ ERROR: Could not load mob data for ID: {}", mobId);
                return;
            }

            // НОВОЕ: Загружаем дерево поведения отдельно если его нет
            if (mobData.getBehaviorTree() == null || mobData.getBehaviorTree().getNodes() == null || mobData.getBehaviorTree().getNodes().isEmpty()) {
                LogHelper.info("🔍 Loading separate behavior tree file for mob: {}", mobId);

                try {
                    Path behaviorFile = level.getServer().getWorldPath(LevelResource.ROOT)
                            .resolve("custommobsforge").resolve("behaviors").resolve(mobId + ".json");

                    LogHelper.info("Looking for behavior tree file: {}", behaviorFile);

                    if (Files.exists(behaviorFile)) {
                        String json = new String(Files.readAllBytes(behaviorFile), StandardCharsets.UTF_8);
                        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
                        com.custommobsforge.custommobsforge.common.data.BehaviorTree tree =
                                gson.fromJson(json, com.custommobsforge.custommobsforge.common.data.BehaviorTree.class);

                        if (tree != null && tree.getNodes() != null && !tree.getNodes().isEmpty()) {
                            mobData.setBehaviorTree(tree);
                            LogHelper.info("✅ Loaded behavior tree from separate file: {} nodes", tree.getNodes().size());
                        } else {
                            LogHelper.warn("❌ Behavior tree file exists but contains no valid nodes");
                        }
                    } else {
                        LogHelper.warn("❌ Behavior tree file not found: {}", behaviorFile);
                    }
                } catch (Exception e) {
                    LogHelper.error("❌ Error loading behavior tree file: {}", e.getMessage());
                    e.printStackTrace();
                }
            }

            entity.setMobData(mobData);
            LogHelper.info("✅ Mob data set successfully");

            if (mobData.getAnimationFilePath() != null) {
                String animPath = mobData.getAnimationFilePath();

                // Проверяем, не загружен ли уже этот файл
                if (AnimationDurationCache.getAnimationInfo(animPath, "idle") == null) {
                    LogHelper.info("🎬 Loading animation file into cache: {}", animPath);
                    AnimationDurationCache.loadAnimationFile(
                            level.getServer(),
                            animPath
                    );
                } else {
                    LogHelper.debug("Animation file already cached: {}", animPath);
                }
            }

            // Проверяем дерево поведения
            if (mobData.getBehaviorTree() != null) {
                var tree = mobData.getBehaviorTree();
                LogHelper.info("🌳 Behavior tree found:");
                LogHelper.info("  - Tree ID: {}", tree.getId());
                LogHelper.info("  - Tree Name: {}", tree.getName());
                LogHelper.info("  - Nodes: {}", (tree.getNodes() != null ? tree.getNodes().size() : 0));
                LogHelper.info("  - Connections: {}", (tree.getConnections() != null ? tree.getConnections().size() : 0));
                LogHelper.info("  - Root Node: {}", (tree.getRootNode() != null ? tree.getRootNode().getType() : "NULL"));

                if (tree.getNodes() != null && !tree.getNodes().isEmpty()) {
                    // Создаем и добавляем BehaviorTreeExecutor
                    LogHelper.info("🚀 Creating BehaviorTreeExecutor...");
                    BehaviorTreeExecutor executor = new BehaviorTreeExecutor(entity, tree);
                    entity.goalSelector.addGoal(1, executor);
                    LogHelper.info("✅ BehaviorTreeExecutor added to entity goals");

                    // Проверяем, что executor может запуститься
                    LogHelper.info("🔍 Executor can use: {}", executor.canUse());

                    // Выводим информацию о узлах
                    LogHelper.info("📋 Tree nodes:");
                    tree.getNodes().forEach(node ->
                            LogHelper.info("  - {} ({}) ID: {}", node.getType(), node.getDescription(), node.getId())
                    );

                    // Выводим информацию о связях
                    if (tree.getConnections() != null) {
                        LogHelper.info("🔗 Tree connections:");
                        tree.getConnections().forEach(conn ->
                                LogHelper.info("  - {} -> {}", conn.getSourceNodeId(), conn.getTargetNodeId())
                        );
                    }
                } else {
                    LogHelper.error("❌ Behavior tree has no nodes!");
                }
            } else {
                LogHelper.warn("❌ No behavior tree found in mob data");
            }

            LogHelper.info("🎬 Playing SPAWN animation");
            entity.playAnimation("SPAWN");
            LogHelper.info("=== END MOB SPAWN DEBUG ===");
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Обрабатываем события получения урона для кастомных мобов
        if (event.getEntity() instanceof CustomMobEntity && !event.getEntity().level().isClientSide) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            LogHelper.info("CustomMob {} received {} damage", entity.getId(), event.getAmount());
            entity.playAnimation("HURT");

            // Триггерим событие урона в дереве поведения
            triggerDamageEvent(entity, event.getAmount(),
                    event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Обрабатываем события смерти для кастомных мобов
        if (event.getEntity() instanceof CustomMobEntity && !event.getEntity().level().isClientSide) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            LogHelper.info("CustomMob {} died", entity.getId());
            entity.playAnimation("DEATH");

            // Триггерим событие смерти в дереве поведения
            triggerDeathEvent(entity);
        }
    }

    /**
     * Триггерит событие урона в дереве поведения
     */
    private static void triggerDamageEvent(CustomMobEntity entity, float damageAmount, boolean fromPlayer) {
        entity.goalSelector.getAvailableGoals().forEach(goal -> {
            if (goal.getGoal() instanceof BehaviorTreeExecutor) {
                BehaviorTreeExecutor executor = (BehaviorTreeExecutor) goal.getGoal();
                executor.getBlackboard().setValue("damage_triggered", true);
                executor.getBlackboard().setValue("last_damage_amount", (double) damageAmount);
                executor.getBlackboard().setValue("last_damage_from_player", fromPlayer);

                LogHelper.info("Triggered damage event in behavior tree for entity {}", entity.getId());
            }
        });
    }

    /**
     * Триггерит событие смерти в дереве поведения
     */
    private static void triggerDeathEvent(CustomMobEntity entity) {
        entity.goalSelector.getAvailableGoals().forEach(goal -> {
            if (goal.getGoal() instanceof BehaviorTreeExecutor) {
                BehaviorTreeExecutor executor = (BehaviorTreeExecutor) goal.getGoal();
                executor.getBlackboard().setValue("death_triggered", true);

                LogHelper.info("Triggered death event in behavior tree for entity {}", entity.getId());
            }
        });
    }
}