package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.event.RequestMobListEvent;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.MobDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

@Mod.EventBusSubscriber(modid = "custommobsforge_server", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onRequestMobList(RequestMobListEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            ServerLevel level = (ServerLevel) player.level();

            // Получаем список всех доступных мобов
            List<MobData> mobs = MobConfigManager.getAvailableMobs(level);

            System.out.println("ServerEventHandler: Sending " + mobs.size() + " mobs to player " + player.getName().getString());

            // Отправляем каждого моба отдельным пакетом
            for (MobData mobData : mobs) {
                NetworkManager.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new MobDataPacket(mobData)
                );
            }
        }
    }
}