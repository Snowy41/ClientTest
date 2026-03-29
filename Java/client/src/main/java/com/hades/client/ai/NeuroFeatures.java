package com.hades.client.ai;

import com.hades.client.api.interfaces.IItem;
import com.hades.client.api.interfaces.IItemStack;

/**
 * Acts as the extraction layer, transforming arbitrary Java object states
 * (IItemStack)
 * into normalized numerical tensors for the NeuralNet to process.
 */
public class NeuroFeatures {

    public static final int FEATURE_COUNT = 11;

    /**
     * Converts a Minecraft item stack into a normalized float array for ML
     * processing.
     * Value mapping: [0.0 - 1.0]
     */
    public static float[] extractFeatures(IItemStack stack, boolean isObsolete) {
        float[] features = new float[FEATURE_COUNT];

        if (stack == null || stack.isNull() || stack.getItem().isNull()) {
            return features; // Empty items return 0 vectors
        }

        IItem item = stack.getItem();

        String name = item.getUnlocalizedName().toLowerCase();

        // [0] Is Weapon
        features[0] = (name.contains("sword") || name.contains("hatchet")) ? 1.0f : 0.0f;

        // [1] Attack Damage / Tier Heuristic (Normalized)
        float damageScore = 0.0f;
        if (name.contains("diamond"))
            damageScore = 1.0f;
        else if (name.contains("iron"))
            damageScore = 0.7f;
        else if (name.contains("stone"))
            damageScore = 0.4f;
        else if (name.contains("wood") || name.contains("gold"))
            damageScore = 0.2f;
        features[1] = damageScore;

        // [2] Is Armor
        features[2] = (name.contains("helmet") || name.contains("chestplate") || name.contains("leggings")
                || name.contains("boots")) ? 1.0f : 0.0f;

        // [3] Is Block (Natively supported by IItem)
        features[3] = item.isBlock() ? 1.0f : 0.0f;

        // [4] Stack Size normalized over max stack size (64)
        features[4] = Math.min((float) stack.getStackSize() / 64.0f, 1.0f);

        // [5] Is Food (MC 1.8.9 unlocalized names: beefCooked, porkchopCooked, chickenCooked, etc.)
        features[5] = (name.contains("apple") || name.contains("beef") || name.contains("porkchop")
                || name.contains("carrot") || name.contains("potato") || name.contains("bread") || name.contains("fish")
                || name.contains("chicken") || name.contains("mutton") || name.contains("rabbit")
                || name.contains("melon") || name.contains("cookie") || name.contains("pie") || name.contains("stew")
                || name.contains("pumpkinpie") || name.contains("mushroomstew"))
                        ? 1.0f
                        : 0.0f;

        // [6] Is Bow or Projectile or Good Splash Potion
        features[6] = (name.contains("bow") || name.contains("arrow") || name.contains("snowball")
                || name.contains("experience") || name.contains("exp_bottle")
                || item.isEgg()) ? 1.0f : 0.0f;

        // [7] Useless Junk Flag
        boolean isExplicitJunk = isJunk(name);
        
        // Potion Intelligence Parsing
        float extremeValue = 0.0f;
        if (name.contains("potion")) {
            int meta = stack.getDamage();
            boolean isSplash = (meta & 16384) != 0;
            int type = meta & 15; // Extract base potion ID
            
            // 4=Poison, 8=Weakness, 10=Slowness, 12=Harming
            boolean isBadEffect = (type == 4 || type == 8 || type == 10 || type == 12);
            // 1=Regen, 2=Speed, 5=Healing, 9=Strength
            boolean isSuperBuff = (type == 1 || type == 2 || type == 5 || type == 9);
            
            if (isBadEffect) {
                if (isSplash) features[6] = 1.0f; // Splash Harming/Poison = Projectile Weapon
                else isExplicitJunk = true;       // Drinkable Poison = Garbage
            } else if (isSuperBuff) {
                extremeValue = 1.0f; // High priority loot
            } else if (type == 0) {
                isExplicitJunk = true; // Water bottle / awkward potion
            } else {
                extremeValue = 0.5f; // Other moderate potions
            }
        }
        
        features[7] = isExplicitJunk ? 1.0f : 0.0f;

        // [8] Is Utility Tool (MC 1.8.9: shovelIron, pickaxeIron, hatchetIron, hoeIron)
        features[8] = (name.contains("pickaxe") || name.contains("hatchet") || name.contains("shovel")
                || name.contains("hoe") || name.contains("shears")) ? 1.0f : 0.0f;

        // [9] Contextual Observance: Is Obsolete (Weaker than another item owned)
        features[9] = isObsolete ? 1.0f : 0.0f;
        
        // [10] Extreme Value (Enchantments + Super Buffs)
        if (stack.getEnchantmentLevel(0) > 0 || stack.getEnchantmentLevel(16) > 0 || stack.getEnchantmentLevel(32) > 0) {
            extremeValue = 1.0f; // Any primary enchantment assigns max extreme value
        }
        if (name.contains("apple") && name.contains("gold")) {
            extremeValue = 1.0f; // Golden Apples
        }
        if (name.contains("enchanted_book") || name.contains("enchantedbook")) {
            extremeValue = 1.0f; // Enchanted Books carry transferable enchantments
        }
        features[10] = extremeValue;

        return features;
    }

