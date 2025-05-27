package com.custommobsforge.custommobsforge.common.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BehaviorNode {
    private String id;
    private String type;
    private String description;
    private String parameter;
    private double x;
    private double y;
    private boolean expanded;

    // Параметры анимации
    private String animationId;
    private double animationSpeed;
    private boolean loopAnimation;

    // Дополнительные параметры
    private Map<String, Object> customParameters = new HashMap<>();

    // Конструкторы
    public BehaviorNode() {
        this.id = UUID.randomUUID().toString();
    }

    public BehaviorNode(String type, String description) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.description = description;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParameter() { return parameter; }
    public void setParameter(String parameter) { this.parameter = parameter; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public String getAnimationId() { return animationId; }
    public void setAnimationId(String animationId) { this.animationId = animationId; }

    public double getAnimationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(double animationSpeed) { this.animationSpeed = animationSpeed; }

    public boolean isLoopAnimation() { return loopAnimation; }
    public void setLoopAnimation(boolean loopAnimation) { this.loopAnimation = loopAnimation; }

    public Map<String, Object> getCustomParameters() { return customParameters; }
    public void setCustomParameters(Map<String, Object> customParameters) { this.customParameters = customParameters; }

    public void setCustomParameter(String key, Object value) {
        customParameters.put(key, value);
    }

    public Object getCustomParameter(String key) {
        return customParameters.get(key);
    }

    public String getCustomParameterAsString(String key, String defaultValue) {
        Object value = customParameters.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public double getCustomParameterAsDouble(String key, double defaultValue) {
        Object value = customParameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public boolean getCustomParameterAsBoolean(String key, boolean defaultValue) {
        Object value = customParameters.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    // Методы сериализации
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(id);
        buffer.writeUtf(type != null ? type : "");
        buffer.writeUtf(description != null ? description : "");
        buffer.writeUtf(parameter != null ? parameter : "");
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeBoolean(expanded);

        buffer.writeUtf(animationId != null ? animationId : "");
        buffer.writeDouble(animationSpeed);
        buffer.writeBoolean(loopAnimation);

        Map<String, String> stringParameters = new HashMap<>();
        for (Map.Entry<String, Object> entry : customParameters.entrySet()) {
            stringParameters.put(entry.getKey(), entry.getValue().toString());
        }

        buffer.writeInt(stringParameters.size());
        for (Map.Entry<String, String> entry : stringParameters.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static BehaviorNode readFromBuffer(FriendlyByteBuf buffer) {
        BehaviorNode node = new BehaviorNode();
        node.id = buffer.readUtf();
        node.type = buffer.readUtf();
        node.description = buffer.readUtf();
        node.parameter = buffer.readUtf();
        node.x = buffer.readDouble();
        node.y = buffer.readDouble();
        node.expanded = buffer.readBoolean();

        node.animationId = buffer.readUtf();
        node.animationSpeed = buffer.readDouble();
        node.loopAnimation = buffer.readBoolean();

        int paramCount = buffer.readInt();
        for (int i = 0; i < paramCount; i++) {
            String key = buffer.readUtf();
            String value = buffer.readUtf();
            node.customParameters.put(key, value);
        }

        return node;
    }
}