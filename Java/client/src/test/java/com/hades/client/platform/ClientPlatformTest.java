package com.hades.client.platform;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for ClientPlatform enum.
 */
public class ClientPlatformTest {

    @Test
    public void testLabyModPlatform() {
        ClientPlatform platform = ClientPlatform.LABYMOD;
        assertEquals("LabyMod", platform.getDisplayName());
        assertEquals("net.labymod.api.LabyAPI", platform.getMarkerClass());
    }

    @Test
    public void testForgePlatform() {
        ClientPlatform platform = ClientPlatform.FORGE;
        assertEquals("Forge", platform.getDisplayName());
        assertEquals("net.minecraftforge.fml.common.Loader", platform.getMarkerClass());
    }

    @Test
    public void testVanillaPlatform() {
        ClientPlatform platform = ClientPlatform.VANILLA;
        assertEquals("Vanilla", platform.getDisplayName());
        assertNull(platform.getMarkerClass());
    }

    @Test
    public void testEnumValues() {
        ClientPlatform[] values = ClientPlatform.values();
        assertEquals(3, values.length);

        // Verify order: LABYMOD first (most specific), VANILLA last (fallback)
        assertEquals(ClientPlatform.LABYMOD, values[0]);
        assertEquals(ClientPlatform.FORGE, values[1]);
        assertEquals(ClientPlatform.VANILLA, values[2]);
    }

    @Test
    public void testVanillaIsLastInValues() {
        // Important: VANILLA must be last so detection loop skips it as fallback
        ClientPlatform[] values = ClientPlatform.values();
        assertEquals(ClientPlatform.VANILLA, values[values.length - 1]);
    }
}
