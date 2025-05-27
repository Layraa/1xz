package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AnimationSyncPacket {
    private int entityId;
    private String animationId;
    private float animationSpeed;
    private boolean loop;

    public AnimationSyncPacket(int entityId, String animationId, float animationSpeed, boolean loop) {
        this.entityId = entityId;
        this.animationId = animationId;
        this.animationSpeed = animationSpeed;
        this.loop = loop;
    }

    public static void encode(AnimationSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.entityId);
        buffer.writeUtf(message.animationId);
        buffer.writeFloat(message.animationSpeed);
        buffer.writeBoolean(message.loop);
    }

    public static AnimationSyncPacket decode(FriendlyByteBuf buffer) {
        return new AnimationSyncPacket(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readBoolean()
        );
    }

    public static void handle(AnimationSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            System.out.println("AnimationSyncPacket: Received animation sync for entity ID " +
                    message.getEntityId() + ", animation: " + message.getAnimationId() +
                    ", loop: " + message.isLoop() + ", speed: " + message.getAnimationSpeed());

            // На клиенте находим сущность и устанавливаем анимацию
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(message.getEntityId());
                if (entity instanceof CustomMobEntity) {
                    CustomMobEntity mobEntity = (CustomMobEntity) entity;
                    System.out.println("AnimationSyncPacket: Setting animation for entity " + entity.getId() +
                            ": " + message.getAnimationId());
                    mobEntity.setAnimation(message.getAnimationId(), message.isLoop(), message.getAnimationSpeed());
                } else {
                    System.out.println("AnimationSyncPacket: Entity not found or not CustomMobEntity: " +
                            message.getEntityId());
                }
            } else {
                System.out.println("AnimationSyncPacket: Client level is null");
            }
        });

        context.setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public String getAnimationId() {
        return animationId;
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public boolean isLoop() {
        return loop;
    }
}