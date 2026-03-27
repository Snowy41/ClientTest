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

public class AutoInventory extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (ms)", 150, 0, 1000, 10);
    private final NumberSetting jitter = new NumberSetting("Jitter (ms)", 50, 0, 500, 10);
    private final BooleanSetting openInvOnly = new BooleanSetting("Open Inv Only", false);
    private final BooleanSetting dropUseless = new BooleanSetting("Drop Junk", true);
    private final BooleanSetting sortHotbar = new BooleanSetting("Sort Hotbar", true);
    
    // Modular Hotbar Routing
    private final com.hades.client.module.setting.InventorySetting layout = new com.hades.client.module.setting.InventorySetting("Layout", "Visually assign your preferred inventory layout.");
    
    // Max stacks to keep (0 = unlimited)
    private final NumberSetting maxBlockStacks = new NumberSetting("Max Block Stacks", 3, 0, 10, 1);
    private final NumberSetting maxFoodStacks = new NumberSetting("Max Food Stacks", 2, 0, 10, 1);
    private final NumberSetting maxArrowStacks = new NumberSetting("Max Arrow Stacks", 2, 0, 10, 1);
    private final NumberSetting maxGapStacks = new NumberSetting("Max Gap Stacks", 2, 0, 10, 1);

    private long lastActionTime = 0;

    public AutoInventory() {
        super("AutoInventory", "Dynamically manages your inventory and routes items modularly.", Category.PLAYER, 0);
        register(delay);
        register(jitter);
        register(openInvOnly);
        register(dropUseless);
        register(sortHotbar);
        register(layout);
        register(maxBlockStacks);
        register(maxFoodStacks);
        register(maxArrowStacks);
        register(maxGapStacks);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc == null || HadesAPI.player == null) return;
        
        if (HadesAPI.Game.isGuiOpen()) {
            Object rawMc = HadesAPI.mc.getRaw();
            if (rawMc != null) {
                try {
                    java.lang.reflect.Field currentScreenField = com.hades.client.util.ReflectionUtil.findField(rawMc.getClass(), "m", "currentScreen", "field_71462_r");
                    if (currentScreenField != null) {
                        Object currentScreen = currentScreenField.get(rawMc);
                        if (currentScreen != null && !currentScreen.getClass().getSimpleName().equals("GuiInventory")) {
                            return;
                        }
                    }
                } catch (Exception e) {}
            }
        } else if (openInvOnly.getValue()) {
            return; // Halt logic natively if Open Inv Only is toggled and we are freely running around
        }

        if (System.currentTimeMillis() < lastActionTime) {
            return;
        }

        InventoryManager inv = InventoryManager.getInstance();

        // Inventory management timing gate
        if (System.currentTimeMillis() < lastActionTime) {
            return;
        }

        // 1. Stack Combination Layer
        for (int i = 9; i < 45; i++) {
            int apiSlot1 = i >= 36 ? i - 36 : i;
            IItemStack stack1 = inv.getSlot(apiSlot1);
            if (stack1 == null || stack1.isNull() || stack1.getItem().isNull()) continue;
            
            int maxStack = stack1.getMaxStackSize();
            if (maxStack <= 1 || stack1.getStackSize() >= maxStack) continue;
            
            for (int j = i + 1; j < 45; j++) {
                int apiSlot2 = j >= 36 ? j - 36 : j;
                IItemStack stack2 = inv.getSlot(apiSlot2);
                if (stack2 == null || stack2.isNull() || stack2.getItem().isNull()) continue;
                
                if (stack2.getStackSize() >= maxStack) continue;
                
                if (stack1.getItem().getId() == stack2.getItem().getId() && stack1.getDamage() == stack2.getDamage()) {
                    com.hades.client.util.HadesLogger.get().info("[AutoInventory] Combining stacks of " + stack1.getItem().getUnlocalizedName() + " from GUI slot " + j + " to " + i);
                    // 1. Pick up stack2
                    inv.windowClick(0, j, 0, 0);
                    // 2. Place onto stack1
                    inv.windowClick(0, i, 0, 0);
                    // 3. Put remainder (if any) back into slot2
                    inv.windowClick(0, j, 0, 0);
                    
                    lastActionTime = System.currentTimeMillis() + delay.getValue().longValue() + (long)(Math.random() * jitter.getValue().doubleValue());
                    return;
                }
            }
        }

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
                
                lastActionTime = System.currentTimeMillis() + delay.getValue().longValue() + (long)(Math.random() * jitter.getValue().doubleValue());
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
            lastActionTime = System.currentTimeMillis() + delay.getValue().longValue() + (long)(Math.random() * jitter.getValue().doubleValue());
            
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
            || name.contains("spider_eye") 
            || name.contains("flesh") 
            || name.contains("seeds")
            || name.contains("egg")
            || name.contains("poisonous");
    }

    private boolean isObsolete(int targetSlot, IItemStack itemStack, InventoryManager inv) {
        if (itemStack == null || itemStack.isNull() || itemStack.getItem().isNull()) return false;
        String name = itemStack.getItem().getUnlocalizedName().toLowerCase();
        
        // Protect extremely valuable items
        if (name.contains("apple") && name.contains("gold")) return false; // Never drop Gapples
        
        if (name.contains("sword") || name.contains("hatchet") || name.contains("pickaxe") || name.contains("spade") || name.contains("bow")) {
            
            float currentScore = 0;
            if (name.contains("sword")) currentScore = NeuroFeatures.getWeaponScore(itemStack);
            else if (name.contains("hatchet") || name.contains("pickaxe") || name.contains("spade")) currentScore = NeuroFeatures.getToolScore(itemStack);
            else if (name.contains("bow")) currentScore = itemStack.getEnchantmentLevel(48) * 1.5f;

            for (int i = 9; i < 45; i++) {
                int apiSlot = i >= 36 ? i - 36 : i;
                if (apiSlot == targetSlot) continue; // Prevent self-evaluation loop
                
                IItemStack other = inv.getSlot(apiSlot);
                if (!other.isNull() && !other.getItem().isNull()) {
                    String otherName = other.getItem().getUnlocalizedName().toLowerCase();
                    if ((name.contains("sword") && otherName.contains("sword")) ||
                        (name.contains("hatchet") && otherName.contains("hatchet")) ||
                        (name.contains("pickaxe") && otherName.contains("pickaxe")) ||
                        (name.contains("spade") && otherName.contains("spade")) ||
                        (name.contains("bow") && otherName.contains("bow"))) {
                        
                        float otherScore = 0;
                        if (otherName.contains("sword")) otherScore = NeuroFeatures.getWeaponScore(other);
                        else if (otherName.contains("hatchet") || otherName.contains("pickaxe") || otherName.contains("spade")) otherScore = NeuroFeatures.getToolScore(other);
                        else if (otherName.contains("bow")) otherScore = other.getEnchantmentLevel(48) * 1.5f;

                        // Only mark as obsolete if there's a STRICTLY better one,
                        // OR if equal score, only drop the one in the HIGHER slot index (tiebreaker)
                        if (otherScore > currentScore) return true;
                        if (otherScore == currentScore && apiSlot > targetSlot) return true;
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
