package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.event.RequestMobListEvent;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class RequestDataPacket {
    public enum RequestType {
        SPECIFIC_MOB,
        ALL_MOBS
    }

    private final RequestType type;
    private final String mobId; // null для ALL_MOBS

    // Конструктор для запроса конкретного моба
    public RequestDataPacket(String mobId) {
        this.type = RequestType.SPECIFIC_MOB;
        this.mobId = mobId;
    }

    // Конструктор для запроса всех мобов
    public RequestDataPacket() {
        this.type = RequestType.ALL_MOBS;
        this.mobId = null;
    }

    public static void encode(RequestDataPacket message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message.type);

        if (message.type == RequestType.SPECIFIC_MOB) {
            buffer.writeUtf(message.mobId);
        }
    }

    public static RequestDataPacket decode(FriendlyByteBuf buffer) {
        RequestType type = buffer.readEnum(RequestType.class);

        if (type == RequestType.SPECIFIC_MOB) {
            String mobId = buffer.readUtf();
            return new RequestDataPacket(mobId);
        } else {
            return new RequestDataPacket();
        }
    }

    public static void handle(RequestDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = (ServerLevel) player.level();

                switch (message.type) {
                    case SPECIFIC_MOB:
                        System.out.println("RequestDataPacket: Request for specific mob: " + message.mobId);

                        MobData mobData = MobConfigManager.loadMobConfig(message.mobId, level);
                        if (mobData != null) {
                            NetworkManager.INSTANCE.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new MobDataPacket(mobData)
                            );
                        }
                        break;

                    case ALL_MOBS:
                        System.out.println("RequestDataPacket: Request for all mobs");

                        // Публикуем событие для запроса списка мобов
                        MinecraftForge.EVENT_BUS.post(new RequestMobListEvent(player));
                        break;
                }
            }
        });

        context.setPacketHandled(true);
    }

    // Геттеры
    public RequestType getType() { return type; }
    public String getMobId() { return mobId; }
}