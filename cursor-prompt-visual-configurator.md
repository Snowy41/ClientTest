# Cursor Prompt — Visual Configurator for Minecraft Client Launcher

## CONTEXT

We have an Electron-based Minecraft client launcher. We inject a .dll which loads our client.jar (which has our own Mixin system and all client modules like PlayerESP, HUD, ItemESP, ChestESP, etc.).

We want to add a "Visual Configurator" feature to the launcher. This lets users visually configure their client modules (colors, thickness, opacity, render mode, etc.) and see a REAL ingame-looking preview — WITHOUT the client or Minecraft running at all.

The scenes shown in the preview are pre-captured by us (the dev team) using a capture tool built into the client while it was running. The launcher ships with these captured scenes and replays them with live module overlays composited on top.

DO NOT recreate Minecraft geometry. DO NOT hardcode fake 3D scenes. The background is always a real captured screenshot/cubemap from actual gameplay. The only thing rendered dynamically is the module overlay (ESP boxes, HUD elements, labels, etc.).

---

## HOW IT WORKS — OVERVIEW

### Phase 1 — Scene Capture (done once by dev team, using a running client)

A capture tool inside client.jar records a "ScenePack" for each module context. It captures:
- A 360 degree cubemap — 6 PNG faces taken by rotating the camera to each face orientation and screenshotting
- A scene.json containing entity/object positions in 3D world space, camera state (FOV, position, yaw, pitch), and metadata

These ScenePacks are committed to the launcher repo under /assets/scenes/ and shipped with the launcher. Users never run the capture tool themselves.

### Phase 2 — Launcher Preview (no client running, ever)

The Electron launcher loads a ScenePack for the selected module and:
1. Renders the cubemap as a Three.js WebGL background with rotations (MB0 down and dragging) — looks exactly like real Minecraft because it IS real Minecraft screenshots as a CubeMap
2. Each animation frame, projects the entity positions from scene.json into 2D screen space using the captured FOV and current camera rotation
3. Sends those projected screen-space positions + the current module config to a headless JVM child process (preview-sdk.jar)
4. The JVM renders the module overlay (ESP boxes, labels, health bars, etc.) to a transparent offscreen buffer and returns raw RGBA pixel bytes
5. The launcher composites the overlay on top of the Three.js canvas
6. When the user changes a config value, only step 3-5 repeat — the background never re-renders

---

## SCENEPACK FORMAT

Implement this exact file structure for every captured scene:

```
/assets/scenes/{moduleId}/{sceneName}/
  cubemap_px.png
  cubemap_nx.png
  cubemap_py.png
  cubemap_ny.png
  cubemap_pz.png
  cubemap_nz.png
  scene.json
```

scene.json must match this schema exactly:

```json
{
  "moduleId": "PlayerESP",
  "sceneName": "forest_pvp",
  "capturedAt": "2024-01-01T00:00:00Z",
  "camera": {
    "fov": 70,
    "position": [0, 64, 0],
    "yaw": 45.0,
    "pitch": -10.0
  },
  "entities": [
    {
      "type": "player",
      "id": "preview_player_1",
      "position": [4.5, 64, 6.2],
      "yaw": 180.0,
      "pitch": 0.0,
      "height": 1.8,
      "width": 0.6,
      "metadata": {
        "health": 16,
        "name": "Notch",
        "distance": 8.3,
        "armorPoints": 18
      }
    }
  ],
  "objects": [
    {
      "type": "chest",
      "position": [2.0, 63, 4.0],
      "facing": "north"
    },
    {
      "type": "item",
      "position": [3.5, 63, 5.0],
      "itemId": "minecraft:diamond_sword",
      "count": 1
    }
  ]
}
```

---

## PART 1 — CAPTURE TOOL (add to client.jar)

Create `src/main/java/com/yourclient/preview/SceneCaptureManager.java`

Requirements:
- Triggered by a dev-only keybind (F8) that only activates when a system property flag is set: `-Dclient.preview.capture=true`
- Rotates the camera to each of 6 cubemap face orientations, takes a screenshot, restores camera (depending on mouse movement)
- Collects all loaded entities within 32 blocks: their world position, bounding box size, type, and relevant metadata (health, name, distance, armor for players; item id and count for items; type for chests/blocks)
- Writes the full ScenePack to a path defined by system property `-Dclient.preview.outputDir=<path>`
- Must restore the player camera to its original rotation after capture
- Must suppress the normal screenshot sound/notification during cubemap capture

