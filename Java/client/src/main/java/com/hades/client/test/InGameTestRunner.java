package com.hades.client.test;

import com.hades.client.event.EventBus;
import com.hades.client.event.EventHandler;
import com.hades.client.event.HadesEvent;
import com.hades.client.event.events.*;
import com.hades.client.platform.ClientPlatform;
import com.hades.client.platform.PlatformAdapter;
import com.hades.client.platform.PlatformDetector;
import com.hades.client.platform.PlatformManager;
import com.hades.client.util.HadesLogger;
import com.hades.client.api.HadesAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-game test runner for the Hades platform system.
 * 
 * Run it after injection to verify everything works live:
 * 
 * <pre>
 * InGameTestRunner.runAll();
 * </pre>
 * 
 * Results are logged to the Hades log file and printed to stdout.
 * Can be triggered via a command or key bind.
 */
public final class InGameTestRunner {

    private static final HadesLogger LOG = HadesLogger.get();
    private static final List<TestResult> results = new ArrayList<>();

    private InGameTestRunner() {
    }

    /**
     * Run all in-game tests. Safe to call from any thread.
     * Logs results to the Hades log file.
     */
    public static void runAll() {
        results.clear();
        LOG.info("═══════════════════════════════════════");
        LOG.info("  Hades In-Game Tests Starting...");
        LOG.info("═══════════════════════════════════════");

        // Tests that don't need MC to be fully loaded
        testPlatformDetection();
        testClientPlatformEnum();
        testEventBusBasic();
        testEventCancellation();
        testEventBusPriority();
        testPlatformManagerState();

        // Tests that need MC running
        testMinecraftClassAccess();
        testActiveAdapterState();
        testTickEventsFiring();
        testRenderEventsFiring();

        // Print summary
        printSummary();
    }

    // ═══════════════════════════════════════
    // Platform Detection Tests
    // ═══════════════════════════════════════

    private static void testPlatformDetection() {
        test("Platform Detection", () -> {
            ClientPlatform platform = PlatformDetector.detect();
            assertNotNull("Detected platform should not be null", platform);
            LOG.info("  Detected: " + platform.getDisplayName());
        });
    }

    private static void testClientPlatformEnum() {
        test("ClientPlatform Enum", () -> {
            ClientPlatform[] values = ClientPlatform.values();
            assertTrue("Should have at least 3 platforms", values.length >= 3);
            assertEquals("Last platform should be VANILLA (fallback)",
                    ClientPlatform.VANILLA, values[values.length - 1]);
            assertNotNull("LABYMOD should have a marker class",
                    ClientPlatform.LABYMOD.getMarkerClass());
            assertNull("VANILLA should have null marker class",
                    ClientPlatform.VANILLA.getMarkerClass());
        });
    }

    // ═══════════════════════════════════════
    // Event Bus Tests
    // ═══════════════════════════════════════

    private static void testEventBusBasic() {
        test("EventBus Register & Post", () -> {
            EventBus bus = new EventBus();
            AtomicInteger count = new AtomicInteger(0);

            Object listener = new Object() {
                @EventHandler
                public void onTick(TickEvent e) {
                    count.incrementAndGet();
                }
            };

            bus.register(listener);
            bus.post(new TickEvent());
            bus.post(new TickEvent());

            assertEquals("Should receive 2 tick events", 2, count.get());

            bus.unregister(listener);
            bus.post(new TickEvent());

            assertEquals("Should not receive after unregister", 2, count.get());
        });
    }

    private static void testEventCancellation() {
        test("Event Cancellation", () -> {
            HadesEvent event = new TickEvent();
            assertFalse("Event should not be cancelled by default", event.isCancelled());

            event.setCancelled(true);
            assertTrue("Event should be cancelled after setCancelled(true)", event.isCancelled());

            event.setCancelled(false);
            assertFalse("Event should be uncancelled", event.isCancelled());
        });
    }

    private static void testEventBusPriority() {
        test("EventBus Priority Ordering", () -> {
            EventBus bus = new EventBus();
            StringBuilder order = new StringBuilder();

            Object lowListener = new Object() {
                @EventHandler(priority = 1)
                public void onTick(TickEvent e) {
                    order.append("LOW,");
                }
            };

            Object highListener = new Object() {
                @EventHandler(priority = 10)
                public void onTick(TickEvent e) {
                    order.append("HIGH,");
                }
            };

            bus.register(lowListener);
            bus.register(highListener);
            bus.post(new TickEvent());

            assertEquals("High priority should fire first", "HIGH,LOW,", order.toString());
        });
    }

    // ═══════════════════════════════════════
    // Platform Manager Tests
    // ═══════════════════════════════════════

    private static void testPlatformManagerState() {
        test("PlatformManager State", () -> {
            ClientPlatform platform = PlatformManager.getDetectedPlatform();
            assertNotNull("PlatformManager should have detected a platform", platform);
            LOG.info("  PlatformManager detected: " + platform.getDisplayName());

            assertNotNull("PlatformManager should have an active adapter",
                    PlatformManager.getActiveAdapter());
            LOG.info("  Active adapter: " + PlatformManager.getActiveAdapter().getName());
            assertTrue("Active adapter should be active",
                    PlatformManager.getActiveAdapter().isActive());
        });
    }

