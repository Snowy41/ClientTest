package com.hades.client.module.impl.misc;

import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public class ExampleModule extends Module {
    private final BooleanSetting toggleSetting = register(new BooleanSetting("Example Toggle", true));
    private final NumberSetting sliderSetting = register(new NumberSetting("Example Slider", 50, 0, 100, 1));
    private final ModeSetting modeSetting = register(
            new ModeSetting("Example Mode", "Mode 1", "Mode 1", "Mode 2", "Mode 3"));

    public ExampleModule() {
        super("Example", "A test module to demonstrate UI settings.", Category.MISC, Keyboard.KEY_NONE);
    }
}
