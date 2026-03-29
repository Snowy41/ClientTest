# Universal Hades VFS IDE - Documentation

The **Universal Hades VFS IDE** is an offline, zero-dependency browser-based development environment explicitly designed to allow you to prototype, test, and render visuals for the Hades Minecraft Client *without* booting up the game.

By leveraging advanced HTML5 directory reading, a custom `.java` regex transpiler, and a lightweight `GL11` WebGL wrapper, this single `.html` file allows you to write actual Java code in the browser and watch it render seamlessly in real-time.

---

## 🚀 Core Features

### 1. The Virtual File System (VFS)
Rather than forcing all development into a single text-box "Scratchpad", the IDE uses a multi-file architecture:
* Utilizing the `<input type="file" webkitdirectory>` standard, the IDE can locally scan an entire directory (e.g., your `Java/client/src/main/java` folder).
* The engine natively detects package headers (`package com.hades.client.util;`) within those files and automatically mounts them onto a virtual file tree mirroring your codebase structure.

### 2. Live Java-to-JavaScript Transpilation
Browsers cannot natively execute `.java` code. When you edit code in the IDE, a bespoke transpilation engine converts your Java rendering classes into runnable JavaScript on the fly:
* **Syntax Translation**: Strips access modifiers (`public`, `protected`), type casts (`(int)`, `(float)`), and variable generic type declarations (`List<String> list` -> `let list`).
* **Namespace Generation**: Reconstructs your Java packages as JavaScript singletons (`window.com.hades.client.util.RenderUtil = {}`). This allows cross-file functionality natively, meaning `ESP.java` can call `RenderUtil.drawBatchedESP(...)` flawlessly inside the browser.

### 3. HadesAPI & GL11 Mocks
To facilitate 1:1 code compatibility with your Java Client:
* **`HadesGL_Core`**: The IDE injects a highly optimized fixed-pipeline WebGL state machine mimicking `org.lwjgl.opengl.GL11`. Matrix stacks (`glPushMatrix()`), translates (`glTranslatef`), Rotations, Ortho, and Perspective cameras are natively supported on `<canvas>`.
* **`HadesAPI` Dummy Objects**: The environment houses a globally accessible `HadesAPI` object that proxies methods like `HadesAPI.renderer.getRenderPosX()`, `HadesAPI.world.getLoadedEntities()`, and `HadesAPI.player`.

### 4. Smart Auto-Discovery Execution Hook
There is no "Run Code" button for specific modules—the IDE intuitively determines what context you're operating under:
* **The Module Hook**: Whenever you click on a `.java` file in the sidebar, the IDE scans it for methods like `onRender3D(Event event)`. If it finds one, it immediately targets that file's logic as the Active Hook, drawing it to the active canvas at 60 FPS.
* **The Utility Rule**: If you click on a utility file (like `RenderUtil.java`) that does *not* possess an `onRender3D` loop, the IDE seamlessly preserves the previous Module Hook. This acts beautifully as a hot-swapping workflow—allowing you to actively type new values into helper methods inside `RenderUtil` while continually watching the active Hook (like `ESP.java`) call those methods and update its visuals dynamically.

---

## 🛠️ Usage Instructions

### Starting the IDE
1. Open `HadesIDE.html` in a Chromium-based browser (Chrome, Edge, Brave).
2. Click **Load 'java' Folder...** on the left sidebar.
3. Select the root folder containing your `com/hades/...` packages (e.g. `client/src/main/java`).
4. Wait a few moments as the File Explorer populates your Virtual File System Tree. *You must select a folder for the directory reader to kick in.*

### Editing & Previewing
* **Syntax Editor**: The middle pane is a custom line-highlighted text editor. Click any `.java` file from the sidebar to load it in. 
* **Auto-Compile**: The code you type is automatically checked, debounced, and re-compiled every ~600ms, rendering near-instant visual feedback. Press `Tab` to indent.
* **Canvas Views**: Use the dropdown in the preview menu to swap your view matrix between **3D Freecam** (Drag mouse to rotate, Scroll to zoom) and **2D Screen** mode (Orthographic flat projection like the game HUD).

---

## ⚠️ Architecture Limitations

> [!WARNING]
> The Java-to-JavaScript Transpilation engine is intentionally naive to prioritize hyper-speed conversion of Visual/GL related logic. 
> 
> The regex tokenization logic natively handles variables, method bindings, single loops, and generic primitives (e.g. `double`, `float`, `boolean`). 
> **It is NOT designed to transpile intricate Java structures.** Pasting complex Networking code, multi-layered interfaces, reflection systems, abstract factory lambdas, or heavy generic subclass-bounds will likely result in the browser throwing a `Transpile Err in file.java` warning inside the console, and it will drop the class from the Visual Workspace. 
> 
> *Target Usage*: Keep the VFS workspace populated tightly with your `render`, `gui`, and `util` packages.
