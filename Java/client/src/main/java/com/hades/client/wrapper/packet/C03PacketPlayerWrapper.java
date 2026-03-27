package com.hades.client.wrapper.packet;

import com.hades.client.api.HadesAPI;
import java.lang.reflect.Field;

/**
 * Wrapper for the C03PacketPlayer and its child classes (C04, C05, C06).
 * Underlying Vanilla class: ip (C03PacketPlayer)
 */
public class C03PacketPlayerWrapper {
    private final Object packet;

    private static Field xField, yField, zField;
    private static Field yawField, pitchField;
    private static Field onGroundField, movingField, rotatingField;
    private static boolean cached = false;

    public C03PacketPlayerWrapper(Object packet) {
        this.packet = packet;
    }

    private static void cache() {
        if (cached)
            return;
        Class<?> clazz = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.network.play.client.C03PacketPlayer", "ip");
        if (clazz != null) {
            xField = com.hades.client.util.ReflectionUtil.findField(clazz, "a", "x", "field_149479_a");
            yField = com.hades.client.util.ReflectionUtil.findField(clazz, "b", "y", "field_149477_b");
            zField = com.hades.client.util.ReflectionUtil.findField(clazz, "c", "z", "field_149478_c");
            yawField = com.hades.client.util.ReflectionUtil.findField(clazz, "d", "yaw", "field_149476_e");
            pitchField = com.hades.client.util.ReflectionUtil.findField(clazz, "e", "pitch", "field_149473_f");
            onGroundField = com.hades.client.util.ReflectionUtil.findField(clazz, "f", "onGround", "field_149474_g");
            movingField = com.hades.client.util.ReflectionUtil.findField(clazz, "g", "moving", "field_149480_h");
            rotatingField = com.hades.client.util.ReflectionUtil.findField(clazz, "h", "rotating", "field_149481_i");
        }
        cached = true;
    }

    public Object getRaw() {
        return packet;
    }

    public double getX() {
        cache();
        return com.hades.client.util.ReflectionUtil.getDoubleField(packet, xField);
    }

    public double getY() {
        cache();
        return com.hades.client.util.ReflectionUtil.getDoubleField(packet, yField);
    }

    public double getZ() {
        cache();
        return com.hades.client.util.ReflectionUtil.getDoubleField(packet, zField);
    }

    public float getYaw() {
        cache();
        return com.hades.client.util.ReflectionUtil.getFloatField(packet, yawField);
    }

    public float getPitch() {
        cache();
        return com.hades.client.util.ReflectionUtil.getFloatField(packet, pitchField);
    }

    public boolean isOnGround() {
        cache();
        return com.hades.client.util.ReflectionUtil.getBoolField(packet, onGroundField);
    }

    public boolean isMoving() {
        cache();
        return com.hades.client.util.ReflectionUtil.getBoolField(packet, movingField);
    }

    public boolean isRotating() {
        cache();
        return com.hades.client.util.ReflectionUtil.getBoolField(packet, rotatingField);
    }

    public void setX(double v) {
        cache();
        com.hades.client.util.ReflectionUtil.setDoubleField(packet, xField, v);
    }

    public void setY(double v) {
        cache();
        com.hades.client.util.ReflectionUtil.setDoubleField(packet, yField, v);
    }

    public void setZ(double v) {
        cache();
        com.hades.client.util.ReflectionUtil.setDoubleField(packet, zField, v);
    }

    public void setYaw(float v) {
        cache();
        com.hades.client.util.ReflectionUtil.setFloatField(packet, yawField, v);
    }

    public void setPitch(float v) {
        cache();
        com.hades.client.util.ReflectionUtil.setFloatField(packet, pitchField, v);
    }

    public void setOnGround(boolean v) {
        cache();
        com.hades.client.util.ReflectionUtil.setBoolField(packet, onGroundField, v);
    }
}
