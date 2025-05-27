package com.custommobsforge.custommobsforge.common.data;

import net.minecraft.network.FriendlyByteBuf;

public class AnimationMapping {
    private String animationName;
    private boolean loop;
    private float speed;
    private String description;

    // Конструкторы
    public AnimationMapping() {
        // Пустой конструктор для Gson
    }

    public AnimationMapping(String animationName, boolean loop, float speed) {
        this.animationName = animationName;
        this.loop = loop;
        this.speed = speed;
    }

    // Геттеры и сеттеры
    public String getAnimationName() { return animationName; }
    public void setAnimationName(String animationName) { this.animationName = animationName; }

    public boolean isLoop() { return loop; }
    public void setLoop(boolean loop) { this.loop = loop; }

    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // Методы сериализации
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(animationName != null ? animationName : "");
        buffer.writeBoolean(loop);
        buffer.writeFloat(speed);
        buffer.writeUtf(description != null ? description : "");
    }

    public static AnimationMapping readFromBuffer(FriendlyByteBuf buffer) {
        AnimationMapping mapping = new AnimationMapping();
        mapping.animationName = buffer.readUtf();
        mapping.loop = buffer.readBoolean();
        mapping.speed = buffer.readFloat();
        mapping.description = buffer.readUtf();
        return mapping;
    }
}