```java
public class SceneCaptureManager {

    public static final String CAPTURE_FLAG = "client.preview.capture";
    public static final String OUTPUT_DIR_PROP = "client.preview.outputDir";

    public void captureScene(MinecraftClient mc, String moduleId, String sceneName) throws IOException {
        if (!Boolean.getBoolean(CAPTURE_FLAG)) return;

        Path outputDir = Path.of(System.getProperty(OUTPUT_DIR_PROP, "./preview-captures"));
        Path sceneDir = outputDir.resolve(moduleId).resolve(sceneName);
        Files.createDirectories(sceneDir);

        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();

        captureCubemapFaces(mc, sceneDir);
        captureSceneJson(mc, sceneDir, moduleId, sceneName);

        mc.player.setYaw(originalYaw);
        mc.player.setPitch(originalPitch);
    }

    private void captureCubemapFaces(MinecraftClient mc, Path dir) throws IOException {
        float[][] rotations = {
            {0f,    0f},
            {180f,  0f},
            {90f,   0f},
            {-90f,  0f},
            {0f,   -90f},
            {0f,    90f}
        };
        String[] names = {"pz", "nz", "px", "nx", "py", "ny"};

        for (int i = 0; i < 6; i++) {
            mc.player.setYaw(rotations[i][0]);
            mc.player.setPitch(rotations[i][1]);
            // wait one render tick, then screenshot
            BufferedImage img = takeScreenshot(mc);
            ImageIO.write(img, "PNG", dir.resolve("cubemap_" + names[i] + ".png").toFile());
        }
    }

    private void captureSceneJson(MinecraftClient mc, Path dir, String moduleId, String sceneName) throws IOException {
        // collect entities within 32 blocks, serialize to scene.json
        // use Gson or Jackson — whichever your project already uses
    }
}
```

---

## PART 2 — PANORAMA RENDERER (Electron renderer process)

Create `src/renderer/preview/PanoramaRenderer.ts`

Requirements:
- Uses Three.js (already available in Electron renderer)
- Loads 6 cubemap PNGs via CubeTextureLoader, sets as scene.background
- Camera sits at world origin (0,0,0), PerspectiveCamera with FOV from scene.json
- Auto-rotates camera yaw at 0.003 radians/frame (cinematic, slow, looping)
- Exposes `projectEntities(entities, camera): ScreenEntity[]` which takes the scene.json entity list and returns their bounding boxes in canvas pixel coordinates using THREE.Vector3.project()
- This projection must account for the current camera rotation so the ESP boxes track correctly as the panorama rotates
- Fires an onFrameReady callback each frame with the projected screen entities — the overlay compositor listens to this

```typescript
import * as THREE from 'three';
import { ScenePack, SceneEntity, ScreenEntity } from './types';

export class PanoramaRenderer {
  private scene = new THREE.Scene();
  private camera: THREE.PerspectiveCamera;
  private renderer: THREE.WebGLRenderer;
  private autoRotateSpeed = 0.003;
  public onFrameReady: (entities: ScreenEntity[]) => void = () => {};

  constructor(canvas: HTMLCanvasElement) {
    this.camera = new THREE.PerspectiveCamera(70, canvas.width / canvas.height, 0.1, 1000);
    this.renderer = new THREE.WebGLRenderer({ canvas, alpha: false });
  }

  loadScenePack(pack: ScenePack): void {
    const loader = new THREE.CubeTextureLoader();
    const texture = loader.load([
      pack.cubemapPaths.px, pack.cubemapPaths.nx,
      pack.cubemapPaths.py, pack.cubemapPaths.ny,
      pack.cubemapPaths.pz, pack.cubemapPaths.nz,
    ]);
    this.scene.background = texture;
    this.camera.fov = pack.camera.fov;
    this.camera.updateProjectionMatrix();
  }

  projectEntities(entities: SceneEntity[]): ScreenEntity[] {
    const w = this.renderer.domElement.width;
    const h = this.renderer.domElement.height;
    return entities.map(entity => {
      const feet = new THREE.Vector3(...entity.position).project(this.camera);
      const head = new THREE.Vector3(entity.position[0], entity.position[1] + entity.height, entity.position[2]).project(this.camera);
      const sx = (feet.x * 0.5 + 0.5) * w;
      const sy = (1 - (feet.y * 0.5 + 0.5)) * h;
      const ex = (head.x * 0.5 + 0.5) * w;
      const ey = (1 - (head.y * 0.5 + 0.5)) * h;
      const boxH = Math.abs(ey - sy);
      const boxW = boxH * (entity.width / entity.height);
      return { id: entity.id, type: entity.type, x: sx - boxW / 2, y: ey, width: boxW, height: boxH, metadata: entity.metadata };
    });
  }

  animate(): void {
    this.camera.rotation.y += this.autoRotateSpeed;
    this.renderer.render(this.scene, this.camera);
    const projected = this.projectEntities(this.currentPack?.entities ?? []);
    this.onFrameReady(projected);
    requestAnimationFrame(() => this.animate());
  }
}
```

