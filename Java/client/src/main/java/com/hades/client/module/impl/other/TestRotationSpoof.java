package com.hades.client.module.impl.other;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.module.Module;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.PacketMapper;
import com.hades.client.wrapper.packet.C03PacketPlayerWrapper;

public class TestRotationSpoof extends Module {

    private boolean spoofYaw = false;

    public TestRotationSpoof() {
        super("TestRotationSpoof", "Spoofs player's yaw sent to the server.", Category.MISC, 0);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        spoofYaw = !spoofYaw;
        HadesLogger.get().info("[TestRotationSpoof] Enabled. Spoofing Yaw + 180 degrees.");
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        Object packet = event.getPacket();
        if (packet == null)
            return;

        String name = PacketMapper.getPacketName(packet);
        if (name.startsWith("C03PacketPlayer")) {
            C03PacketPlayerWrapper wrapper = new C03PacketPlayerWrapper(packet);

            // Only C05 (look) and C06 (poslook) have valid rotation fields updated from the
            // client.
            // But we can just blanket set the yaw if rotating is true.
            if (wrapper.isRotating()) {
                float originalYaw = wrapper.getYaw();
                float spoofedYaw = originalYaw + 180.0f;
                // Keep it within -180 to 180 informally
                if (spoofedYaw > 180.0f)
                    spoofedYaw -= 360.0f;

                wrapper.setYaw(spoofedYaw);

                // Print a few out
                if (Math.random() < 0.05) {
                    HadesLogger.get().info("[TestRotationSpoof] Spoofed YAW from " + originalYaw + " -> " + spoofedYaw);
                }
            }
        }
    }
}
