package com.custommobsforge.custommobsforge.client.gui;

/**
 * Класс для хранения данных об узле дерева поведения
 */
public class NodeData {
    private String id;
    private String type;
    private String description;
    private String parameter;
    private double x;
    private double y;
    private boolean expanded;

    // Новые поля для анимаций
    private String animationId;
    private double animationSpeed;
    private boolean loopAnimation;

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

    // Новые геттеры и сеттеры для анимаций
    public String getAnimationId() { return animationId; }
    public void setAnimationId(String animationId) { this.animationId = animationId; }

    public double getAnimationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(double animationSpeed) { this.animationSpeed = animationSpeed; }

    public boolean isLoopAnimation() { return loopAnimation; }
    public void setLoopAnimation(boolean loopAnimation) { this.loopAnimation = loopAnimation; }
}