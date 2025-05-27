package com.custommobsforge.custommobsforge.common.network;

import com.custommobsforge.custommobsforge.common.CommonCustomMobsForge;
import com.custommobsforge.custommobsforge.common.network.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkManager {
    private static final String PROTOCOL_VERSION = "1";

    public static final ResourceLocation CHANNEL_NAME =
            new ResourceLocation(CommonCustomMobsForge.MOD_ID, "main_channel");

    public static SimpleChannel INSTANCE;

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void registerPackets() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // Основные пакеты
        INSTANCE.registerMessage(nextId(), SpawnMobPacket.class,
                SpawnMobPacket::encode,
                SpawnMobPacket::decode,
                SpawnMobPacket::handle);

        INSTANCE.registerMessage(nextId(), MobDataPacket.class,
                MobDataPacket::encode,
                MobDataPacket::decode,
                MobDataPacket::handle);

        INSTANCE.registerMessage(nextId(), AnimationSyncPacket.class,
                AnimationSyncPacket::encode,
                AnimationSyncPacket::decode,
                AnimationSyncPacket::handle);

        // Пакеты для GUI
        INSTANCE.registerMessage(nextId(), RequestDataPacket.class,
                RequestDataPacket::encode,
                RequestDataPacket::decode,
                RequestDataPacket::handle);

        INSTANCE.registerMessage(nextId(), SaveConfigPacket.class,
                SaveConfigPacket::encode,
                SaveConfigPacket::decode,
                SaveConfigPacket::handle);

        System.out.println("[NetworkManager] Registered " + packetId + " network packets");
    }
}