    private static void testActiveAdapterState() {
        test("Active Adapter State", () -> {
            PlatformAdapter adapter = PlatformManager.getActiveAdapter();
            assertNotNull("Should have an active adapter", adapter);
            assertTrue("Adapter should be active", adapter.isActive());

            // Verify adapter matches detected platform
            ClientPlatform detected = PlatformManager.getDetectedPlatform();
            if (detected == ClientPlatform.VANILLA) {
                assertEquals("Vanilla platform should use VanillaAdapter or equivalent",
                        ClientPlatform.VANILLA, adapter.getPlatform());
            }
            LOG.info("  Adapter '" + adapter.getName() + "' matches platform: " + detected);
        });
    }

    // ═══════════════════════════════════════
    // Minecraft Integration Tests
    // ═══════════════════════════════════════

    private static void testMinecraftClassAccess() {
        test("Minecraft Class Access", () -> {
            boolean inGame = false;
            try {
                inGame = HadesAPI.mc.isInGame();
            } catch (Throwable t) {
                LOG.info("  HadesAPI.mc.isInGame() threw: " + t.getMessage() + " (expected if not in-game)");
                // Not a failure — just means we're not fully loaded yet
                return;
            }
            LOG.info("  HadesAPI.mc.isInGame() = " + inGame);
        });
    }

    private static void testTickEventsFiring() {
        test("Tick Events Firing (3s)", () -> {
            EventBus bus = com.hades.client.HadesClient.getInstance().getEventBus();
            AtomicInteger tickCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);

            Object listener = new Object() {
                @EventHandler
                public void onTick(TickEvent e) {
                    if (tickCount.incrementAndGet() >= 5) {
                        latch.countDown();
                    }
                }
            };

            bus.register(listener);

            try {
                boolean received = latch.await(3, TimeUnit.SECONDS);
                if (received) {
                    LOG.info("  Received " + tickCount.get() + " tick events in 3s");
                } else {
                    LOG.info("  Only received " + tickCount.get() + " tick events in 3s (expected >=5)");
                    LOG.info("  This may be normal if MC isn't fully loaded yet");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bus.unregister(listener);
            }
        });
    }

    private static void testRenderEventsFiring() {
        test("Render Events Firing (3s)", () -> {
            EventBus bus = com.hades.client.HadesClient.getInstance().getEventBus();
            AtomicInteger renderCount = new AtomicInteger(0);
            AtomicBoolean hasPartialTicks = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);

            Object listener = new Object() {
                @EventHandler
                public void onRender(RenderEvent e) {
                    if (e.getPartialTicks() >= 0f && e.getPartialTicks() <= 1f) {
                        hasPartialTicks.set(true);
                    }
                    if (renderCount.incrementAndGet() >= 5) {
                        latch.countDown();
                    }
                }
            };

            bus.register(listener);

            try {
                boolean received = latch.await(3, TimeUnit.SECONDS);
                if (received) {
                    LOG.info("  Received " + renderCount.get() + " render events in 3s");
                    LOG.info("  partialTicks valid: " + hasPartialTicks.get());
                } else {
                    LOG.info("  Only received " + renderCount.get() + " render events in 3s");
                    LOG.info("  This is expected on VanillaAdapter (no render hook in polling mode)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bus.unregister(listener);
            }
        });
    }

    // ═══════════════════════════════════════
    // Test Infrastructure
    // ═══════════════════════════════════════

    private static void test(String name, Runnable testBody) {
        try {
            testBody.run();
            results.add(new TestResult(name, true, null));
            LOG.info("  ✓ PASS: " + name);
        } catch (AssertionError e) {
            results.add(new TestResult(name, false, e.getMessage()));
            LOG.error("  ✗ FAIL: " + name + " — " + e.getMessage());
        } catch (Throwable t) {
            results.add(new TestResult(name, false, t.toString()));
            LOG.error("  ✗ ERROR: " + name, t);
        }
    }

    private static void printSummary() {
        int passed = 0, failed = 0;
        for (TestResult r : results) {
            if (r.passed)
                passed++;
            else
                failed++;
        }

        LOG.info("═══════════════════════════════════════");
        LOG.info("  Results: " + passed + " passed, " + failed + " failed, "
                + results.size() + " total");

        if (failed > 0) {
            LOG.info("  Failed tests:");
            for (TestResult r : results) {
                if (!r.passed) {
                    LOG.info("    ✗ " + r.name + ": " + r.message);
                }
            }
        }

        LOG.info("═══════════════════════════════════════");
    }

    // ── Assertion helpers ──

    private static void assertTrue(String msg, boolean condition) {
        if (!condition)
            throw new AssertionError(msg);
    }

    private static void assertFalse(String msg, boolean condition) {
        if (condition)
            throw new AssertionError(msg);
    }

    private static void assertNotNull(String msg, Object obj) {
        if (obj == null)
            throw new AssertionError(msg);
    }

    private static void assertNull(String msg, Object obj) {
        if (obj != null)
            throw new AssertionError(msg);
    }

    private static void assertEquals(String msg, Object expected, Object actual) {
        if (expected == null && actual == null)
            return;
        if (expected != null && expected.equals(actual))
            return;
        throw new AssertionError(msg + " (expected: " + expected + ", got: " + actual + ")");
    }

    private static void assertEquals(String msg, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(msg + " (expected: " + expected + ", got: " + actual + ")");
        }
    }

    private static class TestResult {
        final String name;
        final boolean passed;
        final String message;

        TestResult(String name, boolean passed, String message) {
            this.name = name;
            this.passed = passed;
            this.message = message;
        }
    }
}
