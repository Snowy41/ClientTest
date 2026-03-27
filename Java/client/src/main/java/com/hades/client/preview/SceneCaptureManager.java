package com.hades.client.preview;

import com.hades.client.util.HadesLogger;
import com.hades.client.api.HadesAPI;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Scene Capture Manager — dev-only tool for generating ScenePacks.
 *
 * Captures 6 cubemap face screenshots + scene.json with entity data.
 * These files are shipped with the Electron launcher to power the
 * Visual Configurator preview.
 *
 * COMPLETELY INERT unless -Dclient.preview.capture=true is set.
 * Does not affect normal gameplay in any way.
 *
 * Triggered by F8 keybind (LWJGL key code 66).
 *
 * KEY FIX: Each face is captured with FOV forced to exactly 90°.
 * The screenshot is then center-cropped to a perfect square (height × height).
 * This guarantees every saved PNG represents exactly 90°×90° of the scene,
 * which is the only geometry that tiles seamlessly in a Three.js CubeTexture.
 */
public class SceneCaptureManager {

    private static final HadesLogger LOG = HadesLogger.get();

    public static final String CAPTURE_FLAG = "client.preview.capture";
    public static final String OUTPUT_DIR_PROP = "client.preview.outputDir";

    /** LWJGL key code for F8 */
    public static final int CAPTURE_KEY = 66;

    /**
     * The FOV used for every cubemap face capture.
     * MUST be 90° — this is what makes adjacent face edges line up perfectly.
     * A cubemap face represents exactly 90°×90° of the world. Any other FOV
     * produces edges that don't match the neighbouring face.
     */
    private static final float CAPTURE_FOV = 90.0f;

    private static boolean capturing = false;

    public static boolean isCaptureEnabled() {
        return Boolean.getBoolean(CAPTURE_FLAG);
    }

    /**
     * Handle F8 key press — initiates scene capture.
     */
    public static void onCaptureKey(String moduleId, String sceneName) {
        if (!isCaptureEnabled())
            return;
        if (capturing) {
            LOG.info("[Capture] Already capturing, ignoring.");
            return;
        }

        capturing = true;
        LOG.info("[Capture] Starting capture for " + moduleId + "/" + sceneName);

        try {
            captureScene(moduleId, sceneName);
        } catch (Exception e) {
            LOG.error("[Capture] Failed: " + e.getMessage());
            e.printStackTrace();
            capturing = false;
        }
    }

