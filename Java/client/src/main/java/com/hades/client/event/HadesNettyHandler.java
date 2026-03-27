package com.hades.client.event;

import com.hades.client.HadesClient;
import com.hades.client.event.events.PacketEvent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class HadesNettyHandler extends ChannelDuplexHandler {
    private static final String HANDLER_NAME = "hades_packet_handler";



    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        PacketEvent.Receive event = new PacketEvent.Receive(msg);
        HadesClient.getInstance().getEventBus().post(event);

        if (event.isCancelled()) {
            return;
        }

        super.channelRead(ctx, event.getPacket());
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        PacketEvent.Send event = new PacketEvent.Send(msg);
        HadesClient.getInstance().getEventBus().post(event);

        if (event.isCancelled()) {
            return;
        }

        super.write(ctx, event.getPacket(), promise);
    }

    public static String getName() {
        return HANDLER_NAME;
    }
}

