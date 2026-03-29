package com.hades.client.module.impl.movement;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public class TargetStrafe extends Module {

    private static TargetStrafe instance;

    public final NumberSetting radius = new NumberSetting("Radius", 2.0, 0.5, 4.5, 0.1);
    public final BooleanSetting autoJump = new BooleanSetting("Auto Jump", true);
    public final BooleanSetting switchOnCollide = new BooleanSetting("Switch On Collide", true);
    public final BooleanSetting keepDistance = new BooleanSetting("Keep Distance", true);

    public int direction = 1;

    public TargetStrafe() {
        super("TargetStrafe", "Automatically circles the target.", Category.MOVEMENT, 0);
        instance = this;
        register(radius);
        register(autoJump);
        register(switchOnCollide);
        register(keepDistance);
    }

    public static TargetStrafe getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        direction = 1;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.player == null) return;

        IEntity target = TargetManager.getInstance().getTarget();
        if (target == null) return;
        
        boolean holdingW = Keyboard.isKeyDown(Keyboard.KEY_W) || HadesAPI.Game.isKeyForwardDown();

        // Switch direction if colliding with walls
        if (switchOnCollide.getValue() && HadesAPI.Player.isCollidedHorizontally()) {
            direction = -direction;
        }

        // Auto Jump logic
        if (autoJump.getValue() && holdingW && HadesAPI.Player.isOnGround()) {
             HadesAPI.Player.setMotionY(0.42);
        }
    }
}
