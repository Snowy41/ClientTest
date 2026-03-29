package com.hades.client.module.impl.player;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.ai.NeuroFeatures;
import com.hades.client.manager.InventoryTaskManager;
import com.hades.client.HadesClient;

public class AutoInventory extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (ms)", 150, 0, 1000, 10);
    private final NumberSetting jitter = new NumberSetting("Jitter (ms)", 50, 0, 500, 10);
    private final BooleanSetting openInvOnly = new BooleanSetting("Open Inv Only", false);
    private final BooleanSetting pauseOnMove = new BooleanSetting("Pause on Move", true);
    private final BooleanSetting dropUseless = new BooleanSetting("Drop Junk", true);
    private final BooleanSetting sortHotbar = new BooleanSetting("Sort Hotbar", true);
    
    // Modular Hotbar Routing
    private final com.hades.client.module.setting.InventorySetting layout = new com.hades.client.module.setting.InventorySetting("Layout", "Visually assign your preferred inventory layout.");
    
    // Max stacks to keep (0 = unlimited)
    private final NumberSetting maxBlockStacks = new NumberSetting("Max Block Stacks", 3, 0, 10, 1);
    private final NumberSetting maxFoodStacks = new NumberSetting("Max Food Stacks", 2, 0, 10, 1);
    private final NumberSetting maxArrowStacks = new NumberSetting("Max Arrow Stacks", 2, 0, 10, 1);
    private final NumberSetting maxGapStacks = new NumberSetting("Max Gap Stacks", 2, 0, 10, 1);

    private java.lang.reflect.Field currentScreenField;
    
    // Resolved GUI classes for reliable instanceof checks (works in obfuscated env)
    private Class<?> guiInventoryClass;
    private Class<?> guiContainerCreativeClass;

    private long lastActionTime = 0;

    public AutoInventory() {
        super("AutoInventory", "Dynamically manages your inventory and routes items modularly.", Category.PLAYER, 0);
        register(delay);
        register(jitter);
        register(openInvOnly);
        register(pauseOnMove);
        register(dropUseless);
        register(sortHotbar);
        register(layout);
        register(maxBlockStacks);
        register(maxFoodStacks);
        register(maxArrowStacks);
        register(maxGapStacks);

        try {
            Class<?> mcClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
            if (mcClass != null) {
                currentScreenField = com.hades.client.util.ReflectionUtil.findField(mcClass, "m", "currentScreen", "field_71462_r");
            }
            guiInventoryClass = com.hades.client.util.ReflectionUtil.findClass(
                    "net.minecraft.client.gui.inventory.GuiInventory", "azc");
            guiContainerCreativeClass = com.hades.client.util.ReflectionUtil.findClass(
                    "net.minecraft.client.gui.inventory.GuiContainerCreative", "ayu");
        } catch (Exception e) {}
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    /**
     * Checks if the current GUI screen is the player's inventory (survival or creative).
     * Uses resolved class references instead of name matching — works in obfuscated environments.
     */
    private boolean isPlayerInventoryScreen() {
        if (currentScreenField == null) return false;
        try {
            Object rawMc = HadesAPI.mc.getRaw();
            if (rawMc == null) return false;
            Object screen = currentScreenField.get(rawMc);
            if (screen == null) return false;
            
            if (guiInventoryClass != null && guiInventoryClass.isInstance(screen)) return true;
            if (guiContainerCreativeClass != null && guiContainerCreativeClass.isInstance(screen)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc == null || HadesAPI.player == null) return;
        
        if (HadesAPI.Game.isGuiOpen()) {
            // A GUI is open — only proceed if it's the player's inventory screen
            if (!isPlayerInventoryScreen()) return;
        } else if (openInvOnly.getValue()) {
            return; // Halt logic natively if Open Inv Only is toggled and we are freely running around
        }
        
        if (pauseOnMove.getValue() && (HadesAPI.player.getMoveForward() != 0 || HadesAPI.player.getMoveStrafing() != 0)) {
            return; // Prevent triggering 'Inventory Move' Intave/Grim flags while actively navigating.
        }

        if (System.currentTimeMillis() < lastActionTime) {
            return;
        }

        // Skip if another module is mid-operation (e.g. AutoArmor 3-step swap)
        InventoryTaskManager taskMgr = InventoryTaskManager.getInstance();
        if (taskMgr.isBusy()) return;
        if (!taskMgr.canActThisTick()) return;

        InventoryManager inv = InventoryManager.getInstance();

        // Stack Combination Layer deleted to enforce strict compliance with Keybind/No-Cursor interactions

        if (dropUseless.getValue()) {
            int bestSlotToDrop = -1;
            
            // Iterate 9 to 44 gui mapped
            for (int i = 9; i < 45; i++) {
                int apiSlot = i >= 36 ? i - 36 : i;
                IItemStack stack = inv.getSlot(apiSlot);
                if (stack == null || stack.isNull() || stack.getItem().isNull()) continue;

                String name = stack.getItem().getUnlocalizedName().toLowerCase();
                boolean junk = isJunk(name);
                boolean obsolete = isObsolete(apiSlot, stack, inv);
                
                if (junk || obsolete) {
                    bestSlotToDrop = i;
                    break;
                }
            }

            if (bestSlotToDrop != -1) {
                com.hades.client.util.HadesLogger.get().info("[AutoInventory] Dropping obsolete trash from GUI slot " + bestSlotToDrop + " via hotkey.");
                
                // Mode 4, Button 1: Drop entire stack via hotkey (CTRL+Q equivalent)
                inv.windowClick(0, bestSlotToDrop, 1, 4);
                
                lastActionTime = System.currentTimeMillis() + taskMgr.getGaussianDelay(delay.getValue().longValue(), jitter.getValue().longValue());
                taskMgr.recordAction();
                return;
            }
        }

        if (sortHotbar.getValue()) {
            java.util.Map<Integer, String> preferredLayout = layout.getValue();
            for (java.util.Map.Entry<Integer, String> entry : preferredLayout.entrySet()) {
                int slotIndex = entry.getKey();
                String category = entry.getValue();
                
                // Currently we only sort hotbar (0-8) natively
                if (slotIndex >= 0 && slotIndex <= 8) {
                    if (routeItem(inv, category, slotIndex)) return;
                }
            }
        }
    }

    private boolean routeItem(InventoryManager inv, String type, int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex > 8) return false;

        // Check what's currently in the target hotbar slot
        IItemStack currentItem = inv.getSlot(hotbarIndex);
        float currentScore = getFitness(currentItem, type);

        int bestSlot = -1;
        float bestScore = currentScore; // Use current item as baseline - only swap if strictly better

        // Only search the MAIN inventory (9-35), not other hotbar slots.
        // This prevents cascading swaps between hotbar positions.
        for (int i = 9; i <= 35; i++) {
            IItemStack stack = inv.getSlot(i);
            float score = getFitness(stack, type);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        // Only swap if we found something strictly better in the main inventory
        if (bestSlot != -1 && bestScore > currentScore) {
            
            // Map the inventory slot to its GUI slot representation
            // Slots 9-35 in API map directly to GUI slots 9-35
            int guiClickSlot = bestSlot;
            
            com.hades.client.util.HadesLogger.get().info("[AutoInventory] Modular Routing: Swapping best " + type + " (score " + bestScore + ") from slot " + bestSlot + " to hotbar " + hotbarIndex);
            
            // Mode 2, Button = target hotbar index (0-8)
            inv.windowClick(0, guiClickSlot, hotbarIndex, 2);
            lastActionTime = System.currentTimeMillis() + InventoryTaskManager.getInstance().getGaussianDelay(delay.getValue().longValue(), jitter.getValue().longValue());
            InventoryTaskManager.getInstance().recordAction();
            
            return true;
        }
        return false;
    }

    private float getFitness(IItemStack stack, String type) {
        if (stack == null || stack.isNull() || stack.getItem().isNull()) return -1f;
        String name = stack.getItem().getUnlocalizedName().toLowerCase();
        
        switch (type) {
            case "weapon":
                if (name.contains("sword")) return NeuroFeatures.getWeaponScore(stack);
                if (name.contains("hatchet")) return NeuroFeatures.getWeaponScore(stack) * 0.5f; // Axes are secondary
                break;
            case "bow":
                if (name.contains("bow")) return 10f + stack.getEnchantmentLevel(48) * 1.5f; // Power
                break;
            case "pickaxe":
                if (name.contains("pickaxe")) return NeuroFeatures.getToolScore(stack);
                break;
            case "axe":
                if (name.contains("hatchet")) return NeuroFeatures.getToolScore(stack);
                break;
            case "shovel":
                if (name.contains("shovel")) return NeuroFeatures.getToolScore(stack);
                break;
            case "block":
                float[] blockFeat = NeuroFeatures.extractFeatures(stack, false);
                if (NeuroFeatures.classifyItemCategory(blockFeat) == 2 && !name.contains("chest") && !name.contains("crafting") && !name.contains("furnace") && !name.contains("anvil") && !name.contains("tnt") && !name.contains("bed")) {
                    return stack.getStackSize(); // Prioritize largest stack of blocks
                }
                break;
            case "food":
                float[] foodFeat = NeuroFeatures.extractFeatures(stack, false);
                if (NeuroFeatures.classifyItemCategory(foodFeat) == 3 || name.contains("apple")) {
                    // Base food value heuristic
                    float foodValue = 0;
                    if (name.contains("apple") && name.contains("gold")) {
                        foodValue = (stack.getDamage() == 1) ? 1000f : 500f; // Enchanted > Normal
                    } else if (name.contains("beef") && name.contains("cooked")) foodValue = 8f;
                    else if (name.contains("porkchop") && name.contains("cooked")) foodValue = 8f;
                    else if (name.contains("mutton") && name.contains("cooked")) foodValue = 6f;
                    else if (name.contains("salmon") && name.contains("cooked")) foodValue = 6f;
                    else if (name.contains("chicken") && name.contains("cooked")) foodValue = 6f;
                    else if (name.contains("potato") && name.contains("baked")) foodValue = 5f;
                    else if (name.contains("bread")) foodValue = 5f;
                    else if (name.contains("apple")) foodValue = 4f;
                    else if (name.contains("carrot")) foodValue = 3f;
                    else if (name.contains("melon")) foodValue = 2f;
                    else foodValue = 1f;

                    // Combine high nutritional value with stack size (Nutrition takes precedence)
                    return (foodValue * 100f) + stack.getStackSize();
                }
                break;
            case "arrow":
                if (name.contains("arrow")) return stack.getStackSize();
                break;
        }
        return -1f;
    }

    private boolean isJunk(String name) {
        return name.contains("rotten") 
            || name.contains("spider") 
            || name.contains("flesh") 
            || name.contains("seeds")
            || name.contains("item.egg")
            || name.contains("poisonous")
            || name.contains("sulphur");
    }

    private boolean isObsolete(int targetSlot, IItemStack itemStack, InventoryManager inv) {
        if (itemStack == null || itemStack.isNull() || itemStack.getItem().isNull()) return false;
        String name = itemStack.getItem().getUnlocalizedName().toLowerCase();
        
        // Protect extremely valuable items
        if (name.contains("apple") && name.contains("gold")) return false; // Never drop Gapples
        
        if (name.contains("sword") || name.contains("hatchet") || name.contains("pickaxe") || name.contains("shovel") || name.contains("bow") || 
            name.contains("helmet") || name.contains("chestplate") || name.contains("leggings") || name.contains("pants") || name.contains("boots")) {
            
            boolean isArmor = name.contains("helmet") || name.contains("chestplate") || name.contains("leggings") || name.contains("pants") || name.contains("boots");
            
            // If this is armor and AutoArmor is enabled, check if this piece is the best
            // available for its slot — if so, don't drop it, let AutoArmor equip it
            if (isArmor) {
                Module autoArmor = HadesClient.getInstance().getModuleManager().getModule("AutoArmor");
                if (autoArmor != null && autoArmor.isEnabled()) {
                    int armorSlotIndex = getArmorSlotIndex(name);
                    if (armorSlotIndex != -1 && isBestAvailableArmor(itemStack, name, armorSlotIndex, targetSlot, inv)) {
                        return false; // AutoArmor will handle equipping this
                    }
                }
            }
            
            float currentScore = 0;
            if (name.contains("sword")) currentScore = NeuroFeatures.getWeaponScore(itemStack);
            else if (name.contains("hatchet") || name.contains("pickaxe") || name.contains("shovel")) currentScore = NeuroFeatures.getToolScore(itemStack);
            else if (name.contains("bow")) currentScore = 10f + itemStack.getEnchantmentLevel(48) * 1.5f + itemStack.getEnchantmentLevel(49) * 1.0f + itemStack.getEnchantmentLevel(50) * 1.0f + itemStack.getEnchantmentLevel(51) * 2.0f;
            else if (isArmor) currentScore = NeuroFeatures.getArmorScore(itemStack);

            // Check inventory slots 0-35 for a better duplicate
            for (int i = 0; i < 36; i++) {
                if (i == targetSlot) continue;
                
                IItemStack other = inv.getSlot(i);
                if (other != null && !other.isNull() && !other.getItem().isNull()) {
                    String otherName = other.getItem().getUnlocalizedName().toLowerCase();
                    if (isSameGearType(name, otherName)) {
                        float otherScore = 0;
                        if (otherName.contains("sword")) otherScore = NeuroFeatures.getWeaponScore(other);
                        else if (otherName.contains("hatchet") || otherName.contains("pickaxe") || otherName.contains("shovel")) otherScore = NeuroFeatures.getToolScore(other);
                        else if (otherName.contains("bow")) otherScore = 10f + other.getEnchantmentLevel(48) * 1.5f + other.getEnchantmentLevel(49) * 1.0f + other.getEnchantmentLevel(50) * 1.0f + other.getEnchantmentLevel(51) * 2.0f;
                        else if (isArmor) otherScore = NeuroFeatures.getArmorScore(other);

                        // Drop if a strictly better one exists
                        if (otherScore > currentScore) return true;
                        // Tiebreaker: keep the one in the LOWER slot index (consistent ordering prevents ping-pong)
                        if (otherScore == currentScore && i < targetSlot) return true;
                    }
                }
            }
            
            // Also check equipped armor slots for armor pieces
            if (isArmor) {
                int armorSlotIndex = getArmorSlotIndex(name);
                if (armorSlotIndex != -1) {
                    IItemStack equipped = inv.getArmorSlot(armorSlotIndex);
                    if (equipped != null && !equipped.isNull() && !equipped.getItem().isNull()) {
                        String equippedName = equipped.getItem().getUnlocalizedName().toLowerCase();
                        if (isSameGearType(name, equippedName)) {
                            float equippedScore = NeuroFeatures.getArmorScore(equipped);
                            // If what we're wearing is better or equal, this inventory piece is obsolete
                            if (equippedScore >= currentScore) return true;
                        }
                    }
                }
            }
        }
        
        // Check excess stacks for stackable items (blocks, food, arrows, golden apples)
        String category = getStackCategory(name, itemStack);
        if (category != null) {
            int maxStacks = getMaxStacksForItem(category);
            if (maxStacks > 0) {
                java.util.List<int[]> stackSizes = new java.util.ArrayList<>();
                for (int i = 0; i < 36; i++) {
                    IItemStack other = inv.getSlot(i);
                    if (!other.isNull() && !other.getItem().isNull()) {
                        String otherName = other.getItem().getUnlocalizedName().toLowerCase();
                        if (category.equals(getStackCategory(otherName, other))) {
                            stackSizes.add(new int[]{i, other.getStackSize()});
                        }
                    }
                }
                
                if (stackSizes.size() > maxStacks) {
                    // Sort descending by stack size. On tie, keep lower slot indices (closer to hotbar/start)
                    stackSizes.sort((a, b) -> {
                        int sizeCmp = Integer.compare(b[1], a[1]);
                        if (sizeCmp != 0) return sizeCmp;
                        return Integer.compare(a[0], b[0]);
                    });
                    
                    // If this item's slot is outside the top maxStacks allowed, it is obsolete
                    for (int n = maxStacks; n < stackSizes.size(); n++) {
                        if (stackSizes.get(n)[0] == targetSlot) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Checks if two gear names belong to the same equipment type.
     */
    private boolean isSameGearType(String name1, String name2) {
        return (name1.contains("sword") && name2.contains("sword")) ||
               (name1.contains("hatchet") && name2.contains("hatchet")) ||
               (name1.contains("pickaxe") && name2.contains("pickaxe")) ||
               (name1.contains("shovel") && name2.contains("shovel")) ||
               (name1.contains("bow") && name2.contains("bow")) ||
               (name1.contains("helmet") && name2.contains("helmet")) ||
               (name1.contains("chestplate") && name2.contains("chestplate")) ||
               ((name1.contains("leggings") || name1.contains("pants")) && (name2.contains("leggings") || name2.contains("pants"))) ||
               (name1.contains("boots") && name2.contains("boots"));
    }
    
    /**
     * Maps an armor piece name to its armor slot index (0=boots, 1=leggings, 2=chestplate, 3=helmet).
     */
    private int getArmorSlotIndex(String name) {
        if (name.contains("boots")) return 0;
        if (name.contains("leggings") || name.contains("pants")) return 1;
        if (name.contains("chestplate")) return 2;
        if (name.contains("helmet")) return 3;
        return -1;
    }
    
    /**
     * Checks if this armor piece is the best available for its slot across the entire inventory.
     * If it is, AutoArmor should be allowed to equip it rather than having it dropped.
     */
    private boolean isBestAvailableArmor(IItemStack piece, String pieceName, int armorSlotIndex, int pieceSlot, InventoryManager inv) {
        float pieceScore = NeuroFeatures.getArmorScore(piece);
        
        // Compare against equipped armor
        IItemStack equipped = inv.getArmorSlot(armorSlotIndex);
        if (equipped != null && !equipped.isNull() && !equipped.getItem().isNull()) {
            float equippedScore = NeuroFeatures.getArmorScore(equipped);
            if (equippedScore >= pieceScore) return false; // Equipped is already better
        }
        
        // Compare against all other pieces of the same type in inventory
        String armorType = "";
        if (pieceName.contains("helmet")) armorType = "helmet";
        else if (pieceName.contains("chestplate")) armorType = "chestplate";
        else if (pieceName.contains("leggings") || pieceName.contains("pants")) armorType = "leggings";
        else if (pieceName.contains("boots")) armorType = "boots";
        
        for (int i = 0; i < 36; i++) {
            if (i == pieceSlot) continue;
            IItemStack other = inv.getSlot(i);
            if (other != null && !other.isNull() && !other.getItem().isNull()) {
                String otherName = other.getItem().getUnlocalizedName().toLowerCase();
                if (otherName.contains(armorType) || (armorType.equals("leggings") && otherName.contains("pants"))) {
                    float otherScore = NeuroFeatures.getArmorScore(other);
                    if (otherScore > pieceScore) return false; // A better piece exists elsewhere
                }
            }
        }
        
        return true; // This is the best available — don't drop it
    }
    
    private String getStackCategory(String name, IItemStack stack) {
        if (name.contains("apple") && name.contains("gold")) return "gap";
        if (name.contains("arrow")) return "arrow";
        
        float[] feat = NeuroFeatures.extractFeatures(stack, false);
        int cat = NeuroFeatures.classifyItemCategory(feat);
        
        if (cat == 3 || name.contains("apple")) return "food";
        if (cat == 2 && !name.contains("chest") && !name.contains("crafting") && !name.contains("furnace") && !name.contains("anvil") && !name.contains("tnt") && !name.contains("bed")) return "block";
        
        return null;
    }
    
    private int getMaxStacksForItem(String category) {
        switch (category) {
            case "gap": return maxGapStacks.getValue().intValue();
            case "arrow": return maxArrowStacks.getValue().intValue();
            case "block": return maxBlockStacks.getValue().intValue();
            case "food": return maxFoodStacks.getValue().intValue();
            default: return 0;
        }
    }

}
