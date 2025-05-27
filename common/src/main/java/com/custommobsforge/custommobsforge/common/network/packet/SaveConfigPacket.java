package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.event.SaveConfigEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveConfigPacket {
    public enum ConfigType {
        MOB_DATA,
        BEHAVIOR_TREE
    }

    private final ConfigType type;
    private final MobData mobData;
    private final BehaviorTree behaviorTree;

    // Конструктор для данных моба
    public SaveConfigPacket(MobData mobData) {
        this.type = ConfigType.MOB_DATA;
        this.mobData = mobData;
        this.behaviorTree = null;
    }

    // Конструктор для дерева поведения
    public SaveConfigPacket(BehaviorTree behaviorTree) {
        this.type = ConfigType.BEHAVIOR_TREE;
        this.mobData = null;
        this.behaviorTree = behaviorTree;
    }

    public static void encode(SaveConfigPacket message, FriendlyByteBuf buffer) {
        // Записываем тип
        buffer.writeEnum(message.type);

        // Записываем данные в зависимости от типа
        switch (message.type) {
            case MOB_DATA:
                message.mobData.writeToBuffer(buffer);
                break;
            case BEHAVIOR_TREE:
                message.behaviorTree.writeToBuffer(buffer);
                break;
        }
    }

    public static SaveConfigPacket decode(FriendlyByteBuf buffer) {
        ConfigType type = buffer.readEnum(ConfigType.class);

        switch (type) {
            case MOB_DATA:
                MobData mobData = MobData.readFromBuffer(buffer);
                return new SaveConfigPacket(mobData);
            case BEHAVIOR_TREE:
                BehaviorTree behaviorTree = BehaviorTree.readFromBuffer(buffer);
                return new SaveConfigPacket(behaviorTree);
            default:
                throw new IllegalArgumentException("Unknown config type: " + type);
        }
    }

    public static void handle(SaveConfigPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.hasPermissions(2)) {
                // Публикуем объединённое событие
                switch (message.type) {
                    case MOB_DATA:
                        System.out.println("Server received SaveConfigPacket for mob: " +
                                message.mobData.getName() + " (ID: " + message.mobData.getId() + ")");
                        MinecraftForge.EVENT_BUS.post(new SaveConfigEvent(message.mobData, player));
                        break;
                    case BEHAVIOR_TREE:
                        System.out.println("Server received SaveConfigPacket for behavior tree: " +
                                message.behaviorTree.getName() + " (ID: " + message.behaviorTree.getId() + ")");
                        MinecraftForge.EVENT_BUS.post(new SaveConfigEvent(message.behaviorTree, player));
                        break;
                }
            }
        });

        context.setPacketHandled(true);
    }

    // Геттеры
    public ConfigType getType() { return type; }
    public MobData getMobData() { return mobData; }
    public BehaviorTree getBehaviorTree() { return behaviorTree; }
}