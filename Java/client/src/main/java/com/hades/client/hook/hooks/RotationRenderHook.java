package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.api.HadesAPI;
import com.hades.client.combat.RotationManager;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Field;

public class RotationRenderHook {

    public static Field yawHeadField, renderYawOffsetField;
    public static Field yawField, pitchField;
    
    public static Field prevYawHeadField, prevRenderYawOffsetField;
    public static Field prevYawField, prevPitchField;

    public static float originalYawHead, originalRenderYawOffset;
    public static float originalYaw, originalPitch;
    
    public static float originalPrevYawHead, originalPrevRenderYawOffset;
    public static float originalPrevYaw, originalPrevPitch;

    public static boolean spoofing = false;

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) Object entity) {
        try {
            if (HadesAPI.player == null || HadesAPI.player.getRaw() != entity) {
                return;
            }

            RotationManager rotManager = RotationManager.getInstance();
            Float scaffoldYaw = com.hades.client.module.impl.movement.Scaffold.getActiveScaffoldYaw();
            Float scaffoldPitch = com.hades.client.module.impl.movement.Scaffold.getActiveScaffoldPitch();
            
            boolean hasScaffold = scaffoldYaw != null && scaffoldPitch != null;

            if (!rotManager.isActive() && !hasScaffold) {
                return;
            }

            if (yawHeadField == null) {
                Class<?> livingClass = ReflectionUtil.findClass("net.minecraft.entity.EntityLivingBase", "pr");
                yawHeadField = ReflectionUtil.findField(livingClass, "aK", "rotationYawHead", "field_70759_as");
                renderYawOffsetField = ReflectionUtil.findField(livingClass, "aI", "renderYawOffset", "field_70761_aq");
                prevYawHeadField = ReflectionUtil.findField(livingClass, "aL", "prevRotationYawHead", "field_70758_at");
                prevRenderYawOffsetField = ReflectionUtil.findField(livingClass, "aJ", "prevRenderYawOffset", "field_70760_ar");

                Class<?> entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
                yawField = ReflectionUtil.findField(entityClass, "y", "rotationYaw", "field_70177_z");
                pitchField = ReflectionUtil.findField(entityClass, "z", "rotationPitch", "field_70125_A");
                
                // FIXED THE MAPPINGS: prevRotationYaw is A, prevRotationPitch is B
                prevYawField = ReflectionUtil.findField(entityClass, "A", "prevRotationYaw", "field_70126_B");
                prevPitchField = ReflectionUtil.findField(entityClass, "B", "prevRotationPitch", "field_70127_C");

                if (yawHeadField != null) yawHeadField.setAccessible(true);
                if (renderYawOffsetField != null) renderYawOffsetField.setAccessible(true);
                if (yawField != null) yawField.setAccessible(true);
                if (pitchField != null) pitchField.setAccessible(true);
                
                if (prevYawHeadField != null) prevYawHeadField.setAccessible(true);
                if (prevRenderYawOffsetField != null) prevRenderYawOffsetField.setAccessible(true);
                if (prevYawField != null) prevYawField.setAccessible(true);
                if (prevPitchField != null) prevPitchField.setAccessible(true);
            }

            if (yawHeadField == null || yawField == null || prevYawField == null) return;
            
            // PREVENT RECURSIVE OVERWRITES:
            // If we are already spoofing, do not overwrite the cached originals 
            // with the already-spoofed values!
            if (spoofing) return;

            // Cache original visual rotations
            originalYawHead = yawHeadField.getFloat(entity);
            originalRenderYawOffset = renderYawOffsetField.getFloat(entity);
            originalYaw = yawField.getFloat(entity);
            originalPitch = pitchField.getFloat(entity);
            
            originalPrevYawHead = prevYawHeadField.getFloat(entity);
            originalPrevRenderYawOffset = prevRenderYawOffsetField.getFloat(entity);
            originalPrevYaw = prevYawField.getFloat(entity);
            originalPrevPitch = prevPitchField.getFloat(entity);

            // Apply spoofed rotations for 3rd-person model rendering
            float targetYaw = rotManager.isActive() ? rotManager.getServerYaw() : scaffoldYaw;
            float targetPitch = rotManager.isActive() ? rotManager.getServerPitch() : scaffoldPitch;
            float targetPrevYaw = rotManager.isActive() ? rotManager.getPrevServerYaw() : scaffoldYaw;
            float targetPrevPitch = rotManager.isActive() ? rotManager.getPrevServerPitch() : scaffoldPitch;

            yawHeadField.setFloat(entity, targetYaw);
            renderYawOffsetField.setFloat(entity, targetYaw);
            yawField.setFloat(entity, targetYaw);
            pitchField.setFloat(entity, targetPitch);
            
            prevYawHeadField.setFloat(entity, targetPrevYaw);
            prevRenderYawOffsetField.setFloat(entity, targetPrevYaw);
            prevYawField.setFloat(entity, targetPrevYaw);
            prevPitchField.setFloat(entity, targetPrevPitch);

            spoofing = true;

        } catch (Exception ignored) {}
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Argument(0) Object entity, @Advice.Thrown Throwable thrown) {
        try {
            if (!spoofing || yawHeadField == null) return;

            if (HadesAPI.player == null || HadesAPI.player.getRaw() != entity) {
                return;
            }

            // Restore original values so the local camera doesn't snap
            yawHeadField.setFloat(entity, originalYawHead);
            renderYawOffsetField.setFloat(entity, originalRenderYawOffset);
            yawField.setFloat(entity, originalYaw);
            pitchField.setFloat(entity, originalPitch);
            
            prevYawHeadField.setFloat(entity, originalPrevYawHead);
            prevRenderYawOffsetField.setFloat(entity, originalPrevRenderYawOffset);
            prevYawField.setFloat(entity, originalPrevYaw);
            prevPitchField.setFloat(entity, originalPrevPitch);

            spoofing = false;
        } catch (Exception ignored) {}
    }
}
