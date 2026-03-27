package com.hades.client.module.impl.other;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.module.Module;
import com.hades.client.util.HadesLogger;

public class TestPacketCancel extends Module {

    public TestPacketCancel() {
        super("TestPacketCancel", "Cancels all S12PacketEntityVelocity (Knockback) randomly.", Category.MISC, 0);
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled())
            return;
        Object packet = event.getPacket();
        if (packet != null) {
            String name = com.hades.client.util.PacketMapper.getPacketName(packet);
            // Test canceling velocity
            if (name.equals("S12PacketEntityVelocity")) {
                event.setCancelled(true);
                HadesLogger.get().info("[TestCancel] Cancelled Velocity Packet!");
            }
        }
    }
}
