package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Исполнитель узла создания частиц
 * Создает эффект частиц вокруг моба
 */
public class SpawnParticleNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        if (entity.level().isClientSide) {
            return BehaviorTreeExecutor.NodeStatus.SUCCESS; // Частицы только на сервере
        }

        // Получаем параметры частиц
        String particleType = getParameter(node, "particle_type", "minecraft:flame", String.class);
        int count = getParameter(node, "count", 10, Integer.class);
        double radius = getParameter(node, "radius", 1.0, Double.class);
        double speed = getParameter(node, "speed", 0.1, Double.class);
        double offsetX = getParameter(node, "offset_x", 0.5, Double.class);
        double offsetY = getParameter(node, "offset_y", 1.0, Double.class);
        double offsetZ = getParameter(node, "offset_z", 0.5, Double.class);

        ServerLevel serverLevel = (ServerLevel) entity.level();

        // Определяем тип частиц
        ParticleOptions particles = getParticleType(particleType);

        if (particles == null) {
            System.err.println("[SpawnParticleNode] Unknown particle type: " + particleType);
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Создаем частицы
        double entityX = entity.getX();
        double entityY = entity.getY() + offsetY;
        double entityZ = entity.getZ();

        for (int i = 0; i < count; i++) {
            // Случайное смещение в радиусе
            double randomX = entityX + (Math.random() - 0.5) * radius * 2;
            double randomY = entityY + (Math.random() - 0.5) * offsetY;
            double randomZ = entityZ + (Math.random() - 0.5) * radius * 2;

            // Случайная скорость
            double velX = (Math.random() - 0.5) * speed;
            double velY = Math.random() * speed;
            double velZ = (Math.random() - 0.5) * speed;

            serverLevel.sendParticles(particles,
                    randomX, randomY, randomZ,
                    1, // количество для этой позиции
                    offsetX, offsetY, offsetZ, // дополнительное смещение
                    speed // базовая скорость
            );
        }

        System.out.println("[SpawnParticleNode] Spawned " + count + " " + particleType +
                " particles around entity " + entity.getId());

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }

    /**
     * Преобразует строковое название частицы в ParticleOptions
     */
    private ParticleOptions getParticleType(String particleType) {
        switch (particleType.toLowerCase()) {
            case "minecraft:flame":
            case "flame":
                return ParticleTypes.FLAME;
            case "minecraft:smoke":
            case "smoke":
                return ParticleTypes.SMOKE;
            case "minecraft:heart":
            case "heart":
                return ParticleTypes.HEART;
            case "minecraft:explosion":
            case "explosion":
                return ParticleTypes.EXPLOSION;
            case "minecraft:enchant":
            case "enchant":
                return ParticleTypes.ENCHANT;
            case "minecraft:portal":
            case "portal":
                return ParticleTypes.PORTAL;
            case "minecraft:witch":
            case "witch":
                return ParticleTypes.WITCH;
            case "minecraft:note":
            case "note":
                return ParticleTypes.NOTE;
            case "minecraft:happy_villager":
            case "happy_villager":
                return ParticleTypes.HAPPY_VILLAGER;
            case "minecraft:angry_villager":
            case "angry_villager":
                return ParticleTypes.ANGRY_VILLAGER;
            case "minecraft:cloud":
            case "cloud":
                return ParticleTypes.CLOUD;
            case "minecraft:crit":
            case "crit":
                return ParticleTypes.CRIT;
            case "minecraft:damage_indicator":
            case "damage_indicator":
                return ParticleTypes.DAMAGE_INDICATOR;
            case "minecraft:dragon_breath":
            case "dragon_breath":
                return ParticleTypes.DRAGON_BREATH;
            case "minecraft:end_rod":
            case "end_rod":
                return ParticleTypes.END_ROD;
            case "minecraft:firework":
            case "firework":
                return ParticleTypes.FIREWORK;
            case "minecraft:totem":
            case "totem":
                return ParticleTypes.TOTEM_OF_UNDYING;
            default:
                return ParticleTypes.FLAME; // Fallback
        }
    }
}