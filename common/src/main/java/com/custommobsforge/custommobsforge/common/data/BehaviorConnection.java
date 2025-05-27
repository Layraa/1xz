package com.custommobsforge.custommobsforge.common.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class BehaviorConnection {
    private String id;
    private String sourceNodeId;
    private String targetNodeId;

    public BehaviorConnection() {
        this.id = UUID.randomUUID().toString();
    }

    public BehaviorConnection(String sourceNodeId, String targetNodeId) {
        this.id = UUID.randomUUID().toString();
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(id);
        buffer.writeUtf(sourceNodeId);
        buffer.writeUtf(targetNodeId);
    }

    public static BehaviorConnection readFromBuffer(FriendlyByteBuf buffer) {
        BehaviorConnection connection = new BehaviorConnection();
        connection.id = buffer.readUtf();
        connection.sourceNodeId = buffer.readUtf();
        connection.targetNodeId = buffer.readUtf();
        return connection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BehaviorConnection that = (BehaviorConnection) o;
        return sourceNodeId.equals(that.sourceNodeId) &&
                targetNodeId.equals(that.targetNodeId);
    }

    @Override
    public int hashCode() {
        int result = sourceNodeId.hashCode();
        result = 31 * result + targetNodeId.hashCode();
        return result;
    }
}