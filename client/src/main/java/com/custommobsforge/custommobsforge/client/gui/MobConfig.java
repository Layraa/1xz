package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.AnimationConfig;
import java.util.*;

/**
 * Класс-конфигурация моба
 */
public class MobConfig {
    private UUID id;
    private String name;
    private String modelPath;
    private String texturePath;
    private Map<String, Double> attributes = new HashMap<>();
    private Map<String, AnimationConfig> animationMappings = new HashMap<>();
    private UUID behaviorTreeId;
    private String animationFilePath;

    public String getAnimationFilePath() { return animationFilePath; }
    public void setAnimationFilePath(String animationFilePath) { this.animationFilePath = animationFilePath; }

    // Настройки спавна
    private boolean canSpawnDay = true;
    private boolean canSpawnNight = true;
    private List<String> spawnBiomes = new ArrayList<>();

    // Конструктор
    public MobConfig() {
        this.id = UUID.randomUUID();

        // Инициализация атрибутов по умолчанию
        attributes.put("maxHealth", 20.0);
        attributes.put("movementSpeed", 0.25);
        attributes.put("attackDamage", 3.0);
        attributes.put("armor", 0.0);
    }

    // Геттеры и сеттеры
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }

    public String getTexturePath() { return texturePath; }
    public void setTexturePath(String texturePath) { this.texturePath = texturePath; }

    public Map<String, Double> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Double> attributes) { this.attributes = attributes; }

    public Map<String, AnimationConfig> getAnimationMappings() { return animationMappings; }
    public void setAnimationMappings(Map<String, AnimationConfig> animationMappings) {
        this.animationMappings = animationMappings;
    }

    public UUID getBehaviorTreeId() { return behaviorTreeId; }
    public void setBehaviorTreeId(UUID behaviorTreeId) { this.behaviorTreeId = behaviorTreeId; }

    public boolean isCanSpawnDay() { return canSpawnDay; }
    public void setCanSpawnDay(boolean canSpawnDay) { this.canSpawnDay = canSpawnDay; }

    public boolean isCanSpawnNight() { return canSpawnNight; }
    public void setCanSpawnNight(boolean canSpawnNight) { this.canSpawnNight = canSpawnNight; }

    public List<String> getSpawnBiomes() { return spawnBiomes; }
    public void setSpawnBiomes(List<String> spawnBiomes) { this.spawnBiomes = spawnBiomes; }
}