package com.custommobsforge.custommobsforge.common.entity;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.AnimationMapping;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import com.custommobsforge.custommobsforge.common.network.packet.MobDataPacket;
import com.custommobsforge.custommobsforge.common.config.ClientMobDataCache;
import mod.azure.azurelib.rewrite.animation.dispatch.command.AzCommand;
import mod.azure.azurelib.rewrite.animation.play_behavior.AzPlayBehaviors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomMobEntity extends PathfinderMob {

    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge-Common");

    private static final EntityDataAccessor<String> MOB_ID =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Boolean> IS_ATTACKING =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.BOOLEAN);

    private MobData mobData;

    // === СИСТЕМА СИНХРОНИЗАЦИИ КОСТЕЙ ===
    private Map<String, Vec3> lastKnownBonePositions = new ConcurrentHashMap<>();
    private long lastBoneUpdateTime = 0;
    private boolean isAttacking = false;
    private static final long BONE_DATA_TIMEOUT = 200; // 200мс

    // === ССЫЛКА НА СЕРВЕРНЫЙ МЕНЕДЖЕР ===
    private Object serverBoneColliderManager; // EnhancedBoneColliderManager только на сервере

    // Управление анимациями AzureLib 3.0
    private String currentAnimation = "";
    private boolean isLoopingAnimation = true;
    private float animationSpeed = 1.0f;
    private String lastPlayedAnimation = "";
    public boolean hasTreeAnimation = false;
    private long lastAnimationTime = 0;
    private static final long ANIMATION_COOLDOWN = 1000;

    public CustomMobEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // === МЕТОДЫ СИНХРОНИЗАЦИИ КОСТЕЙ ===

    /**
     * Обновляет позиции костей с клиента (вызывается через пакет)
     */
    public void updateBonePositions(Map<String, Vec3> bonePositions, long timestamp) {
        if (timestamp > lastBoneUpdateTime) {
            this.lastKnownBonePositions.putAll(bonePositions);
            this.lastBoneUpdateTime = timestamp;

            String side = this.level().isClientSide ? "CLIENT" : "SERVER";
            LOGGER.debug("[BoneSync-{}] Updated {} bone positions for entity {}",
                    side, bonePositions.size(), this.getId());
        }
    }

    /**
     * Получает мировую позицию кости (основной метод для атак)
     */
    public Vec3 getBoneWorldPosition(String boneName) {
        Vec3 position = lastKnownBonePositions.get(boneName);

        if (position != null) {
            if (System.currentTimeMillis() - lastBoneUpdateTime < BONE_DATA_TIMEOUT) {
                return position;
            }
        }

        // Fallback на виртуальную систему
        return getVirtualBonePositionWithAnimation(boneName);
    }

    /**
     * Виртуальные позиции с учетом текущей анимации и состояния
     */
    private Vec3 getVirtualBonePositionWithAnimation(String boneName) {
        Vec3 basePos = this.position();
        Vec3 lookDir = this.getLookAngle();
        double height = this.getBbHeight();

        // Вычисляем фазу анимации
        long currentTime = System.currentTimeMillis();
        double animationPhase = 0.0;

        if (this.isAttacking()) {
            long attackStartTime = currentTime - 500;
            long attackDuration = 2500;
            animationPhase = Math.min(1.0, (currentTime - attackStartTime) / (double)attackDuration);
        }

        switch (boneName.toLowerCase()) {
            case "greatsword":
            case "sword":
            case "weapon":
                return calculateWeaponPosition(basePos, lookDir, height, animationPhase);

            case "right_arm":
            case "rightarm":
                return calculateRightArmPosition(basePos, lookDir, height, animationPhase);

            case "left_arm":
            case "leftarm":
                return calculateLeftArmPosition(basePos, lookDir, height, animationPhase);

            case "head":
                return basePos.add(0, height * 0.9, 0);

            case "body":
            case "chest":
                return basePos.add(0, height * 0.6, 0);

            case "hurtbox":
            default:
                return basePos.add(0, height * 0.5, 0);
        }
    }

    /**
     * Вычисляет позицию оружия с учетом анимации
     */
    private Vec3 calculateWeaponPosition(Vec3 basePos, Vec3 lookDir, double height, double animationPhase) {
        if (this.isAttacking() && animationPhase > 0.0) {
            double swingAngle;
            double distance;
            double heightOffset;

            if (animationPhase < 0.3) {
                // Замах назад
                double t = animationPhase / 0.3;
                swingAngle = Math.PI * 0.2 + t * Math.PI * 0.3;
                distance = 1.5 + t * 0.5;
                heightOffset = height * (0.9 - t * 0.2);
            } else if (animationPhase < 0.7) {
                // Удар вперед
                double t = (animationPhase - 0.3) / 0.4;
                swingAngle = Math.PI * 0.5 + t * Math.PI * 0.4;
                distance = 2.0 + t * 0.8;
                heightOffset = height * (0.7 - t * 0.3);
            } else {
                // Завершение
                double t = (animationPhase - 0.7) / 0.3;
                swingAngle = Math.PI * 0.9 - t * Math.PI * 0.2;
                distance = 2.8 - t * 0.8;
                heightOffset = height * (0.4 + t * 0.2);
            }

            Vec3 rightDir = new Vec3(-lookDir.z, 0, lookDir.x).normalize();
            Vec3 forwardComponent = lookDir.scale(Math.cos(swingAngle) * distance);
            Vec3 sideComponent = rightDir.scale(Math.sin(swingAngle) * distance);

            return basePos.add(forwardComponent).add(sideComponent).add(0, heightOffset, 0);

        } else {
            // В покое - меч висит сбоку
            Vec3 rightDir = new Vec3(-lookDir.z, 0, lookDir.x).normalize();
            return basePos.add(rightDir.scale(0.8)).add(0, height * 0.6, 0);
        }
    }

    private Vec3 calculateRightArmPosition(Vec3 basePos, Vec3 lookDir, double height, double animationPhase) {
        Vec3 rightDir = new Vec3(-lookDir.z, 0, lookDir.x).normalize();

        if (this.isAttacking() && animationPhase > 0.0) {
            double extension = Math.sin(animationPhase * Math.PI) * 0.8;
            return basePos.add(rightDir.scale(0.6))
                    .add(lookDir.scale(extension))
                    .add(0, height * 0.8, 0);
        } else {
            return basePos.add(rightDir.scale(0.6)).add(0, height * 0.8, 0);
        }
    }

    private Vec3 calculateLeftArmPosition(Vec3 basePos, Vec3 lookDir, double height, double animationPhase) {
        Vec3 leftDir = new Vec3(lookDir.z, 0, -lookDir.x).normalize();

        if (this.isAttacking() && animationPhase > 0.0) {
            double extension = Math.sin(animationPhase * Math.PI) * 0.4;
            return basePos.add(leftDir.scale(0.6))
                    .add(lookDir.scale(extension * 0.5))
                    .add(0, height * 0.8, 0);
        } else {
            return basePos.add(leftDir.scale(0.6)).add(0, height * 0.8, 0);
        }
    }

    /**
     * Включает синхронизацию костей (вызывается при начале атаки)
     */
    public void startAttack() {
        this.isAttacking = true;
        this.entityData.set(IS_ATTACKING, true);

        // Создаем менеджер если его нет (только на сервере)
        if (this.serverBoneColliderManager == null && !this.level().isClientSide) {
            try {
                Class<?> enhancedManagerClass = Class.forName("com.custommobsforge.custommobsforge.server.behavior.EnhancedBoneColliderManager");
                this.serverBoneColliderManager = enhancedManagerClass.getConstructor(CustomMobEntity.class).newInstance(this);

                LOGGER.info("[CustomMobEntity] Created EnhancedBoneColliderManager for attack on entity {}", this.getId());
            } catch (Exception e) {
                LOGGER.error("[CustomMobEntity] Failed to create EnhancedBoneColliderManager: {}", e.getMessage());
            }
        }

        String side = this.level().isClientSide ? "CLIENT" : "SERVER";
        LOGGER.debug("[BoneSync-{}] Started attack mode for entity {}", side, this.getId());
    }

    /**
     * Отключает синхронизацию костей (вызывается при окончании атаки)
     */
    public void stopAttack() {
        this.isAttacking = false;
        this.entityData.set(IS_ATTACKING, false);
        this.lastKnownBonePositions.clear();

        // Очищаем коллайдеры через рефлексию
        if (serverBoneColliderManager != null && !this.level().isClientSide) {
            try {
                serverBoneColliderManager.getClass().getMethod("cleanup").invoke(serverBoneColliderManager);
            } catch (Exception e) {
                LOGGER.error("Error cleaning up bone colliders: {}", e.getMessage());
            }
        }

        String side = this.level().isClientSide ? "CLIENT" : "SERVER";
        LOGGER.debug("[BoneSync-{}] Stopped attack mode for entity {}", side, this.getId());
    }

    /**
     * Проверяет, находится ли моб в режиме атаки
     */
    public boolean isAttacking() {
        if (this.level().isClientSide) {
            return this.entityData.get(IS_ATTACKING);
        } else {
            return this.isAttacking;
        }
    }

    /**
     * Форсированная инициализация системы атак
     */
    public void forceInitializeAttackSystem() {
        if (!this.level().isClientSide && serverBoneColliderManager == null) {
            try {
                Class<?> enhancedManagerClass = Class.forName("com.custommobsforge.custommobsforge.server.behavior.EnhancedBoneColliderManager");
                this.serverBoneColliderManager = enhancedManagerClass.getConstructor(CustomMobEntity.class).newInstance(this);

                LOGGER.info("[CustomMobEntity] Force-initialized EnhancedBoneColliderManager for entity {}", this.getId());
            } catch (Exception e) {
                LOGGER.error("[CustomMobEntity] Failed to force-initialize EnhancedBoneColliderManager: {}", e.getMessage());
            }
        }
    }

    /**
     * Получает список всех синхронизированных костей
     */
    public List<String> getAvailableBoneNames() {
        List<String> boneNames = new ArrayList<>(lastKnownBonePositions.keySet());

        // Добавляем виртуальные кости как fallback
        if (!boneNames.contains("rightArm")) boneNames.add("rightArm");
        if (!boneNames.contains("leftArm")) boneNames.add("leftArm");
        if (!boneNames.contains("sword")) boneNames.add("sword");
        if (!boneNames.contains("weapon")) boneNames.add("weapon");
        if (!boneNames.contains("greatsword")) boneNames.add("greatsword");
        if (!boneNames.contains("head")) boneNames.add("head");
        if (!boneNames.contains("body")) boneNames.add("body");
        if (!boneNames.contains("hurtbox")) boneNames.add("hurtbox");

        return boneNames;
    }

    // === МЕТОДЫ ДЛЯ СЕРВЕРНОГО МЕНЕДЖЕРА КОЛЛАЙДЕРОВ ===

    public Object getServerBoneColliderManager() {
        return serverBoneColliderManager;
    }

    public void setServerBoneColliderManager(Object boneColliderManager) {
        this.serverBoneColliderManager = boneColliderManager;
    }

    public long getLastBoneUpdateTime() {
        return lastBoneUpdateTime;
    }

    public void clearTreeAnimation() {
        this.hasTreeAnimation = false;
        LOGGER.debug("[CustomMobEntity] Cleared tree animation flag for entity {}", this.getId());
    }

    // === УПРАВЛЕНИЕ АНИМАЦИЯМИ AzureLib 3.0 ===

    /**
     * Проигрывает анимацию через новую систему AzCommand
     */
    public void playAnimation(String actionKey) {
        if (mobData != null && mobData.getAnimations() != null) {
            AnimationMapping mapping = mobData.getAnimations().get(actionKey);
            if (mapping != null) {
                setAnimation(mapping.getAnimationName(), mapping.isLoop(), mapping.getSpeed());
                lastPlayedAnimation = actionKey;
                lastAnimationTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Устанавливает анимацию через AzCommand
     */
    public void setAnimation(String animationId, boolean loop, float speed) {
        this.currentAnimation = animationId;
        this.isLoopingAnimation = loop;
        this.animationSpeed = speed;

        hasTreeAnimation = !loop;

        if (!this.level().isClientSide) {
            // Используем новую систему AzCommand для синхронизации анимаций
            try {
                AzCommand animationCommand = AzCommand.create(
                        "main_controller",
                        animationId,
                        loop ? AzPlayBehaviors.LOOP : AzPlayBehaviors.PLAY_ONCE
                );

                animationCommand.sendForEntity(this);

                LOGGER.debug("[CustomMobEntity] Sent AzCommand for animation: {} (loop: {}, speed: {})",
                        animationId, loop, speed);

            } catch (Exception e) {
                LOGGER.error("[CustomMobEntity] Failed to send AzCommand: {}", e.getMessage());

                // Fallback на старую систему
                NetworkManager.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> this),
                        new AnimationSyncPacket(this.getId(), animationId, speed, loop)
                );
            }
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D, 0.0F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MOB_ID, "");
        this.entityData.define(IS_ATTACKING, false);
    }

    public void setMobId(String mobId) {
        this.entityData.set(MOB_ID, mobId);
    }

    public String getMobId() {
        return this.entityData.get(MOB_ID);
    }

    public MobData getMobData() {
        return mobData;
    }

    public void setMobData(MobData mobData) {
        this.mobData = mobData;

        if (mobData != null && mobData.getAttributes() != null) {
            applyMobAttributes(mobData);
        }

        syncMobDataWithClient();
    }

    private void applyMobAttributes(MobData mobData) {
        var attributes = mobData.getAttributes();

        if (attributes.containsKey("maxHealth")) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(attributes.get("maxHealth"));
            this.setHealth(this.getMaxHealth());
        }
        if (attributes.containsKey("movementSpeed")) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(attributes.get("movementSpeed"));
        }
        if (attributes.containsKey("attackDamage")) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attributes.get("attackDamage"));
        }
        if (attributes.containsKey("armor")) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(attributes.get("armor"));
        }
        if (attributes.containsKey("knockbackResistance")) {
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(attributes.get("knockbackResistance"));
        }
    }

    private void syncMobDataWithClient() {
        if (!this.level().isClientSide && this.mobData != null) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new MobDataPacket(this.mobData)
            );
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (mobData == null && !this.getMobId().isEmpty()) {
            loadMobDataFromCache();
        }

        if (!this.level().isClientSide && mobData != null && !this.isDeadOrDying()) {
            if (!hasTreeAnimation) {
                if (this.getNavigation().isInProgress() && this.getNavigation().getTargetPos() != null) {
                    playBaseAnimation("walk");
                } else {
                    playBaseAnimation("idle");
                }
            }
        }
    }

    private void loadMobDataFromCache() {
        if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                MobData cachedData = ClientMobDataCache.getMobData(this.getMobId());
                if (cachedData != null) {
                    this.setMobData(cachedData);
                }
            });
        }
    }

    private void playBaseAnimation(String animName) {
        if (!hasTreeAnimation && !currentAnimation.equals(animName)) {
            this.currentAnimation = animName;
            this.isLoopingAnimation = true;
            this.animationSpeed = 1.0f;

            // Используем новую систему AzCommand
            if (!this.level().isClientSide) {
                try {
                    AzCommand baseAnimCommand = AzCommand.create("main_controller", animName, AzPlayBehaviors.LOOP);
                    baseAnimCommand.sendForEntity(this);
                } catch (Exception e) {
                    // Fallback на старую систему
                    syncAnimation();
                }
            }
        }
    }

    private void syncAnimation() {
        if (!this.level().isClientSide) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new AnimationSyncPacket(this.getId(), currentAnimation, animationSpeed, isLoopingAnimation)
            );
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("MobId", this.getMobId());

        if (!currentAnimation.isEmpty()) {
            compound.putString("CurrentAnimation", currentAnimation);
            compound.putBoolean("AnimationLoop", isLoopingAnimation);
            compound.putFloat("AnimationSpeed", animationSpeed);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setMobId(compound.getString("MobId"));

        if (compound.contains("CurrentAnimation")) {
            currentAnimation = compound.getString("CurrentAnimation");
            isLoopingAnimation = compound.getBoolean("AnimationLoop");
            animationSpeed = compound.getFloat("AnimationSpeed");
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (super.hurt(source, amount)) {
            this.playAnimation("HURT");
            return true;
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        this.playAnimation("DEATH");
        super.die(source);
    }

    // Добавить в CustomMobEntity.java метод для совместимости с новой системой анимаций:

    /**
     * Проигрывает анимацию через AzCommand с улучшенной обработкой ошибок
     */
    public void playAnimationSafe(String actionKey) {
        try {
            if (mobData != null && mobData.getAnimations() != null) {
                AnimationMapping mapping = mobData.getAnimations().get(actionKey);
                if (mapping != null) {
                    setAnimationSafe(mapping.getAnimationName(), mapping.isLoop(), mapping.getSpeed());
                    lastPlayedAnimation = actionKey;
                    lastAnimationTime = System.currentTimeMillis();
                    return;
                }
            }

            // Если нет маппинга, пробуем напрямую
            setAnimationSafe(actionKey, false, 1.0f);

        } catch (Exception e) {
            LOGGER.error("[CustomMobEntity] Error playing animation '{}': {}", actionKey, e.getMessage());
        }
    }

    /**
     * Безопасная установка анимации с обработкой ошибок
     */
    public void setAnimationSafe(String animationId, boolean loop, float speed) {
        try {
            this.currentAnimation = animationId;
            this.isLoopingAnimation = loop;
            this.animationSpeed = speed;
            this.hasTreeAnimation = !loop;

            if (!this.level().isClientSide) {
                // Пробуем новую систему AzCommand
                try {
                    AzCommand animationCommand = AzCommand.create(
                            "main_controller",
                            animationId,
                            loop ? AzPlayBehaviors.LOOP : AzPlayBehaviors.PLAY_ONCE
                    );

                    animationCommand.sendForEntity(this);

                    LOGGER.debug("[CustomMobEntity] Successfully sent AzCommand for animation: {} (loop: {}, speed: {})",
                            animationId, loop, speed);

                } catch (Exception azCommandError) {
                    LOGGER.warn("[CustomMobEntity] AzCommand failed, using fallback: {}", azCommandError.getMessage());

                    // Fallback на старую систему
                    NetworkManager.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> this),
                            new AnimationSyncPacket(this.getId(), animationId, speed, loop)
                    );
                }
            }

        } catch (Exception e) {
            LOGGER.error("[CustomMobEntity] Critical error setting animation '{}': {}", animationId, e.getMessage());
        }
    }
}