package com.hades.client.util;

import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads images (and animated GIFs) from URLs in background threads and caches them as GL textures.
 */
public class UrlImageCache {

    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class FrameData {
        int textureId = -1;
        long delayMs;
        FrameData(int textureId, long delayMs) {
            this.textureId = textureId;
            this.delayMs = delayMs;
        }
    }

    private static class CacheEntry {
        volatile int state; // 0 = downloading, 1 = downloaded (raw ready), 2 = uploaded (GL texture), -1 = failed
        
        volatile int imgWidth;
        volatile int imgHeight;
        
        // Raw data pre-upload
        List<BufferedImage> rawFrames = new ArrayList<>();
        List<Long> rawDelays = new ArrayList<>();
        
        // Gl textures post-upload
        List<FrameData> frames = new ArrayList<>();
        long totalDurationMs = 0;
    }

    public static boolean drawUrlImage(String url, float x, float y, float width, float height, boolean animated) {
        CacheEntry entry = getOrStartDownload(url);
        if (entry == null || entry.state == 0 || entry.state == -1) return false;

        if (entry.state == 1) {
            uploadFrames(entry);
            if (entry.state == -1) return false;
        }

        int textureId = getCurrentFrameTexture(entry, animated);
        if (textureId != -1) {
            renderTexture(textureId, x, y, width, height);
            return true;
        }
        return false;
    }

    public static boolean drawUrlImageCircle(String url, float cx, float cy, float size, boolean animated) {
        return drawUrlImageCircle(url, cx, cy, size, animated, 0xFFFFFFFF);
    }

    public static boolean drawUrlImageCircle(String url, float cx, float cy, float size, boolean animated, int tintColor) {
        CacheEntry entry = getOrStartDownload(url);
        if (entry == null || entry.state == 0 || entry.state == -1) return false;

        if (entry.state == 1) {
            uploadFrames(entry);
            if (entry.state == -1) return false;
        }

        int textureId = getCurrentFrameTexture(entry, animated);
        if (textureId != -1) {
            renderTextureCircle(textureId, cx, cy, size, tintColor);
            return true;
        }
        return false;
    }

    private static int getCurrentFrameTexture(CacheEntry entry, boolean animated) {
        if (entry.frames.isEmpty()) return -1;
        if (!animated || entry.frames.size() == 1) return entry.frames.get(0).textureId;

        long time = System.currentTimeMillis() % entry.totalDurationMs;
        long elapsed = 0;
        for (FrameData frame : entry.frames) {
            elapsed += frame.delayMs;
            if (time < elapsed) return frame.textureId;
        }
        return entry.frames.get(entry.frames.size() - 1).textureId;
    }

    public static void invalidate(String url) {
        CacheEntry entry = cache.remove(url);
        if (entry != null && !entry.frames.isEmpty()) {
            for (FrameData fd : entry.frames) {
                if (fd.textureId != -1) {
                    try { GL11.glDeleteTextures(fd.textureId); } catch (Exception ignored) {}
                }
            }
        }
    }

    private static CacheEntry getOrStartDownload(String url) {
        if (url == null || url.isEmpty()) return null;
        CacheEntry entry = cache.get(url);
        if (entry == null) {
            entry = new CacheEntry();
            cache.put(url, entry);
            final CacheEntry e = entry;
            Thread t = new Thread(() -> downloadImage(url, e), "Hades-ImgDL");
            t.setDaemon(true);
            t.start();
        }
        return entry;
    }

    // ══════════════════════════════════════════
    // Internal Downloading & Decoding
    // ══════════════════════════════════════════

