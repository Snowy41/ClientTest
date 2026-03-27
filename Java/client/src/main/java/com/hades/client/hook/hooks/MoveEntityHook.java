package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.event.events.MoveEvent;
import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;

public class MoveEntityHook {
    
    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.This Object entity,
            @Advice.Argument(value = 0, readOnly = false) double x,
            @Advice.Argument(value = 1, readOnly = false) double y,
            @Advice.Argument(value = 2, readOnly = false) double z) {
        
        if (HadesAPI.player != null && !HadesAPI.player.isNull() && entity == HadesAPI.player.getRaw()) {
            com.hades.client.event.EventBus bus = HadesClient.getInstance().getEventBus();
            if (bus != null) {
                MoveEvent event = new MoveEvent(x, y, z);
                bus.post(event);
                x = event.getX();
                y = event.getY();
                z = event.getZ();
            }
        }
    }
}
