package com.hades.client.module.impl.other;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.module.Module;
import com.hades.client.util.HadesLogger;

public class TestPacketRead extends Module {

    public TestPacketRead() {
        super("TestPacketRead", "Logs incoming and outgoing packets to the console.", Category.MISC, 0);
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled())
            return;
        Object packet = event.getPacket();
        if (packet != null) {
            String name = com.hades.client.util.PacketMapper.getPacketName(packet);
            // Don't log keep alive or move heavily to avoid spam if desired,
            // but for a pure test let's log everything throttled
            if (Math.random() < 0.05) { // 5% chance to log to avoid console death
                HadesLogger.get().info("[TestRead] IN: " + name);
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled())
            return;
        Object packet = event.getPacket();
        if (packet != null) {
            String name = com.hades.client.util.PacketMapper.getPacketName(packet);
            if (Math.random() < 0.05) {
                HadesLogger.get().info("[TestRead] OUT: " + name);
            }
        }
    }
}
