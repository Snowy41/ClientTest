package com.hades.client.event;

import com.hades.client.event.events.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the EventBus: registration, dispatching, unregistration,
 * priority ordering, and event type isolation.
 */
public class EventBusTest {

    private EventBus eventBus;

    @Before
    public void setUp() {
        eventBus = new EventBus();
    }

    // ── Registration & Dispatching ──

    @Test
    public void testRegisterAndPost() {
        TestListener listener = new TestListener();
        eventBus.register(listener);

        eventBus.post(new TickEvent());

        assertEquals(1, listener.tickCount);
    }

    @Test
    public void testMultipleEventsDispatched() {
        TestListener listener = new TestListener();
        eventBus.register(listener);

        eventBus.post(new TickEvent());
        eventBus.post(new TickEvent());
        eventBus.post(new TickEvent());

        assertEquals(3, listener.tickCount);
    }

    @Test
    public void testDifferentEventTypes() {
        TestListener listener = new TestListener();
        eventBus.register(listener);

        eventBus.post(new TickEvent());
        eventBus.post(new RenderEvent(0.5f));
        eventBus.post(new KeyEvent(42, true));

        assertEquals(1, listener.tickCount);
        assertEquals(1, listener.renderCount);
        assertEquals(1, listener.keyCount);
    }

    @Test
    public void testEventDataPassedCorrectly() {
        TestListener listener = new TestListener();
        eventBus.register(listener);

        eventBus.post(new RenderEvent(0.75f));

        assertEquals(0.75f, listener.lastPartialTicks, 0.001f);
    }

    @Test
    public void testKeyEventDataPassedCorrectly() {
        TestListener listener = new TestListener();
        eventBus.register(listener);

        eventBus.post(new KeyEvent(54, true));

        assertEquals(54, listener.lastKeyCode);
        assertTrue(listener.lastKeyPressed);

        eventBus.post(new KeyEvent(54, false));

        assertEquals(54, listener.lastKeyCode);
        assertFalse(listener.lastKeyPressed);
    }

    // ── Unregistration ──

    @Test
    public void testUnregister() {
        TestListener listener = new TestListener();
        eventBus.register(listener);

        eventBus.post(new TickEvent());
        assertEquals(1, listener.tickCount);

        eventBus.unregister(listener);

        eventBus.post(new TickEvent());
        assertEquals(1, listener.tickCount); // Should NOT increment
    }

    // ── Event Type Isolation ──

    @Test
    public void testEventTypeIsolation() {
        TickOnlyListener tickListener = new TickOnlyListener();
        eventBus.register(tickListener);

        // Post a RenderEvent — should NOT be received by TickOnlyListener
        eventBus.post(new RenderEvent(1.0f));

        assertEquals(0, tickListener.tickCount);

        // Post a TickEvent — should be received
        eventBus.post(new TickEvent());

        assertEquals(1, tickListener.tickCount);
    }

    // ── Priority Ordering ──

    @Test
    public void testPriorityOrdering() {
        StringBuilder order = new StringBuilder();
        LowPriorityListener low = new LowPriorityListener(order);
        HighPriorityListener high = new HighPriorityListener(order);

        // Register low first, then high — high should still fire first
        eventBus.register(low);
        eventBus.register(high);

        eventBus.post(new TickEvent());

        assertEquals("HIGH,LOW,", order.toString());
    }

    // ── No Listeners ──

    @Test
    public void testPostWithNoListeners() {
        // Should not throw
        eventBus.post(new TickEvent());
    }

    // ── Multiple Listeners ──

    @Test
    public void testMultipleListeners() {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();
        eventBus.register(listener1);
        eventBus.register(listener2);

        eventBus.post(new TickEvent());

        assertEquals(1, listener1.tickCount);
        assertEquals(1, listener2.tickCount);
    }

    // ── Render2D Event ──

    @Test
    public void testRender2DEvent() {
        Render2DListener listener = new Render2DListener();
        eventBus.register(listener);

        eventBus.post(new Render2DEvent(0.5f, 1920, 1080));

        assertEquals(1, listener.count);
        assertEquals(1920, listener.lastWidth);
        assertEquals(1080, listener.lastHeight);
    }

    // ── Render3D Event ──

    @Test
    public void testRender3DEvent() {
        Render3DListener listener = new Render3DListener();
        eventBus.register(listener);

        eventBus.post(new Render3DEvent(0.25f));

        assertEquals(1, listener.count);
        assertEquals(0.25f, listener.lastPartialTicks, 0.001f);
    }

    // ═══════════════════════════════════════════
    // Test listener classes
    // ═══════════════════════════════════════════

    public static class TestListener {
        int tickCount = 0;
        int renderCount = 0;
        int keyCount = 0;
        float lastPartialTicks = 0;
        int lastKeyCode = 0;
        boolean lastKeyPressed = false;

        @EventHandler
        public void onTick(TickEvent event) {
            tickCount++;
        }

        @EventHandler
        public void onRender(RenderEvent event) {
            renderCount++;
            lastPartialTicks = event.getPartialTicks();
        }

        @EventHandler
        public void onKey(KeyEvent event) {
            keyCount++;
            lastKeyCode = event.getKeyCode();
            lastKeyPressed = event.isPressed();
        }
    }

    public static class TickOnlyListener {
        int tickCount = 0;

        @EventHandler
        public void onTick(TickEvent event) {
            tickCount++;
        }
    }

    public static class HighPriorityListener {
        private final StringBuilder order;

        HighPriorityListener(StringBuilder order) {
            this.order = order;
        }

        @EventHandler(priority = 10)
        public void onTick(TickEvent event) {
            order.append("HIGH,");
        }
    }

    public static class LowPriorityListener {
        private final StringBuilder order;

        LowPriorityListener(StringBuilder order) {
            this.order = order;
        }

        @EventHandler(priority = 1)
        public void onTick(TickEvent event) {
            order.append("LOW,");
        }
    }

    public static class Render2DListener {
        int count = 0;
        int lastWidth = 0;
        int lastHeight = 0;

        @EventHandler
        public void onRender2D(Render2DEvent event) {
            count++;
            lastWidth = event.getScaledWidth();
            lastHeight = event.getScaledHeight();
        }
    }

    public static class Render3DListener {
        int count = 0;
        float lastPartialTicks = 0;

        @EventHandler
        public void onRender3D(Render3DEvent event) {
            count++;
            lastPartialTicks = event.getPartialTicks();
        }
    }
}
