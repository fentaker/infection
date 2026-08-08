package com.allum.infection.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * Неизменяемый идентификатор позиции блока (мир + координаты).
 * Используется как ключ в HashMap/HashSet — не хранит ссылку на World,
 * чтобы не мешать выгрузке мира, только UUID.
 */
public record BlockKey(UUID worldId, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location loc) {
        return new BlockKey(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Возвращает Block для этого ключа, либо null если мир не загружен.
     */
    public Block toBlock() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    public String serialize() {
        return worldId + ";" + x + ";" + y + ";" + z;
    }

    public static BlockKey deserialize(String raw) {
        String[] parts = raw.split(";");
        return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
