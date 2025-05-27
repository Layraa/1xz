package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.event.SaveConfigEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveMobDataPacket {
    private final MobData mobData;

    public SaveMobDataPacket(MobData mobData) {
        this.mobData = mobData;
    }

    // Метод для получения данных моба
    public MobData getMobData() {
        return mobData;
    }

    public static void encode(SaveMobDataPacket message, FriendlyByteBuf buffer) {
        // ДОБАВЛЕНО: Логирование
        System.out.println("Encoding SaveMobDataPacket for mob: " +
                (message.mobData != null ? message.mobData.getName() + " (ID: " + message.mobData.getId() + ")" : "null"));

        if (message.mobData != null) {
            message.mobData.writeToBuffer(buffer);
        } else {
            // Запись пустых данных для предотвращения ошибок
            buffer.writeBoolean(false);
        }
    }

    public static SaveMobDataPacket decode(FriendlyByteBuf buffer) {
        try {
            // Проверяем, есть ли данные для чтения
            if (buffer.isReadable()) {
                MobData data = MobData.readFromBuffer(buffer);
                // ДОБАВЛЕНО: Логирование
                System.out.println("Decoded SaveMobDataPacket for mob: " +
                        (data != null ? data.getName() + " (ID: " + data.getId() + ")" : "null"));
                return new SaveMobDataPacket(data);
            } else {
                System.err.println("Error decoding SaveMobDataPacket: Buffer not readable");
                return new SaveMobDataPacket(null);
            }
        } catch (Exception e) {
            System.err.println("Error decoding SaveMobDataPacket: " + e.getMessage());
            e.printStackTrace();
            return new SaveMobDataPacket(null);
        }
    }

    public static void handle(SaveMobDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                if (player != null && player.hasPermissions(2)) { // Проверка прав оператора
                    // ДОБАВЛЕНО: Расширенное логирование
                    System.out.println("Server handling SaveMobDataPacket from player: " + player.getName().getString());

                    // Проверим, что данные не null
                    if (message.getMobData() == null) {
                        System.err.println("Error: MobData is null in SaveMobDataPacket");
                        return;
                    }

                    // ДОБАВЛЕНО: Подробное логирование перед публикацией события
                    System.out.println("Publishing SaveMobDataEvent for mob: " + message.getMobData().getName() +
                            " (ID: " + message.getMobData().getId() + ")");

                    // Публикуем событие с полученными данными
                    boolean eventPosted = MinecraftForge.EVENT_BUS.post(
                            new SaveConfigEvent(message.getMobData(), player));

                    // ДОБАВЛЕНО: Проверка результата публикации события
                    System.out.println("SaveMobDataEvent posted successfully: " + !eventPosted);
                } else {
                    System.err.println("Error: Player is null or doesn't have permissions");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error processing SaveMobDataPacket: " + e.getMessage());
            }
        });

        context.setPacketHandled(true);
    }
}