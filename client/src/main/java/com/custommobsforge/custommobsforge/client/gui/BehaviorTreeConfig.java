package com.custommobsforge.custommobsforge.client.gui;

import java.util.*;

/**
 * Класс-конфигурация дерева поведения
 */
public class BehaviorTreeConfig {
    private UUID id;
    private String name;
    private List<NodeData> nodes = new ArrayList<>();
    private List<ConnectionData> connections = new ArrayList<>();

    // Добавьте эти поля для связи с мобом
    private UUID mobId;
    private String mobName;

    // Конструктор
    public BehaviorTreeConfig() {
        this.id = UUID.randomUUID();
    }

    // Геттеры и сеттеры
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<NodeData> getNodes() { return nodes; }
    public void setNodes(List<NodeData> nodes) { this.nodes = nodes; }

    public List<ConnectionData> getConnections() { return connections; }
    public void setConnections(List<ConnectionData> connections) { this.connections = connections; }

    // Добавьте эти методы для работы с mobId и mobName
    public UUID getMobId() { return mobId; }
    public void setMobId(UUID mobId) { this.mobId = mobId; }

    public String getMobName() { return mobName; }
    public void setMobName(String mobName) { this.mobName = mobName; }
}