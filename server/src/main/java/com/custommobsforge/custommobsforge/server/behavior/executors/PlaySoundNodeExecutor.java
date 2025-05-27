package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Holder;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Исполнитель узла воспроизведения звука
 * Воспроизводит звук в позиции моба
 */
public class PlaySoundNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры звука
        String soundId = getParameter(node, "sound_id", "", String.class);
        float volume = getParameter(node, "volume", 1.0f, Float.class);
        float pitch = getParameter(node, "pitch", 1.0f, Float.class);
        double range = getParameter(node, "range", 16.0, Double.class);
        String categoryStr = getParameter(node, "category", "HOSTILE", String.class);

        if (soundId.isEmpty()) {
            System.err.println("[PlaySoundNode] No sound ID specified");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Определяем категорию звука
        SoundSource soundSource = getSoundSource(categoryStr);

        // Находим звуковое событие
        SoundEvent soundEvent = getSoundEvent(soundId);

        if (soundEvent == null) {
            System.err.println("[PlaySoundNode] Unknown sound: " + soundId);
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Воспроизводим звук
        entity.level().playSound(
                null, // player (null = всем игрокам)
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                soundEvent,
                soundSource,
                volume,
                pitch
        );

        System.out.println("[PlaySoundNode] Played sound '" + soundId +
                "' at volume " + volume + " and pitch " + pitch);

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }

    /**
     * Получает SoundEvent по строковому ID
     */
    private SoundEvent getSoundEvent(String soundId) {
        try {
            ResourceLocation resourceLocation = new ResourceLocation(soundId);
            Holder<SoundEvent> soundHolder = ForgeRegistries.SOUND_EVENTS.getHolder(resourceLocation).orElse(null);

            if (soundHolder != null) {
                return soundHolder.value();
            }
        } catch (Exception e) {
            // Fallback к прямым звукам
        }

        // Пробуем популярные звуки напрямую
        switch (soundId.toLowerCase()) {
            case "entity.generic.hurt":
                return net.minecraft.sounds.SoundEvents.GENERIC_HURT;
            case "entity.generic.death":
                return net.minecraft.sounds.SoundEvents.GENERIC_DEATH;
            case "entity.player.attack.strong":
                return net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG;
            case "entity.player.attack.weak":
                return net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_WEAK;
            case "entity.zombie.ambient":
                return net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT;
            case "entity.zombie.hurt":
                return net.minecraft.sounds.SoundEvents.ZOMBIE_HURT;
            case "entity.zombie.death":
                return net.minecraft.sounds.SoundEvents.ZOMBIE_DEATH;
            case "entity.skeleton.ambient":
                return net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT;
            case "entity.enderman.teleport":
                return net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;
            case "entity.wither.spawn":
                return net.minecraft.sounds.SoundEvents.WITHER_SPAWN;
            case "entity.ender_dragon.growl":
                return net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL;
            case "block.note_block.harp":
                return net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "ui.toast.challenge_complete":
                return net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case "ambient.cave":
                return net.minecraft.sounds.SoundEvents.ANVIL_USE;
            case "entity.arrow.shoot":
                return net.minecraft.sounds.SoundEvents.ARROW_SHOOT;
            case "entity.bat.ambient":
                return net.minecraft.sounds.SoundEvents.BAT_AMBIENT;
            case "block.bell.use":
                return net.minecraft.sounds.SoundEvents.BELL_BLOCK;
            case "entity.blaze.ambient":
                return net.minecraft.sounds.SoundEvents.BLAZE_AMBIENT;
            case "entity.cat.ambient":
                return net.minecraft.sounds.SoundEvents.CAT_AMBIENT;
            case "entity.creeper.primed":
                return net.minecraft.sounds.SoundEvents.CREEPER_PRIMED;
            case "entity.dragon_fireball.explode":
                return net.minecraft.sounds.SoundEvents.DRAGON_FIREBALL_EXPLODE;
            case "entity.ghast.ambient":
                return net.minecraft.sounds.SoundEvents.GHAST_AMBIENT;
            case "entity.horse.ambient":
                return net.minecraft.sounds.SoundEvents.HORSE_AMBIENT;
            case "entity.iron_golem.ambient":
                return net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER;
            case "entity.phantom.ambient":
                return net.minecraft.sounds.SoundEvents.PHANTOM_AMBIENT;
            case "entity.spider.ambient":
                return net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT;
            case "entity.villager.ambient":
                return net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT;
            case "entity.wolf.ambient":
                return net.minecraft.sounds.SoundEvents.WOLF_AMBIENT;
            default:
                return net.minecraft.sounds.SoundEvents.GENERIC_HURT; // Fallback
        }
    }

    /**
     * Получает SoundSource по строковому названию
     */
    private SoundSource getSoundSource(String category) {
        try {
            return SoundSource.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            switch (category.toLowerCase()) {
                case "mob":
                case "mobs":
                case "hostile":
                    return SoundSource.HOSTILE;
                case "ambient":
                    return SoundSource.AMBIENT;
                case "music":
                    return SoundSource.MUSIC;
                case "block":
                case "blocks":
                    return SoundSource.BLOCKS;
                case "player":
                case "players":
                    return SoundSource.PLAYERS;
                case "neutral":
                    return SoundSource.NEUTRAL;
                case "master":
                    return SoundSource.MASTER;
                case "weather":
                    return SoundSource.WEATHER;
                case "record":
                case "records":
                    return SoundSource.RECORDS;
                case "voice":
                    return SoundSource.VOICE;
                default:
                    return SoundSource.HOSTILE;
            }
        }
    }
}