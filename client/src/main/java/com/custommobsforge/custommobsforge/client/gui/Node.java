package com.custommobsforge.custommobsforge.client.gui;

import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.*;

public class Node {
    private final UUID id; // Уникальный идентификатор
    private final StringProperty type;
    private final StringProperty description;
    private final StringProperty parameter;
    private final DoubleProperty x;
    private final DoubleProperty y;
    private final BooleanProperty expanded;

    // Новые свойства для анимаций
    private final StringProperty animationId;
    private final DoubleProperty animationSpeed;
    private final BooleanProperty loopAnimation;



    public Node(String type, String description, String parameter) {
        this.id = UUID.randomUUID();
        this.type = new SimpleStringProperty(type);
        this.description = new SimpleStringProperty(description);
        this.parameter = new SimpleStringProperty(parameter != null ? parameter : "");
        this.x = new SimpleDoubleProperty(0);
        this.y = new SimpleDoubleProperty(0);
        this.expanded = new SimpleBooleanProperty(true);

        // Инициализация новых свойств
        this.animationId = new SimpleStringProperty("");
        this.animationSpeed = new SimpleDoubleProperty(1.0);
        this.loopAnimation = new SimpleBooleanProperty(false);

    }

    // Копирующий конструктор для клонирования узла
    public Node(Node source) {
        this.id = UUID.randomUUID(); // Новый ID для клона
        this.type = new SimpleStringProperty(source.getType());
        this.description = new SimpleStringProperty(source.getDescription());
        this.parameter = new SimpleStringProperty(source.getParameter());
        this.x = new SimpleDoubleProperty(source.getX());
        this.y = new SimpleDoubleProperty(source.getY());
        this.expanded = new SimpleBooleanProperty(source.isExpanded());

        // Копирование свойств анимации
        this.animationId = new SimpleStringProperty(source.getAnimationId());
        this.animationSpeed = new SimpleDoubleProperty(source.getAnimationSpeed());
        this.loopAnimation = new SimpleBooleanProperty(source.isLoopAnimation());
    }

    // Геттеры и сеттеры
    public UUID getId() { return id; }

    public String getType() { return type.get(); }
    public void setType(String value) { type.set(value); }
    public StringProperty typeProperty() { return type; }

    public String getDescription() { return description.get(); }
    public void setDescription(String value) { description.set(value); }
    public StringProperty descriptionProperty() { return description; }

    public String getParameter() { return parameter.get(); }
    public void setParameter(String value) { parameter.set(value); }
    public StringProperty parameterProperty() { return parameter; }

    public double getX() { return x.get(); }
    public void setX(double value) { x.set(value); }
    public DoubleProperty xProperty() { return x; }

    public double getY() { return y.get(); }
    public void setY(double value) { y.set(value); }
    public DoubleProperty yProperty() { return y; }

    

    public boolean isExpanded() { return expanded.get(); }
    public void setExpanded(boolean value) { expanded.set(value); }
    public BooleanProperty expandedProperty() { return expanded; }

    // Новые геттеры и сеттеры для анимаций
    public String getAnimationId() { return animationId.get(); }
    public void setAnimationId(String value) { animationId.set(value); }
    public StringProperty animationIdProperty() { return animationId; }

    public double getAnimationSpeed() { return animationSpeed.get(); }
    public void setAnimationSpeed(double value) { animationSpeed.set(value); }
    public DoubleProperty animationSpeedProperty() { return animationSpeed; }

    public boolean isLoopAnimation() { return loopAnimation.get(); }
    public void setLoopAnimation(boolean value) { loopAnimation.set(value); }
    public BooleanProperty loopAnimationProperty() { return loopAnimation; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                ", type='" + getType() + '\'' +
                ", description='" + getDescription() + '\'' +
                '}';
    }
}