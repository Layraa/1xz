package com.custommobsforge.custommobsforge.common.event;

import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.data.MobData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class SaveConfigEvent extends Event {
    public enum ConfigType {
        MOB_DATA,
        BEHAVIOR_TREE
    }

    private final ConfigType type;
    private final ServerPlayer player;
    private final MobData mobData;
    private final BehaviorTree behaviorTree;

    // Конструктор для сохранения данных моба
    public SaveConfigEvent(MobData mobData, ServerPlayer player) {
        this.type = ConfigType.MOB_DATA;
        this.mobData = mobData;
        this.behaviorTree = null;
        this.player = player;
    }

    // Конструктор для сохранения дерева поведения
    public SaveConfigEvent(BehaviorTree behaviorTree, ServerPlayer player) {
        this.type = ConfigType.BEHAVIOR_TREE;
        this.mobData = null;
        this.behaviorTree = behaviorTree;
        this.player = player;
    }

    public ConfigType getType() {
        return type;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public MobData getMobData() {
        return mobData;
    }

    public BehaviorTree getBehaviorTree() {
        return behaviorTree;
    }

    public boolean isMobData() {
        return type == ConfigType.MOB_DATA;
    }

    public boolean isBehaviorTree() {
        return type == ConfigType.BEHAVIOR_TREE;
    }
}