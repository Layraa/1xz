package com.custommobsforge.custommobsforge.client.gui;

import java.util.Objects;
import java.util.UUID;

public class Connection {
    private final UUID id;
    private final Node sourceNode;
    private final Node targetNode;

    public Connection(Node sourceNode, Node targetNode) {
        this.id = UUID.randomUUID();
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    public UUID getId() {
        return id;
    }

    public Node getSourceNode() {
        return sourceNode;
    }

    public Node getTargetNode() {
        return targetNode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Connection that = (Connection) o;
        return Objects.equals(sourceNode, that.sourceNode) &&
                Objects.equals(targetNode, that.targetNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNode, targetNode);
    }

    @Override
    public String toString() {
        return "Connection{" +
                "id=" + id +
                ", source=" + sourceNode.getId() +
                ", target=" + targetNode.getId() +
                '}';
    }
}