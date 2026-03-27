package com.hades.client.wrapper;

import com.hades.client.api.HadesAPI;

/**
 * An object-oriented wrapper over the raw Minecraft 1.8.9 EntityPlayer object.
 */
public class HadesPlayer extends HadesEntity {

    public HadesPlayer(Object playerObject) {
        super(playerObject);
        if (!isPlayer()) {
            throw new IllegalArgumentException("Object is not an EntityPlayer: " + playerObject.getClass().getName());
        }
    }

    public boolean isSprinting() {
        if (HadesAPI.player != null && entityObject == HadesAPI.player.getRaw()) {
            return HadesAPI.player.isSprinting();
        }
        return false;
    }

    public void setSprinting(boolean sprinting) {
        if (HadesAPI.player != null && entityObject == HadesAPI.player.getRaw()) {
            HadesAPI.player.setSprinting(sprinting);
        }
    }

    // TODO: Add getArmorValue, getHeldItem, getName, etc. when needed.
}
