package io.th0rgal.oraxen.mechanics.provided.gameplay.custom_block.noteblock;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.custom_block.noteblock.OraxenNoteBlockInteractEvent;
import io.th0rgal.oraxen.mechanics.provided.gameplay.custom_block.CustomBlockHelpers;
import io.th0rgal.oraxen.mechanics.provided.gameplay.storage.StorageMechanic;
import io.th0rgal.oraxen.utils.BlockHelpers;
import io.th0rgal.oraxen.utils.EventUtils;
import io.th0rgal.oraxen.utils.VersionUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static io.th0rgal.oraxen.utils.BlockHelpers.isLoaded;

public class NoteBlockMechanicListener implements Listener {
    public static class NoteBlockMechanicPaperListener implements Listener {

        @EventHandler
        public void onFallingBlockLandOnCarpet(EntityRemoveFromWorldEvent event) {
            if (!(event.getEntity() instanceof FallingBlock fallingBlock)) return;
            NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(fallingBlock.getBlockData());
            if (mechanic == null || Objects.equals(OraxenBlocks.getCustomBlockMechanic(fallingBlock.getLocation()), mechanic))
                return;
            if (mechanic.isDirectional() && !mechanic.directional().isParentBlock())
                mechanic = mechanic.directional().getParentMechanic();

            ItemStack itemStack = OraxenItems.getItemById(mechanic.getItemID()).build();
            fallingBlock.setDropItem(false);
            fallingBlock.getWorld().dropItemNaturally(fallingBlock.getLocation(), itemStack);
        }
    }

    public static class NoteBlockMechanicPhysicsListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPistonPush(BlockPistonExtendEvent event) {
            if (event.getBlocks().stream().anyMatch(block -> block.getType().equals(Material.NOTE_BLOCK)))
                event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPistonPull(BlockPistonRetractEvent event) {
            if (event.getBlocks().stream().anyMatch(block -> block.getType().equals(Material.NOTE_BLOCK)))
                event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onBlockPhysics(final BlockPhysicsEvent event) {
            final Block block = event.getBlock();
            final Block aboveBlock = block.getRelative(BlockFace.UP);
            final Block belowBlock = block.getRelative(BlockFace.DOWN);
            // If block below is NoteBlock, it will be affected by the break
            // Call updateAndCheck from it to fix vertical stack of NoteBlocks
            // if belowBlock is not a NoteBlock we must ensure the above is not, if it is call updateAndCheck from block
            if (belowBlock.getType() == Material.NOTE_BLOCK) {
                event.setCancelled(true);
                updateAndCheck(belowBlock);
            } else if (aboveBlock.getType() == Material.NOTE_BLOCK) {
                event.setCancelled(true);
                updateAndCheck(aboveBlock);
            }
            if (block.getType() == Material.NOTE_BLOCK) {
                event.setCancelled(true);
                updateAndCheck(block);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onNoteblockPowered(final GenericGameEvent event) {
            Block block = event.getLocation().getBlock();

            Location eLoc = block.getLocation();
            if (!isLoaded(event.getLocation()) || !isLoaded(eLoc)) return;

            // This GameEvent only exists in 1.19
            // If server is 1.18 check if its there and if not return
            // If 1.19 we can check if this event is fired
            if (!VersionUtil.atOrAbove("1.19")) return;
            if (event.getEvent() != GameEvent.NOTE_BLOCK_PLAY) return;
            if (block.getType() != Material.NOTE_BLOCK) return;
            NoteBlock data = (NoteBlock) block.getBlockData().clone();
            Bukkit.getScheduler().runTaskLater(OraxenPlugin.get(), () -> block.setBlockData(data, false), 1L);
        }

        public void updateAndCheck(Block block) {
            final Block blockAbove = block.getRelative(BlockFace.UP);
            if (blockAbove.getType() == Material.NOTE_BLOCK)
                blockAbove.getState().update(true, true);
            Block nextBlock = blockAbove.getRelative(BlockFace.UP);
            if (nextBlock.getType() == Material.NOTE_BLOCK) updateAndCheck(blockAbove);
        }
    }

    // TODO Make this function less of a clusterfuck and more readable
    // Make sure this isnt handling it together with above when placing CB against CB
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceAgainstNoteBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        if (hand == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || block.getType() != Material.NOTE_BLOCK) return;
        if (!player.isSneaking() && BlockHelpers.isInteractable(block)) return;
        if (event.useInteractedBlock() == Event.Result.DENY || !OraxenBlocks.isOraxenNoteBlock(block)) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        if (OraxenBlocks.isOraxenNoteBlock(item)) return;
        if (item == null) return;

        Material type = item.getType();
        if (type == Material.AIR) return;

        BlockData newData = type.isBlock() ? type.createBlockData() : null;
        CustomBlockHelpers.makePlayerPlaceBlock(player, event.getHand(), item, block, event.getBlockFace(), newData);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(OraxenNoteBlockInteractEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        NoteBlockMechanic mechanic = event.getMechanic();
        StorageMechanic storageMechanic = mechanic.storage();
        if (storageMechanic == null) return;

        switch (storageMechanic.getStorageType()) {
            case STORAGE, SHULKER -> storageMechanic.openStorage(block, player);
            case PERSONAL -> storageMechanic.openPersonalStorage(player, block.getLocation(), null);
            case DISPOSAL -> storageMechanic.openDisposal(player, block.getLocation(), null);
            case ENDERCHEST -> player.openInventory(player.getEnderChest());
        }
        event.setCancelled(true);
    }

    // If block is not a custom block, play the correct sound according to the below block or default
    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotePlayed(final NotePlayEvent event) {
        if (event.getInstrument() != Instrument.PIANO) event.setCancelled(true);
        else {
            if (instrumentMap.isEmpty()) instrumentMap = getInstrumentMap();
            String blockType = event.getBlock().getRelative(BlockFace.DOWN).getType().toString().toLowerCase();
            Instrument fakeInstrument = instrumentMap.entrySet().stream().filter(e -> e.getValue().contains(blockType)).map(Map.Entry::getKey).findFirst().orElse(Instrument.PIANO);
            // This is deprecated, but seems to be without reason
            event.setInstrument(fakeInstrument);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFallingOraxenBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock fallingBlock) {
            BlockData blockData = fallingBlock.getBlockData();
            NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(blockData);
            if (mechanic == null) return;
            OraxenBlocks.place(mechanic.getItemID(), event.getBlock().getLocation());
            fallingBlock.setDropItem(false);
        }
    }

    @EventHandler
    public void onBreakBeneathFallingOraxenBlock(BlockBreakEvent event) {
        NoteMechanicHelpers.handleFallingOraxenBlockAbove(event.getBlock());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSetFire(final PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || block.getType() != Material.NOTE_BLOCK) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getBlockFace() != BlockFace.UP) return;
        if (item == null) return;

        NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(block);
        if (mechanic == null) return;
        if (!mechanic.canIgnite()) return;
        if (item.getType() != Material.FLINT_AND_STEEL && item.getType() != Material.FIRE_CHARGE) return;

        EventUtils.callEvent(new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCatchFire(final BlockIgniteEvent event) {
        Block block = event.getBlock();
        NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(block);
        if (mechanic == null) return;
        if (!mechanic.canIgnite()) event.setCancelled(true);
        else {
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1, 1);
            block.getRelative(BlockFace.UP).setType(Material.FIRE);
        }
    }

    // Used to determine what instrument to use when playing a note depending on below block
    public static Map<Instrument, List<String>> instrumentMap = new HashMap<>();

    private static Map<Instrument, List<String>> getInstrumentMap() {
        Map<Instrument, List<String>> map = new HashMap<>();
        map.put(Instrument.BELL, List.of("gold_block"));
        map.put(Instrument.BASS_DRUM, Arrays.asList("stone", "netherrack", "bedrock", "observer", "coral", "obsidian", "anchor", "quartz"));
        map.put(Instrument.FLUTE, List.of("clay"));
        map.put(Instrument.CHIME, List.of("packed_ice"));
        map.put(Instrument.GUITAR, List.of("wool"));
        map.put(Instrument.XYLOPHONE, List.of("bone_block"));
        map.put(Instrument.IRON_XYLOPHONE, List.of("iron_block"));
        map.put(Instrument.COW_BELL, List.of("soul_sand"));
        map.put(Instrument.DIDGERIDOO, List.of("pumpkin"));
        map.put(Instrument.BIT, List.of("emerald_block"));
        map.put(Instrument.BANJO, List.of("hay_bale"));
        map.put(Instrument.PLING, List.of("glowstone"));
        map.put(Instrument.BASS_GUITAR, List.of("wood"));
        map.put(Instrument.SNARE_DRUM, Arrays.asList("sand", "gravel", "concrete_powder", "soul_soil"));
        map.put(Instrument.STICKS, Arrays.asList("glass", "sea_lantern", "beacon"));

        return map;
    }
}