    private static void downloadImage(String url, CacheEntry entry) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "HadesClient/1.0");

            if (conn.getResponseCode() != 200) {
                HadesLogger.get().warn("UrlImageCache: HTTP " + conn.getResponseCode() + " for " + url);
                entry.state = -1;
                return;
            }

            try (InputStream is = conn.getInputStream();
                 ImageInputStream iis = ImageIO.createImageInputStream(is)) {

                java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) throw new RuntimeException("No ImageReader found");

                ImageReader reader = readers.next();
                reader.setInput(iis);

                int numFrames = reader.getNumImages(true);
                BufferedImage master = null;
                Graphics2D g2d = null;

                for (int i = 0; i < numFrames; i++) {
                    BufferedImage frameImg = reader.read(i);
                    if (i == 0) {
                        entry.imgWidth = frameImg.getWidth();
                        entry.imgHeight = frameImg.getHeight();
                        master = new BufferedImage(entry.imgWidth, entry.imgHeight, BufferedImage.TYPE_INT_ARGB);
                        g2d = master.createGraphics();
                        g2d.setBackground(new java.awt.Color(0, 0, 0, 0));
                    }

                    // For GIFs, we need to handle disposal methods. For simplicity, we assume
                    // most basic avatars just paint over the previous frame (DisposalMethod 1 or 0).
                    // We extract metadata to get X/Y offset and delay.
                    long delayMs = 100;
                    int xOff = 0, yOff = 0;
                    String disposal = "none";

                    try {
                        IIOMetadata meta = reader.getImageMetadata(i);
                        String metaFormat = meta.getNativeMetadataFormatName();
                        if (metaFormat != null) {
                            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(metaFormat);
                            NodeList children = root.getChildNodes();
                            for (int j = 0; j < children.getLength(); j++) {
                                Node nodeItem = children.item(j);
                                if (nodeItem.getNodeName().equals("GraphicControlExtension")) {
                                    NamedNodeMap attr = nodeItem.getAttributes();
                                    Node delayNode = attr.getNamedItem("delayTime");
                                    if (delayNode != null) delayMs = Integer.parseInt(delayNode.getNodeValue()) * 10L;
                                    Node dispNode = attr.getNamedItem("disposalMethod");
                                    if (dispNode != null) disposal = dispNode.getNodeValue();
                                }
                                if (nodeItem.getNodeName().equals("ImageDescriptor")) {
                                    NamedNodeMap attr = nodeItem.getAttributes();
                                    Node xNode = attr.getNamedItem("imageLeftPosition");
                                    if (xNode != null) xOff = Integer.parseInt(xNode.getNodeValue());
                                    Node yNode = attr.getNamedItem("imageTopPosition");
                                    if (yNode != null) yOff = Integer.parseInt(yNode.getNodeValue());
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (delayMs == 0) delayMs = 100;

                    // Composite
                    if (disposal.equals("restoreToBackgroundColor")) {
                        g2d.clearRect(0, 0, entry.imgWidth, entry.imgHeight);
                    }
                    g2d.drawImage(frameImg, xOff, yOff, null);

                    // Copy the composite out to our raw list
                    BufferedImage finalFrame = new BufferedImage(entry.imgWidth, entry.imgHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D fg = finalFrame.createGraphics();
                    fg.drawImage(master, 0, 0, null);
                    fg.dispose();

                    entry.rawFrames.add(finalFrame);
                    entry.rawDelays.add(delayMs);

                    if (disposal.equals("restoreToPrevious")) {
                        // Complex to replicate perfectly without a full buffer stack,
                        // most basic avatars don't use this.
                    }
                }

                if (g2d != null) g2d.dispose();
                reader.dispose();

                if (!entry.rawFrames.isEmpty()) {
                    entry.state = 1;
                    HadesLogger.get().info("UrlImageCache: Downloaded/Parsed " + numFrames + " frames from " + url);
                } else {
                    entry.state = -1;
                }
            }
        } catch (Exception e) {
            HadesLogger.get().error("UrlImageCache: Failed to download " + url, e);
            entry.state = -1;
        }
    }

    private static void uploadFrames(CacheEntry entry) {
        try {
            for (int i = 0; i < entry.rawFrames.size(); i++) {
                BufferedImage img = entry.rawFrames.get(i);
                int[] pixels = new int[img.getWidth() * img.getHeight()];
                img.getRGB(0, 0, img.getWidth(), img.getHeight(), pixels, 0, img.getWidth());
                
                int texId = uploadTexturePixels(pixels, img.getWidth(), img.getHeight());
                if (texId != -1) {
                    long delay = entry.rawDelays.get(i);
                    entry.frames.add(new FrameData(texId, delay));
                    entry.totalDurationMs += delay;
                }
            }
            entry.rawFrames.clear();
            entry.rawDelays.clear();
            
            entry.state = entry.frames.isEmpty() ? -1 : 2;
        } catch (Exception e) {
            HadesLogger.get().error("UrlImageCache: GL upload frames failed", e);
            entry.state = -1;
        }
    }

    private static int uploadTexturePixels(int[] pixels, int width, int height) {
        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
            buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
            buffer.put((byte) (pixel & 0xFF));         // B
            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
        buffer.flip();

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        return texId;
    }

    // ══════════════════════════════════════════
    // Rendering
    // ══════════════════════════════════════════

    private static void renderTexture(int textureId, float x, float y, float width, float height) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + width, y);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    private static void renderTextureCircle(int textureId, float cx, float cy, float size, int tintColor) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GL11.glStencilMask(0xFF);
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);

        float radius = size / 2f;
        float centerX = cx + radius;
        float centerY = cy + radius;
        int segments = 32;
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            GL11.glVertex2f(centerX + (float) Math.cos(angle) * radius,
                           centerY + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();

        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GL11.glStencilMask(0x00);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        float tR = ((tintColor >> 16) & 0xFF) / 255f;
        float tG = ((tintColor >> 8) & 0xFF) / 255f;
        float tB = (tintColor & 0xFF) / 255f;
        float tA = ((tintColor >> 24) & 0xFF) / 255f;
        GL11.glColor4f(tR, tG, tB, tA);
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(cx, cy);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(cx, cy + size);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(cx + size, cy + size);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(cx + size, cy);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glPopAttrib();
    }
}
