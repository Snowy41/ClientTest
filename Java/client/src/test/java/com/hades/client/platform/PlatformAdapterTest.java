package com.hades.client.platform;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for PlatformAdapter interface contract.
 * Verifies that adapters implement the interface correctly
 * and follow the expected lifecycle.
 */
public class PlatformAdapterTest {

    @Test
    public void testAdapterInterfaceContract() {
        // Create a mock adapter to verify the interface works
        PlatformAdapter adapter = new PlatformAdapter() {
            private boolean active = false;

            @Override
            public String getName() {
                return "Test";
            }

            @Override
            public ClientPlatform getPlatform() {
                return ClientPlatform.VANILLA;
            }

            @Override
            public boolean initialize(com.hades.client.event.EventBus bus) {
                active = true;
                return true;
            }

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public void shutdown() {
                active = false;
            }

            @Override
            public void displayClickGUI() {
            }

            @Override
            public void closeClickGUI() {
            }
        };

        // Verify initial state
        assertEquals("Test", adapter.getName());
        assertEquals(ClientPlatform.VANILLA, adapter.getPlatform());
        assertFalse(adapter.isActive());

        // Verify lifecycle: initialize → active → shutdown → inactive
        com.hades.client.event.EventBus bus = new com.hades.client.event.EventBus();
        assertTrue(adapter.initialize(bus));
        assertTrue(adapter.isActive());

        adapter.shutdown();
        assertFalse(adapter.isActive());
    }

    @Test
    public void testAdapterCanFailInitialization() {
        PlatformAdapter failingAdapter = new PlatformAdapter() {
            @Override
            public String getName() {
                return "FailingAdapter";
            }

            @Override
            public ClientPlatform getPlatform() {
                return ClientPlatform.VANILLA;
            }

            @Override
            public boolean initialize(com.hades.client.event.EventBus hadesEventBus) {
                return false;
            }

            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public void shutdown() {
            }

            @Override
            public void displayClickGUI() {
            }

            @Override
            public void closeClickGUI() {
            }
        };

        com.hades.client.event.EventBus bus = new com.hades.client.event.EventBus();
        assertFalse(failingAdapter.initialize(bus));
        assertFalse(failingAdapter.isActive());
    }
}
