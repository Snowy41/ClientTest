package com.hades.client.platform.adapters;

import net.labymod.api.client.entity.player.tag.renderer.TagRenderer;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.client.render.state.entity.EntitySnapshot;
import net.labymod.api.laby3d.render.queue.SubmissionCollector;
import com.hades.client.api.HadesAPI;
import com.hades.client.module.impl.render.HadesRoleTags;
import java.util.UUID;

public class LabyRoleTagRenderer implements TagRenderer {

    @Override
    public void begin(EntitySnapshot snapshot) {
    }

    @Override
    public float getHeight() {
        return 0.3f; // Same native 3D margin height as Spotify widgets
    }

    @Override
    public float getWidth() {
        return 0f; // Returning 0 bypasses LabyMod's left-translation logic allowing our Matrix centered 3D math
    }

    @Override
    public void render(Stack stack, SubmissionCollector collector, EntitySnapshot snapshot) {
        HadesRoleTags module = HadesRoleTags.getInstance();
        if (module == null || HadesAPI.Player.isNull()) {
            return;
        }

        // Try mapping the snapshot to an actual Entity without raw reflection
        com.hades.client.api.interfaces.IEntity entity = getSnapshotEntity(snapshot);
        if (entity == null) {
            return;
        }

        UUID uuid = entity.getUUID();
        if (uuid == null) {
            return;
        }

        String role = com.hades.client.module.impl.render.RoleManager.getRole(uuid);
        if (role == null) {
            return;
        }

        boolean isSelf = HadesAPI.player.getUUID() != null && HadesAPI.player.getUUID().equals(uuid);
        if (isSelf && HadesAPI.mc.getThirdPersonView() == 0) return;

        // We no longer need to bypass LabyMod's matrix stack!
        // Construct a native Component matching the LabyMod 4 Text formatting structure
        net.labymod.api.client.component.Component textComponent = net.labymod.api.client.component.Component.text("HADES ", net.labymod.api.client.component.format.TextColor.color(0xFFFFFFFF))
                .append(net.labymod.api.client.component.Component.text(role, net.labymod.api.client.component.format.TextColor.color(HadesRoleTags.getRoleColor(role))));

        int backgroundArgb = net.labymod.api.util.color.format.ColorFormat.ARGB32.pack(0.0f, 0.0f, 0.0f, 0.40f);
        int seeThroughColor = net.labymod.api.util.color.format.ColorFormat.ARGB32.withAlpha(0xFFFFFFFF, 128);

        float xOffset = 0.0f;
        float yOffset = 0.0f;

        if (collector != null) {
            boolean discrete = this.isDiscrete(snapshot);
            if (!discrete) {
                // Background & through-wall text
                collector.order(2).submitComponent(stack, textComponent, xOffset, yOffset, seeThroughColor, snapshot.lightCoords(), backgroundArgb, 8);
                // Foreground vivid text
                collector.order(3).submitComponent(stack, textComponent, xOffset, yOffset, 0xFFFFFFFF, snapshot.lightCoords(), 0, 4);
            } else {
                // Sneaking / Discrete
                collector.order(3).submitComponent(stack, textComponent, xOffset, yOffset, seeThroughColor, snapshot.lightCoords(), backgroundArgb, 4);
            }
        }
    }

    private final java.util.Map<EntitySnapshot, com.hades.client.api.interfaces.IEntity> snapshotCache = new java.util.WeakHashMap<>();

    private com.hades.client.api.interfaces.IEntity getSnapshotEntity(EntitySnapshot snapshot) {
        if (snapshot == null || HadesAPI.world == null || HadesAPI.world.isNull()) return null;

        com.hades.client.api.interfaces.IEntity cached = snapshotCache.get(snapshot);
        if (cached != null) return cached;

        com.hades.client.api.interfaces.IEntity closest = null;
        double minDstSq = 9.0; // Max search radius of 3 blocks to accommodate interpolation desyncs

        // LabyMod's snapshot coordinates are absolute lerped tick positions natively!
        double absX = snapshot.x();
        double absY = snapshot.y();
        double absZ = snapshot.z();

        for (com.hades.client.api.interfaces.IEntity entity : HadesAPI.world.getLoadedEntities()) {
            if (entity.isPlayer()) {
                double eX = entity.getX();
                double eY = entity.getY();
                double eZ = entity.getZ();

                double dx = eX - absX;
                double dy = eY - absY;
                double dz = eZ - absZ;

                // Quick AABB rejection
                if (dx * dx < minDstSq && dy * dy < minDstSq && dz * dz < minDstSq) {
                    double dstSq = dx * dx + dy * dy + dz * dz;
                    if (dstSq < minDstSq) {
                        minDstSq = dstSq;
                        closest = entity;
                    }
                }
            }
        }
        
        if (closest != null) {
            snapshotCache.put(snapshot, closest);
        }
        
        return closest;
    }

    @Override
    public boolean isDiscrete(EntitySnapshot snapshot) {
        return false;
    }

    @Override
    public boolean shouldCenterName() {
        return false;
    }

    @Override
    public boolean isOnlyVisibleOnMainTag() {
        // COUNTER-INTUITIVE: LabyMod's AbstractPositionRenderer explicitly HIDES Custom passes 
        // IF this returns false! We MUST return TRUE so `isTagHidden()` safely falls through.
        return true;
    }

    @Override
    public float getScale() {
        return 1.0f; // Pass native scale straight through
    }

    @Override
    public boolean isVisible() {
        return HadesRoleTags.getInstance() != null;
    }
}
