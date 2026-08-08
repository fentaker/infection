package com.allum.infection.listeners;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.model.BlockKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;

/**
 * Любой источник огня (кремень-и-сталь, лава, файербол, горящая стрела),
 * оказавшийся на заражённом блоке — через 5 сек возвращает конкретно этот
 * блок в исходный материал. Цепной реакции по территории не запускает.
 */
public class FireListener implements Listener {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;

    public FireListener(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block fireBlock = event.getBlock();
        Block below = fireBlock.getRelative(BlockFace.DOWN);
        scheduleRevertIfInfected(below);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLavaFlow(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.LAVA) {
            return;
        }
        Block below = event.getToBlock().getRelative(BlockFace.DOWN);
        scheduleRevertIfInfected(below);
    }

    private void scheduleRevertIfInfected(Block block) {
        if (!manager.isInfected(block)) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        long delayTicks = plugin.cfgInt("fire.revert-delay-seconds", 5) * 20L;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!manager.isInfected(key)) {
                return; // уже снято (например через /infection kill)
            }
            Block current = key.toBlock();
            if (current != null) {
                current.getWorld().spawnParticle(Particle.LARGE_SMOKE, current.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0.0);
            }
            manager.revertBlock(key);
        }, delayTicks);
    }
}
