package com.hades.client.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps obfuscated Minecraft 1.8.9 packet class names to their clear vanilla
 * names.
 */
public class PacketMapper {
    private static final Map<String, String> MAPPINGS = new HashMap<>();

    static {
        // --- OUTBOUND (Client -> Server) ---
        add("ia", "S20PacketEntityProperties"); // Actually mostly inbound but based on user list
        add("ib", "S1DPacketEntityEffect");
        add("ic", "INetHandlerPlayServer");
        add("id", "C14PacketTabComplete");
        add("ie", "C01PacketChatMessage");
        add("ig", "C16PacketClientStatus");
        add("ih", "C15PacketClientSettings");
        add("ii", "C0FPacketConfirmTransaction");
        add("ij", "C11PacketEnchantItem");
        add("ik", "C0EPacketClickWindow");
        add("il", "C0DPacketCloseWindow");
        add("im", "C17PacketCustomPayload");
        add("in", "C02PacketUseEntity");
        add("io", "C00PacketKeepAlive");

        add("ip", "C03PacketPlayer");
        add("ip$a", "C03PacketPlayer$C04PacketPlayerPosition");
        add("ip$b", "C03PacketPlayer$C06PacketPlayerPosLook");
        add("ip$c", "C03PacketPlayer$C05PacketPlayerLook");
        add("is$a", "C0BPacketEntityAction$Action");

        add("iq", "C13PacketPlayerAbilities");
        add("ir", "C07PacketPlayerDigging");
        add("is", "C0BPacketEntityAction");
        add("it", "C0CPacketInput");
        add("iu", "C19PacketResourcePackStatus");
        add("iv", "C09PacketHeldItemChange");
        add("iw", "C10PacketCreativeInventoryAction");
        add("ix", "C12PacketUpdateSign");
        add("iy", "C0APacketAnimation");
        add("iz", "C18PacketSpectate");

        add("ja", "C08PacketPlayerBlockPlacement");
        add("jc", "C00Handshake");
        add("jg", "S02PacketLoginSuccess"); // Corrected typo from user list
        add("jh", "S01PacketEncryptionRequest");
        add("ji", "S03PacketEnableCompression");
        add("jj", "S00PacketDisconnect");
        add("jl", "C00PacketLoginStart");
        add("jm", "C01PacketEncryptionResponse");
        add("jq", "S01PacketPong");
        add("jr", "S00PacketServerInfo");
        add("ju", "C01PacketPing");
        add("jv", "C00PacketServerQuery");

        // --- INBOUND (Server -> Client) ---
        add("fi", "S08PacketPlayerPosLook");
        add("fk", "S0EPacketSpawnObject");
        add("fl", "S11PacketSpawnExperienceOrb");
        add("fm", "S2CPacketSpawnGlobalEntity");
        add("fn", "S0FPacketSpawnMob");
        add("fo", "S10PacketSpawnPainting");
        add("fp", "S0CPacketSpawnPlayer");
        add("fq", "S0BPacketAnimation");
        add("fr", "S37PacketStatistics");
        add("fs", "S25PacketBlockBreakAnim");
        add("ft", "S35PacketUpdateTileEntity");
        add("fu", "S24PacketBlockAction");
        add("fv", "S23PacketBlockChange");
        add("fw", "S41PacketServerDifficulty");
        add("fx", "S3APacketTabComplete");
        add("fy", "S02PacketChat");
        add("fz", "S22PacketMultiBlockChange");

        add("ga", "S32PacketConfirmTransaction");
        add("gb", "S2EPacketCloseWindow");
        add("gc", "S2DPacketOpenWindow");
        add("gd", "S30PacketWindowItems");
        add("ge", "S31PacketWindowProperty");
        add("gf", "S2FPacketSetSlot");
        add("gg", "S3FPacketCustomPayload");
        add("gh", "S40PacketDisconnect");
        add("gi", "S19PacketEntityStatus");
        add("gj", "S49PacketUpdateEntityNBT");
        add("gk", "S27PacketExplosion");
        add("gl", "S46PacketSetCompressionLevel");
        add("gm", "S2BPacketChangeGameState");
        add("gn", "S00PacketKeepAlive");
        add("go", "S21PacketChunkData");
        add("gp", "S26PacketMapChunkBulk");
        add("gq", "S28PacketEffect");
        add("gr", "S2APacketParticles");
        add("gs", "S29PacketSoundEffect");
        add("gt", "S01PacketJoinGame");
        add("gu", "S34PacketMaps");

        add("gv", "S14PacketEntity");
        add("gv$a", "S14PacketEntity$S15PacketEntityRelMove");
        add("gv$b", "S14PacketEntity$S17PacketEntityLookMove");
        add("gv$c", "S14PacketEntity$S16PacketEntityLook");

        add("gw", "S36PacketSignEditorOpen");
        add("gx", "S39PacketPlayerAbilities");
        add("gy", "S42PacketCombatEvent");
        add("gz", "S38PacketPlayerListItem");

        add("ha", "S0APacketUseBed");
        add("hb", "S13PacketDestroyEntities");
        add("hc", "S1EPacketRemoveEntityEffect");
        add("hd", "S48PacketResourcePackSend");
        add("he", "S07PacketRespawn");
        add("hf", "S19PacketEntityHeadLook");
        add("hg", "S44PacketWorldBorder");
        add("hh", "S43PacketCamera");
        add("hi", "S09PacketHeldItemChange");
        add("hj", "S3DPacketDisplayScoreboard");
        add("hk", "S1CPacketEntityMetadata");
        add("hl", "S1BPacketEntityAttach");
        add("hm", "S12PacketEntityVelocity");
        add("hn", "S04PacketEntityEquipment");
        add("ho", "S1FPacketSetExperience");
        add("hp", "S06PacketUpdateHealth");
        add("hq", "S3BPacketScoreboardObjective");
        add("hr", "S3EPacketTeams");
        add("hs", "S3CPacketUpdateScore");
        add("ht", "S05PacketSpawnPosition");
        add("hu", "S03PacketTimeUpdate");
        add("hv", "S45PacketTitle");
        add("hw", "S33PacketUpdateSign");
        add("hx", "S47PacketPlayerListHeaderFooter");
        add("hy", "S0DPacketCollectItem");
        add("hz", "S18PacketEntityTeleport");
    }

    private static void add(String obf, String clear) {
        MAPPINGS.put(obf, clear);
    }

    /**
     * Gets the mapped visual name of a packet based on its class, resolving
     * nested classes like ip$a properly.
     */
    public static String getPacketName(Object packet) {
        if (packet == null)
            return "null";

        Class<?> clazz = packet.getClass();
        String fullName = clazz.getName();

        // Strip out packages formatting to extract the simple class name (handles
        // nested classes e.g., default.package.ip$a -> ip$a)
        int lastDotIndex = fullName.lastIndexOf('.');
        String obfName = (lastDotIndex == -1) ? fullName : fullName.substring(lastDotIndex + 1);

        return MAPPINGS.getOrDefault(obfName, obfName);
    }
}
