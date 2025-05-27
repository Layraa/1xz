package com.custommobsforge.custommobsforge.common.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class RequestMobListEvent extends Event {
    private final ServerPlayer player;

    public RequestMobListEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}