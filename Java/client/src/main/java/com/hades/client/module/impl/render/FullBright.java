package com.hades.client.module.impl.render;

import com.hades.client.module.Module;
import com.hades.client.api.HadesAPI;

/**
 * FullBright module. Sets gamma to maximum so you can see in the dark.
 */
public class FullBright extends Module {

    private float previousGamma = 0f;

    public FullBright() {
        super("FullBright", "Maximum brightness - see in the dark", Category.RENDER, 0);
    }

    @Override
    protected void onEnable() {
        previousGamma = HadesAPI.mc.getGamma();
        HadesAPI.mc.setGamma(100f);
    }

    @Override
    protected void onDisable() {
        HadesAPI.mc.setGamma(previousGamma);
    }
}
