package com.hades.client.hook.hooks;

import com.hades.client.manager.ProxyManager;
import com.hades.client.manager.ProxyManager;
import com.hades.client.util.HadesLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import net.bytebuddy.asm.Advice;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class BootstrapConnectHook {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.This Bootstrap bootstrap,
            @Advice.Argument(value = 0, readOnly = false) SocketAddress remoteAddress) {

        if (ProxyManager.getInstance().isActive()) {
            if (!(remoteAddress instanceof InetSocketAddress)) return;
            
            InetSocketAddress target = (InetSocketAddress) remoteAddress;

            try {
                ProxyManager.ProxyEntry activeProxy = ProxyManager.getInstance().getActiveProxy();
                int proxyPort = Integer.parseInt(activeProxy.port);
                remoteAddress = new InetSocketAddress(activeProxy.ip, proxyPort);
                HadesLogger.get().info("[Proxy] Bootstrapping proxy route to: " + remoteAddress);

                // We must wrap the existing ChannelInitializer to inject our ProxyHandshakeHandler
                java.lang.reflect.Field handlerField = io.netty.bootstrap.AbstractBootstrap.class.getDeclaredField("handler");
                handlerField.setAccessible(true);
                final ChannelHandler originalHandler = (ChannelHandler) handlerField.get(bootstrap);
                
                bootstrap.handler(new com.hades.client.hook.hooks.ProxyChannelInitializer(originalHandler, target));
            } catch (Exception e) {
                HadesLogger.get().error("[Proxy] Critical Hook Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
