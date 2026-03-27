package com.hades.client.hook;

import com.hades.client.util.HadesLogger;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;

/**
 * HookManager dynamically attaches standard Java Instrumentation via
 * ByteBuddyAgent.
 * 
 * Since our DLL injects cleanly using AttachCurrentThread and calls
 * HadesAgent.initialize(),
 * we can instantly acquire full Instrumentation power over the already-running
 * JVM.
 */
public class HookManager {

    private static final HadesLogger LOG = HadesLogger.get();
    private static Instrumentation instrumentation;

    /**
     * Installs the ByteBuddy agent to get the Instrumentation instance,
     * then proceeds to register core game hooks.
     */
    public static void install() {
        if (instrumentation != null) {
            LOG.info("HookManager already installed.");
            return;
        }

        try {
            LOG.info("Attempting dynamic attach via ByteBuddyAgent...");
            instrumentation = ByteBuddyAgent.install();

            if (instrumentation != null) {
                LOG.info("Successfully acquired Instrumentation instance!");
                registerHooks();
            } else {
                LOG.error("Failed to acquire Instrumentation instance.");
            }
        } catch (Exception e) {
            LOG.error("Error during dynamic attach: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * Setup redefining/retransforming target Minecraft classes
     */
    private static void registerHooks() {
        LOG.info("Registering ByteBuddy hooks...");

        try {
            // Exclude our classes and ByteBuddy from LaunchWrapper's transformation.
            // This is CRITICAL because ByteBuddy is compiled for Java 5 (has JSR/RET),
            // and LabyMod's GlStateTrackerTransformer uses COMPUTE_FRAMES which crashes on
            // JSR/RET!
            // Find the LaunchClassLoader from any thread — it may not be our current thread's CL
            ClassLoader launchCL = null;
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null && cl.getClass().getName().contains("LaunchClassLoader")) {
                launchCL = cl;
            } else {
                // Search all threads for the LaunchClassLoader
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    ClassLoader tcl = t.getContextClassLoader();
                    if (tcl != null && tcl.getClass().getName().contains("LaunchClassLoader")) {
                        launchCL = tcl;
                        LOG.info("Found LaunchClassLoader on thread: " + t.getName());
                        break;
                    }
                }
            }

            if (launchCL != null) {
                // Add transformer exclusions (public method on LaunchClassLoader)
                try {
                    java.lang.reflect.Method addExclusion = launchCL.getClass().getMethod("addTransformerExclusion",
                            String.class);
                    addExclusion.invoke(launchCL, "net.bytebuddy.");
                    addExclusion.invoke(launchCL, "com.hades.");
                    LOG.info("Added LaunchWrapper transformer exclusions for ByteBuddy & Hades");
                } catch (Exception e) {
                    LOG.error("Failed to add transformer exclusions! " + e.getMessage());
                }

                // Inject our JAR into LaunchClassLoader's classpath so hook classes
                // are visible to instrumented MC classes.
                // LaunchClassLoader overrides addURL() as PUBLIC — no setAccessible needed!
                try {
                    java.net.URL hadesJarUrl = HookManager.class.getProtectionDomain()
                            .getCodeSource().getLocation();
                    if (hadesJarUrl != null) {
                        // Use LaunchClassLoader's own public addURL, NOT URLClassLoader's protected one
                        java.lang.reflect.Method addURL = launchCL.getClass().getMethod("addURL", java.net.URL.class);
                        addURL.invoke(launchCL, hadesJarUrl);
                        LOG.info("Injected Hades JAR into LaunchClassLoader: " + hadesJarUrl);
                    } else {
                        LOG.error("Could not determine Hades JAR URL from ProtectionDomain!");
                    }
                } catch (Exception e) {
                    LOG.error("Failed to inject JAR into LaunchClassLoader: " + e.getMessage());
                }
            } else {
                LOG.error("Could not find LaunchClassLoader on any thread!");
            }

            // Target Minecraft 1.8.9 Vanilla/LabyMod obfuscated names:
            // class: net.minecraft.client.Minecraft -> ave
            // method: runTick -> s

            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    // Disable type validation and ignore unknown Mixin annotations during
                    // retransformation
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    // Add listener to log ALL ByteBuddy events directly into Hades Logger
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onDiscovery(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded) {
                            if (typeName.equals("ave"))
                                LOG.info("ByteBuddy discovered: " + typeName);
                        }

                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed: " + typeDescription.getName());
                        }

                        @Override
                        public void onIgnored(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded) {
                            if (typeDescription.getName().equals("ave"))
                                LOG.info("ByteBuddy ignored: " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error instrumenting " + typeName + ": " + throwable.getMessage());
                        }

                        @Override
                        public void onComplete(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded) {
                            if (typeName.equals("ave"))
                                LOG.info("ByteBuddy completed: " + typeName);
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("ave"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(com.hades.client.hook.hooks.RunTickHook.class)
                                            .on(net.bytebuddy.matcher.ElementMatchers.named("s")))
                            .visit(
                                    net.bytebuddy.asm.Advice.to(com.hades.client.hook.hooks.ClickMouseHook.class)
                                            .on(net.bytebuddy.matcher.ElementMatchers.named("aw"))))
                    .installOn(instrumentation);

            // ── Hook EntityPlayerSP.onUpdateWalkingPlayer (bew.n) ──
            // Fires MotionEvent PRE/POST around position packet sending.
            // Required for rotation spoofing (KillAura, Scaffold, etc.)
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (EntityPlayerSP): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (EntityPlayerSP) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("bew"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.UpdateWalkingPlayerHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("p")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("onUpdateWalkingPlayer")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesNoArguments())
                                            )))
                    .installOn(instrumentation);

