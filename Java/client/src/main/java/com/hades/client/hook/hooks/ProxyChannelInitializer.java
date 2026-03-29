package com.hades.client.hook.hooks;

import com.hades.client.manager.pipeline.ProxyHandshakeHandler;
import com.hades.client.util.HadesLogger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public class ProxyChannelInitializer extends ChannelInitializer<Channel> {

    private final ChannelHandler originalHandler;
    private final InetSocketAddress target;

    public ProxyChannelInitializer(ChannelHandler originalHandler, InetSocketAddress target) {
        this.originalHandler = originalHandler;
        this.target = target;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        HadesLogger.get().info("[Proxy] Netty initChannel() triggered for pipeline...");
        
        // Forward to original handler first
        if (originalHandler instanceof ChannelInitializer) {
            try {
                Method initMethod = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
                initMethod.setAccessible(true);
                initMethod.invoke(originalHandler, ch);
            } catch (Exception e) {
                ch.pipeline().addLast("original_init", originalHandler);
            }
        } else {
            if (originalHandler != null) {
                ch.pipeline().addLast("original_init", originalHandler);
            }
        }
        
        // Now inject our proxy handshake handler at the absolute FRONT of the pipeline
        HadesLogger.get().info("[Proxy] Injecting ProxyHandshakeHandler at FRONT of pipeline.");
        ch.pipeline().addFirst("proxy_handshake", new ProxyHandshakeHandler(target));
        HadesLogger.get().info("[Proxy] Injected successfully.");
    }
}
