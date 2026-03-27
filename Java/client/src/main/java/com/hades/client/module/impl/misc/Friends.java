package com.hades.client.module.impl.misc;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.notification.Notification;
import com.hades.client.notification.NotificationManager;

import java.util.HashSet;
import java.util.Set;

public class Friends extends Module {
    private static final Set<java.util.UUID> friends = new HashSet<>();
    private boolean wasMiddleMouseDown = false;

    public Friends() {
        super("Friends", "Middle click players to friend them. Friends are ignored by combat modules.", Category.MISC, 0);
        this.setEnabled(true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        // Enforce always enabled if possible or let them disable it
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (HadesAPI.mc == null || HadesAPI.world == null || HadesAPI.mc.isInGui()) return;

        boolean isMiddleMouseDown = HadesAPI.mc.isButtonDown(2);

        if (isMiddleMouseDown && !wasMiddleMouseDown) {
            if (HadesAPI.mc.getMouseOverType() == 2) {
                Object rawEntity = HadesAPI.mc.getMouseOverEntity();
                if (rawEntity != null) {
                    for (com.hades.client.api.interfaces.IEntity e : HadesAPI.world.getLoadedEntities()) {
                        if (e.getRaw() == rawEntity && e.isPlayer()) {
                            java.util.UUID uuid = e.getUUID();
                            if (uuid != null) {
                                String name = e.getName();
                                if (friends.contains(uuid)) {
                                    friends.remove(uuid);
                                    NotificationManager.getInstance().show("Friends", "Removed " + name + " from friends.", Notification.Type.DISABLED, 2000);
                                } else {
                                    friends.add(uuid);
                                    NotificationManager.getInstance().show("Friends", "Added " + name + " to friends.", Notification.Type.ENABLED, 2000);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        wasMiddleMouseDown = isMiddleMouseDown;
    }

    public static boolean isFriend(com.hades.client.api.interfaces.IEntity entity) {
        if (entity == null || !entity.isPlayer()) return false;
        java.util.UUID uuid = entity.getUUID();
        return uuid != null && friends.contains(uuid);
    }
}
