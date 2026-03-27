package com.hades.client.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps obfuscated MC 1.8.9 packet field names to human-readable names.
 *
 * Structure:
 * outerKey = simple obfuscated class name (e.g. "ip", "ip$a", "fi")
 * innerKey = obfuscated field name (e.g. "a", "b")
 * value = readable name (e.g. "x", "yaw")
 *
 * If no mapping is found the original obfuscated name is returned unchanged.
 */
public class PacketFieldMapper {

    // Map<className, Map<obfField, readableName>>
    private static final Map<String, Map<String, String>> MAPPINGS = new HashMap<>();

    static {
        // ── C03PacketPlayer (ip) – base ───────────────────────────────────────────
        Map<String, String> c03 = new HashMap<>();
        c03.put("a", "x");
        c03.put("b", "y");
        c03.put("c", "z");
        c03.put("d", "yaw");
        c03.put("e", "pitch");
        c03.put("f", "onGround");
        c03.put("g", "moving");
        c03.put("h", "rotating");
        MAPPINGS.put("ip", c03);
        MAPPINGS.put("ip$a", c03); // C04PacketPlayerPosition – same fields
        MAPPINGS.put("ip$b", c03); // C06PacketPlayerPosLook – same fields
        MAPPINGS.put("ip$c", c03); // C05PacketPlayerLook – same fields

        // ── C00PacketKeepAlive (io) ───────────────────────────────────────────────
        Map<String, String> c00ka = new HashMap<>();
        c00ka.put("a", "keepAliveId");
        MAPPINGS.put("io", c00ka);

        // ── C01PacketChatMessage (ie) ─────────────────────────────────────────────
        Map<String, String> c01chat = new HashMap<>();
        c01chat.put("a", "message");
        MAPPINGS.put("ie", c01chat);

        // ── C02PacketUseEntity (in) ───────────────────────────────────────────────
        Map<String, String> c02 = new HashMap<>();
        c02.put("a", "entityId");
        c02.put("b", "action"); // USE / ATTACK / INTERACT_AT
        c02.put("c", "hitVecX");
        c02.put("d", "hitVecY");
        c02.put("e", "hitVecZ");
        MAPPINGS.put("in", c02);

        // ── C07PacketPlayerDigging (ir) ───────────────────────────────────────────
        Map<String, String> c07 = new HashMap<>();
        c07.put("a", "status");
        c07.put("b", "position");
        c07.put("c", "facing");
        MAPPINGS.put("ir", c07);

        // ── C08PacketPlayerBlockPlacement (ja) ───────────────────────────────────
        Map<String, String> c08 = new HashMap<>();
        c08.put("a", "position");
        c08.put("b", "placedBlockDirection");
        c08.put("c", "stack");
        c08.put("d", "facingX");
        c08.put("e", "facingY");
        c08.put("f", "facingZ");
        MAPPINGS.put("ja", c08);

        // ── C09PacketHeldItemChange (iv) ──────────────────────────────────────────
        Map<String, String> c09 = new HashMap<>();
        c09.put("a", "slotId");
        MAPPINGS.put("iv", c09);

        // ── C0APacketAnimation (iy) ───────────────────────────────────────────────
        // No fields beyond super

        // ── C0BPacketEntityAction (is) ────────────────────────────────────────────
        Map<String, String> c0b = new HashMap<>();
        c0b.put("a", "entityId");
        c0b.put("b", "action");
        c0b.put("c", "auxData");
        MAPPINGS.put("is", c0b);

        // ── C0CPacketInput (it) ───────────────────────────────────────────────────
        Map<String, String> c0c = new HashMap<>();
        c0c.put("a", "strafeSpeed");
        c0c.put("b", "forwardSpeed");
        c0c.put("c", "jumping");
        c0c.put("d", "sneaking");
        MAPPINGS.put("it", c0c);

        // ── C0EPacketClickWindow (ik) ─────────────────────────────────────────────
        Map<String, String> c0e = new HashMap<>();
        c0e.put("a", "windowId");
        c0e.put("b", "slotId");
        c0e.put("c", "mouseButton");
        c0e.put("d", "mode");
        c0e.put("e", "transactionId");
        c0e.put("f", "clickedItem");
        MAPPINGS.put("ik", c0e);

        // ── C13PacketPlayerAbilities (iq) ────────────────────────────────────────
        Map<String, String> c13 = new HashMap<>();
        c13.put("a", "invulnerable");
        c13.put("b", "flying");
        c13.put("c", "allowFlying");
        c13.put("d", "creativeMode");
        c13.put("e", "flySpeed");
        c13.put("f", "walkSpeed");
        MAPPINGS.put("iq", c13);

        // ── C17PacketCustomPayload (im) ───────────────────────────────────────────
        Map<String, String> c17 = new HashMap<>();
        c17.put("a", "channel");
        c17.put("b", "data");
        MAPPINGS.put("im", c17);

        // ══════════════════════════════════════════════════════════════════════════
        // INBOUND (Server → Client)
        // ══════════════════════════════════════════════════════════════════════════

        // ── S00PacketKeepAlive (gn) ───────────────────────────────────────────────
        Map<String, String> s00ka = new HashMap<>();
        s00ka.put("a", "keepAliveId");
        MAPPINGS.put("gn", s00ka);

        // ── S01PacketJoinGame (gt) ────────────────────────────────────────────────
        Map<String, String> s01join = new HashMap<>();
        s01join.put("a", "entityId");
        s01join.put("b", "gameType");
        s01join.put("c", "hardcoreMode");
        s01join.put("d", "worldType");
        s01join.put("e", "difficulty");
        s01join.put("f", "reducedDebugInfo");
        s01join.put("g", "maxPlayers");
        MAPPINGS.put("gt", s01join);

        // ── S02PacketChat (fy) ────────────────────────────────────────────────────
        Map<String, String> s02chat = new HashMap<>();
        s02chat.put("a", "chatComponent");
        s02chat.put("b", "type");
        MAPPINGS.put("fy", s02chat);

        // ── S03PacketTimeUpdate (hu) ──────────────────────────────────────────────
        Map<String, String> s03time = new HashMap<>();
        s03time.put("a", "totalWorldTime");
        s03time.put("b", "worldTime");
        MAPPINGS.put("hu", s03time);

        // ── S04PacketEntityEquipment (hn) ─────────────────────────────────────────
        Map<String, String> s04 = new HashMap<>();
        s04.put("a", "entityId");
        s04.put("b", "equipmentSlot");
        s04.put("c", "itemStack");
        MAPPINGS.put("hn", s04);

        // ── S05PacketSpawnPosition (ht) ───────────────────────────────────────────
        Map<String, String> s05 = new HashMap<>();
        s05.put("a", "spawnPos");
        MAPPINGS.put("ht", s05);

        // ── S06PacketUpdateHealth (hp) ────────────────────────────────────────────
        Map<String, String> s06 = new HashMap<>();
        s06.put("a", "health");
        s06.put("b", "foodLevel");
        s06.put("c", "saturationLevel");
        MAPPINGS.put("hp", s06);

        // ── S07PacketRespawn (he) ─────────────────────────────────────────────────
        Map<String, String> s07 = new HashMap<>();
        s07.put("a", "dimension");
        s07.put("b", "difficulty");
        s07.put("c", "gameType");
        s07.put("d", "worldType");
        MAPPINGS.put("he", s07);

        // ── S08PacketPlayerPosLook (fi) ───────────────────────────────────────────
        Map<String, String> s08 = new HashMap<>();
        s08.put("a", "x");
        s08.put("b", "y");
        s08.put("c", "z");
        s08.put("d", "yaw");
        s08.put("e", "pitch");
        s08.put("f", "flags");
        MAPPINGS.put("fi", s08);

        // ── S09PacketHeldItemChange (hi) ──────────────────────────────────────────
        Map<String, String> s09 = new HashMap<>();
        s09.put("a", "heldItemHotbarIndex");
        MAPPINGS.put("hii", s09); // note: hi conflicts with S19PacketEntityHeadLook

        // ── S12PacketEntityVelocity (hm) ─────────────────────────────────────────
        Map<String, String> s12 = new HashMap<>();
        s12.put("a", "entityId");
        s12.put("b", "motionX");
        s12.put("c", "motionY");
        s12.put("d", "motionZ");
        MAPPINGS.put("hm", s12);

        // ── S13PacketDestroyEntities (hb) ────────────────────────────────────────
        Map<String, String> s13 = new HashMap<>();
        s13.put("a", "entityIds");
        MAPPINGS.put("hb", s13);

        // ── S14PacketEntity (gv) – base + subclasses ─────────────────────────────
        Map<String, String> s14 = new HashMap<>();
        s14.put("a", "entityId");
        s14.put("b", "posX");
        s14.put("c", "posY");
        s14.put("d", "posZ");
        s14.put("e", "yaw");
        s14.put("f", "pitch");
        s14.put("g", "onGround");
        s14.put("h", "isMoving");
        s14.put("i", "isRotating");
        MAPPINGS.put("gv", s14);
        MAPPINGS.put("gv$a", s14); // S15PacketEntityRelMove
        MAPPINGS.put("gv$b", s14); // S17PacketEntityLookMove
        MAPPINGS.put("gv$c", s14); // S16PacketEntityLook

        // ── S18PacketEntityTeleport (hz) ─────────────────────────────────────────
        Map<String, String> s18 = new HashMap<>();
        s18.put("a", "entityId");
        s18.put("b", "posX");
        s18.put("c", "posY");
        s18.put("d", "posZ");
        s18.put("e", "yaw");
        s18.put("f", "pitch");
        s18.put("g", "onGround");
        MAPPINGS.put("hz", s18);

        // ── S19PacketEntityHeadLook (hf) ─────────────────────────────────────────
        Map<String, String> s19head = new HashMap<>();
        s19head.put("a", "entityId");
        s19head.put("b", "yaw");
        MAPPINGS.put("hf", s19head);

        // ── S1CPacketEntityMetadata (hk) ─────────────────────────────────────────
        Map<String, String> s1c = new HashMap<>();
        s1c.put("a", "entityId");
        s1c.put("b", "metadata");
        MAPPINGS.put("hk", s1c);

        // ── S1FPacketSetExperience (ho) ───────────────────────────────────────────
        Map<String, String> s1f = new HashMap<>();
        s1f.put("a", "experienceBar");
        s1f.put("b", "totalExperience");
        s1f.put("c", "level");
        MAPPINGS.put("ho", s1f);

        // ── S20PacketEntityProperties (ia) ───────────────────────────────────────
        Map<String, String> s20 = new HashMap<>();
        s20.put("a", "entityId");
        s20.put("b", "props");
        MAPPINGS.put("ia", s20);

        // ── S21PacketChunkData (go) ───────────────────────────────────────────────
        Map<String, String> s21 = new HashMap<>();
        s21.put("a", "chunkX");
        s21.put("b", "chunkZ");
        s21.put("c", "fullChunk");
        s21.put("d", "extractedSize");
        s21.put("e", "biomeArray");
        s21.put("f", "chunkData");
        MAPPINGS.put("go", s21);

        // ── S25PacketBlockBreakAnim (fs) ──────────────────────────────────────────
        Map<String, String> s25 = new HashMap<>();
        s25.put("a", "breakerId");
        s25.put("b", "position");
        s25.put("c", "destroyStage");
        MAPPINGS.put("fs", s25);

        // ── S27PacketExplosion (gk) ───────────────────────────────────────────────
        Map<String, String> s27 = new HashMap<>();
        s27.put("a", "posX");
        s27.put("b", "posY");
        s27.put("c", "posZ");
        s27.put("d", "strength");
        s27.put("e", "affectedBlockPositions");
        s27.put("f", "playerX");
        s27.put("g", "playerY");
        s27.put("h", "playerZ");
        MAPPINGS.put("gk", s27);

        // ── S29PacketSoundEffect (gs) ─────────────────────────────────────────────
        Map<String, String> s29 = new HashMap<>();
        s29.put("a", "soundName");
        s29.put("b", "posX");
        s29.put("c", "posY");
        s29.put("d", "posZ");
        s29.put("e", "volume");
        s29.put("f", "pitch");
        MAPPINGS.put("gs", s29);

        // ── S2FPacketSetSlot (gf) ─────────────────────────────────────────────────
        Map<String, String> s2f = new HashMap<>();
        s2f.put("a", "windowId");
        s2f.put("b", "slot");
        s2f.put("c", "item");
        MAPPINGS.put("gf", s2f);

        // ── S39PacketPlayerAbilities (gx) ────────────────────────────────────────
        Map<String, String> s39 = new HashMap<>();
        s39.put("a", "invulnerable");
        s39.put("b", "flying");
        s39.put("c", "allowFlying");
        s39.put("d", "creativeMode");
        s39.put("e", "flySpeed");
        s39.put("f", "walkSpeed");
        MAPPINGS.put("gx", s39);

        // ── S41PacketServerDifficulty (fw) ───────────────────────────────────────
        Map<String, String> s41 = new HashMap<>();
        s41.put("a", "difficulty");
        s41.put("b", "difficultyLocked");
        MAPPINGS.put("fw", s41);
    }

    /**
     * Looks up the human-readable name for a field in a given (obfuscated) class.
     *
     * @param simpleClassName Simple name of the obfuscated class (e.g. "ip",
     *                        "ip$a")
     * @param obfFieldName    The obfuscated field name (e.g. "a")
     * @return Human-readable name if mapped, otherwise the original obfFieldName
     */
    public static String resolve(String simpleClassName, String obfFieldName) {
        Map<String, String> fields = MAPPINGS.get(simpleClassName);
        if (fields == null)
            return obfFieldName;
        return fields.getOrDefault(obfFieldName, obfFieldName);
    }
}