---

## PART 3 — PREVIEW SDK (headless JVM child process)

Create `preview-sdk/src/PreviewSDK.java` — this is a standalone jar with NO Minecraft dependencies.

Requirements:
- Entry point reads newline-delimited JSON from stdin in a loop
- Supports two message types:
  - `{ "type": "render", "module": "PlayerESP", "config": {...}, "entities": [...screenEntities] }` — render overlay, return pixel bytes
  - `{ "type": "schema", "module": "PlayerESP" }` — return the config schema for that module
- Renders to an offscreen LWJGL framebuffer (headless GLFW context, invisible window)
- Each module implements `ModulePreviewRenderer` interface
- After rendering, writes pixel bytes as base64 JSON to stdout: `{ "type": "frame", "width": 1280, "height": 720, "data": "<base64 RGBA>" }`
- On schema request, writes: `{ "type": "schema", "module": "PlayerESP", "fields": [...] }`

```java
public interface ModulePreviewRenderer {
    String getModuleId();
    void render(HeadlessGLContext ctx, ModuleConfig config, List<ScreenEntity> entities);
    ConfigSchema getSchema();
}

// Example registration
public class PreviewRegistry {
    private static final Map<String, ModulePreviewRenderer> REGISTRY = new HashMap<>();
    static {
        register(new PlayerESPPreview());
        register(new ChestESPPreview());
        register(new ItemESPPreview());
        register(new HUDPreview());
    }
    public static void register(ModulePreviewRenderer r) { REGISTRY.put(r.getModuleId(), r); }
    public static ModulePreviewRenderer get(String id) { return REGISTRY.get(id); }
}
```

Adding a new module preview requires ONLY creating a new class that implements `ModulePreviewRenderer` and calling `register()` in the static block. Zero other changes.

---

## PART 4 — IPC BRIDGE (Electron main process)

Create `src/main/preview/PreviewBridge.ts`

Requirements:
- Spawns `preview-sdk.jar` as a child process on first use: `java -jar assets/preview-sdk.jar`
- Keeps the process alive for the session (do not restart per render)
- Sends JSON messages to the JVM via stdin, reads responses from stdout
- Exposes two async methods to the renderer process via ipcMain:
  - `getSchema(moduleId: string): Promise<ConfigSchema>`
  - `renderOverlay(moduleId: string, config: object, entities: ScreenEntity[]): Promise<ImageData>`
- Converts the returned base64 RGBA buffer into an ImageData object for canvas compositing
- Handle JVM crash/restart gracefully with automatic respawn

```typescript
import { spawn, ChildProcess } from 'child_process';
import { ipcMain } from 'electron';
import * as readline from 'readline';

export class PreviewBridge {
  private jvm: ChildProcess | null = null;
  private pending = new Map<string, (data: any) => void>();

  start(): void {
    this.jvm = spawn('java', ['-jar', 'assets/preview-sdk.jar']);
    const rl = readline.createInterface({ input: this.jvm.stdout! });
    rl.on('line', (line) => {
      const msg = JSON.parse(line);
      const resolve = this.pending.get(msg.requestId);
      if (resolve) { resolve(msg); this.pending.delete(msg.requestId); }
    });
    this.jvm.on('exit', () => { this.jvm = null; setTimeout(() => this.start(), 1000); });
  }

  send(payload: object): Promise<any> {
    const requestId = crypto.randomUUID();
    return new Promise((resolve) => {
      this.pending.set(requestId, resolve);
      this.jvm!.stdin!.write(JSON.stringify({ ...payload, requestId }) + '\n');
    });
  }

  registerIpcHandlers(): void {
    ipcMain.handle('preview:schema', (_, moduleId) => this.send({ type: 'schema', module: moduleId }));
    ipcMain.handle('preview:render', (_, moduleId, config, entities) =>
      this.send({ type: 'render', module: moduleId, config, entities }));
  }
}
```

---

## PART 5 — COMPOSITOR (Electron renderer process)

Create `src/renderer/preview/OverlayCompositor.ts`

Requirements:
- Holds a reference to a second canvas element layered on top of the Three.js canvas (position: absolute, pointer-events: none)
- Listens to PanoramaRenderer.onFrameReady — receives projected ScreenEntity list each frame
- Debounces render calls: if config has not changed and entity positions have not moved more than 1px, skip re-render
- When a render is needed: calls `ipcRenderer.invoke('preview:render', moduleId, config, entities)`
- Receives base64 RGBA back, decodes to ImageData, draws to overlay canvas via `ctx.putImageData()`
- On config change from the config panel: immediately triggers a new render call

