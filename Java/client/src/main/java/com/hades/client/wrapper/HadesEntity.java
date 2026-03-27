package com.hades.client.wrapper;

import java.util.Objects;

/**
 * An object-oriented wrapper over the raw Minecraft 1.8.9 Entity object.
 * Greatly simplifies module development by abstracting away the underlying
 * reflection/MC calls.
 */
public class HadesEntity {
    protected final Object entityObject;
    protected final com.hades.client.api.interfaces.IEntity iEntity;

    public HadesEntity(Object entityObject) {
        this.entityObject = Objects.requireNonNull(entityObject, "Entity object cannot be null");
        this.iEntity = new com.hades.client.api.provider.Vanilla189Entity(entityObject);
    }

    public Object getRaw() {
        return entityObject;
    }

    public double getX() {
        return iEntity.getX();
    }

    public double getY() {
        return iEntity.getY();
    }

    public double getZ() {
        return iEntity.getZ();
    }

    public float getYaw() {
        return iEntity.getYaw();
    }

    public void setYaw(float yaw) {
        iEntity.setYaw(yaw);
    }

    public float getPitch() {
        return entityObject instanceof com.hades.client.api.interfaces.IEntity ? ((com.hades.client.api.interfaces.IEntity)entityObject).getPitch() : 0;
    }

    public void setPitch(float pitch) {
        iEntity.setPitch(pitch);
    }

    public boolean isOnGround() {
        return iEntity.isOnGround();
    }

    public double getDistanceTo(HadesEntity other) {
        double dx = getX() - other.getX();
        double dy = getY() - other.getY();
        double dz = getZ() - other.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double getDistanceTo(double x, double y, double z) {
        double dx = getX() - x;
        double dy = getY() - y;
        double dz = getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean isPlayer() {
        return iEntity.isPlayer();
    }

    public boolean isLiving() {
        return iEntity.isLiving();
    }

    public float getHealth() {
        return iEntity.getHealth();
    }

    public int getHurtTime() {
        return iEntity.getHurtTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HadesEntity that = (HadesEntity) o;
        return entityObject.equals(that.entityObject);
    }

    @Override
    public int hashCode() {
        return entityObject.hashCode();
    }
}
