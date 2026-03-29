package com.hades.client.api.provider;

import com.hades.client.api.interfaces.INetwork;
import com.hades.client.util.ReflectionUtil;
import com.hades.client.util.HadesLogger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Vanilla189Network implements INetwork {

    private final Class<?> minecraftClass;
    private final Method getMinecraftMethod;
    private final Field thePlayerField;

    private Field sendQueueField;
    private Field networkManagerField;
    private Field channelField;
    private Method sendPacketMethod;

    private Class<?> packetClass;
    private Class<?> entityPlayerSPClass;
    private Class<?> netHandlerClass;
    private Class<?> networkManagerClass;

    // C02
    private Class<?> c02PacketClass;
    private Class<?> c02ActionEnum;
    private Class<?> entityClassRef;

    // C08
    private Class<?> c08PacketClass;
    private Class<?> itemStackClass;
    private Method getCurrentItemMethod;

    // C07
    private Class<?> c07PacketClass;
    private Class<?> c07ActionEnum;
    private Class<?> blockPosClass;
    private Class<?> enumFacingClass;

    // S12
    private Class<?> s12PacketClass;
    private Field s12EntityIdField;
    private Field s12MotionXField, s12MotionYField, s12MotionZField;

    // S27
    private Class<?> s27PacketClass;
    private Field s27MotionXField, s27MotionYField, s27MotionZField;

    // C03
    private Class<?> c03PacketClass;
    private Field c03YawField, c03PitchField, c03OnGroundField;

    // C05
    private Class<?> c05PacketClass;

    public Vanilla189Network() {
        minecraftClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
        getMinecraftMethod = ReflectionUtil.findMethod(minecraftClass, new String[] { "A", "getMinecraft", "func_71410_x" });
        thePlayerField = ReflectionUtil.findField(minecraftClass, "h", "thePlayer", "field_71439_g");

        entityPlayerSPClass = ReflectionUtil.findClass("net.minecraft.client.entity.EntityPlayerSP", "bew");
        if (entityPlayerSPClass != null) {
            sendQueueField = ReflectionUtil.findField(entityPlayerSPClass, "a", "sendQueue", "field_71174_a");
        }

        netHandlerClass = ReflectionUtil.findClass("net.minecraft.client.network.NetHandlerPlayClient", "bcy");
        if (netHandlerClass != null) {
            networkManagerField = ReflectionUtil.findField(netHandlerClass, "c", "netManager", "field_147302_e");
        }

        networkManagerClass = ReflectionUtil.findClass("net.minecraft.network.NetworkManager", "ek");
        if (networkManagerClass != null) {
            channelField = ReflectionUtil.findField(networkManagerClass, "k", "channel", "field_150746_k");
        }

        packetClass = ReflectionUtil.findClass("net.minecraft.network.Packet", "ff");
        if (netHandlerClass != null && packetClass != null) {
            sendPacketMethod = ReflectionUtil.findMethod(netHandlerClass, new String[] { "a", "addToSendQueue", "func_147297_a" }, packetClass);
        }

        // C02
        c02PacketClass = ReflectionUtil.findClass("net.minecraft.network.play.client.C02PacketUseEntity", "in");
        c02ActionEnum = ReflectionUtil.findClass("net.minecraft.network.play.client.C02PacketUseEntity$Action", "in$a");
        entityClassRef = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");

        // C08
        c08PacketClass = ReflectionUtil.findClass("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement", "ja");
        itemStackClass = ReflectionUtil.findClass("net.minecraft.item.ItemStack", "zx");

        // C07
        c07PacketClass = ReflectionUtil.findClass("net.minecraft.network.play.client.C07PacketPlayerDigging", "ir");
        c07ActionEnum = ReflectionUtil.findClass("net.minecraft.network.play.client.C07PacketPlayerDigging$Action", "ir$a");
        blockPosClass = ReflectionUtil.findClass("net.minecraft.util.BlockPos", "cj");
        enumFacingClass = ReflectionUtil.findClass("net.minecraft.util.EnumFacing", "cq");

        // S12
        s12PacketClass = ReflectionUtil.findClass("net.minecraft.network.play.server.S12PacketEntityVelocity", "hm");
        if (s12PacketClass != null) {
            s12EntityIdField = ReflectionUtil.findField(s12PacketClass, "a", "entityID", "field_149417_a");
            s12MotionXField = ReflectionUtil.findField(s12PacketClass, "b", "motionX", "field_149415_b");
            s12MotionYField = ReflectionUtil.findField(s12PacketClass, "c", "motionY", "field_149416_c");
            s12MotionZField = ReflectionUtil.findField(s12PacketClass, "d", "motionZ", "field_149414_d");
        }

        // S27
        s27PacketClass = ReflectionUtil.findClass("net.minecraft.network.play.server.S27PacketExplosion", "gk");
        if (s27PacketClass != null) {
            s27MotionXField = ReflectionUtil.findField(s27PacketClass, "f", "field_149152_f"); // motionX
            s27MotionYField = ReflectionUtil.findField(s27PacketClass, "g", "field_149153_g"); // motionY
            s27MotionZField = ReflectionUtil.findField(s27PacketClass, "h", "field_149159_h"); // motionZ
        }

        // C03
        c03PacketClass = ReflectionUtil.findClass("net.minecraft.network.play.client.C03PacketPlayer", "ip");
        if (c03PacketClass != null) {
            // Find fields by iterating to avoid hardcoding obfuscated field letters if possible
            // In C03PacketPlayer, the first float is yaw, second float is pitch.
            // The boolean onGround is 'f' in obfuscation usually.
            int floatCount = 0;
            for (Field f : c03PacketClass.getDeclaredFields()) {
                if (f.getType() == float.class) {
                    if (floatCount == 0) c03YawField = f;
                    else if (floatCount == 1) c03PitchField = f;
                    floatCount++;
                }
            }
            c03OnGroundField = ReflectionUtil.findField(c03PacketClass, "f", "onGround", "field_149474_g");
            if (c03YawField != null) c03YawField.setAccessible(true);
            if (c03PitchField != null) c03PitchField.setAccessible(true);
        }

        // C05 dynamic scanning
        if (c03PacketClass != null) {
            for (Class<?> clazz : c03PacketClass.getDeclaredClasses()) {
                try {
                    // C05PacketPlayerLook has exactly (float, float, boolean)
                    if (clazz.getConstructor(float.class, float.class, boolean.class) != null) {
                        c05PacketClass = clazz;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private Object getSendQueue() {
        try {
            Object mc = getMinecraftMethod != null ? getMinecraftMethod.invoke(null) : null;
            Object p = mc != null && thePlayerField != null ? thePlayerField.get(mc) : null;
            if (p == null || sendQueueField == null) return null;
            return sendQueueField.get(p);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Channel getNettyChannel() {
        try {
            Object sendQueue = getSendQueue();
            if (sendQueue == null || networkManagerField == null) return null;
            Object networkManager = networkManagerField.get(sendQueue);
            if (networkManager == null || channelField == null) return null;
            return (Channel) channelField.get(networkManager);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void sendPacket(Object packet) {
        try {
            Object sendQueue = getSendQueue();
            if (sendQueue != null && sendPacketMethod != null) {
                sendPacketMethod.invoke(sendQueue, packet);
            }
        } catch (Exception e) {
            HadesLogger.get().error("Failed to send packet", e);
        }
    }

    @Override
    public void sendPacketDirect(Object packet) {
        try {
            Channel channel = getNettyChannel();
            if (channel != null && channel.isOpen()) {
                ChannelHandlerContext ctx = channel.pipeline().context("hades_packet_handler");
                if (ctx != null) {
                    ctx.writeAndFlush(packet);
                } else {
                    channel.writeAndFlush(packet);
                }
            }
        } catch (Exception e) {
            HadesLogger.get().error("Failed to send packet directly", e);
        }
    }

    @Override
    public void sendInteractPacket(Object targetEntity) {
        try {
            if (c02PacketClass != null && c02ActionEnum != null && entityClassRef != null) {
                Object interactAction = c02ActionEnum.getEnumConstants()[0]; // INTERACT
                Constructor<?> c02Const = c02PacketClass.getConstructor(entityClassRef, c02ActionEnum);
                Object packet = c02Const.newInstance(targetEntity, interactAction);
                sendPacketDirect(packet);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void sendAttackPacket(Object targetEntity) {
        try {
            if (c02PacketClass != null && c02ActionEnum != null && entityClassRef != null) {
                Object attackAction = null;
                for (Object constant : c02ActionEnum.getEnumConstants()) {
                    if (constant.toString().equals("ATTACK")) {
                        attackAction = constant;
                        break;
                    }
                }
                if (attackAction != null) {
                    Constructor<?> c02Const = c02PacketClass.getConstructor(entityClassRef, c02ActionEnum);
                    Object packet = c02Const.newInstance(targetEntity, attackAction);
                    sendPacketDirect(packet);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void sendBlockPacket(Object player) {
        try {
            if (c08PacketClass != null && player != null && itemStackClass != null) {
                if (getCurrentItemMethod == null) {
                    getCurrentItemMethod = ReflectionUtil.findMethod(player.getClass(), new String[]{"bz", "getHeldItem", "func_70694_bm", "getCurrentEquippedItem"});
                }
                Object heldItem = null;
                if (getCurrentItemMethod != null) heldItem = getCurrentItemMethod.invoke(player);

                Constructor<?> c08Const = c08PacketClass.getConstructor(itemStackClass);
                Object packet = c08Const.newInstance(heldItem);
                sendPacketDirect(packet);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void sendUnblockPacket() {
        try {
            if (c07PacketClass != null && c07ActionEnum != null && blockPosClass != null && enumFacingClass != null) {
                Object releaseAction = c07ActionEnum.getEnumConstants()[5]; // RELEASE_USE_ITEM
                Constructor<?> bpConst = blockPosClass.getConstructor(int.class, int.class, int.class);
                Object dummyPos = bpConst.newInstance(-1, -1, -1);
                Object dummyFacing = enumFacingClass.getEnumConstants()[0]; // DOWN
                
                Constructor<?> c07Const = c07PacketClass.getConstructor(c07ActionEnum, blockPosClass, enumFacingClass);
                Object packet = c07Const.newInstance(releaseAction, dummyPos, dummyFacing);
                sendPacketDirect(packet);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Object createC0APacket() {
        try {
            Class<?> c0aClass = ReflectionUtil.findClass("net.minecraft.network.play.client.C0APacketAnimation", "iy");
            if (c0aClass != null) {
                return c0aClass.getConstructor().newInstance();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Object createC05Packet(float yaw, float pitch, boolean onGround) {
        try {
            if (c05PacketClass != null) {
                return c05PacketClass.getConstructor(float.class, float.class, boolean.class).newInstance(yaw, pitch, onGround);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Object createC08PacketForBlock(int x, int y, int z, int facing, float hitX, float hitY, float hitZ, Object heldItem) {
        try {
            if (c08PacketClass != null && blockPosClass != null) {
                // BlockPos supports (double, double, double) or (int, int, int). The double one is safer in 1.8.9 if int is not explicitly found when searching with double.
                // We use double, double, double here as mapped originally.
                Object posObj = blockPosClass.getConstructor(double.class, double.class, double.class).newInstance((double) x, (double) y, (double) z);
                
                // C08PacketPlayerBlockPlacement(BlockPos, int, ItemStack, float, float, float)
                Constructor<?> c08Const = c08PacketClass.getConstructor(blockPosClass, int.class, itemStackClass, float.class, float.class, float.class);
                return c08Const.newInstance(posObj, facing, heldItem, hitX, hitY, hitZ);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Object createC02Packet(Object targetEntity) {
        try {
            Class<?> c02Class = ReflectionUtil.findClass("net.minecraft.network.play.client.C02PacketUseEntity", "in");
            Class<?> actionEnum = ReflectionUtil.findClass("net.minecraft.network.play.client.C02PacketUseEntity$Action", "in$a");
            Class<?> entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
            
            if (c02Class != null && actionEnum != null && entityClass != null) {
                Object attackAction = null;
                for (Object constant : actionEnum.getEnumConstants()) {
                    if (constant.toString().equals("ATTACK")) {
                        attackAction = constant;
                        break;
                    }
                }
                if (attackAction != null) {
                    return c02Class.getConstructor(entityClass, actionEnum).newInstance(targetEntity, attackAction);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean isS12Packet(Object packet) {
        return s12PacketClass != null && s12PacketClass.isInstance(packet);
    }

    @Override
    public int getS12EntityId(Object packet) {
        return ReflectionUtil.getIntField(packet, s12EntityIdField);
    }

    @Override
    public void scaleS12Velocity(Object packet, double horizontal, double vertical) {
        try {
            if (s12MotionXField != null) {
                int mx = s12MotionXField.getInt(packet);
                s12MotionXField.setInt(packet, (int) (mx * horizontal));
            }
            if (s12MotionYField != null) {
                int my = s12MotionYField.getInt(packet);
                s12MotionYField.setInt(packet, (int) (my * vertical));
            }
            if (s12MotionZField != null) {
                int mz = s12MotionZField.getInt(packet);
                s12MotionZField.setInt(packet, (int) (mz * horizontal));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean isS27Packet(Object packet) {
        return s27PacketClass != null && s27PacketClass.isInstance(packet);
    }

    @Override
    public void scaleS27Velocity(Object packet, double horizontal, double vertical) {
        try {
            if (s27MotionXField != null) {
                float mx = s27MotionXField.getFloat(packet);
                s27MotionXField.setFloat(packet, (float) (mx * horizontal));
            }
            if (s27MotionYField != null) {
                float my = s27MotionYField.getFloat(packet);
                s27MotionYField.setFloat(packet, (float) (my * vertical));
            }
            if (s27MotionZField != null) {
                float mz = s27MotionZField.getFloat(packet);
                s27MotionZField.setFloat(packet, (float) (mz * horizontal));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean isC03Packet(Object packet) {
        return c03PacketClass != null && c03PacketClass.isInstance(packet);
    }

    @Override
    public void setC03Rotations(Object packet, float yaw, float pitch) {
        try {
            // Need to set 'rotating' boolean to true to ensure server reads rotations!
            // 'rotating' or 'hasRot' is usually field 'h'.
            Field hasRotField = ReflectionUtil.findField(c03PacketClass, "h", "rotating", "field_149481_i", "hasRot");
            if (hasRotField != null) {
                hasRotField.setAccessible(true);
                hasRotField.setBoolean(packet, true);
            }

            if (c03YawField != null) c03YawField.setFloat(packet, yaw);
            if (c03PitchField != null) c03PitchField.setFloat(packet, pitch);
        } catch (Exception ignored) {}
    }

    @Override
    public void setC03OnGround(Object packet, boolean onGround) {
        try {
            if (c03OnGroundField != null) c03OnGroundField.setBoolean(packet, onGround);
        } catch (Exception ignored) {}
    }


    @Override
    public int getPacketEntityId(Object packet) {
        if (packet == null) return -1;
        try {
            Field f = ReflectionUtil.findField(packet.getClass(), "a", "entityId");
            if (f != null) return ReflectionUtil.getIntField(packet, f);
        } catch (Exception ignored) {}
        return -1;
    }

    @Override
    public double[] getS14EntityMoveDelta(Object packet) {
        if (packet == null) return null;
        try {
            byte x = (byte) ReflectionUtil.getByteField(packet, ReflectionUtil.findField(packet.getClass(), "b", "posX"));
            byte y = (byte) ReflectionUtil.getByteField(packet, ReflectionUtil.findField(packet.getClass(), "c", "posY"));
            byte z = (byte) ReflectionUtil.getByteField(packet, ReflectionUtil.findField(packet.getClass(), "d", "posZ"));
            return new double[]{x, y, z};
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public double[] getS18EntityPos(Object packet) {
        if (packet == null) return null;
        try {
            int x = ReflectionUtil.getIntField(packet, ReflectionUtil.findField(packet.getClass(), "b", "posX"));
            int y = ReflectionUtil.getIntField(packet, ReflectionUtil.findField(packet.getClass(), "c", "posY"));
            int z = ReflectionUtil.getIntField(packet, ReflectionUtil.findField(packet.getClass(), "d", "posZ"));
            return new double[]{x, y, z};
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean isC0BPacket(Object packet) {
        if (packet == null) return false;
        return packet.getClass().getSimpleName().contains("C0BPacketEntityAction");
    }

    @Override
    public int getC0BAction(Object packet) {
        try {
            if (isC0BPacket(packet)) {
                java.lang.reflect.Field actionField = ReflectionUtil.findField(packet.getClass(), "b", "action", "field_149515_b");
                if (actionField != null) {
                    return ReflectionUtil.getIntField(packet, actionField);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    @Override
    public boolean isC02Packet(Object packet) {
        return c02PacketClass != null && c02PacketClass.isInstance(packet);
    }

    @Override
    public String getC02Action(Object packet) {
        if (!isC02Packet(packet)) return null;
        try {
            for (java.lang.reflect.Field f : packet.getClass().getDeclaredFields()) {
                // Use cached c02ActionEnum type instead of simple name check.
                // In obfuscated env, the enum class is "in$a" (simpleName="a"), not "Action".
                if (f.getType().isEnum() && (c02ActionEnum != null && c02ActionEnum.isAssignableFrom(f.getType()))) {
                    f.setAccessible(true);
                    Object action = f.get(packet);
                    if (action != null) return action.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
