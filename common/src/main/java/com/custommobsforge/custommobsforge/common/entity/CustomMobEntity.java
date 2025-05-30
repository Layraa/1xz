package com.custommobsforge.custommobsforge.common.entity;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.AnimationMapping;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import com.custommobsforge.custommobsforge.common.network.packet.MobDataPacket;
import com.custommobsforge.custommobsforge.common.config.ClientMobDataCache;
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
import mod.azure.azurelib.animatable.GeoEntity;
import mod.azure.azurelib.core.animatable.instance.AnimatableInstanceCache;
import mod.azure.azurelib.core.animation.AnimatableManager;
import mod.azure.azurelib.core.animation.Animation;
import mod.azure.azurelib.core.animation.RawAnimation;
import mod.azure.azurelib.core.object.PlayState;
import mod.azure.azurelib.util.AzureLibUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomMobEntity extends PathfinderMob implements GeoEntity {

    // Общий логгер для common модуля
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge-Common");

    private static final EntityDataAccessor<String> MOB_ID =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private MobData mobData;
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);

    // НОВОЕ: Синхронизация состояния атаки с клиентом
    private static final EntityDataAccessor<Boolean> IS_ATTACKING =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.BOOLEAN);

    // Поля для AzureLib
    private String currentAnimation = "";
    private boolean isLoopingAnimation = true;
    private float animationSpeed = 1.0f;

    // Простое управление анимациями
    private String lastPlayedAnimation = "";
    public boolean hasTreeAnimation = false;
    private long lastAnimationTime = 0;
    private static final long ANIMATION_COOLDOWN = 1000;

    // === СИСТЕМА СИНХРОНИЗАЦИИ КОСТЕЙ ===
    private Map<String, Vec3> lastKnownBonePositions = new ConcurrentHashMap<>();
    private long lastBoneUpdateTime = 0;
    private boolean isAttacking = false;
    private static final long BONE_DATA_TIMEOUT = 200; // 200мс

    // === ССЫЛКА НА СЕРВЕРНЫЙ МЕНЕДЖЕР (через Object чтобы избежать серверных импортов) ===
    private Object serverBoneColliderManager; // Будет приведен к BoneColliderManager только на сервере

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
            LOGGER.info("[BoneSync-{}] Updated {} bone positions for entity {}",
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

        return getVirtualBonePosition(boneName);
    }

    public long getLastBoneUpdateTime() {
        return lastBoneUpdateTime;
    }

    /**
     * Fallback система виртуальных костей
     */
    private Vec3 getVirtualBonePosition(String boneName) {
        Vec3 basePos = this.position();
        Vec3 lookDir = this.getLookAngle();
        double height = this.getBbHeight();

        switch (boneName.toLowerCase()) {
            case "rightarm":
            case "right_arm":
                Vec3 rightDir = new Vec3(-lookDir.z, 0, lookDir.x).normalize();
                return basePos.add(rightDir.x * 0.6, height * 0.8, rightDir.z * 0.6);

            case "leftarm":
            case "left_arm":
                Vec3 leftDir = new Vec3(lookDir.z, 0, -lookDir.x).normalize();
                return basePos.add(leftDir.x * 0.6, height * 0.8, leftDir.z * 0.6);

            case "sword":
            case "weapon":
            case "greatsword":
                return basePos.add(lookDir.x * 2.0, height * 0.8, lookDir.z * 2.0);

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
     * Включает синхронизацию костей (вызывается при начале атаки)
     */
    public void startAttack() {
        this.isAttacking = true;

        // НОВОЕ: Синхронизируем состояние с клиентом
        this.entityData.set(IS_ATTACKING, true);

        String side = this.level().isClientSide ? "CLIENT" : "SERVER";
        LOGGER.info("[BoneSync-{}] Started attack mode for entity {}", side, this.getId());
    }

    /**
     * Отключает синхронизацию костей (вызывается при окончании атаки)
     */
    public void stopAttack() {
        this.isAttacking = false;

        // НОВОЕ: Синхронизируем состояние с клиентом
        this.entityData.set(IS_ATTACKING, false);

        this.lastKnownBonePositions.clear();

        // Очищаем коллайдеры костей через рефлексию (избегаем серверных импортов)
        if (serverBoneColliderManager != null && !this.level().isClientSide) {
            try {
                serverBoneColliderManager.getClass().getMethod("cleanup").invoke(serverBoneColliderManager);
            } catch (Exception e) {
                LOGGER.error("Error cleaning up bone colliders: {}", e.getMessage());
            }
        }

        String side = this.level().isClientSide ? "CLIENT" : "SERVER";
        LOGGER.info("[BoneSync-{}] Stopped attack mode for entity {}", side, this.getId());
    }

    /**
     * Проверяет, находится ли моб в режиме атаки
     */
    public boolean isAttacking() {
        // ИЗМЕНЕНО: Берем состояние из синхронизированных данных
        if (this.level().isClientSide) {
            // На клиенте используем синхронизированное состояние
            return this.entityData.get(IS_ATTACKING);
        } else {
            // На сервере используем локальное состояние
            return this.isAttacking;
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

    /**
     * Получает серверный менеджер коллайдеров костей (только на сервере)
     */
    public Object getServerBoneColliderManager() {
        return serverBoneColliderManager;
    }

    /**
     * Устанавливает серверный менеджер коллайдеров костей (только на сервере)
     */
    public void setServerBoneColliderManager(Object boneColliderManager) {
        this.serverBoneColliderManager = boneColliderManager;
    }

    public void clearTreeAnimation() {
        this.hasTreeAnimation = false;
        LOGGER.debug("[CustomMobEntity] Cleared tree animation flag for entity {}", this.getId());
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
        this.entityData.define(IS_ATTACKING, false); // НОВОЕ
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

    public void setAnimation(String animationId, boolean loop, float speed) {
        this.currentAnimation = animationId;
        this.isLoopingAnimation = loop;
        this.animationSpeed = speed;

        hasTreeAnimation = !loop;

        if (!this.level().isClientSide) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new AnimationSyncPacket(this.getId(), animationId, speed, loop)
            );
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        mod.azure.azurelib.core.animation.AnimationController<CustomMobEntity> controller =
                new mod.azure.azurelib.core.animation.AnimationController<>(this, "controller", 0, event -> {
                    if (this.currentAnimation == null || this.currentAnimation.isEmpty()) {
                        return PlayState.STOP;
                    }

                    Animation.LoopType loopType = isLoopingAnimation ?
                            Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE;

                    try {
                        RawAnimation animation = RawAnimation.begin().then(currentAnimation, loopType);
                        return event.setAndContinue(animation);
                    } catch (Exception e) {
                        LOGGER.error("Error in animation controller: {}", e.getMessage());
                        return PlayState.STOP;
                    }
                });

        controller.setAnimationSpeedHandler(animatable -> (double) this.animationSpeed);
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
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

    private void syncAnimation() {
        if (!this.level().isClientSide) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new AnimationSyncPacket(this.getId(), currentAnimation, animationSpeed, isLoopingAnimation)
            );
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
            syncAnimation();
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
}