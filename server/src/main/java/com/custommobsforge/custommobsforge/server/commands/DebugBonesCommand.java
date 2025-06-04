package com.custommobsforge.custommobsforge.server.commands;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.bone.BonePositionCache;
import com.custommobsforge.custommobsforge.server.util.EntityBoneHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;

/**
 * Команда для отладки системы костей
 */
public class DebugBonesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("debugbones")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(DebugBonesCommand::listBones)
                        )
                )
                .then(Commands.literal("position")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .then(Commands.argument("boneName", StringArgumentType.string())
                                        .executes(DebugBonesCommand::getBonePosition)
                                )
                        )
                )
                .then(Commands.literal("cache")
                        .then(Commands.literal("stats")
                                .executes(DebugBonesCommand::getCacheStats)
                        )
                        .then(Commands.literal("clear")
                                .executes(DebugBonesCommand::clearCache)
                        )
                        .then(Commands.literal("entity")
                                .then(Commands.argument("entityId", IntegerArgumentType.integer())
                                        .executes(DebugBonesCommand::showEntityCache)
                                )
                        )
                )
                .then(Commands.literal("types")
                        .then(Commands.argument("boneName", StringArgumentType.string())
                                .executes(DebugBonesCommand::checkBoneTypes)
                        )
                )
        );
    }

    /**
     * Показывает список всех костей энтити
     */
    private static int listBones(CommandContext<CommandSourceStack> context) {
        try {
            Entity entity = EntityArgument.getEntity(context, "entity");
            CommandSourceStack source = context.getSource();

            if (entity.level().isClientSide) {
                // На клиенте можем получить реальный список костей
                Set<String> bones = EntityBoneHelper.getAvailableBones(entity);

                source.sendSuccess(() -> Component.literal("§6=== Available Bones for Entity " + entity.getId() + " ==="), false);
                source.sendSuccess(() -> Component.literal("§eTotal bones: " + bones.size()), false);

                int count = 0;
                StringBuilder weaponBones = new StringBuilder("§c§lWeapon Bones: ");
                StringBuilder limbBones = new StringBuilder("§a§lLimb Bones: ");
                StringBuilder bodyBones = new StringBuilder("§b§lBody Bones: ");
                StringBuilder attackBones = new StringBuilder("§d§lAttack Bones: ");
                StringBuilder otherBones = new StringBuilder("§7§lOther Bones: ");

                for (String bone : bones) {
                    if (EntityBoneHelper.isWeaponBone(bone)) {
                        weaponBones.append(bone).append(", ");
                    } else if (EntityBoneHelper.isArmBone(bone) || EntityBoneHelper.isLegBone(bone)) {
                        limbBones.append(bone).append(", ");
                    } else if (EntityBoneHelper.isBodyBone(bone) || EntityBoneHelper.isHeadBone(bone)) {
                        bodyBones.append(bone).append(", ");
                    } else if (EntityBoneHelper.isAttackBone(bone)) {
                        attackBones.append(bone).append(", ");
                    } else {
                        otherBones.append(bone).append(", ");
                    }
                    count++;
                }

                // Отправляем результаты
                sendIfNotEmpty(source, weaponBones);
                sendIfNotEmpty(source, limbBones);
                sendIfNotEmpty(source, bodyBones);
                sendIfNotEmpty(source, attackBones);
                sendIfNotEmpty(source, otherBones);

            } else {
                source.sendSuccess(() -> Component.literal("§cThis command works only on client side for getting real bone list"), false);

                // На сервере показываем кэшированные кости
                if (entity instanceof CustomMobEntity) {
                    Map<String, Vec3> cachedBones = BonePositionCache.getAllBonePositions(entity.getId());
                    if (cachedBones != null && !cachedBones.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§6=== Cached Bones for Entity " + entity.getId() + " ==="), false);
                        source.sendSuccess(() -> Component.literal("§eCached bones: " + cachedBones.size()), false);

                        for (String bone : cachedBones.keySet()) {
                            Vec3 pos = cachedBones.get(bone);
                            source.sendSuccess(() -> Component.literal(String.format("§7%s: (%.2f, %.2f, %.2f)",
                                    bone, pos.x, pos.y, pos.z)), false);
                        }
                    } else {
                        source.sendSuccess(() -> Component.literal("§cNo cached bone data for this entity"), false);
                    }
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Получает позицию конкретной кости
     */
    private static int getBonePosition(CommandContext<CommandSourceStack> context) {
        try {
            Entity entity = EntityArgument.getEntity(context, "entity");
            String boneName = StringArgumentType.getString(context, "boneName");
            CommandSourceStack source = context.getSource();

            Vec3 position = EntityBoneHelper.getBoneWorldPosition(entity, boneName);

            if (position != null) {
                source.sendSuccess(() -> Component.literal(String.format(
                        "§6Bone '§e%s§6' position for entity %d: §a(%.2f, %.2f, %.2f)",
                        boneName, entity.getId(), position.x, position.y, position.z)), false);

                // Дополнительная информация о типе кости
                StringBuilder typeInfo = new StringBuilder("§7Type: ");
                if (EntityBoneHelper.isWeaponBone(boneName)) typeInfo.append("§cWeapon ");
                if (EntityBoneHelper.isArmBone(boneName)) typeInfo.append("§aArm ");
                if (EntityBoneHelper.isLegBone(boneName)) typeInfo.append("§aLeg ");
                if (EntityBoneHelper.isBodyBone(boneName)) typeInfo.append("§bBody ");
                if (EntityBoneHelper.isHeadBone(boneName)) typeInfo.append("§bHead ");
                if (EntityBoneHelper.isAttackBone(boneName)) typeInfo.append("§dAttack ");

                source.sendSuccess(() -> Component.literal(typeInfo.toString()), false);
            } else {
                source.sendSuccess(() -> Component.literal(String.format(
                        "§cBone '§e%s§c' not found or position unavailable for entity %d",
                        boneName, entity.getId())), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Показывает статистику кэша
     */
    private static int getCacheStats(CommandContext<CommandSourceStack> context) {
        try {
            BonePositionCache.CacheStats stats = BonePositionCache.getStats();
            CommandSourceStack source = context.getSource();

            source.sendSuccess(() -> Component.literal("§6=== Bone Position Cache Stats ==="), false);
            source.sendSuccess(() -> Component.literal("§eTotal entities: " + stats.totalEntities), false);
            source.sendSuccess(() -> Component.literal("§eTotal bones: " + stats.totalBones), false);
            source.sendSuccess(() -> Component.literal("§eActive entities: " + stats.activeEntities), false);
            source.sendSuccess(() -> Component.literal("§eMemory usage: ~" + (stats.totalBones * 24) + " bytes"), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Очищает кэш
     */
    private static int clearCache(CommandContext<CommandSourceStack> context) {
        try {
            BonePositionCache.clear();
            context.getSource().sendSuccess(() -> Component.literal("§aBone position cache cleared"), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Показывает кэш для конкретной энтити
     */
    private static int showEntityCache(CommandContext<CommandSourceStack> context) {
        try {
            int entityId = IntegerArgumentType.getInteger(context, "entityId");
            CommandSourceStack source = context.getSource();

            Map<String, Vec3> bones = BonePositionCache.getAllBonePositions(entityId);

            if (bones != null && !bones.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§6=== Cached Bones for Entity " + entityId + " ==="), false);
                source.sendSuccess(() -> Component.literal("§eBones: " + bones.size()), false);

                bones.forEach((boneName, position) -> {
                    source.sendSuccess(() -> Component.literal(String.format(
                            "§7%s: §f(%.2f, %.2f, %.2f)",
                            boneName, position.x, position.y, position.z)), false);
                });
            } else {
                source.sendSuccess(() -> Component.literal("§cNo cached data for entity " + entityId), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Проверяет типы кости
     */
    private static int checkBoneTypes(CommandContext<CommandSourceStack> context) {
        try {
            String boneName = StringArgumentType.getString(context, "boneName");
            CommandSourceStack source = context.getSource();

            source.sendSuccess(() -> Component.literal("§6=== Bone Type Analysis for '" + boneName + "' ==="), false);

            StringBuilder types = new StringBuilder("§eDetected types: ");
            boolean hasType = false;

            if (EntityBoneHelper.isWeaponBone(boneName)) {
                types.append("§cWeapon ");
                hasType = true;
            }
            if (EntityBoneHelper.isArmBone(boneName)) {
                types.append("§aArm ");
                hasType = true;
            }
            if (EntityBoneHelper.isLegBone(boneName)) {
                types.append("§aLeg ");
                hasType = true;
            }
            if (EntityBoneHelper.isBodyBone(boneName)) {
                types.append("§bBody ");
                hasType = true;
            }
            if (EntityBoneHelper.isHeadBone(boneName)) {
                types.append("§bHead ");
                hasType = true;
            }
            if (EntityBoneHelper.isAttackBone(boneName)) {
                types.append("§dAttack ");
                hasType = true;
            }

            if (!hasType) {
                types.append("§7Unknown");
            }

            source.sendSuccess(() -> Component.literal(types.toString()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Отправляет сообщение, если строка не пустая
     */
    private static void sendIfNotEmpty(CommandSourceStack source, StringBuilder sb) {
        String str = sb.toString();
        if (str.length() > str.indexOf(": ") + 2) { // Если есть что-то кроме заголовка
            // Убираем последнюю запятую
            String finalStr = str.substring(0, str.length() - 2);
            source.sendSuccess(() -> Component.literal(finalStr), false);
        }
    }
}