package com.hades.client.platform;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for PlatformDetector.
 * 
 * Note: In a test environment (no MC/LabyMod classes loaded),
 * detect() should fall back to VANILLA since no marker classes exist.
 */
public class PlatformDetectorTest {

    @Test
    public void testDetectFallsBackToVanilla() {
        // In test environment, no LabyMod/Forge classes are on the classpath
        // (they're compileOnly, not testImplementation), so detection falls back
        ClientPlatform detected = PlatformDetector.detect();
        assertEquals(ClientPlatform.VANILLA, detected);
    }

    @Test
    public void testDetectWithCustomClassLoader() {
        // Test with a classloader that doesn't have any MC classes
        ClassLoader emptyLoader = new ClassLoader() {
        };
        ClientPlatform detected = PlatformDetector.detect(emptyLoader);
        assertEquals(ClientPlatform.VANILLA, detected);
    }

    @Test
    public void testDetectWithSystemClassLoader() {
        ClientPlatform detected = PlatformDetector.detect(ClassLoader.getSystemClassLoader());
        assertEquals(ClientPlatform.VANILLA, detected);
    }

    @Test
    public void testDetectReturnsNonNull() {
        // Detection should never return null
        ClientPlatform detected = PlatformDetector.detect();
        assertNotNull(detected);
    }

    @Test
    public void testDetectIsConsistent() {
        // Multiple calls should return the same result
        ClientPlatform first = PlatformDetector.detect();
        ClientPlatform second = PlatformDetector.detect();
        assertEquals(first, second);
    }
}
