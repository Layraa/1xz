package com.custommobsforge.custommobsforge.common.event;

import com.custommobsforge.custommobsforge.common.data.MobData;
import net.minecraftforge.eventbus.api.Event;

public class MobDataReceivedEvent extends Event {
    private final MobData mobData;

    public MobDataReceivedEvent(MobData mobData) {
        this.mobData = mobData;
    }

    public MobData getMobData() {
        return mobData;
    }
}