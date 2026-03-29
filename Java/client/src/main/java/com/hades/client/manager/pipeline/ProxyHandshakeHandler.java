package com.hades.client.manager.pipeline;

import com.hades.client.manager.ProxyManager;
import com.hades.client.util.HadesLogger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Queue;

public class ProxyHandshakeHandler extends ChannelDuplexHandler {

    private final InetSocketAddress target;
    private boolean proxyEstablished = false;
    private final Queue<BufferedWrite> writeBuffer = new LinkedList<>();

    public ProxyHandshakeHandler(InetSocketAddress target) {
        this.target = target;
    }

    private int socksState = 0;
    private ByteBuf backlog = null;

    private static class BufferedWrite {
        final Object msg;
        final ChannelPromise promise;
        BufferedWrite(Object msg, ChannelPromise promise) {
            this.msg = msg;
            this.promise = promise;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        HadesLogger.get().info("[Proxy] HandshakeHandler: channelActive() triggered! Socket connection established.");
        ProxyManager pm = ProxyManager.getInstance();
        ProxyManager.ProxyEntry activeProxy = pm.getActiveProxy();
        
        if (activeProxy == null) {
            super.channelActive(ctx);
            return;
        }

        if (activeProxy.type == ProxyManager.ProxyType.HTTP || activeProxy.type == ProxyManager.ProxyType.RESIDENTIAL) {
            HadesLogger.get().info("[Proxy] Starting HTTP CONNECT Handshake...");
            String auth = "";
            if (activeProxy.username != null && !activeProxy.username.isEmpty()) {
                String creds = activeProxy.username + ":" + activeProxy.password;
                auth = "Proxy-Authorization: Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)) + "\r\n";
            }

            String request = "CONNECT " + target.getHostString() + ":" + target.getPort() + " HTTP/1.1\r\n" +
                    "Host: " + target.getHostString() + ":" + target.getPort() + "\r\n" +
                    auth +
                    "\r\n";

            ctx.writeAndFlush(Unpooled.wrappedBuffer(request.getBytes(StandardCharsets.UTF_8)));
            HadesLogger.get().info("[Proxy] Sent target HTTP proxy tunnel connection frame.");
        } else if (activeProxy.type == ProxyManager.ProxyType.SOCKS5) {
            HadesLogger.get().info("[Proxy] Starting SOCKS5 Handshake...");
            boolean hasAuth = activeProxy.username != null && !activeProxy.username.isEmpty();
            byte[] greeting = hasAuth ? new byte[]{0x05, 0x01, 0x02} : new byte[]{0x05, 0x01, 0x00};
            ctx.writeAndFlush(Unpooled.wrappedBuffer(greeting));
            HadesLogger.get().info("[Proxy] Sent SOCKS5 Greeting (Auth: " + hasAuth + ").");
            socksState = 0;
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (proxyEstablished || !(msg instanceof ByteBuf)) {
            super.channelRead(ctx, msg);
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        if (backlog == null) backlog = Unpooled.buffer();
        backlog.writeBytes(in);
        in.release(); // release incoming chunk

        ProxyManager pm = ProxyManager.getInstance();
        ProxyManager.ProxyEntry activeProxy = pm.getActiveProxy();
        if (activeProxy == null) return;
        
        HadesLogger.get().info("[Proxy] channelRead: Incoming " + backlog.readableBytes() + " bytes from proxy backend.");

        if (activeProxy.type == ProxyManager.ProxyType.HTTP || activeProxy.type == ProxyManager.ProxyType.RESIDENTIAL) {
            byte[] bytes = new byte[backlog.readableBytes()];
            backlog.getBytes(backlog.readerIndex(), bytes);
            String response = new String(bytes, StandardCharsets.UTF_8);

            if (response.contains("\r\n\r\n")) {
                if (response.contains(" 200")) {
                    int idx = response.indexOf("\r\n\r\n");
                    backlog.skipBytes(idx + 4);

                    onHandshakeSuccess(ctx);
                } else if (response.contains("407") || response.contains("401")) {
                    HadesLogger.get().error("[Proxy] HTTP Authentication Failed: " + response);
                    ctx.close();
                } else {
                    HadesLogger.get().error("[Proxy] HTTP Proxy connection refused: " + response);
                    ctx.close();
                }
            }
        } else if (activeProxy.type == ProxyManager.ProxyType.SOCKS5) {
            if (socksState == 0) {
                if (backlog.readableBytes() < 2) return;
                backlog.skipBytes(1); // skip version
                int method = backlog.readUnsignedByte();

                if (method == 0x02) {
                    String user = activeProxy.username == null ? "" : activeProxy.username;
                    String pwd = activeProxy.password == null ? "" : activeProxy.password;

                    ByteBuf authBuf = Unpooled.buffer();
                    authBuf.writeByte(0x01);
                    authBuf.writeByte(user.length());
                    authBuf.writeBytes(user.getBytes(StandardCharsets.UTF_8));
                    authBuf.writeByte(pwd.length());
                    authBuf.writeBytes(pwd.getBytes(StandardCharsets.UTF_8));

                    ctx.writeAndFlush(authBuf);
                    socksState = 1;
                } else if (method == 0x00) {
                    sendSocksConnect(ctx);
                    socksState = 2;
                } else {
                    HadesLogger.get().error("[Proxy] SOCKS5 method not supported: " + method);
                    ctx.close();
                }
            } else if (socksState == 1) {
                if (backlog.readableBytes() < 2) return;
                backlog.skipBytes(1);
                int status = backlog.readUnsignedByte();
                if (status == 0x00) {
                    sendSocksConnect(ctx);
                    socksState = 2;
                } else {
                    HadesLogger.get().error("[Proxy] SOCKS Auth Failed (Status: " + status + ")");
                    ctx.close();
                }
            } else if (socksState == 2) {
                if (backlog.readableBytes() < 4) return;
                
                // Peek atyp to determine total length
                int atyp = backlog.getUnsignedByte(backlog.readerIndex() + 3);
                int requiredLength = 6; // base 4 + port 2
                if (atyp == 0x01) {
                    requiredLength += 4; // IPv4
                } else if (atyp == 0x03) {
                    if (backlog.readableBytes() < 5) return;
                    int domainLen = backlog.getUnsignedByte(backlog.readerIndex() + 4);
                    requiredLength += 1 + domainLen;
                } else if (atyp == 0x04) {
                    requiredLength += 16; // IPv6
                }
                
                if (backlog.readableBytes() < requiredLength) return;
                
                backlog.skipBytes(1);
                int status = backlog.readUnsignedByte();
                if (status == 0x00) {
                    backlog.skipBytes(requiredLength - 2); // consume remaining address/port bytes
                    onHandshakeSuccess(ctx);
                } else {
                    HadesLogger.get().error("[Proxy] SOCKS Connect Failed (Status: " + status + ")");
                    ctx.close();
                }
            }
        }
    }

    private void sendSocksConnect(ChannelHandlerContext ctx) {
        ByteBuf cBuf = Unpooled.buffer();
        cBuf.writeByte(0x05);
        cBuf.writeByte(0x01);
        cBuf.writeByte(0x00);
        cBuf.writeByte(0x03);

        byte[] hostBytes = target.getHostString().getBytes(StandardCharsets.UTF_8);
        cBuf.writeByte(hostBytes.length);
        cBuf.writeBytes(hostBytes);
        cBuf.writeShort(target.getPort());

        ctx.writeAndFlush(cBuf);
    }

    private void onHandshakeSuccess(ChannelHandlerContext ctx) {
        proxyEstablished = true;
        HadesLogger.get().info("[Proxy] Handshake Success! Pushing game packets securely...");

        for (BufferedWrite bw : writeBuffer) {
            ctx.write(bw.msg, bw.promise);
        }
        writeBuffer.clear();
        ctx.flush();

        ctx.pipeline().remove(this);

        if (backlog != null) {
            if (backlog.isReadable()) {
                ctx.fireChannelRead(backlog.retain());
            }
            backlog.release();
            backlog = null;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!proxyEstablished) {
            HadesLogger.get().info("[Proxy] Buffering outbound packet securely...");
            writeBuffer.add(new BufferedWrite(msg, promise));
        } else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (proxyEstablished) {
            super.flush(ctx);
        }
    }
}
