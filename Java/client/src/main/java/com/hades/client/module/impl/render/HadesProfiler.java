package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventBus;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.util.HadesLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HadesProfiler extends Module {

    private final BooleanSetting sortByMemory = new BooleanSetting("Sort by Memory", "Prioritize memory leak detection over CPU cost", false);
    private final BooleanSetting logToConsole = new BooleanSetting("Log to Console", "Spam to log file for analysis", true);

    private long lastLog = 0;

    public HadesProfiler() {
        super("HadesProfiler", "Displays Top CPU/Memory heavy modules", Category.RENDER, 0); // 0 = Unbound Key
        register(sortByMemory);
        register(logToConsole);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        EventBus.isProfilerEnabled = true;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        EventBus.isProfilerEnabled = false;
        EventBus.PROFILER_METRICS.clear();
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;

        List<Map.Entry<String, EventBus.ProfilerEntry>> entries = new ArrayList<>(EventBus.PROFILER_METRICS.entrySet());

        entries.sort((a, b) -> {
            if (sortByMemory.getValue()) {
                return Long.compare(b.getValue().lastSecAllocatedBytes, a.getValue().lastSecAllocatedBytes);
            } else {
                return Long.compare(b.getValue().lastSecNanos, a.getValue().lastSecNanos);
            }
        });

        int y = 50;
        int x = 10;
        
        int boxHeight = 15 + (Math.min(entries.size(), 12) * 12);
        HadesAPI.Render.drawRoundedRect(x - 5, y - 5, 260, boxHeight, 4, 0x90000000);
        HadesAPI.Render.drawString("Hades Profiler (per second)", x, y, 0xFF55FF, 1.0f);
        y += 12;

        boolean shouldLog = logToConsole.getValue() && System.currentTimeMillis() - lastLog > 1000;
        if (shouldLog) {
            HadesLogger.get().info("=== HADES PROFILER REPORT ===");
            lastLog = System.currentTimeMillis();
        }

        for (int i = 0; i < Math.min(entries.size(), 12); i++) {
            Map.Entry<String, EventBus.ProfilerEntry> e = entries.get(i);
            String name = e.getKey();
            EventBus.ProfilerEntry data = e.getValue();
            
            float ms = data.lastSecNanos / 1_000_000f;
            long kb = data.lastSecAllocatedBytes / 1024;
            long mb = kb / 1024;
            
            String memText = mb > 0 ? (mb + " MB/s") : (kb + " KB/s");
            String text = String.format("%d. %s: %.2f ms | %s", (i + 1), name, ms, memText);
            
            int color = 0xAAAAAA;
            if (ms > 5.0f || kb > 500) color = 0xFFAAAA;     // Red if slightly expensive
            if (ms > 20.0f || kb > 2000) color = 0xFF5555;   // Bright Red if heavily expensive

            HadesAPI.Render.drawString(text, x, y, color, 0.9f);
            y += 12;

            if (shouldLog) {
                HadesLogger.get().info(text);
            }
        }
        
        if (shouldLog) {
            HadesLogger.get().info("=============================");
        }
    }
}
