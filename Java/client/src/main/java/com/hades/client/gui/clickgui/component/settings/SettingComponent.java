package com.hades.client.gui.clickgui.component.settings;

import com.hades.client.gui.clickgui.component.Component;
import com.hades.client.module.setting.Setting;

public abstract class SettingComponent<T extends Setting<?>> extends Component {
    protected final T setting;

    public SettingComponent(T setting, float height) {
        this.setting = setting;
        this.height = height;
    }

    public float getClipTop() { return clipTop; }
    public float getClipBottom() { return clipBottom; }
    
    @Override
    public void keyTyped(char typedChar, int keyCode) {}

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {}

    public T getSetting() { return setting; }
}