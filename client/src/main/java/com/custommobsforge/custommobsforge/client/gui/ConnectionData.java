package com.custommobsforge.custommobsforge.client.gui;

/**
 * Класс для хранения данных о соединении между узлами дерева поведения
 */
public class ConnectionData {
    private String sourceNodeId;
    private String targetNodeId;

    // Геттеры и сеттеры
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
}