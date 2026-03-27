package com.hades.client.event;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.util.HadesLogger;

public class PacketPipelineHook {
    private static final HadesLogger LOG = HadesLogger.get();



    @EventHandler
    public void onTick(TickEvent event) {
        if (com.hades.client.api.HadesAPI.Player.isNull())
            return;

        try {
            io.netty.channel.Channel channel = com.hades.client.api.HadesAPI.Game.getNettyChannel();
            if (channel != null && channel.pipeline() != null) {
                if (channel.pipeline().get(HadesNettyHandler.getName()) == null) {
                    if (channel.pipeline().get("packet_handler") != null) {
                        channel.pipeline().addBefore("packet_handler", HadesNettyHandler.getName(),
                                new HadesNettyHandler());
                    } else {
                        channel.pipeline().addFirst(HadesNettyHandler.getName(), new HadesNettyHandler());
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to inject HadesNettyHandler", e);
        }
    }
}