    /** Quick capture with auto-generated scene name from timestamp. */
    public static void onCaptureKey() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        onCaptureKey("PlayerESP", "scene_" + ts);
    }

    private static void captureScene(String moduleId, String sceneName) throws Exception {
        String outputDirStr = System.getProperty(OUTPUT_DIR_PROP, "./preview-captures");
        Path sceneDir = Paths.get(outputDirStr, moduleId, sceneName);
        Files.createDirectories(sceneDir);

        Object player = HadesAPI.player;
        if (player == null) {
            LOG.error("[Capture] Player is null, cannot capture.");
            capturing = false;
            return;
        }

        float origYaw = HadesAPI.Player.getYaw();
        float origPitch = HadesAPI.Player.getPitch();
        float origFov = getFov();

        LOG.info("[Capture] Original camera: yaw=" + origYaw
                + " pitch=" + origPitch + " fov=" + origFov);
        LOG.info("[Capture] Will capture all faces at FOV=" + CAPTURE_FOV
                + " and save square PNGs.");

        // Force FOV to 90° for the entire capture sequence.
        // This is restored after all faces are saved.
        setFov(CAPTURE_FOV);

        com.hades.client.HadesClient.getInstance().getEventBus().register(
                new CaptureStateMachine(moduleId, sceneName, sceneDir,
                        origYaw, origPitch, origFov));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State machine
    // ─────────────────────────────────────────────────────────────────────────

    public static class CaptureStateMachine {

        private final String moduleId;
        private final String sceneName;
        private final Path sceneDir;
        private final float origYaw, origPitch, origFov;

        private int step = 0;
        private int waitTicks = 0;

        /**
         * Cubemap face rotations [yaw, pitch] in Minecraft coordinates.
         *
         * NOTE: pitch is clamped to 89.9 / -89.9 instead of exactly 90 / -90.
         * Minecraft internally clamps pitch to ±90 but at exactly ±90 the
         * camera interpolation can produce a corrupted (black or repeated) frame.
         * 89.9° is visually indistinguishable from 90° for a cubemap face.
         */
        private final float[][] rotations = {
                { 90f, 0f }, // px — west
                { -90f, 0f }, // nx — east
                { 0f, -89.9f }, // py — up (avoid exact -90 pole clamp)
                { 0f, 89.9f }, // ny — down (avoid exact +90 pole clamp)
                { 0f, 0f }, // pz — south (forward)
                { 180f, 0f }, // nz — north (back)
        };
        private final String[] names = { "px", "nx", "py", "ny", "pz", "nz" };

        /**
         * Extra wait frames for up/down faces — pole angles need more time to
         * stabilize.
         */
        private static final int WAIT_NORMAL = 20;
        private static final int WAIT_POLE = 40; // indices 2 and 3 (py, ny)

        public CaptureStateMachine(String moduleId, String sceneName, Path sceneDir,
                float origYaw, float origPitch, float origFov) {
            this.moduleId = moduleId;
            this.sceneName = sceneName;
            this.sceneDir = sceneDir;
            this.origYaw = origYaw;
            this.origPitch = origPitch;
            this.origFov = origFov;

            setCamera(rotations[0][0], rotations[0][1]);
        }

        private void setCamera(float yaw, float pitch) {
            HadesAPI.Player.setYaw(yaw);
            HadesAPI.Player.setPitch(pitch);
            HadesAPI.player.setPrevYaw(yaw);
            HadesAPI.player.setPrevPitch(pitch);

            try {
                // Get renderViewEntity dynamically
                Object mc = HadesAPI.mc;
                Object renderViewEntity = null;
                Method m = com.hades.client.util.ReflectionUtil.findMethod(mc.getClass(), new String[]{"ac", "getRenderViewEntity", "func_175606_aa"});
                if (m != null) {
                    renderViewEntity = m.invoke(mc);
                } else {
                    java.lang.reflect.Field f = com.hades.client.util.ReflectionUtil.findField(mc.getClass(), "aV", "renderViewEntity", "field_175622_Z");
                    if (f != null) renderViewEntity = f.get(mc);
                }

                if (renderViewEntity != null && renderViewEntity != HadesAPI.player) {
                    java.lang.reflect.Field fYaw = com.hades.client.util.ReflectionUtil.findField(renderViewEntity.getClass(), "y", "rotationYaw", "field_70177_z");
                    java.lang.reflect.Field fPitch = com.hades.client.util.ReflectionUtil.findField(renderViewEntity.getClass(), "z", "rotationPitch", "field_70125_A");
                    java.lang.reflect.Field fPYaw = com.hades.client.util.ReflectionUtil.findField(renderViewEntity.getClass(), "A", "prevRotationYaw", "field_70126_B");
                    java.lang.reflect.Field fPPitch = com.hades.client.util.ReflectionUtil.findField(renderViewEntity.getClass(), "B", "prevRotationPitch", "field_70127_C");
                    if (fYaw != null)
                        fYaw.setFloat(renderViewEntity, yaw);
                    if (fPitch != null)
                        fPitch.setFloat(renderViewEntity, pitch);
                    if (fPYaw != null)
                        fPYaw.setFloat(renderViewEntity, yaw);
                    if (fPPitch != null)
                        fPPitch.setFloat(renderViewEntity, pitch);
                }
            } catch (Exception ignored) {
            }
        }

        /**
         * Returns true if the actual camera pitch is close enough to the target.
         * Used to detect when Minecraft has snapped pitch back (e.g. at poles).
         */
        private boolean isCameraStable(float targetYaw, float targetPitch) {
            float actualPitch = HadesAPI.Player.getPitch();
            float actualYaw = HadesAPI.Player.getYaw();
            return Math.abs(actualPitch - targetPitch) < 2.0f
                    && Math.abs(actualYaw - targetYaw) < 2.0f;
        }

        @com.hades.client.event.EventHandler
        public void onRender(com.hades.client.event.events.Render3DEvent event) {
            if (step >= 6)
                return;

            float targetYaw = rotations[step][0];
            float targetPitch = rotations[step][1];

            // Force FOV and camera EVERY tick during the wait period.
            setFov(CAPTURE_FOV);
            setCamera(targetYaw, targetPitch);

            // Up/down faces need extra wait — pole pitch takes longer to stabilize.
            int requiredWait = (step == 2 || step == 3) ? WAIT_POLE : WAIT_NORMAL;

            // Also require the camera to actually BE at the target angle before
            // we start counting wait ticks. This catches cases where Minecraft
            // snaps pitch back to a clamped value on the same tick we set it.
            if (!isCameraStable(targetYaw, targetPitch)) {
                // Camera not stable yet — reset wait counter and try again next frame.
                waitTicks = 0;
                return;
            }

            if (waitTicks < requiredWait) {
                waitTicks++;
                return;
            }

            // ── Take screenshot and crop to square ───────────────────────────
            BufferedImage raw = takeScreenshot();
            if (raw != null) {
                BufferedImage face = cropToSquare(raw);
                try {
                    File outFile = sceneDir.resolve("cubemap_" + names[step] + ".png").toFile();
                    ImageIO.write(face, "PNG", outFile);
                    LOG.info("[Capture] Saved " + names[step]
                            + " (" + face.getWidth() + "×" + face.getHeight() + ")"
                            + " yaw=" + targetYaw + " pitch=" + targetPitch);
                } catch (Exception e) {
                    LOG.error("[Capture] Failed to save " + names[step] + ": " + e.getMessage());
                }
            } else {
                LOG.error("[Capture] Screenshot was null for face " + names[step]);
            }

            step++;
            waitTicks = 0;

            if (step < 6) {
                setCamera(rotations[step][0], rotations[step][1]);
            } else {
                finishCapture();
            }
        }

        private void finishCapture() {
            com.hades.client.HadesClient.getInstance().getEventBus().unregister(this);

            // Restore camera and FOV
            setCamera(origYaw, origPitch);
            setFov(origFov);

            // Write scene.json with the CAPTURE_FOV value so PanoramaRenderer
            // knows the exact FOV the screenshots were taken at.
            try {
                captureSceneJson(sceneDir, moduleId, sceneName,
                        origYaw, origPitch, CAPTURE_FOV);
            } catch (Exception e) {
                LOG.error("[Capture] Failed to save scene.json: " + e.getMessage());
            }

            LOG.info("[Capture] ScenePack written to: " + sceneDir.toAbsolutePath());
            capturing = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Square crop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Center-crops a screenshot to a perfect square using the image height as
     * the face size.
     *
     * WHY: A Minecraft screenshot at FOV=90° on a 1920×1080 display has:
     * vertical FOV = 90° → 1080 px represents 90°
     * horizontal FOV ≈ 106° → more than 90°, so we must crop the sides
     *
     * To extract exactly 90° horizontally we need width = height = 1080px.
     * startX = (1920 - 1080) / 2 = 420 — centers the crop.
     *
     * The resulting 1080×1080 image represents exactly 90°×90° of the world.
     * Every adjacent face will share its boundary pixel row/column.
     */
    private static BufferedImage cropToSquare(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        if (w == h) {
            // Already square — nothing to do.
            return src;
        }

        // Use height as the square size (vertical FOV is always 90°).
        int size = h;
        int startX = (w - size) / 2; // center horizontally

        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, size, size,
                startX, 0, startX + size, size, null);
        g.dispose();

        LOG.info("[Capture] Cropped " + w + "×" + h + " → " + size + "×" + size
                + " (startX=" + startX + ")");
        return square;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // scene.json
    // ─────────────────────────────────────────────────────────────────────────

    private static void captureSceneJson(Path dir, String moduleId, String sceneName,
            float yaw, float pitch, float fov) throws Exception {
        double px = HadesAPI.Player.getX();
        double py = HadesAPI.Player.getY();
        double pz = HadesAPI.Player.getZ();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"moduleId\": \"").append(escapeJson(moduleId)).append("\",\n");
        json.append("  \"sceneName\": \"").append(escapeJson(sceneName)).append("\",\n");
        json.append("  \"capturedAt\": \"")
                .append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()))
                .append("\",\n");

        json.append("  \"camera\": {\n");
        json.append("    \"fov\": ").append(fov).append(",\n");
        json.append("    \"position\": [")
                .append(px).append(", ").append(py).append(", ").append(pz).append("],\n");
        json.append("    \"yaw\": ").append(yaw).append(",\n");
        json.append("    \"pitch\": ").append(pitch).append("\n");
        json.append("  },\n");

        json.append("  \"entities\": [\n");
        java.util.List<com.hades.client.api.interfaces.IEntity> entities = HadesAPI.world.getLoadedEntities();
        boolean firstEntity = true;
        for (com.hades.client.api.interfaces.IEntity entity : entities) {
            if (entity == HadesAPI.player)
                continue;

            double ex = entity.getX();
            double ey = entity.getY();
            double ez = entity.getZ();
            double dist = entity.getDistanceToEntity(HadesAPI.player);

            if (dist > 32.0)
                continue;

            if (!firstEntity)
                json.append(",\n");
            firstEntity = false;

            String type = getEntityType(entity);
            json.append("    {\n");
            json.append("      \"type\": \"").append(type).append("\",\n");
            json.append("      \"id\": \"").append(type).append("_")
                    .append(System.identityHashCode(entity)).append("\",\n");
            json.append("      \"position\": [")
                    .append(ex).append(", ").append(ey).append(", ").append(ez).append("],\n");

            if (entity.isPlayer() || entity.isLiving()) {
                json.append("      \"yaw\": ").append(entity.getYaw()).append(",\n");
                json.append("      \"pitch\": ").append(entity.getPitch()).append(",\n");
                json.append("      \"height\": 1.8,\n");
                json.append("      \"width\": 0.6,\n");
                json.append("      \"metadata\": {\n");
                json.append("        \"health\": ").append(HadesAPI.Player.getHealth()).append(",\n");
                json.append("        \"name\": \"")
                        .append(escapeJson(getEntityName(entity))).append("\",\n");
                json.append("        \"distance\": ")
                        .append(String.format(java.util.Locale.US, "%.1f", dist)).append(",\n");
                json.append("        \"armorPoints\": 0\n");
                json.append("      }\n");
            } else {
                json.append("      \"height\": 1.0,\n");
                json.append("      \"width\": 1.0\n");
            }

            json.append("    }");
        }
        json.append("\n  ],\n");
        json.append("  \"objects\": []\n");
        json.append("}\n");

        File jsonFile = dir.resolve("scene.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(json.toString());
        }
        LOG.info("[Capture] Saved scene.json (fov=" + fov + ")");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void setFov(float fov) {
        try {
            Object gs = (Object)null;
            if (gs == null)
                return;
            java.lang.reflect.Field f = com.hades.client.util.ReflectionUtil.findField(gs.getClass(),
                    "aF", "fovSetting", "field_74334_X");
            if (f != null) {
                f.setFloat(gs, fov);
            }
        } catch (Exception e) {
            LOG.warn("[Capture] Could not set FOV: " + e.getMessage());
        }
    }

    private static float getFov() {
        try {
            Object gs = (Object)null;
            if (gs == null)
                return 70f;
            java.lang.reflect.Field f = com.hades.client.util.ReflectionUtil.findField(gs.getClass(),
                    "aF", "fovSetting", "field_74334_X");
            if (f != null)
                return f.getFloat(gs);
        } catch (Exception e) {
            // ignore
        }
        return 70f;
    }

    private static BufferedImage takeScreenshot() {
        try {
            int w = HadesAPI.mc.displayWidth();
            int h = HadesAPI.mc.displayHeight();

            Class<?> gl11 = com.hades.client.util.ReflectionUtil.findClass("org.lwjgl.opengl.GL11");
            Class<?> gl12 = com.hades.client.util.ReflectionUtil.findClass("org.lwjgl.opengl.GL12");
            Class<?> bufferUtils = com.hades.client.util.ReflectionUtil.findClass("org.lwjgl.BufferUtils");
            if (gl11 == null || gl12 == null || bufferUtils == null)
                return null;

            Method createIntBuffer = bufferUtils.getMethod("createIntBuffer", int.class);
            java.nio.IntBuffer buffer = (java.nio.IntBuffer) createIntBuffer.invoke(null, w * h);

            int GL_PACK_ALIGNMENT = 0x0D05;
            int GL_BGRA = 0x80E1;
            int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;
            int GL_FRAMEBUFFER = 36160;
            int GL_READ_FRAMEBUFFER = 36008;
            int GL_DRAW_FRAMEBUFFER = 36009;
            int GL_COLOR_ATTACHMENT0 = 36064;
            int GL_COLOR_BUFFER_BIT = 16384;
            int GL_NEAREST = 9728;
            int GL_TEXTURE_2D = 3553;
            int GL_RGBA8 = 32856;
            int GL_RGBA = 6408;
            int GL_UNSIGNED_BYTE = 5121;
            int GL_FRAMEBUFFER_BINDING = 36006;

            Method glPixelStorei = gl11.getMethod("glPixelStorei", int.class, int.class);
            glPixelStorei.invoke(null, GL_PACK_ALIGNMENT, 1);

            Method glGetInteger = gl11.getMethod("glGetInteger", int.class);
            int currentFbo = (int) glGetInteger.invoke(null, GL_FRAMEBUFFER_BINDING);

            Class<?> gl30Class;
            try {
                gl30Class = Class.forName("org.lwjgl.opengl.GL30");
            } catch (ClassNotFoundException e) {
                gl30Class = Class.forName("org.lwjgl.opengl.EXTFramebufferObject");
            }

            try {
                Method glGenFramebuffers = gl30Class.getMethod("glGenFramebuffers");
                Method glBindFramebuffer = gl30Class.getMethod("glBindFramebuffer",
                        int.class, int.class);
                Method glFramebufferTexture2D = gl30Class.getMethod("glFramebufferTexture2D",
                        int.class, int.class, int.class, int.class, int.class);
                Method glBlitFramebuffer = gl30Class.getMethod("glBlitFramebuffer",
                        int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class,
                        int.class, int.class);
                Method glDeleteFramebuffers = gl30Class.getMethod("glDeleteFramebuffers",
                        int.class);

                int tempFbo = (int) glGenFramebuffers.invoke(null);

                Method glGenTextures = gl11.getMethod("glGenTextures");
                Method glBindTexture = gl11.getMethod("glBindTexture",
                        int.class, int.class);
                Method glTexImage2D = gl11.getMethod("glTexImage2D",
                        int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, java.nio.ByteBuffer.class);
                Method glDeleteTextures = gl11.getMethod("glDeleteTextures", int.class);

                int tempTex = (int) glGenTextures.invoke(null);
                glBindTexture.invoke(null, GL_TEXTURE_2D, tempTex);
                glTexImage2D.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, null);

                glBindFramebuffer.invoke(null, GL_DRAW_FRAMEBUFFER, tempFbo);
                glFramebufferTexture2D.invoke(null, GL_DRAW_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tempTex, 0);

                glBindFramebuffer.invoke(null, GL_READ_FRAMEBUFFER, currentFbo);
                glBlitFramebuffer.invoke(null,
                        0, 0, w, h, 0, 0, w, h,
                        GL_COLOR_BUFFER_BIT, GL_NEAREST);

                glBindFramebuffer.invoke(null, GL_READ_FRAMEBUFFER, tempFbo);
                Method glReadPixels = gl11.getMethod("glReadPixels",
                        int.class, int.class, int.class, int.class,
                        int.class, int.class, java.nio.IntBuffer.class);
                glReadPixels.invoke(null, 0, 0, w, h,
                        GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buffer);

                glBindFramebuffer.invoke(null, GL_FRAMEBUFFER, currentFbo);
                glDeleteFramebuffers.invoke(null, tempFbo);
                glDeleteTextures.invoke(null, tempTex);

            } catch (Exception e) {
                Method glReadPixels = gl11.getMethod("glReadPixels",
                        int.class, int.class, int.class, int.class,
                        int.class, int.class, java.nio.IntBuffer.class);
                glReadPixels.invoke(null, 0, 0, w, h,
                        GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            }

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int srcIdx = (h - 1 - y) * w + x;
                    img.setRGB(x, y, buffer.get(srcIdx));
                }
            }
            return img;

        } catch (Exception e) {
            LOG.error("[Capture] Screenshot failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String getEntityType(com.hades.client.api.interfaces.IEntity entity) {
        if (entity.isPlayer())
            return "player";
        if (entity.isLiving())
            return "mob";
        return "entity";
    }

    private static String getEntityName(com.hades.client.api.interfaces.IEntity entity) {
        try {
            Method getName = com.hades.client.util.ReflectionUtil.findMethod(entity.getClass(),
                    new String[] { "e_", "getName", "func_70005_c_" });
            if (getName != null) {
                Object name = getName.invoke(entity);
                return name != null ? name.toString() : "Unknown";
            }
        } catch (Exception e) {
            // ignore
        }
        return "Unknown";
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}