---

## PART 6 — CONFIG PANEL (React component)

Create `src/renderer/components/ConfigPanel.tsx`

Requirements:
- On module select, calls `ipcRenderer.invoke('preview:schema', moduleId)` to get the schema
- Dynamically renders controls from the schema — no hardcoded module-specific UI anywhere
- Supported control types: ColorPicker (rgba + hex + alpha slider), Slider (min/max/step), Toggle (boolean), Dropdown (enum), SectionHeader (visual group divider)
- Every onChange immediately updates a config state object and calls `OverlayCompositor.updateConfig(newConfig)`
- Config state is persisted to localStorage keyed by moduleId so settings survive navigation

---

## PART 7 — MODULE SELECTOR

Create `src/renderer/components/ModuleSelector.tsx`

Requirements:
- Reads available modules by scanning `/assets/scenes/` directory at runtime
- Displays as a horizontal tab bar or vertical sidebar (your choice, match existing launcher style)
- On select: loads the ScenePack for that module, calls `PanoramaRenderer.loadScenePack()`, calls `ipcRenderer.invoke('preview:schema', moduleId)` to populate config panel
- Highlights active module tab

---

## FILE STRUCTURE TO CREATE

```
# Java side (add to existing client.jar project)
src/main/java/com/yourclient/preview/
  SceneCaptureManager.java

# Java side (new standalone jar, no MC deps)
preview-sdk/src/
  PreviewSDK.java              ← main entry point, stdin/stdout loop
  PreviewRegistry.java         ← module registration
  HeadlessGLContext.java       ← LWJGL headless framebuffer wrapper
  ModulePreviewRenderer.java   ← interface
  ConfigSchema.java            ← schema data classes
  ScreenEntity.java            ← projected entity data class
  modules/
    PlayerESPPreview.java
    ChestESPPreview.java
    ItemESPPreview.java
    HUDPreview.java

# Electron main process (add to existing launcher)
src/main/preview/
  PreviewBridge.ts

# Electron renderer process (add to existing launcher)
src/renderer/preview/
  PanoramaRenderer.ts
  OverlayCompositor.ts
  types.ts                     ← ScenePack, SceneEntity, ScreenEntity, ConfigSchema types
src/renderer/components/
  ConfigPanel.tsx
  ModuleSelector.tsx
  controls/
    ColorPicker.tsx
    Slider.tsx
    Toggle.tsx
    Dropdown.tsx

# Assets (committed to repo, generated by capture tool)
assets/scenes/
  PlayerESP/forest_pvp/
    cubemap_px.png ... cubemap_nz.png
    scene.json
  ChestESP/dungeon/
    cubemap_*.png
    scene.json
  ItemESP/ground_scatter/
    ...
  HUD/default/
    ...
```

---

## BUILD ORDER

Implement in this exact order to always have something working at each step:

1. PanoramaRenderer.ts — load a single cubemap (use any 6 placeholder PNGs), auto-rotate, confirm it looks correct in Electron
2. scene.json parser + projectEntities() — load scene.json, project positions, draw debug circles on overlay canvas to verify projection accuracy
3. preview-sdk.jar — headless LWJGL context, PlayerESP stub that draws a colored rectangle at the given screen coordinates, confirm IPC round-trip works end to end
4. PreviewBridge.ts + ipcMain handlers — wire Electron main to JVM, confirm latency is under 16ms for a single render call
5. OverlayCompositor.ts — composite the JVM pixel buffer onto the overlay canvas, confirm it aligns with the panorama correctly
6. ConfigSchema + ConfigPanel.tsx — dynamic UI generation from schema, confirm live updates flow through to overlay
7. ModuleSelector.tsx — multi-module switching, confirm each module loads its own ScenePack and schema
8. Capture tool in client.jar — run against a live game, generate real ScenePacks, replace placeholder assets
9. Remaining module previews — ChestESPPreview, ItemESPPreview, HUDPreview

---

## KEY CONSTRAINTS

- Adding a new module preview requires ONLY creating a new class implementing ModulePreviewRenderer and registering it. No changes to any other file.
- The background (cubemap) never re-renders on config change — only the overlay does.
- The JVM process stays alive for the entire launcher session. Never restart it per render.
- All module-specific UI is generated from the schema. No hardcoded config panels.
- The capture tool must be completely inert unless the system property flag is set. It must not affect normal gameplay in any way.
```
