package com.hades.client.module.impl.player;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.MultiSelectSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.ReflectionUtil;
import com.hades.client.ai.NeuroFeatures;
import com.hades.client.manager.InventoryTaskManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class StorageStealer extends Module {

    // Loot category IDs — swords & armor are ALWAYS looted and not listed here
    private static final int LOOT_TOOLS       = 0;
    private static final int LOOT_BOWS        = 1;
    private static final int LOOT_ARROWS      = 2;
    private static final int LOOT_SNOWBALLS   = 3;
    private static final int LOOT_FOOD        = 4;
    private static final int LOOT_BLOCKS      = 5;
    private static final int LOOT_FISHING_ROD = 6;
    private static final int LOOT_PEARLS      = 7;
    private static final int LOOT_POTIONS     = 8;
    private static final int LOOT_GAPPLES     = 9;
    private static final int LOOT_XP_BOTTLES  = 10;
    private static final int LOOT_ENCH_BOOKS  = 11;
    private static final int LOOT_EGGS        = 12;

    private final NumberSetting delay = new NumberSetting("Delay (ms)", 100, 0, 1000, 10);
    private final NumberSetting jitter = new NumberSetting("Jitter (ms)", 50, 0, 500, 10);
    private final BooleanSetting onlyUseful = new BooleanSetting("Only Useful (ML)", true);
    private final MultiSelectSetting lootItems = new MultiSelectSetting("Loot Items", "Select which optional item types to loot",
            new MultiSelectSetting.Option("Tools",        LOOT_TOOLS,       null),
            new MultiSelectSetting.Option("Bows",         LOOT_BOWS,        null),
            new MultiSelectSetting.Option("Arrows",       LOOT_ARROWS,      null),
            new MultiSelectSetting.Option("Snowballs",    LOOT_SNOWBALLS,   null),
            new MultiSelectSetting.Option("Food",         LOOT_FOOD,        null),
            new MultiSelectSetting.Option("Blocks",       LOOT_BLOCKS,      null),
            new MultiSelectSetting.Option("Fishing Rods", LOOT_FISHING_ROD, null),
            new MultiSelectSetting.Option("Ender Pearls", LOOT_PEARLS,      null),
            new MultiSelectSetting.Option("Potions",        LOOT_POTIONS,     null),
            new MultiSelectSetting.Option("Golden Apples",  LOOT_GAPPLES,     null),
            new MultiSelectSetting.Option("XP Bottles",     LOOT_XP_BOTTLES,  null),
            new MultiSelectSetting.Option("Enchanted Books",LOOT_ENCH_BOOKS,  null),
            new MultiSelectSetting.Option("Eggs",           LOOT_EGGS,        null)
    );
    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", true);
    private final NumberSetting closeDelay = new NumberSetting("Close Delay (ms)", 200, 0, 1000, 10);
    private final BooleanSetting checkMenu = new BooleanSetting("Check Menu", true);
    private final BooleanSetting reverseSteal = new BooleanSetting("Dump Trash", true);

    private long lastStealTime = 0;
    private long emptyChestTime = -1;

    // Session-level loot memory: prevents duplicate gear extraction from the same chest
    // (Server latency means items aren't in inventory yet on the next tick)
    private int sessionWindowId = -1;
    private final java.util.Set<String> lootedGearThisSession = new java.util.HashSet<>();

    // Reflection caches
    private Class<?> guiChestClass;
    private Class<?> guiContainerClass;
    private Class<?> containerClass;
    private Class<?> slotClass;
    private Class<?> iInventoryClass;

    private Field currentScreenField;
    private Field inventorySlotsField;
    private Field windowIdField;
    private Field slotListField;
    private Field lowerChestInventoryField;
    
    private Method getStackMethod;
    private Method getSizeInventoryMethod;
    private Method getNameMethod;

    public StorageStealer() {
        super("StorageStealer", "Intelligently logic-steals desired items from chests natively faster than humans.", Category.PLAYER, 0);
        register(delay);
        register(jitter);
        register(onlyUseful);
        register(lootItems);
        register(autoClose);
        register(closeDelay);
        register(checkMenu);
        register(reverseSteal);
        
        try {
            Class<?> mcClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
            if (mcClass != null) {
                currentScreenField = ReflectionUtil.findField(mcClass, "m", "currentScreen", "field_71462_r");
            }

            guiChestClass = ReflectionUtil.findClass("net.minecraft.client.gui.inventory.GuiChest", "ayr");
            guiContainerClass = ReflectionUtil.findClass("net.minecraft.client.gui.inventory.GuiContainer", "ayl");
            containerClass = ReflectionUtil.findClass("net.minecraft.inventory.Container", "xi");
            slotClass = ReflectionUtil.findClass("net.minecraft.inventory.Slot", "yg");
            iInventoryClass = ReflectionUtil.findClass("net.minecraft.inventory.IInventory", "og");

            if (guiContainerClass != null) {
                inventorySlotsField = ReflectionUtil.findField(guiContainerClass, "h", "inventorySlots", "field_147002_h");
            }
            if (containerClass != null) {
                windowIdField = ReflectionUtil.findField(containerClass, "d", "windowId", "field_75152_c");
                slotListField = ReflectionUtil.findField(containerClass, "c", "inventorySlots", "field_75151_b");
            }
            if (slotClass != null) {
                getStackMethod = ReflectionUtil.findMethod(slotClass, new String[]{"d", "getStack", "func_75211_c"});
            }
            if (guiChestClass != null) {
                lowerChestInventoryField = ReflectionUtil.findField(guiChestClass, "w", "lowerChestInventory", "field_147015_w");
            }
            if (iInventoryClass != null) {
                getSizeInventoryMethod = ReflectionUtil.findMethod(iInventoryClass, new String[]{"o_", "getSizeInventory", "func_70302_i_"});
                getNameMethod = ReflectionUtil.findMethod(iInventoryClass, new String[]{"e_", "getName", "func_70005_c_"});
            }
        } catch (Exception e) {}
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc == null || HadesAPI.player == null) return;
        if (!HadesAPI.Game.isGuiOpen()) {
            if (sessionWindowId != -1) {
                lootedGearThisSession.clear();
                sessionWindowId = -1;
            }
            return;
        }

        if (System.currentTimeMillis() - lastStealTime < delay.getValue()) return;

        try {
            Object currentScreen = currentScreenField.get(HadesAPI.mc.getRaw());
            if (currentScreen == null || !guiChestClass.isInstance(currentScreen)) return;

            Object lowerChestInfo = lowerChestInventoryField.get(currentScreen);
            int chestSize = (int) getSizeInventoryMethod.invoke(lowerChestInfo);

            Object container = inventorySlotsField.get(currentScreen);
            int windowId = windowIdField.getInt(container);
            List<?> slots = (List<?>) slotListField.get(container);
            
            if (checkMenu.getValue() && isServerMenu(lowerChestInfo, slots, chestSize)) {
                return;
            }

            // Detect new chest session — reset loot memory
            if (windowId != sessionWindowId) {
                sessionWindowId = windowId;
                lootedGearThisSession.clear();
            }

            boolean inventoryFull = isInventoryFull(slots, chestSize);
            boolean isEmpty = true;

            java.util.List<Integer> bestSlots = new java.util.ArrayList<>();
            float[] slotScores = new float[chestSize];

            if (!inventoryFull) {
                for (int i = 0; i < chestSize; i++) {
                    Object slotObj = slots.get(i);
                    Object rawStack = getStackMethod.invoke(slotObj);

                    if (rawStack != null) {
                        IItemStack wrapper = new IItemStack(rawStack);
                        float score = getLootScore(wrapper);

                        // Skip if "Only Useful" is explicitly checked and score fails
                        if (onlyUseful.getValue() && score < 0.5f) {
                            continue; 
                        }

                        // Session duplicate check: skip if we already looted this gear type from this chest
                        String gearKey = getUniqueGearKey(wrapper);
                        if (gearKey != null && lootedGearThisSession.contains(gearKey)) {
                            continue;
                        }

                        slotScores[i] = score;
                        bestSlots.add(i);
                    }
                }
                
                bestSlots.sort((a, b) -> Float.compare(slotScores[b], slotScores[a])); // Highest to Lowest!

                for (int i : bestSlots) {
                    // Record gear type BEFORE extraction (server hasn't confirmed yet)
                    try {
                        Object slotObj = slots.get(i);
                        Object rawStack = getStackMethod.invoke(slotObj);
                        if (rawStack != null) {
                            IItemStack lootedItem = new IItemStack(rawStack);
                            String gearKey = getUniqueGearKey(lootedItem);
                            if (gearKey != null) {
                                lootedGearThisSession.add(gearKey);
                            }
                        }
                    } catch (Exception ignored) {}

                    // Extraction execution logic
                    InventoryManager.getInstance().windowClick(windowId, i, 0, 1);
                    lastStealTime = System.currentTimeMillis() + InventoryTaskManager.getInstance().getGaussianDelay(0, jitter.getValue().longValue());
                    InventoryTaskManager.getInstance().recordAction();
                    isEmpty = false;
                    break; // Wait for tick-based native delay
                }
            }

            // Dump Trash Reverse Steal sequence (Only trigger if nothing valuable was locally stolen this tick)
            if (isEmpty && reverseSteal.getValue()) {
                InventoryManager inv = InventoryManager.getInstance();
                for (int i = 0; i < 36; i++) {
                    IItemStack invStack = inv.getSlot(i);
                    if (invStack != null && !invStack.isNull() && !invStack.getItem().isNull()) {
                        String name = invStack.getItem().getUnlocalizedName().toLowerCase();
                        
                        // Strict Trash Filtering Inverse check
                        boolean junk = name.contains("rotten") || name.contains("poisonous") || name.contains("seeds") || name.contains("item.egg")
                                       || name.contains("spider") || name.contains("flesh") || name.contains("sulphur");
                        boolean inferiorGear = isGear(name) && hasBetterGear(invStack, name);
                        
                        if (junk || inferiorGear) {
                            // Map the API slots 0-35 back to strictly bounded chest native Gui container layout bounds
                            int playerGuiSlot = chestSize + ((i >= 9) ? (i - 9) : (i + 27));
                            inv.windowClick(windowId, playerGuiSlot, 0, 1);
                            
                            lastStealTime = System.currentTimeMillis() + InventoryTaskManager.getInstance().getGaussianDelay(0, jitter.getValue().longValue());
                            InventoryTaskManager.getInstance().recordAction();
                            isEmpty = false;
                            break;
                        }
                    }
                }
            }

            if (isEmpty) {
                if (emptyChestTime == -1) {
                    emptyChestTime = System.currentTimeMillis() + InventoryTaskManager.getInstance().getGaussianDelay(0, jitter.getValue().longValue());
                } else if (System.currentTimeMillis() - emptyChestTime >= closeDelay.getValue()) {
                    if (autoClose.getValue()) HadesAPI.player.closeScreen();
                    emptyChestTime = -1;
                    lootedGearThisSession.clear();
                    sessionWindowId = -1;
                }
            } else {
                emptyChestTime = -1;
            }

        } catch (Exception e) {}
    }

    /**
     * Multi-layered server GUI detection. Checks:
     * 1) Inventory name for color codes (§) — real vanilla chests never have them
     * 2) Common menu keyword patterns in the title
     * 3) Item-based heuristic — GUI buttons are typically unstackable items with colored display names
     */
    private boolean isServerMenu(Object lowerChestInfo, List<?> slots, int chestSize) {
        // Layer 1: Check inventory title for color codes or menu-like naming
        try {
            if (getNameMethod != null) {
                String title = (String) getNameMethod.invoke(lowerChestInfo);
                if (title != null) {
                    // Color codes (§) are a dead giveaway — vanilla chests NEVER have them
                    if (title.contains("\u00A7")) return true;

                    String lower = title.toLowerCase();
                    // Common server menu keywords
                    if (lower.contains("menu") || lower.contains("shop") || lower.contains("kit") ||
                        lower.contains("select") || lower.contains("warp") || lower.contains("lobby") ||
                        lower.contains("teleport") || lower.contains("cosmetic") || lower.contains("upgrade") ||
                        lower.contains("quest") || lower.contains("settings") || lower.contains("profile") ||
                        lower.contains("stats") || lower.contains("info") || lower.contains("game") ||
                        lower.contains("vote") || lower.contains("rank") || lower.contains("crate") ||
                        lower.contains("reward") || lower.contains("daily") || lower.contains("navigator")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Layer 2: Item-based heuristic — scan chest contents for GUI-like items
        try {
            int guiItemCount = 0;
            int totalItems = 0;

            for (int i = 0; i < chestSize && i < slots.size(); i++) {
                Object slotObj = slots.get(i);
                Object rawStack = getStackMethod.invoke(slotObj);
                if (rawStack == null) continue;

                IItemStack wrapper = new IItemStack(rawStack);
                if (wrapper.isNull() || wrapper.getItem().isNull()) continue;
                totalItems++;

                String displayName = wrapper.getDisplayName();
                // GUI buttons are typically renamed items with color codes
                if (displayName != null && displayName.contains("\u00A7") && wrapper.getMaxStackSize() == 1) {
                    guiItemCount++;
                }
            }

            // If 50%+ of non-empty slots look like GUI buttons, it's a server menu
            if (totalItems > 0 && (float) guiItemCount / totalItems >= 0.5f) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Failsafe bounding loop to securely verify memory capacity prior to native drag-dropping.
     * Conditionally overrides and proceeds if valid identical ML-filtered items exist offering stack merges natively.
     */
    private boolean isInventoryFull(List<?> chestSlots, int chestSize) {
        InventoryManager inv = InventoryManager.getInstance();
        boolean hasEmpty = false;
        
        for (int i = 0; i < 36; i++) {
            IItemStack stack = inv.getSlot(i);
            if (stack == null || stack.isNull() || stack.getItem().isNull()) {
                hasEmpty = true;
                break;
            }
        }
        
        if (hasEmpty) return false;

        try {
            for (int i = 0; i < chestSize; i++) {
                Object slotObj = chestSlots.get(i);
                Object rawStack = getStackMethod.invoke(slotObj);
                if (rawStack != null) {
                    IItemStack chestItem = new IItemStack(rawStack);
                    if (onlyUseful.getValue() && getLootScore(chestItem) < 0.5f) continue;
                    
                    for (int j = 0; j < 36; j++) {
                        IItemStack invItem = inv.getSlot(j);
                        if (!invItem.isNull() && !invItem.getItem().isNull() &&
                            invItem.getItem().getUnlocalizedName().equals(chestItem.getItem().getUnlocalizedName()) &&
                            invItem.getDamage() == chestItem.getDamage() &&
                            invItem.getStackSize() < invItem.getMaxStackSize()) {
                            return false; // Safely stackable dynamically bridging the full-inventory block!
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        return true;
    }

    /**
     * Determines the explicit dynamic extraction value for algorithmic array sorting descending.
     * Useless items will return explicit explicit negatives unless forced by auto-looting overrides.
     */
    private float getLootScore(IItemStack stack) {
        if (stack == null || stack.isNull() || stack.getItem().isNull()) return -1.0f;
        
        String name = stack.getItem().getUnlocalizedName().toLowerCase();
        
        // Contextual fast-fail layer (Absolute garbage)
        if (name.contains("rotten") || name.contains("poisonous") || name.contains("seeds")
            || name.contains("spider") || name.contains("flesh") || name.contains("sulphur")) {
            return -1.0f;
        }

        // ── ALWAYS-LOOT: Armor (best-piece intelligence) ──
        if (isArmorPiece(name)) {
            if (!isArmorWorthLooting(stack, name)) return -1.0f;
            return 10.0f + NeuroFeatures.getArmorScore(stack);
        }

        // ── ALWAYS-LOOT: Swords ──
        if (name.contains("sword")) {
            if (hasBetterGear(stack, name)) return -1.0f;
            return 15.0f + getExactScore(stack, name);
        }

        // ── Eggs (type-based check via ItemEgg class, not string) ──
        if (stack.getItem().isEgg()) {
            return lootItems.isSelected(LOOT_EGGS) ? 3.0f : -1.0f;
        }

        // ── USER-SELECTABLE CATEGORIES (gated by lootItems setting) ──

        // Golden Apples
        if (name.contains("apple") && name.contains("gold")) {
            return lootItems.isSelected(LOOT_GAPPLES) ? 100.0f : -1.0f;
        }

        // Tools (pickaxe, axe, shovel) — NOT swords
        if (name.contains("pickaxe") || name.contains("hatchet") || name.contains("shovel")) {
            if (!lootItems.isSelected(LOOT_TOOLS)) return -1.0f;
            if (hasBetterGear(stack, name)) return -1.0f;
            return 5.0f + getExactScore(stack, name);
        }

        // Bows
        if (name.contains("bow") && !name.contains("bowl")) {
            return lootItems.isSelected(LOOT_BOWS) ? 6.0f : -1.0f;
        }

        // Arrows
        if (name.contains("arrow")) {
            return lootItems.isSelected(LOOT_ARROWS) ? 4.0f : -1.0f;
        }

        // Snowballs
        if (name.contains("snowball")) {
            return lootItems.isSelected(LOOT_SNOWBALLS) ? 3.0f : -1.0f;
        }

        // Fishing Rods
        if (name.contains("fishing")) {
            return lootItems.isSelected(LOOT_FISHING_ROD) ? 4.5f : -1.0f;
        }

        // Ender Pearls
        if (name.contains("pearl")) {
            return lootItems.isSelected(LOOT_PEARLS) ? 7.0f : -1.0f;
        }

        // Potions
        if (name.contains("potion")) {
            return lootItems.isSelected(LOOT_POTIONS) ? 7.0f : -1.0f;
        }

        // Experience Bottles
        if (name.contains("experience") || name.contains("exp_bottle")) {
            return lootItems.isSelected(LOOT_XP_BOTTLES) ? 5.0f : -1.0f;
        }

        // Enchanted Books
        if (name.contains("enchanted_book") || name.contains("enchantedbook")) {
            return lootItems.isSelected(LOOT_ENCH_BOOKS) ? 6.0f : -1.0f;
        }

        // Blocks (buildable block items)
        if (isPlaceableBlock(name)) {
            return lootItems.isSelected(LOOT_BLOCKS) ? 2.0f : -1.0f;
        }

        // Food (generic edibles not already covered)
        if (isFood(name)) {
            return lootItems.isSelected(LOOT_FOOD) ? 3.0f : -1.0f;
        }

        // ── FALLBACK: ML Predictor for anything else ──
        float[] features = NeuroFeatures.extractFeatures(stack, false);
        float keepProbability = NeuroFeatures.predictDesirability(features);
        return keepProbability;
    }

    /**
     * Identifies common placeable block items for the Blocks loot category.
     */
    private boolean isPlaceableBlock(String name) {
        return name.contains("tile.") || name.contains("block");
    }

    /**
     * Identifies common food items for the Food loot category.
     * Excludes garbage (rotten flesh, poisonous potato, etc.) which is already filtered above.
     */
    private boolean isFood(String name) {
        return name.contains("beef") || name.contains("pork") || name.contains("chicken") ||
               name.contains("mutton") || name.contains("rabbit") || name.contains("bread") ||
               name.contains("carrot") || name.contains("potato") || name.contains("melon") ||
               name.contains("cookie") || name.contains("stew") || name.contains("pie") ||
               name.contains("cake") || name.contains("cod") || name.contains("salmon") ||
               name.contains("fish") || name.contains("apple");
    }

    /**
     * Checks if an armor piece from the chest is worth looting.
     * Compares against BOTH equipped armor AND any same-type armor already in inventory.
     * Only loots if the chest piece is strictly better than the best we already own.
     */
    private boolean isArmorWorthLooting(IItemStack chestArmor, String name) {
        InventoryManager inv = InventoryManager.getInstance();
        float chestScore = NeuroFeatures.getArmorScore(chestArmor);
        float bestOwnedScore = 0;
        boolean ownsThisType = false;
        
        int armorSlotIndex = getArmorSlotIndex(name);
        
        // Check equipped armor slot
        if (armorSlotIndex != -1) {
            IItemStack equipped = inv.getArmorSlot(armorSlotIndex);
            if (equipped != null && !equipped.isNull() && !equipped.getItem().isNull()) {
                bestOwnedScore = NeuroFeatures.getArmorScore(equipped);
                ownsThisType = true;
            }
        }
        
        // Check all inventory slots (0-35) for same-type armor pieces already looted/owned
        for (int i = 0; i < 36; i++) {
            IItemStack invStack = inv.getSlot(i);
            if (invStack == null || invStack.isNull() || invStack.getItem().isNull()) continue;
            String invName = invStack.getItem().getUnlocalizedName().toLowerCase();
            if (getArmorSlotIndex(invName) == armorSlotIndex) {
                float invScore = NeuroFeatures.getArmorScore(invStack);
                if (invScore > bestOwnedScore) {
                    bestOwnedScore = invScore;
                }
                ownsThisType = true;
            }
        }
        
        // If we don't own this type at all, always loot
        if (!ownsThisType) return true;
        
        // Only loot if the chest piece is strictly better than the best we already own
        return chestScore > bestOwnedScore;
    }
    
    private boolean isArmorPiece(String name) {
        return name.contains("helmet") || name.contains("chestplate") || name.contains("leggings") || name.contains("pants") || name.contains("boots");
    }
    
    private int getArmorSlotIndex(String name) {
        if (name.contains("boots")) return 0;
        if (name.contains("leggings") || name.contains("pants")) return 1;
        if (name.contains("chestplate")) return 2;
        if (name.contains("helmet")) return 3;
        return -1;
    }

    private boolean isGear(String uName) {
        return uName.contains("sword") || uName.contains("pickaxe") || uName.contains("hatchet") ||
               uName.contains("shovel");
    }

    private boolean hasBetterGear(IItemStack chestStack, String uName) {
        InventoryManager inv = InventoryManager.getInstance();
        float targetScore = getExactScore(chestStack, uName);
        
        // Armor check natively loops equipped plates to compare active wear
        if (isArmorPiece(uName)) {
            int armorSlotIndex = getArmorSlotIndex(uName);
            if (armorSlotIndex != -1) {
                IItemStack equipped = inv.getArmorSlot(armorSlotIndex);
                if (equipped != null && !equipped.isNull() && !equipped.getItem().isNull()) {
                    float equippedScore = NeuroFeatures.getArmorScore(equipped);
                    if (equippedScore >= targetScore) return true; // We map equipped as superior natively natively!
                }
            }
        }
        
        // Check main inventory (hotbar + backpack)
        for (int i = 0; i < 36; i++) {
            IItemStack invStack = inv.getSlot(i);
            if (!invStack.isNull() && !invStack.getItem().isNull()) {
                // Ignore matching evaluating memory-slot object exactly
                if (chestStack.getRaw() == invStack.getRaw()) continue; 
                
                String invName = invStack.getItem().getUnlocalizedName().toLowerCase();
                
                if (isArmorPiece(uName)) {
                    if (getArmorSlotIndex(invName) == getArmorSlotIndex(uName)) {
                        float invScore = getExactScore(invStack, invName);
                        if (invScore >= targetScore) return true;
                    }
                } else if (getGearType(invName).equals(getGearType(uName))) {
                    float invScore = getExactScore(invStack, invName);
                    if (invScore >= targetScore) {
                        return true; // We already possess a mechanically better/equal substitute in BP!
                    }
                }
            }
        }
        return false;
    }

    private float getExactScore(IItemStack stack, String unlocalizedName) {
        if (unlocalizedName.contains("sword")) return NeuroFeatures.getWeaponScore(stack);
        if (unlocalizedName.contains("pickaxe") || unlocalizedName.contains("shovel") || unlocalizedName.contains("hatchet")) return NeuroFeatures.getToolScore(stack);
        if (unlocalizedName.contains("helmet") || unlocalizedName.contains("chestplate") || unlocalizedName.contains("leggings") || unlocalizedName.contains("pants") || unlocalizedName.contains("boots")) return NeuroFeatures.getArmorScore(stack);
        return 0f;
    }

    private String getGearType(String name) {
        if (name.contains("sword")) return "sword";
        if (name.contains("pickaxe")) return "pickaxe";
        if (name.contains("hatchet")) return "axe";
        if (name.contains("shovel")) return "shovel";
        return "none";
    }

    /**
     * Returns a unique gear key for session-level duplicate detection.
     * Non-gear / stackable items (food, blocks, arrows, potions) return null (no restriction).
     * Gear items return their category (e.g. "sword", "helmet", "pickaxe") so only
     * one of each type is looted per chest session.
     */
    private String getUniqueGearKey(IItemStack stack) {
        if (stack == null || stack.isNull() || stack.getItem().isNull()) return null;
        String name = stack.getItem().getUnlocalizedName().toLowerCase();

        // Armor — one per slot type per session
        if (name.contains("helmet")) return "armor_helmet";
        if (name.contains("chestplate")) return "armor_chestplate";
        if (name.contains("leggings") || name.contains("pants")) return "armor_leggings";
        if (name.contains("boots")) return "armor_boots";

        // Weapons — one sword per session
        if (name.contains("sword")) return "weapon_sword";

        // Tools — one of each type per session
        if (name.contains("pickaxe")) return "tool_pickaxe";
        if (name.contains("hatchet")) return "tool_axe";
        if (name.contains("shovel")) return "tool_shovel";

        // Bow — one per session
        if (name.contains("bow")) return "weapon_bow";

        // Stackable / consumable items: no duplicate restriction
        return null;
    }
}
