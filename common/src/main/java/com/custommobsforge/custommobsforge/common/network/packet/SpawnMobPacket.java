package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpawnMobPacket {
    private String mobId;
    private double x, y, z;

    public SpawnMobPacket(String mobId, double x, double y, double z) {
        this.mobId = mobId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(SpawnMobPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.mobId);
        buffer.writeDouble(message.x);
        buffer.writeDouble(message.y);
        buffer.writeDouble(message.z);
    }

    public static SpawnMobPacket decode(FriendlyByteBuf buffer) {
        return new SpawnMobPacket(
                buffer.readUtf(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    public static void handle(SpawnMobPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = (ServerLevel) player.level();

                // Загрузка данных моба
                MobData mobData = MobConfigManager.loadMobConfig(message.mobId, level);

                if (mobData != null) {
                    // Создание и спавн моба
                    CustomMobEntity entity = EntityRegistry.CUSTOM_MOB.get().create(level);

                    if (entity != null) {
                        entity.setPos(message.x, message.y, message.z);
                        entity.setMobId(message.mobId);
                        entity.setMobData(mobData);

                        level.addFreshEntity(entity);
                        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                                MobSpawnType.COMMAND, null, null);
                    }
                }
            }
        });

        context.setPacketHandled(true);
    }
}