package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.HadesClient;
import org.lwjgl.opengl.GL11;

public class ScoreboardModule extends Module {

    private static ScoreboardModule instance;
    public static boolean isHadesFetching = false;

    public final BooleanSetting blur = new BooleanSetting("Blur Background", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", true);
    public final BooleanSetting border = new BooleanSetting("Border", true);
    public final BooleanSetting roundedCorners = new BooleanSetting("Rounded Corners", true);
    public final BooleanSetting hideRedNumbers = new BooleanSetting("Hide Red Numbers", false);
    public final BooleanSetting dropShadow = new BooleanSetting("Drop Shadow", true);

    public final NumberSetting scoreX = new NumberSetting("Score X", -1, -1, 4000, 1); // -1 = Right align
    public final NumberSetting scoreY = new NumberSetting("Score Y", -1, -1, 4000, 1); // -1 = Center vertical align


    public ScoreboardModule() {
        super("Scoreboard", "Redesigns the scoreboard with client themes", Module.Category.HUD, 0);
        instance = this;
        this.register(blur);
        this.register(outline);
        this.register(border);
        this.register(hideRedNumbers);
        this.register(roundedCorners);
        this.register(dropShadow);

        scoreX.setHidden(true);
        scoreY.setHidden(true);
        this.register(scoreX);
        this.register(scoreY);

        // Force enable on default without triggering a notification spam
        try {
            java.lang.reflect.Field enabledField = Module.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(this, true);
        } catch (Exception e) {
            this.setEnabled(true);
        }

        // Ensure it's registered
        try {
            HadesClient.getInstance().getEventBus().register(this);
        } catch (Exception e) {}
    }

    public static ScoreboardModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        com.hades.client.platform.PlatformAdapter adapter = com.hades.client.platform.PlatformManager.getActiveAdapter();
        if (adapter != null) {
            adapter.forceVanillaScoreboard(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        com.hades.client.platform.PlatformAdapter adapter = com.hades.client.platform.PlatformManager.getActiveAdapter();
        if (adapter != null) {
            adapter.forceVanillaScoreboard(false);
        }
    }

    private boolean hasLoggedInit = false;

    @EventHandler(priority = 0)
    public void onRender2D(Render2DEvent event) {
        try {
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (!hasLoggedInit) {
            com.hades.client.util.HadesLogger.get().info("[ScoreboardModule] onRender2D invoked natively! Player isNull: " + HadesAPI.Player.isNull());
            hasLoggedInit = true;
        }

        if (HadesAPI.Player.isNull()) {
            return;
        }

        com.hades.client.api.interfaces.scoreboard.IScoreboard scoreboard = HadesAPI.world.getScoreboard();
        if (scoreboard == null) {
            com.hades.client.util.HadesLogger.get().info("[ScoreboardModule] Aborting: scoreboard is null");
            return;
        }

        com.hades.client.api.interfaces.scoreboard.IScoreObjective objective;
        try {
            isHadesFetching = true;
            objective = scoreboard.getObjectiveInDisplaySlot(1);
        } finally {
            isHadesFetching = false;
        }
        if (objective == null) {
            com.hades.client.util.HadesLogger.get().info("[ScoreboardModule] Aborting: objective is null");
            return;
        }

        java.util.List<com.hades.client.api.interfaces.scoreboard.IScore> rawScores = scoreboard.getScores(objective);
        if (rawScores == null || rawScores.isEmpty()) {
            com.hades.client.util.HadesLogger.get().info("[ScoreboardModule] Aborting: scores list is empty or null");
            return;
        }

        // Clone and limit to 15 entries (Minecraft default limit)
        java.util.List<com.hades.client.api.interfaces.scoreboard.IScore> scores = new java.util.ArrayList<>(rawScores);
        if (scores.size() > 15) {
            scores = scores.subList(Math.max(scores.size() - 15, 0), scores.size());
        }
        // Vanilla renders bottom to top index, meaning highest score is on top. Since we draw sequentially downward:
        java.util.Collections.reverse(scores);

        float fontH = getFontH();
        float rowHeight = fontH + 2f; 
        
        String footerText = "Using Hades.tf";
        float footerWidth = getStrWidth(footerText);

        float maxNameWidth = getStrWidth(objective.getDisplayName());
        float maxScoreWidth = 0;

        java.util.List<FormattedScore> formattedScores = new java.util.ArrayList<>();
        for (com.hades.client.api.interfaces.scoreboard.IScore score : scores) {
            String name = scoreboard.formatPlayerName(score);
            String scoreText = "§c" + score.getScorePoints();

            float nWidth = getStrWidth(name);
            float sWidth = getStrWidth(scoreText);

            if (nWidth > maxNameWidth) maxNameWidth = nWidth;
            if (sWidth > maxScoreWidth) maxScoreWidth = sWidth;

            formattedScores.add(new FormattedScore(name, scoreText));
        }

        if (footerWidth > maxNameWidth) maxNameWidth = footerWidth;

        boolean hideRed = hideRedNumbers.getValue();
        float gap = hideRed ? 0 : 15f;
        float totalWidth = maxNameWidth + gap + (hideRed ? 0 : maxScoreWidth);
        float totalHeight = (formattedScores.size() + 2) * rowHeight; // +1 for the title, +1 for footer

        int scaledWidth = event.getScaledWidth();
        int scaledHeight = event.getScaledHeight();

        float yOffset = scoreY.getValue().floatValue() == -1f ? (scaledHeight / 2f - totalHeight / 2f) : scoreY.getValue().floatValue();
        float xOffset = scoreX.getValue().floatValue() == -1f ? (scaledWidth - totalWidth - 4f) : scoreX.getValue().floatValue();

        float padding = 3f;
        float finalX = xOffset - padding;
        float finalY = yOffset - padding;
        float finalW = totalWidth + padding * 2;
        float finalH = totalHeight + padding * 2;
        float radius = roundedCorners.getValue() ? 4f : 0f;

        // Draw shadow layer first
        if (dropShadow.getValue()) {
            HadesAPI.Render.drawRoundedShadow(finalX, finalY, finalW, finalH, radius, 8f);
        }

        // Draw border underlying rect if engaged
        boolean drawBorder = border.getValue() || outline.getValue();
        if (drawBorder) {
            if (radius > 0) {
                HadesAPI.Render.drawRoundedRect(finalX - 1, finalY - 1, finalW + 2, finalH + 2, radius + 1, HadesAPI.Render.color(255, 40, 40, 200));
            } else {
                HadesAPI.Render.drawRect(finalX - 1, finalY - 1, finalW + 2, finalH + 2, HadesAPI.Render.color(255, 40, 40, 200));
            }
        }

        // Draw Background
        int bgColor = HadesAPI.Render.color(0, 0, 0, 110);
        if (blur.getValue()) {
            int[] sr = HadesAPI.Game.getScaledResolution();
            float scaleFactor = sr != null && sr.length > 2 ? sr[2] : 2f;
            com.hades.client.util.BlurUtil.drawBlurredRect(finalX, finalY, finalW, finalH, radius, bgColor, 2, scaleFactor);
        } else {
            if (radius > 0) {
                HadesAPI.Render.drawRoundedRect(finalX, finalY, finalW, finalH, radius, bgColor);
            } else {
                HadesAPI.Render.drawRect(finalX, finalY, finalW, finalH, bgColor);
            }
        }

        // Draw Title
        String title = objective.getDisplayName();
        float titleX = finalX + (finalW / 2f) - (getStrWidth(title) / 2f);
        drawStr(title, titleX, yOffset, HadesAPI.Render.color(255, 255, 255));

        // Draw Scores
        float currentY = yOffset + rowHeight;
        for (FormattedScore fs : formattedScores) {
            // Draw Player Name
            drawStr(fs.name, xOffset, currentY, HadesAPI.Render.color(255, 255, 255));

            // Draw Red Score Value natively bridging the Gap
            if (!hideRed) {
                float scoreXPos = xOffset + totalWidth - getStrWidth(fs.scoreText);
                drawStr(fs.scoreText, scoreXPos, currentY, HadesAPI.Render.color(255, 85, 85)); // 0xFF5555
            }
            currentY += rowHeight;
        }

        // Draw Footer
        float footerX = finalX + (finalW / 2f) - (footerWidth / 2f);
        drawStr(footerText, footerX, currentY, Theme.ACCENT_PRIMARY);

        } catch (Throwable t) {
            com.hades.client.util.HadesLogger.get().error("[ScoreboardModule] Exception during onRender2D: ", t);
        }
    }

    private float fontSize = 14f;

    private float getStrWidth(String text) {
        if (text == null) return 0;
        return HadesAPI.Render.getStringWidth(text, fontSize, false, false);
    }
    
    private void drawStr(String text, float x, float y, int color) {
        if (text == null) return;
        HadesAPI.Render.drawString(text, x, y, color, fontSize, false, false, dropShadow.getValue());
    }
    
    private float getFontH() {
        return HadesAPI.Render.getFontHeight(fontSize, false, false);
    }

    private static class FormattedScore {
        String name;
        String scoreText;
        FormattedScore(String name, String scoreText) {
            this.name = name;
            this.scoreText = scoreText;
        }
    }
}
