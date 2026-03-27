package com.hades.client.ai;

import com.hades.client.api.interfaces.IItem;
import com.hades.client.api.interfaces.IItemStack;

/**
 * Acts as the extraction layer, transforming arbitrary Java object states
 * (IItemStack)
 * into normalized numerical tensors for the NeuralNet to process.
 */
public class NeuroFeatures {

    public static final int FEATURE_COUNT = 10;

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

        // [5] Is Food
        features[5] = (name.contains("apple") || name.contains("beef") || name.contains("porkchop")
                || name.contains("carrot") || name.contains("potato") || name.contains("bread") || name.contains("fish")
                || name.contains("chicken") || name.contains("mutton") || name.contains("rabbit")
                || name.contains("melon") || name.contains("cookie") || name.contains("pie") || name.contains("stew"))
                        ? 1.0f
                        : 0.0f;

        // [6] Is Bow or Projectile
        features[6] = (name.contains("bow") || name.contains("arrow") || name.contains("snowball")
                || name.contains("egg") || name.contains("potion")) ? 1.0f : 0.0f;

        // [7] Useless Junk Flag
        features[7] = isJunk(name) ? 1.0f : 0.0f;

        // [8] Is Utility Tool
        features[8] = (name.contains("pickaxe") || name.contains("hatchet") || name.contains("shovel")
                || name.contains("hoe")) ? 1.0f : 0.0f;

        // [9] Contextual Observance: Is Obsolete (Weaker than another item owned)
        features[9] = isObsolete ? 1.0f : 0.0f;

        return features;
    }

    private static boolean isJunk(String name) {
        return name.contains("rotten")
                || name.contains("string")
                || name.contains("feather")
                || name.contains("seeds")
                || name.contains("bone")
                || name.contains("spider")
                || name.contains("flesh");
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

        if (name.contains("diamond"))
            score = 4.0f;
        else if (name.contains("iron"))
            score = 3.0f;
        else if (name.contains("stone"))
            score = 2.0f;
        else if (name.contains("wood") || name.contains("gold"))
            score = 1.0f;

        // 16 = Sharpness
        score += stack.getEnchantmentLevel(16) * 0.75f;

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

        if (name.contains("diamond"))
            score = 4.0f;
        else if (name.contains("iron"))
            score = 3.0f;
        else if (name.contains("gold"))
            score = 2.0f;
        else if (name.contains("leather") || name.contains("chainmail"))
            score = 1.0f;

        // 0 = Protection
        score += stack.getEnchantmentLevel(0) * 0.5f;

        if (stack.getMaxDamage() > 0) {
            score -= (stack.getDamage() / (float) stack.getMaxDamage()) * 0.1f;
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
       -4.0f   // [9] Obsolete
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
