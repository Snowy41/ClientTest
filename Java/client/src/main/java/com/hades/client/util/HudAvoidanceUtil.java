package com.hades.client.util;

import com.hades.client.platform.PlatformManager;
import com.hades.client.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-element collision detection against LabyMod 4 HUD widgets.
 * Only active when the detected platform is LABYMOD.
 *
 * Each Hades HUD element calls {@link #findSafeY} before rendering.
 * The method checks if the element's intended bounding box overlaps with any
 * active LabyMod widget and, if so, shifts the element downward until it
 * no longer collides.
 */
public class HudAvoidanceUtil {

    private static final float GAP = 3f;

    // Cache widget rectangles for one frame to avoid repeated reflection
    private static long lastCacheTime = 0;
    private static final List<float[]> cachedRects = new ArrayList<>();

    /**
     * Given the intended position and size of a Hades element, returns an
     * adjusted Y coordinate that does not overlap any LabyMod widget.
     *
     * @param x     intended X
     * @param y     intended Y
     * @param width element width
     * @param height element height
     * @param screenWidth  the screen width from event scale
     * @param screenHeight the screen height from event scale
     * @return the Y value to draw at (may equal the input if no collision)
     */
    public static float findSafeY(float x, float y, float width, float height, float screenWidth, float screenHeight) {
        if (PlatformManager.getDetectedPlatform() != ClientPlatform.LABYMOD) {
            return y;
        }

        List<float[]> rects = getWidgetRects(screenWidth, screenHeight);
        if (rects.isEmpty()) return y;

        // Iteratively push our element downward while it overlaps any widget
        boolean moved = true;
        int iterations = 0;
        while (moved && iterations < 10) {
            moved = false;
            for (float[] wr : rects) {
                if (rectsOverlap(x, y, width, height, wr[0], wr[1], wr[2], wr[3])) {
                    y = wr[1] + wr[3] + GAP; // move just below this widget
                    moved = true;
                }
            }
            iterations++;
        }
        return y;
    }

    // ── Widget rectangle collection (cached per frame) ──────────────────

    private static List<float[]> getWidgetRects(float screenWidth, float screenHeight) {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime < 50) {
            return cachedRects;
        }
        lastCacheTime = now;
        cachedRects.clear();

        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object labyAPI = labyClass.getMethod("labyAPI").invoke(null);
            Object ingameOverlay = labyAPI.getClass().getMethod("ingameOverlay").invoke(labyAPI);
            
            java.util.List<?> activities = (java.util.List<?>) ingameOverlay.getClass().getMethod("getActivities").invoke(ingameOverlay);

            for (Object activity : activities) {
                if (activity.getClass().getName().endsWith("HudWidgetOverlay")) {
                    java.lang.reflect.Field rendererWidgetField = activity.getClass().getDeclaredField("rendererWidget");
                    rendererWidgetField.setAccessible(true);
                    Object rendererWidget = rendererWidgetField.get(activity);
                    
                    if (rendererWidget == null) continue;

                    java.util.List<?> children = (java.util.List<?>) rendererWidget.getClass().getMethod("getChildren").invoke(rendererWidget);
                    
                    for (Object child : children) {
                        try {
                            Object hudWidget = child.getClass().getMethod("hudWidget").invoke(child);
                            if (hudWidget == null) continue;
                            
                            boolean isVisible = (boolean) hudWidget.getClass().getMethod("isVisibleInGame").invoke(hudWidget);
                            if (!isVisible) continue;

                            Object boundsObj = child.getClass().getMethod("scaledBounds").invoke(child);
                            if (boundsObj == null) continue;

                            float rx = ((Number) boundsObj.getClass().getMethod("getX").invoke(boundsObj)).floatValue();
                            float ry = ((Number) boundsObj.getClass().getMethod("getY").invoke(boundsObj)).floatValue();
                            float rw = ((Number) boundsObj.getClass().getMethod("getWidth").invoke(boundsObj)).floatValue();
                            float rh = ((Number) boundsObj.getClass().getMethod("getHeight").invoke(boundsObj)).floatValue();

                            if (rx >= -rw && ry >= -rh && rx <= screenWidth && ry <= screenHeight) {
                                cachedRects.add(new float[]{rx, ry, rw, rh});
                            }
                        } catch (Exception ignored) {
                            // Skip a specific widget if reflection fails
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Overall reflection failure
        }
        return cachedRects;
    }

    // ── AABB overlap test ───────────────────────────────────────────────

    private static boolean rectsOverlap(float x1, float y1, float w1, float h1,
                                        float x2, float y2, float w2, float h2) {
        return x1 < x2 + w2
            && x1 + w1 > x2
            && y1 < y2 + h2
            && y1 + h1 > y2;
    }
}
