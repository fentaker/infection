package com.allum.infection.tasks;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.model.BlockKey;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Раз в small-tick-seconds пытается вырастить маленький гриб на случайном
 * заражённом блоке; раз в giant-tick-seconds — гигантский гриб.
 */
public class MushroomTask extends BukkitRunnable {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;
    private final Random random = new Random();
    private int ticksElapsed = 0;

    public MushroomTask(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    @Override
    public void run() {
        if (!manager.isActive()) {
            return;
        }
        ticksElapsed++;

        int smallEvery = plugin.cfgInt("mushrooms.small-tick-seconds", 10);
        if (ticksElapsed % smallEvery == 0) {
            trySmallMushroom();
        }
        int giantEvery = plugin.cfgInt("mushrooms.giant-tick-seconds", 60);
        if (ticksElapsed % giantEvery == 0) {
            tryGiantMushroom();
        }
    }

    private void trySmallMushroom() {
        double chance = plugin.cfgDouble("mushrooms.small-chance", 0.15);
        if (random.nextDouble() > chance) {
            return;
        }
        BlockKey key = manager.randomInfectedKey();
        if (key == null) {
            return;
        }
        Block block = key.toBlock();
        if (block == null) {
            return;
        }
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() != Material.AIR) {
            return;
        }
        Material mushroom = random.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM;
        above.setType(mushroom, false);
    }

    private void tryGiantMushroom() {
        double chance = plugin.cfgDouble("mushrooms.giant-chance", 0.03);
        if (random.nextDouble() > chance) {
            return;
        }
        BlockKey key = manager.randomInfectedKey();
        if (key == null) {
            return;
        }
        Block block = key.toBlock();
        if (block == null) {
            return;
        }
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() != Material.AIR) {
            return;
        }
        TreeType type = random.nextBoolean() ? TreeType.RED_MUSHROOM : TreeType.BROWN_MUSHROOM;
        block.getWorld().generateTree(above.getLocation(), random, type);
    }
}
