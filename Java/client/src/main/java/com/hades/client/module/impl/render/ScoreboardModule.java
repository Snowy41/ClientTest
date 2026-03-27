package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;

public class ScoreboardModule extends Module {

    private static ScoreboardModule instance;

    public final BooleanSetting blur = new BooleanSetting("Blur Background", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", true);
    public final BooleanSetting border = new BooleanSetting("Border", true);
    public final BooleanSetting roundedCorners = new BooleanSetting("Rounded Corners", true);
    public final BooleanSetting customFont = new BooleanSetting("Custom Font", true);
    public final BooleanSetting hideRedNumbers = new BooleanSetting("Hide Red Numbers", false);
    public final BooleanSetting dropShadow = new BooleanSetting("Drop Shadow", true);

    public ScoreboardModule() {
        super("Scoreboard", "Redesigns the scoreboard with client themes", Module.Category.HUD, 0);
        instance = this;
        this.register(blur);
        this.register(outline);
        this.register(border);
        this.register(hideRedNumbers);
        this.register(roundedCorners);
        this.register(customFont);
        this.register(dropShadow);
    }

    public static ScoreboardModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // com.hades.client.platform.PlatformAdapter adapter = com.hades.client.platform.PlatformManager.getActiveAdapter();
        // if (adapter != null) {
        //     adapter.forceVanillaScoreboard(true);
        // }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        // com.hades.client.platform.PlatformAdapter adapter = com.hades.client.platform.PlatformManager.getActiveAdapter();
        // if (adapter != null) {
        //     adapter.forceVanillaScoreboard(false);
        // }
    }

    @EventHandler(priority = 0)
    public void onRender2D(Render2DEvent event) {
        if (HadesAPI.Player.isNull()) return;

        // BINARY SEARCH: Only draw a simple red rectangle to see if the Hotbar is still broken.
        // We have completely removed ALL scoreboard data lookups and ALL custom font rendering.
        HadesAPI.Render.drawRect(50, 50, 100, 100, 0xFFFF0000); // Solid Red Box
    }
}
