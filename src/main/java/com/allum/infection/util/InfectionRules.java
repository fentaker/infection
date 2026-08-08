package com.allum.infection.util;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Правила заражения блоков: какие материалы никогда не заражаются,
 * и переводы типов мобов для отображаемых имён заражённых существ.
 */
public final class InfectionRules {

    private static final Set<Material> IMMUNE = new HashSet<>();
    private static final Map<EntityType, String> MOB_NAMES = new HashMap<>();

    static {
        IMMUNE.add(Material.OBSIDIAN);
        IMMUNE.add(Material.CRYING_OBSIDIAN);
        IMMUNE.add(Material.BEDROCK);

        // Блоки-контейнеры — сохраняем инвентари
        IMMUNE.add(Material.CHEST);
        IMMUNE.add(Material.TRAPPED_CHEST);
        IMMUNE.add(Material.BARREL);
        IMMUNE.add(Material.FURNACE);
        IMMUNE.add(Material.BLAST_FURNACE);
        IMMUNE.add(Material.SMOKER);
        IMMUNE.add(Material.HOPPER);
        IMMUNE.add(Material.DISPENSER);
        IMMUNE.add(Material.DROPPER);
        IMMUNE.add(Material.BREWING_STAND);
        IMMUNE.add(Material.ENDER_CHEST);
        IMMUNE.add(Material.LECTERN);

        for (Material m : Material.values()) {
            if (m.name().endsWith("SHULKER_BOX")) {
                IMMUNE.add(m);
            }
        }

        MOB_NAMES.put(EntityType.COW, "Заражённая корова");
        MOB_NAMES.put(EntityType.CHICKEN, "Заражённая курица");
        MOB_NAMES.put(EntityType.PIG, "Заражённая свинья");
        MOB_NAMES.put(EntityType.SHEEP, "Заражённая овца");
        MOB_NAMES.put(EntityType.HORSE, "Заражённая лошадь");
        MOB_NAMES.put(EntityType.WOLF, "Заражённый волк");
        MOB_NAMES.put(EntityType.CAT, "Заражённый кот");
        MOB_NAMES.put(EntityType.RABBIT, "Заражённый кролик");
        MOB_NAMES.put(EntityType.VILLAGER, "Заражённый житель");
        MOB_NAMES.put(EntityType.ZOMBIE, "Заражённый зомби");
        MOB_NAMES.put(EntityType.SKELETON, "Заражённый скелет");
        MOB_NAMES.put(EntityType.SPIDER, "Заражённый паук");
        MOB_NAMES.put(EntityType.CREEPER, "Заражённый крипер");
        MOB_NAMES.put(EntityType.FOX, "Заражённая лиса");
        MOB_NAMES.put(EntityType.WOLF, "Заражённый волк");
        MOB_NAMES.put(EntityType.PANDA, "Заражённая панда");
        MOB_NAMES.put(EntityType.TURTLE, "Заражённая черепаха");
        MOB_NAMES.put(EntityType.LLAMA, "Заражённая лама");
        MOB_NAMES.put(EntityType.GOAT, "Заражённый козёл");
        MOB_NAMES.put(EntityType.POLAR_BEAR, "Заражённый белый медведь");
        MOB_NAMES.put(EntityType.BEE, "Заражённая пчела");
    }

    private InfectionRules() {
    }

    /** Может ли материал быть заражён (твёрдый блок, не в списке иммунных). */
    public static boolean canInfect(Material material) {
        if (material == null || !material.isSolid()) {
            return false;
        }
        return !IMMUNE.contains(material);
    }

    public static String infectedDisplayName(EntityType type) {
        return MOB_NAMES.getOrDefault(type, "Заражённое существо");
    }
}
