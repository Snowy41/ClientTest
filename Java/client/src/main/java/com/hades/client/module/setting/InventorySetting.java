package com.hades.client.module.setting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class InventorySetting extends Setting<Map<Integer, String>> {
    
    private final List<Icon> availableIcons = new ArrayList<>();

    public InventorySetting(String name, String description) {
        super(name, description, new HashMap<>());
        
        // Register default icons with 1.8.9 Item IDs
        availableIcons.add(new Icon("weapon", "Weapon", 276, 0)); // Diamond Sword
        availableIcons.add(new Icon("bow", "Bow", 261, 0)); // Bow
        availableIcons.add(new Icon("pickaxe", "Pickaxe", 278, 0)); // Diamond Pickaxe
        availableIcons.add(new Icon("axe", "Axe", 279, 0)); // Diamond Axe
        availableIcons.add(new Icon("spade", "Spade", 277, 0)); // Diamond Shovel
        availableIcons.add(new Icon("block", "Block", 4, 0)); // Cobblestone
        availableIcons.add(new Icon("food", "Food", 364, 0)); // Steak
        availableIcons.add(new Icon("gap", "Gap", 322, 1)); // Enchanted Golden Apple
        availableIcons.add(new Icon("arrow", "Arrow", 262, 0)); // Arrow
        availableIcons.add(new Icon("pearl", "Pearl", 368, 0)); // Ender Pearl
        availableIcons.add(new Icon("potion", "Potion", 373, 16421)); // Splash Potion of Healing
    }

    public List<Icon> getAvailableIcons() {
        return availableIcons;
    }

    public static class Icon {
        private final String id;
        private final String displayName;
        private final int itemId;
        private final int itemMeta;
        
        // Cache the dynamically created ItemStack for rendering
        private Object cachedItemStack = null;

        public Icon(String id, String displayName, int itemId, int itemMeta) {
            this.id = id;
            this.displayName = displayName;
            this.itemId = itemId;
            this.itemMeta = itemMeta;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getItemId() { return itemId; }
        public int getItemMeta() { return itemMeta; }
        
        public Object getCachedItemStack() {
            if (cachedItemStack == null) {
                cachedItemStack = com.hades.client.util.ItemRenderUtil.createItemStack(itemId, itemMeta);
            }
            return cachedItemStack;
        }
    }
}