    private static boolean isJunk(String name) {
        return name.contains("rotten")
                || name.contains("string")
                || name.contains("feather")
                || name.contains("seeds")
                || name.contains("bone")
                || name.contains("spider")
                || name.contains("flesh")
                || name.contains("sulphur");
    }

    /**
     * Natively computes the overall lethal score of a weapon including NBT
     * Sharpness levels.
     */
    public static float getWeaponScore(IItemStack stack) {
        if (stack == null || stack.isNull())
            return 0;
        String name = stack.getItem().getUnlocalizedName().toLowerCase();
        float score = 0;

        // Base Vanilla Attack Damages (Sword)
        if (name.contains("diamond"))
            score = 7.0f;
        else if (name.contains("iron"))
            score = 6.0f;
        else if (name.contains("stone"))
            score = 5.0f;
        else if (name.contains("wood") || name.contains("gold"))
            score = 4.0f;

        // 16 = Sharpness (+1.25 per level in 1.8.9)
        score += stack.getEnchantmentLevel(16) * 1.25f;
        // 20 = Fire Aspect (+1 per level equivalent utility)
        score += stack.getEnchantmentLevel(20) * 1.0f;

        if (stack.getMaxDamage() > 0) {
            score -= (stack.getDamage() / (float) stack.getMaxDamage()) * 0.1f;
        }

        return score;
    }

    /**
     * Natively computes the overall defensive score of an armor piece including NBT
     * Protection levels.
     */
    public static float getArmorScore(IItemStack stack) {
        if (stack == null || stack.isNull())
            return 0;
        String name = stack.getItem().getUnlocalizedName().toLowerCase();
        float score = 0;

        // Base Vanilla Tier Sorting (MC names: helmetDiamond, helmetIron, helmetChain, helmetGold, helmetCloth)
        if (name.contains("diamond"))
            score = 50.0f;
        else if (name.contains("iron"))
            score = 40.0f;
        else if (name.contains("chain"))
            score = 30.0f;
        else if (name.contains("gold"))
            score = 20.0f;
        else if (name.contains("cloth") || name.contains("leather"))
            score = 10.0f;

        // 0 = Protection (+4 equivalents per tier allows highly enchanted lower tiers to win)
        score += stack.getEnchantmentLevel(0) * 4.0f;
        // 1 = Fire Protection, 3 = Blast Protection, 4 = Projectile Protection (lesser value)
        score += stack.getEnchantmentLevel(1) * 1.5f;
        score += stack.getEnchantmentLevel(3) * 1.5f;
        score += stack.getEnchantmentLevel(4) * 1.5f;
        // 7 = Thorns
        score += stack.getEnchantmentLevel(7) * 1.0f;
        // 34 = Unbreaking
        score += stack.getEnchantmentLevel(34) * 0.5f;

        if (stack.getMaxDamage() > 0) {
            score -= (stack.getDamage() / (float) stack.getMaxDamage());
        }

        return score;
    }

