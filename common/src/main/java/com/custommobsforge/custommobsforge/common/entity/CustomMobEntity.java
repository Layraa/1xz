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

public class CustomMobEntity extends PathfinderMob implements GeoEntity {

    private static final EntityDataAccessor<String> MOB_ID =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private MobData mobData;
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);

    // Поля для AzureLib
    private String currentAnimation = "";
    private boolean isLoopingAnimation = true;
    private float animationSpeed = 1.0f;

    // Простое управление анимациями
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

    public void clearTreeAnimation() {
        this.hasTreeAnimation = false;
        // НЕ очищаем currentAnimation - просто сбрасываем флаг
        System.out.println("[CustomMobEntity] Cleared tree animation flag for entity " + this.getId());
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

        // Применяем атрибуты
        if (mobData != null && mobData.getAttributes() != null) {
            applyMobAttributes(mobData);
        }

        // Синхронизируем с клиентами
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

    // Простое воспроизведение анимации
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

        // Помечаем, что есть анимация из дерева поведения
        hasTreeAnimation = !loop; // Только незацикленные анимации блокируют базовые

        // Синхронизируем с клиентами
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
                        System.err.println("Error in animation controller: " + e.getMessage());
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

        // Сохраняем текущую анимацию
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

        // Загружаем текущую анимацию
        if (compound.contains("CurrentAnimation")) {
            currentAnimation = compound.getString("CurrentAnimation");
            isLoopingAnimation = compound.getBoolean("AnimationLoop");
            animationSpeed = compound.getFloat("AnimationSpeed");
        }
    }

    // В методе tick() используем правильный метод для получения скорости:
    @Override
    public void tick() {
        super.tick();

        // Загружаем данные моба если их нет
        if (mobData == null && !this.getMobId().isEmpty()) {
            loadMobDataFromCache();
        }

        // ВСЕГДА проигрываем базовые анимации на сервере
        if (!this.level().isClientSide && mobData != null && !this.isDeadOrDying()) {

            // Если нет активной анимации из дерева - играем базовые
            if (!hasTreeAnimation) {
                // Проверяем навигацию вместо скорости для более точного определения
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

    /**
     * Загружает данные моба из кэша (на клиенте) или конфигурации (на сервере)
     */
    private void loadMobDataFromCache() {
        if (this.level().isClientSide) {
            // На клиенте загружаем из кэша
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                MobData cachedData = ClientMobDataCache.getMobData(this.getMobId());
                if (cachedData != null) {
                    this.setMobData(cachedData);
                }
            });
        } else {
            // На сервере загружаем из конфигурации (это делается в MobSpawnEventHandler)
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