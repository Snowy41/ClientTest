package com.hades.client.module.impl.misc;

import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.MultiSelectSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LabyPlusSpoofing extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final MultiSelectSetting spoofMode = register(
            new MultiSelectSetting("Spoof Mode", "LabyMod Plus, Creator, or Staff",
                    new MultiSelectSetting.Option("LabyMod Plus", 10, null),
                    new MultiSelectSetting.Option("Cosmetic Creator", 11, null),
                    new MultiSelectSetting.Option("Staff", 12, null)));

    private final BooleanSetting reducedSprayCooldown = register(
            new BooleanSetting("Reduced Spray CD", "2s spray cooldown instead of 60s (Plus perk)", true));

    private final BooleanSetting draftEmotes = register(
            new BooleanSetting("Draft Emotes", "Access unreleased emotes (Creator perk)", true));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    // Cached refs
    private boolean injected = false;

    public LabyPlusSpoofing() {
        super("LabyPlusSpoofing", "Spoof LabyMod Plus, Cosmetic Creator, or Staff status",
                Category.MISC, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        injected = false;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Hades-LabyPlusSpoofing");
            t.setDaemon(true);
            return t;
        });

        // Periodically attempt injection (user data may reload)
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (injectGroupData()) {
                    triggerUiRefresh();
                }
            } catch (Throwable t) {
                // Silently retry
            }
        }, 500, 3000, TimeUnit.MILLISECONDS);

        LOG.info("[LabyPlusSpoofing] Enabled - Spoofer running.");
    }

    @Override
    protected void onDisable() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
        injected = false;
        LOG.info("[LabyPlusSpoofing] Disabled");
    }

    @Override
    public String getDisplaySuffix() {
        int count = spoofMode.getValue() != null ? spoofMode.getValue().size() : 0;
        return count + " Roles";
    }

    @SuppressWarnings("unchecked")
    private boolean injectGroupData() {
        if (!isLabyModPresent()) return false;

        try {
            // Get the client's own GameUser
            Object gameUserService = net.labymod.api.Laby.references().gameUserService();
            if (gameUserService == null) return false;

            Method clientGameUser = gameUserService.getClass().getMethod("clientGameUser");
            Object clientUser = clientGameUser.invoke(gameUserService);
            if (clientUser == null) return false;

            // Get the GameUserData from the DefaultGameUser
            Object userData = null;
            for (Method m : clientUser.getClass().getDeclaredMethods()) {
                if (m.getReturnType().getSimpleName().equals("GameUserData") && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    userData = m.invoke(clientUser);
                    break;
                }
            }
            if (userData == null) {
                for (Field f : clientUser.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("GameUserData")) {
                        f.setAccessible(true);
                        userData = f.get(clientUser);
                        break;
                    }
                }
            }
            if (userData == null) return false;

            // Get the groups list from GameUserData
            List<Object> groups = (List<Object>) userData.getClass().getMethod("getGroups").invoke(userData);
            if (groups == null) {
                LOG.info("[LabyPlusSpoofing] Could not find groups list in GameUserData");
                return false;
            }

            boolean changed = false;

            if (spoofMode.isSelected(10)) changed |= addGroup(groups, 10);
            if (spoofMode.isSelected(11)) changed |= addGroup(groups, 11);
            if (spoofMode.isSelected(12)) changed |= addGroup(groups, 1);

            // Set the visible group
            Method setVisible = null;
            for (Method m : clientUser.getClass().getMethods()) {
                if (m.getName().equals("setVisibleGroup") && m.getParameterCount() == 1) {
                    setVisible = m;
                    break;
                }
            }
            if (setVisible != null) {
                if (spoofMode.isSelected(12)) {
                    setVisible.invoke(clientUser, 1);
                } else if (spoofMode.isSelected(11)) {
                    setVisible.invoke(clientUser, 11);
                } else if (spoofMode.isSelected(10)) {
                    setVisible.invoke(clientUser, 10);
                }
            }

            if (spoofMode.isSelected(12)) {
                setStaffFlag(clientUser);
            }

            if (changed && !injected) {
                injected = true;
                LOG.info("[LabyPlusSpoofing] Locally injected group identifiers. UI updated.");
            }

            return changed;
        } catch (Exception e) {
            if (!injected) {
                LOG.error("[LabyPlusSpoofing] Injection failed", e);
            }
            return false;
        }
    }

    private boolean addGroup(List<Object> groups, int targetId) {
        try {
            for (Object existing : groups) {
                Method getIdent = existing.getClass().getMethod("getIdentifier");
                int existingId = (int) getIdent.invoke(existing);
                if (existingId == targetId) return false; // already exists
            }

            Class<?> groupIdClass = Class.forName("net.labymod.core.main.user.group.GroupIdentifier");
            Object newGroupId = createGroupIdentifier(groupIdClass, targetId);
            if (newGroupId != null) {
                groups.add(0, newGroupId);
                LOG.info("[LabyPlusSpoofing] Injected GroupIdentifier " + targetId);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void triggerUiRefresh() {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object references = labyClass.getMethod("references").invoke(null);
            Object netService = references.getClass().getMethod("labyModNetService").invoke(references);
            Method reload = netService.getClass().getMethod("reload");
            reload.invoke(netService);
        } catch (Exception e) {}
    }

    private Object createGroupIdentifier(Class<?> clazz, int id) {
        try {
            // Try constructor with int parameter
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 1 && params[0] == int.class) {
                    return c.newInstance(id);
                }
            }

            // Try no-arg
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                c.setAccessible(true);
                if (c.getParameterCount() == 0) {
                    Object instance = c.newInstance();
                    for (Field f : clazz.getDeclaredFields()) {
                        if (f.getType() == int.class) {
                            f.setAccessible(true);
                            f.set(instance, id);
                            return instance;
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private void setStaffFlag(Object clientUser) {
        try {
            Method profile = clientUser.getClass().getMethod("profile");
            Object profileObj = profile.invoke(clientUser);
            if (profileObj == null) return;

            Method visibleGroup = profileObj.getClass().getMethod("visibleGroup");
            Object group = visibleGroup.invoke(profileObj);
            if (group == null) return;

            for (Field f : group.getClass().getDeclaredFields()) {
                if (f.getType() == boolean.class) {
                    String name = f.getName().toLowerCase();
                    if (name.contains("staff")) {
                        f.setAccessible(true);
                        f.set(group, true);
                        return;
                    }
                }
            }
        } catch (Exception e) {}
    }

    private boolean isLabyModPresent() {
        try {
            Class.forName("net.labymod.api.Laby");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
