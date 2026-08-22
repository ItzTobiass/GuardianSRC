package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaffoldCheck {

    private static final int    WINDOW_MS             = 1000;
    private static final int    MAX_PLACE_PER_SEC     = 8;
    private static final int    BUFFER_MAX            = 5;
    private static final double MAX_REACH             = 6.0;
    private static final float  ROTATION_PITCH_MAX    = 52.0f;

    private static final Set<Material> ITEM_WHITELIST = EnumSet.of(
            Material.END_CRYSTAL,
            Material.ARMOR_STAND,
            Material.WIND_CHARGE,
            Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME,
            Material.PAINTING,
            Material.FLINT_AND_STEEL,
            Material.BONE_MEAL,
            Material.BUCKET,
            Material.WATER_BUCKET,
            Material.LAVA_BUCKET,
            Material.POWDER_SNOW_BUCKET,
            Material.OAK_BOAT, Material.SPRUCE_BOAT, Material.BIRCH_BOAT,
            Material.JUNGLE_BOAT, Material.ACACIA_BOAT, Material.DARK_OAK_BOAT,
            Material.MANGROVE_BOAT, Material.CHERRY_BOAT, Material.BAMBOO_RAFT,
            Material.MINECART, Material.CHEST_MINECART, Material.HOPPER_MINECART,
            Material.TNT_MINECART, Material.FURNACE_MINECART
    );

    private static final Set<Material> BLOCK_BLACKLIST = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.BARREL, Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CRAFTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL, Material.ENCHANTING_TABLE, Material.BEACON,
            Material.DISPENSER, Material.DROPPER, Material.HOPPER,
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.IRON_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            Material.IRON_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON,
            Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON, Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
            Material.WHITE_BED, Material.ORANGE_BED,
            Material.MAGENTA_BED, Material.LIGHT_BLUE_BED, Material.YELLOW_BED,
            Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
            Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED,
            Material.BLUE_BED, Material.BROWN_BED, Material.GREEN_BED,
            Material.RED_BED, Material.BLACK_BED,
            Material.CAKE, Material.COMPOSTER, Material.GRINDSTONE,
            Material.LOOM, Material.CARTOGRAPHY_TABLE, Material.STONECUTTER,
            Material.NOTE_BLOCK, Material.JUKEBOX, Material.RESPAWN_ANCHOR,
            Material.DAYLIGHT_DETECTOR
    );

    private final Map<UUID, Deque<Long>> placeTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>     speedBuffer     = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>     invalidBuffer   = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>     rotBuffer       = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (isExempt(player)) return;

        WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
        InteractionHand hand = wrapper.getHand();

        if (hand != InteractionHand.MAIN_HAND) return;

        Material held = player.getInventory().getItemInMainHand().getType();

        if (ITEM_WHITELIST.contains(held)) return;

        Vector3i blockPos = wrapper.getBlockPosition();

        Block clickedBlock = player.getWorld().getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        Material clickedType = clickedBlock.getType();

        if (BLOCK_BLACKLIST.contains(clickedType)) return;
        if (!clickedType.isSolid()) return;

        double eyeX   = player.getEyeLocation().getX();
        double eyeY   = player.getEyeLocation().getY();
        double eyeZ   = player.getEyeLocation().getZ();
        double blockCX = blockPos.getX() + 0.5;
        double blockCY = blockPos.getY() + 0.5;
        double blockCZ = blockPos.getZ() + 0.5;
        double reach  = Math.sqrt(Math.pow(eyeX - blockCX, 2) + Math.pow(eyeY - blockCY, 2) + Math.pow(eyeZ - blockCZ, 2));

        if (reach > MAX_REACH) return;

        boolean isPlaceable = held != Material.AIR && held.isBlock();
        double  playerY     = player.getLocation().getY();
        int     playerBlockY = (int) Math.floor(playerY);
        boolean belowFeet   = blockPos.getY() <= playerBlockY && blockPos.getY() >= playerBlockY - 3;

        if (!isPlaceable) {
            if (belowFeet) {
                int b = invalidBuffer.getOrDefault(player.getUniqueId(), 0) + 1;
                invalidBuffer.put(player.getUniqueId(), b);
                if (b >= BUFFER_MAX) {
                    AlertUtil.sendAlert(player, "Scaffold[NoItem]", b);
                    invalidBuffer.put(player.getUniqueId(), b / 2);
                }
            }
            return;
        }

        decay(invalidBuffer, player.getUniqueId());

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        if (belowFeet) {
            Deque<Long> timestamps = placeTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
            timestamps.addLast(now);
            pruneWindow(timestamps, now);

            if (timestamps.size() > MAX_PLACE_PER_SEC) {
                int b = speedBuffer.getOrDefault(uuid, 0) + 1;
                speedBuffer.put(uuid, b);
                if (b >= BUFFER_MAX) {
                    AlertUtil.sendAlert(player, "Scaffold[Speed]", b);
                    speedBuffer.put(uuid, b / 2);
                }
            } else {
                decay(speedBuffer, uuid);
            }

            float pitch = player.getLocation().getPitch();
            if (pitch < ROTATION_PITCH_MAX) {
                int b = rotBuffer.getOrDefault(uuid, 0) + 1;
                rotBuffer.put(uuid, b);
                if (b >= BUFFER_MAX) {
                    AlertUtil.sendAlert(player, "Scaffold[Rotation]", b);
                    rotBuffer.put(uuid, b / 2);
                }
            } else {
                decay(rotBuffer, uuid);
            }
        } else {
            decay(speedBuffer, uuid);
            decay(rotBuffer, uuid);
        }
    }

    private void pruneWindow(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
            deque.pollFirst();
        }
    }

    private void decay(Map<UUID, Integer> map, UUID uuid) {
        map.put(uuid, Math.max(0, map.getOrDefault(uuid, 0) - 1));
    }

    private boolean isExempt(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    public void cleanup(UUID uuid) {
        placeTimestamps.remove(uuid);
        speedBuffer.remove(uuid);
        invalidBuffer.remove(uuid);
        rotBuffer.remove(uuid);
    }
}