package com.hades.client.module.impl.render;

import com.hades.client.HadesClient;
import com.hades.client.module.Module;

public class HUDEditorModule extends Module {

    public HUDEditorModule() {
        super("HUDEditor", "Opens the HUD Editor screen", Category.RENDER, 0); // No default keybind, user can set it
    }

    @Override
    protected void onEnable() {
        // Toggle the internal state back off immediately so it acts as a trigger button
        this.setEnabled(false);

        // Trigger the overlay proxy to open, but hide the ClickGUI panels
        HadesClient.getInstance().getClickGUI().setVisible(true);
        HadesClient.getInstance().getClickGUI().setVisible(false);
        
        // Open HUD editor state
        HadesClient.getInstance().getHudEditorScreen().open();
    }
}
