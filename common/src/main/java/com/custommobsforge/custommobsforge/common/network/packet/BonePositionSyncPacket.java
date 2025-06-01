package com.custommobsforge.custommobsforge.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Пакет для синхронизации позиций костей с клиента на сервер
 */
public class BonePositionSyncPacket {
    private final int entityId;
    private final Map<String, Vec3> bonePositions;
    private final long timestamp;

    public BonePositionSyncPacket(int entityId, Map<String, Vec3> bonePositions) {
        this.entityId = entityId;
        this.bonePositions = bonePositions;
        this.timestamp = System.currentTimeMillis();
    }

    // Конструктор для декодирования
    public BonePositionSyncPacket(int entityId, Map<String, Vec3> bonePositions, long timestamp) {
        this.entityId = entityId;
        this.bonePositions = bonePositions;
        this.timestamp = timestamp;
    }

    public static void encode(BonePositionSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        buf.writeLong(packet.timestamp);
        buf.writeInt(packet.bonePositions.size());

        for (Map.Entry<String, Vec3> entry : packet.bonePositions.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeDouble(entry.getValue().x);
            buf.writeDouble(entry.getValue().y);
            buf.writeDouble(entry.getValue().z);
        }
    }

    public static BonePositionSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        long timestamp = buf.readLong();
        int size = buf.readInt();

        Map<String, Vec3> bonePositions = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String boneName = buf.readUtf();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            bonePositions.put(boneName, new Vec3(x, y, z));
        }

        return new BonePositionSyncPacket(entityId, bonePositions, timestamp);
    }

    public static void handle(BonePositionSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                ServerLevel level = context.getSender().serverLevel();
                Entity entity = level.getEntity(packet.entityId);

                if (entity instanceof CustomMobEntity) {
                    CustomMobEntity mobEntity = (CustomMobEntity) entity;

                    // Обновляем позиции костей
                    mobEntity.updateBonePositions(packet.bonePositions, packet.timestamp);
                }
            }
        });
        context.setPacketHandled(true);
    }

    // Геттеры для отладки
    public int getEntityId() {
        return entityId;
    }

    public Map<String, Vec3> getBonePositions() {
        return bonePositions;
    }

    public long getTimestamp() {
        return timestamp;
    }
}