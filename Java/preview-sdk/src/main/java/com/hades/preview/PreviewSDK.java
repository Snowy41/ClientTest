package com.hades.preview;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for the Preview SDK.
 * Reads newline-delimited JSON from stdin, renders module overlays
 * to a headless GL framebuffer, and writes responses to stdout.
 *
 * Message types:
 *   IN:  { "type": "render", "module": "PlayerESP", "config": {...}, "entities": [...], "requestId": "..." }
 *   OUT: { "type": "frame", "width": 1280, "height": 720, "data": "<base64 RGBA>", "requestId": "..." }
 *
 *   IN:  { "type": "schema", "module": "PlayerESP", "requestId": "..." }
 *   OUT: { "type": "schema", "module": "PlayerESP", "fields": [...], "requestId": "..." }
 */
public class PreviewSDK {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private static HeadlessGLContext glContext;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        int width = DEFAULT_WIDTH;
        int height = DEFAULT_HEIGHT;

        // Parse optional size args: --width 1920 --height 1080
        for (int i = 0; i < args.length - 1; i++) {
            if ("--width".equals(args[i])) width = Integer.parseInt(args[i + 1]);
            if ("--height".equals(args[i])) height = Integer.parseInt(args[i + 1]);
        }

        // Initialize headless GL
        glContext = new HeadlessGLContext();
        glContext.init(width, height);
        System.err.println("[PreviewSDK] Initialized GL context: " + width + "x" + height);

        // Stdin/stdout JSON loop
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(System.out, true);

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
                    String type = msg.get("type").getAsString();
                    String requestId = msg.has("requestId") ? msg.get("requestId").getAsString() : null;

                    switch (type) {
                        case "render":
                            handleRender(msg, requestId, writer);
                            break;
                        case "schema":
                            handleSchema(msg, requestId, writer);
                            break;
                        default:
                            writeError(writer, requestId, "Unknown message type: " + type);
                    }
                } catch (Exception e) {
                    System.err.println("[PreviewSDK] Error processing message: " + e.getMessage());
                    e.printStackTrace(System.err);
                    writeError(writer, null, e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[PreviewSDK] Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            glContext.destroy();
        }
    }

    private static void handleRender(JsonObject msg, String requestId, PrintWriter writer) {
        String moduleId = msg.get("module").getAsString();
        ModulePreviewRenderer renderer = PreviewRegistry.get(moduleId);
        if (renderer == null) {
            writeError(writer, requestId, "Unknown module: " + moduleId);
            return;
        }

        // Parse config
        Map<String, Object> config = new HashMap<>();
        if (msg.has("config") && msg.get("config").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : msg.getAsJsonObject("config").entrySet()) {
                JsonElement v = entry.getValue();
                if (v.isJsonPrimitive()) {
                    if (v.getAsJsonPrimitive().isBoolean()) config.put(entry.getKey(), v.getAsBoolean());
                    else if (v.getAsJsonPrimitive().isNumber()) config.put(entry.getKey(), v.getAsDouble());
                    else config.put(entry.getKey(), v.getAsString());
                }
            }
        }

        // Parse entities
        List<ScreenEntity> entities = new ArrayList<>();
        if (msg.has("entities") && msg.get("entities").isJsonArray()) {
            JsonArray arr = msg.getAsJsonArray("entities");
            for (JsonElement el : arr) {
                entities.add(gson.fromJson(el, ScreenEntity.class));
            }
        }

        // Render
        glContext.bind();
        renderer.render(glContext, config, entities);

        // Read pixels and encode
        byte[] pixels = glContext.readPixels();
        String base64 = Base64.getEncoder().encodeToString(pixels);

        // Write response
        JsonObject response = new JsonObject();
        response.addProperty("type", "frame");
        response.addProperty("width", glContext.getWidth());
        response.addProperty("height", glContext.getHeight());
        response.addProperty("data", base64);
        if (requestId != null) response.addProperty("requestId", requestId);
        writer.println(response.toString());
    }

    private static void handleSchema(JsonObject msg, String requestId, PrintWriter writer) {
        String moduleId = msg.get("module").getAsString();
        ModulePreviewRenderer renderer = PreviewRegistry.get(moduleId);
        if (renderer == null) {
            writeError(writer, requestId, "Unknown module: " + moduleId);
            return;
        }

        ConfigSchema schema = renderer.getSchema();
        JsonObject response = new JsonObject();
        response.addProperty("type", "schema");
        response.addProperty("module", moduleId);
        response.add("fields", gson.toJsonTree(schema.getFields()));
        if (requestId != null) response.addProperty("requestId", requestId);
        writer.println(response.toString());
    }

    private static void writeError(PrintWriter writer, String requestId, String error) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "error");
        response.addProperty("error", error);
        if (requestId != null) response.addProperty("requestId", requestId);
        writer.println(response.toString());
    }
}