    /**
     * Natively computes the overall utility score of a tool including NBT
     * Efficiency levels.
     */
    public static float getToolScore(IItemStack stack) {
        if (stack == null || stack.isNull())
            return 0;
        String name = stack.getItem().getUnlocalizedName().toLowerCase();
        float score = 0;

        // MC 1.8.9: pickaxeDiamond, hatchetDiamond, shovelDiamond, hoeDiamond
        if (name.contains("diamond"))
            score = 4.0f;
        else if (name.contains("iron"))
            score = 3.0f;
        else if (name.contains("stone"))
            score = 2.0f;
        else if (name.contains("wood") || name.contains("gold"))
            score = 1.0f;

        // 32 = Efficiency
        score += stack.getEnchantmentLevel(32) * 0.5f;
        // 33 = Silk Touch
        score += stack.getEnchantmentLevel(33) * 1.0f;
        // 35 = Fortune
        score += stack.getEnchantmentLevel(35) * 0.8f;
        // 34 = Unbreaking
        score += stack.getEnchantmentLevel(34) * 0.3f;

        if (stack.getMaxDamage() > 0) {
            score -= (stack.getDamage() / (float) stack.getMaxDamage()) * 0.1f;
        }

        return score;
    }

    // ==========================================
    // NEURAL NETWORK IMPLEMENTATION (MLP)
    // ==========================================

    // Layer 1: Desirability Weights (Logistic Regression for Keep/Loot)
    private static final float[] DESIRABILITY_WEIGHTS = {
        2.5f,  // [0] Weapon
        1.5f,  // [1] Damage Score
        2.0f,  // [2] Armor
        1.5f,  // [3] Block
        0.5f,  // [4] Stack Size
        1.5f,  // [5] Food
        2.0f,  // [6] Bow/Projectile
       -5.0f,  // [7] Junk
        1.5f,  // [8] Utility Tool
       -4.0f,  // [9] Obsolete
        3.0f   // [10] Extreme Value (Gapples, Potions, Enchantments)
    };
    private static final float DESIRABILITY_BIAS = -0.5f;

    /**
     * Executes a forward pass utilizing Sigmoid activation.
     * Returns a probability [0.0 - 1.0] of how valuable the item is for looting/keeping.
     */
    public static float predictDesirability(float[] features) {
        float z = DESIRABILITY_BIAS;
        for (int i = 0; i < FEATURE_COUNT && i < features.length; i++) {
            z += features[i] * DESIRABILITY_WEIGHTS[i];
        }
        // Sigmoid Activation
        return (float) (1.0 / (1.0 + Math.exp(-z)));
    }

    /**
     * Softmax Multi-Class Categorization.
     * 0=Weapon, 1=Armor, 2=Block, 3=Food, 4=Utility, 5=Projectile, 6=Junk, 7=Unknown
     */
    public static int classifyItemCategory(float[] features) {
        float[] logits = new float[7];
        
        logits[0] = features[0] * 3.0f + features[1] * 2.0f - features[9] * 2.0f;
        logits[1] = features[2] * 3.0f - features[9] * 2.0f;
        logits[2] = features[3] * 3.0f + features[4] * 1.0f;
        logits[3] = features[5] * 3.0f;
        logits[4] = features[8] * 3.0f - features[9] * 2.0f;
        logits[5] = features[6] * 3.0f;
        logits[6] = features[7] * 4.0f + features[9] * 1.0f;
        
        float max = Float.NEGATIVE_INFINITY;
        for (float l : logits) if (l > max) max = l;
        
        float sum = 0;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = (float) Math.exp(logits[i] - max);
            sum += logits[i];
        }
        
        int highest = 0;
        for (int i = 0; i < logits.length; i++) {
            logits[i] /= sum;
            if (logits[i] > logits[highest]) highest = i;
        }
        
        return logits[highest] > 0.4f ? highest : 7;
    }

}
