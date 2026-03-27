package com.hades.client.event;

import com.hades.client.event.events.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for HadesEvent base class and the cancellation system.
 */
public class HadesEventTest {

    @Test
    public void testDefaultNotCancelled() {
        TickEvent event = new TickEvent();
        assertFalse(event.isCancelled());
    }

    @Test
    public void testSetCancelled() {
        TickEvent event = new TickEvent();
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testCancelAndUncancel() {
        TickEvent event = new TickEvent();
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        event.setCancelled(false);
        assertFalse(event.isCancelled());
    }

    @Test
    public void testAllEventsExtendHadesEvent() {
        // Verify all events extend HadesEvent
        assertTrue(new TickEvent() instanceof HadesEvent);
        assertTrue(new RenderEvent(0f) instanceof HadesEvent);
        assertTrue(new Render2DEvent(0f, 0, 0) instanceof HadesEvent);
        assertTrue(new Render3DEvent(0f) instanceof HadesEvent);
        assertTrue(new KeyEvent(0, false) instanceof HadesEvent);
        assertTrue(new MotionEvent(MotionEvent.State.PRE, 0, 0, 0, 0, 0, false) instanceof HadesEvent);
        assertTrue(new PacketEvent.Send(null) instanceof HadesEvent);
        assertTrue(new PacketEvent.Receive(null) instanceof HadesEvent);
    }

    @Test
    public void testPacketEventCancellation() {
        PacketEvent.Send event = new PacketEvent.Send("test");
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertTrue(event.isSend());
        assertFalse(event.isReceive());
    }

    @Test
    public void testMotionEventPhases() {
        MotionEvent pre = new MotionEvent(
                MotionEvent.State.PRE, 1.0, 2.0, 3.0, 45.0f, 90.0f, true);
        assertTrue(pre.isPre());
        assertFalse(pre.isPost());
        assertEquals(1.0, pre.getX(), 0.001);
        assertEquals(2.0, pre.getY(), 0.001);
        assertEquals(3.0, pre.getZ(), 0.001);
        assertEquals(45.0f, pre.getYaw(), 0.001f);
        assertEquals(90.0f, pre.getPitch(), 0.001f);
        assertTrue(pre.isOnGround());

        pre.setX(10.0);
        assertEquals(10.0, pre.getX(), 0.001);

        MotionEvent post = new MotionEvent(
                MotionEvent.State.POST, 1.0, 2.0, 3.0, 45.0f, 90.0f, true);
        assertFalse(post.isPre());
        assertTrue(post.isPost());
    }

    @Test
    public void testRenderEventPartialTicks() {
        RenderEvent event = new RenderEvent(0.5f);
        assertEquals(0.5f, event.getPartialTicks(), 0.001f);
    }

    @Test
    public void testRender2DEventScaledResolution() {
        Render2DEvent event = new Render2DEvent(0.33f, 1920, 1080);
        assertEquals(0.33f, event.getPartialTicks(), 0.001f);
        assertEquals(1920, event.getScaledWidth());
        assertEquals(1080, event.getScaledHeight());
    }

    @Test
    public void testRender3DEventPartialTicks() {
        Render3DEvent event = new Render3DEvent(0.75f);
        assertEquals(0.75f, event.getPartialTicks(), 0.001f);
    }

    @Test
    public void testKeyEventProperties() {
        KeyEvent pressed = new KeyEvent(42, true);
        assertEquals(42, pressed.getKeyCode());
        assertTrue(pressed.isPressed());

        KeyEvent released = new KeyEvent(42, false);
        assertEquals(42, released.getKeyCode());
        assertFalse(released.isPressed());
    }
}
