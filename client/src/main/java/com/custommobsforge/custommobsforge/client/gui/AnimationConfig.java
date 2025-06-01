package com.custommobsforge.custommobsforge.client.gui;

public class AnimationConfig {
    private String animationName;
    private boolean loop;
    private float speed;
    private String description = "";

    public AnimationConfig(String animationName, boolean loop, float speed) {
        this.animationName = animationName;
        this.loop = loop;
        this.speed = speed;
    }

    // Геттеры и сеттеры
    public String getAnimationName() { return animationName; }
    public boolean isLoop() { return loop; }
    public float getSpeed() { return speed; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}