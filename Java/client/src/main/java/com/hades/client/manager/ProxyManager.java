package com.hades.client.manager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Base64;

public class ProxyManager {

    public enum ProxyType {
        HTTP, SOCKS5, RESIDENTIAL
    }

    public enum ProxyState {
        UNKNOWN, TESTING, VALID, INVALID
    }

    public static class ProxyEntry {
        public String ip;
        public String port;
        public String username;
        public String password;
        public ProxyType type;
        public ProxyState state = ProxyState.UNKNOWN;
        public long ping = -1;

        public ProxyEntry(String ip, String port, String username, String password, ProxyType type) {
            this.ip = ip;
            this.port = port;
            this.username = username;
            this.password = password;
            this.type = type;
        }

        public String getFormatted() {
            String base = ip + ":" + port;
            if (username != null && !username.isEmpty()) {
                base += ":" + username + ":" + password.replaceAll(".", "*");
            }
            return base;
        }
    }

    private static ProxyManager instance;
    private final List<ProxyEntry> savedProxies = new ArrayList<>();
    private ProxyEntry activeProxy = null;
    private InetSocketAddress originalTarget;

    private ProxyManager() {}

    public static ProxyManager getInstance() {
        if (instance == null) {
            instance = new ProxyManager();
        }
        return instance;
    }

    public List<ProxyEntry> getSavedProxies() {
        return savedProxies;
    }

    public ProxyEntry getActiveProxy() {
        return activeProxy;
    }

    public void addProxy(ProxyEntry proxy) {
        if (!savedProxies.contains(proxy)) {
            savedProxies.add(proxy);
        }
    }

    public void removeProxy(ProxyEntry proxy) {
        savedProxies.remove(proxy);
        if (activeProxy == proxy) {
            activeProxy = null;
        }
    }

    public void setActiveProxy(ProxyEntry proxy) {
        this.activeProxy = proxy;
    }

    public void disableProxy() {
        this.activeProxy = null;
    }

    public boolean isActive() {
        return activeProxy != null;
    }

    public void setOriginalTarget(InetSocketAddress target) {
        this.originalTarget = target;
    }

    public InetSocketAddress getOriginalTarget() {
        return originalTarget;
    }

    public void refreshAll() {
        for (ProxyEntry entry : savedProxies) {
            testProxyAsync(entry);
        }
    }

    public void testProxyAsync(ProxyEntry proxy) {
        if (proxy.state == ProxyState.TESTING) return;

        proxy.state = ProxyState.TESTING;
        proxy.ping = -1;

        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                int pPort = Integer.parseInt(proxy.port);

                if (proxy.type == ProxyType.HTTP || proxy.type == ProxyType.RESIDENTIAL) {
                    java.net.Proxy javaProxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(proxy.ip, pPort));
                    URL url = new URL("http://1.1.1.1");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection(javaProxy);
                    
                    if (proxy.username != null && !proxy.username.isEmpty()) {
                        String creds = proxy.username + ":" + proxy.password;
                        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        conn.setRequestProperty("Proxy-Authorization", basicAuth);
                    }
                    
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    conn.connect();
                    conn.getResponseCode(); // Execute tunnel and retrieve headers natively
                    
                    proxy.state = ProxyState.VALID;
                } else if (proxy.type == ProxyType.SOCKS5) {
                    // TCP ping bypasses Java built-in SOCKS5 auth handler conflicts
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(proxy.ip, pPort), 4000);
                    s.close();
                    
                    proxy.state = ProxyState.VALID;
                }
                
                proxy.ping = System.currentTimeMillis() - start;

            } catch (Exception e) {
                proxy.state = ProxyState.INVALID;
                proxy.ping = -1;
            }
        });
    }
}
