package com.custommobsforge.custommobsforge.common.registry;

import com.custommobsforge.custommobsforge.common.CommonCustomMobsForge;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CommonCustomMobsForge.MOD_ID);

    public static final RegistryObject<EntityType<CustomMobEntity>> CUSTOM_MOB =
            ENTITY_TYPES.register("custom_mob",
                    () -> EntityType.Builder.of(CustomMobEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.8F)
                            .build(new ResourceLocation(CommonCustomMobsForge.MOD_ID, "custom_mob").toString()));
}