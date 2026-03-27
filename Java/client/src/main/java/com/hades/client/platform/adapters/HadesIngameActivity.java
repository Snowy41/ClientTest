package com.hades.client.platform.adapters;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.util.LabyRenderer;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.types.IngameOverlayActivity;
import net.labymod.api.client.gui.screen.widget.SimpleWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.render.matrix.Stack;

public class HadesIngameActivity extends IngameOverlayActivity {

    @Override
    public void initialize(Parent parent) {
        super.initialize(parent);

        SimpleWidget hadesWidget = new SimpleWidget() {
            @Override
            public void render(Stack stack, MutableMouse mouse, float tickDelta) {
                // Here, LabyMod has natively prepared the Stack and ScreenContext
                int scaledW = Laby.labyAPI().minecraft().minecraftWindow().getScaledWidth();
                int scaledH = Laby.labyAPI().minecraft().minecraftWindow().getScaledHeight();

                // Setup Context so out FontRenderer uses LabyMod Native Texts and GL states
                LabyRenderer.setCurrentStack(stack);
                HadesAPI.Render.setLabyRenderContext(true);

                // Fire the Hades native Render2D event natively inside LabyMod's engine
                HadesClient.getInstance().getEventBus().post(new Render2DEvent(tickDelta, scaledW, scaledH));

                // Cleanup
                HadesAPI.Render.setLabyRenderContext(false);
                LabyRenderer.setCurrentStack(null);
            }
        };

        hadesWidget.addId("hades-hud");
        ((Document) this.document).addChild(hadesWidget);
    }

    @Override
    public boolean isVisible() {
        return true; // Always visible while in-game, preventing the "hidden when Gui Screen is closed" bug
    }
}