            // ── Hook MovementInputFromOptions.updatePlayerMoveState (bev.a) ──
            // Intercepts WASD key tracking immediately after Vanilla polls the Keyboard but before any
            // Physics calculations. This is crucial for Silent Movement because if forward is zero mathematically, player
            // shouldn't Sprint and shouldn't get Sprint Knockback!
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (MovementInput): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (MovementInput) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.namedOneOf("net.minecraft.util.MovementInputFromOptions", "bev"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.MovementInputHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("a")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("updatePlayerMoveState")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesNoArguments())
                                            )))
                    .installOn(instrumentation);

            // ── Hook Entity.moveEntity (pk.d) ──
            // Fires MoveEvent which allows altering X/Y/Z motion without setting velocity directly.
            // Critical for Flight, Speed, LongJump anticheat bypasses.
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (Entity): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (Entity) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("pk"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.MoveEntityHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("d")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("moveEntity")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(double.class, double.class, double.class))
                                            )))
                    .installOn(instrumentation);

            // ── Hook EntityLivingBase.moveEntityWithHeading (pr.g(float, float)) ──
            // Sets rotationYaw = auraYaw right before moveFlying runs.
            // This is the DEFINITIVE place to modify yaw for physics because:
            // 1. It runs AFTER all input processing and mixin hooks
            // 2. moveFlying reads this.rotationYaw directly
            // 3. Nothing can override yaw between our Enter and moveFlying
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (EntityLivingBase): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (EntityLivingBase) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("pr"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.MoveEntityWithHeadingHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("g")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("moveEntityWithHeading")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(float.class, float.class))
                                            ))
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.JumpHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("bF")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("jump")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesNoArguments())
                                            ))
                            // ── Hook EntityLivingBase.setSprinting (pr.d) ──
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.SetSprintingHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("h")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("d"))
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("setSprinting")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(boolean.class))
                                            )))
                    .installOn(instrumentation);

            // ── Hook Entity.applyEntityCollision (pk.i/func_70108_f) ──
            // Prevents Vanilla's local client-side 0.05 knockback accumulation when inside entities.
            // GrimAC has a lenience of exactly 0.08 per entity, but KillAura's strict aim
            // causes the client to ram the target continuously, accumulating >0.08 desync
            // over 2-3 ticks entirely natively, resulting in 0.098 positional flags.
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (Entity): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (Entity) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("pk").or(net.bytebuddy.matcher.ElementMatchers.named("net.minecraft.entity.Entity")))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.ApplyEntityCollisionHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("i")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("applyEntityCollision"))
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("func_70108_f")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(1))
                                            )))
                    .installOn(instrumentation);

            // ── Hook RendererLivingEntity.doRender (bjl.a) ──
            // Fires RotationRenderHook which temporarily spoofs body/head rotations 
            // exclusively for the 3rd-person rendering logic.
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (RendererLivingEntity): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (RendererLivingEntity) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("bjl"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.RotationRenderHook.class)
                                            .on(net.bytebuddy.matcher.ElementMatchers.named("a")
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(6)))))
                    .installOn(instrumentation);



            // ── Hook EntityRenderer.updateCameraAndRender (bfk.a(float, long)) ──
            // Fires Render2DEvent at the ultimate end of the game's rendering frame.
            // This is guaranteed to run ALWAYS and perfectly circumvents LabyMod's pipeline.
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .with(new net.bytebuddy.agent.builder.AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded,
                                net.bytebuddy.dynamic.DynamicType dynamicType) {
                            LOG.info("ByteBuddy transformed (EntityRenderer): " + typeDescription.getName());
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                            LOG.error("ByteBuddy error (EntityRenderer) " + typeName + ": " + throwable.getMessage());
                        }
                    })
                    .type(net.bytebuddy.matcher.ElementMatchers.named("bfk").or(net.bytebuddy.matcher.ElementMatchers.named("net.minecraft.client.renderer.EntityRenderer")))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.UpdateCameraAndRenderHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("a")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("updateCameraAndRender")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(float.class, long.class))
                                            )))
                    .installOn(instrumentation);

            // ── Hook PacketDecoder.decode (net.labymod.core.labyconnect.pipeline.PacketDecoder) ──
            // Gracefully suppresses the "Packet with id X is not registered" crash which brings
            // the whole client down when LabyConnect disconnects with garbage framing bytes remaining.
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .type(net.bytebuddy.matcher.ElementMatchers.named("net.labymod.core.labyconnect.pipeline.PacketDecoder"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.LabyPacketDecoderHook.class)
                                            .on(
                                                    net.bytebuddy.matcher.ElementMatchers.named("decode")
                                            )))
                    .installOn(instrumentation);

            // ── Hook Scoreboard.getObjectiveInDisplaySlot (auo.a(int)) ──
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .type(net.bytebuddy.matcher.ElementMatchers.named("auo").or(net.bytebuddy.matcher.ElementMatchers.named("net.minecraft.scoreboard.Scoreboard")))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.ScoreboardObjectiveHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("a")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("getObjectiveInDisplaySlot"))
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("func_96539_a")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(1))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArgument(0, int.class))
                                            )))
                    .installOn(instrumentation);
            LOG.info("ByteBuddy hooked: Scoreboard.getObjectiveInDisplaySlot");

            // ── Hook Gui.drawRect (avp.a(int, int, int, int, int)) ──
            // CRITICAL: must use takesArguments(5) to match ONLY the static drawRect,
            // not every method named "a" on the Gui class (there are many overloads).
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .type(net.bytebuddy.matcher.ElementMatchers.named("avp").or(net.bytebuddy.matcher.ElementMatchers.named("net.minecraft.client.gui.Gui")))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.GuiDrawRectHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("a")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("drawRect")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.isStatic())
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArguments(5))
                                            )))
                    .installOn(instrumentation);
            LOG.info("ByteBuddy hooked: Gui.drawRect (avp.a IIIII)");

            // ── Hook FontRenderer.drawString (avn.a(String, float, float, int, boolean)) ──
            // Match methods taking a String as first argument (drawString variants)
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .type(net.bytebuddy.matcher.ElementMatchers.named("avn").or(net.bytebuddy.matcher.ElementMatchers.named("net.minecraft.client.gui.FontRenderer")))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.FontRendererHook.class)
                                            .on(
                                                    (net.bytebuddy.matcher.ElementMatchers.named("a")
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("drawString"))
                                                    .or(net.bytebuddy.matcher.ElementMatchers.named("drawStringWithShadow")))
                                                    .and(net.bytebuddy.matcher.ElementMatchers.takesArgument(0, String.class))
                                            )))
                    .installOn(instrumentation);
            LOG.info("ByteBuddy hooked: FontRenderer.drawString (avn.a String...)");

            // ── Hook LabyMod 4 ScoreboardHudWidget (if present) ──
            // Uses string literal class matching so it won't crash on Vanilla
            new net.bytebuddy.agent.builder.AgentBuilder.Default()
                    .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(new net.bytebuddy.ByteBuddy()
                            .with(net.bytebuddy.dynamic.scaffold.TypeValidation.of(false))
                            .with(net.bytebuddy.implementation.attribute.AnnotationRetention.DISABLED))
                    .type(net.bytebuddy.matcher.ElementMatchers.named("net.labymod.core.client.gui.hud.hudwidget.ScoreboardHudWidget"))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                            .visit(
                                    net.bytebuddy.asm.Advice.to(
                                            com.hades.client.hook.hooks.LabyScoreboardHook.class)
                                            .on(net.bytebuddy.matcher.ElementMatchers.named("render"))
                            ))
                    .installOn(instrumentation);
            LOG.info("ByteBuddy hooked: LabyMod ScoreboardHudWidget.render");
            LOG.info("Finished registering hooks.");
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            LOG.error("Failed to register ByteBuddy hooks: " + e.getMessage() + "\n" + sw.toString());
        }
    }
}
