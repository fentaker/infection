package com.allum.infection.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Держатель NamespacedKey-констант для PersistentDataContainer.
 * Инициализируется один раз при старте плагина.
 */
public final class Keys {

    public static NamespacedKey INFECTED_MOB;      // byte-флаг: моб/игрок заражён
    public static NamespacedKey INFECTION_TIME;     // long: timestamp заражения (millis, только мобы)
    public static NamespacedKey TOOL_ITEM;           // byte-флаг: предмет — жезл заражения

    public static NamespacedKey GRIBOVIK_ITEM;       // byte-флаг: предмет "Грибовик"
    public static NamespacedKey SPORE_ITEM;          // byte-флаг: предмет "Грибная спора"
    public static NamespacedKey ADAPTATION_POTION;   // byte-флаг: предмет "Зелье адаптации" (любой из 3 видов бутылок)
    public static NamespacedKey ADAPTATION_CLOUD;    // byte-флаг: облако долгоиграющего "Зелья адаптации"

    private Keys() {
    }

    public static void init(Plugin plugin) {
        INFECTED_MOB = new NamespacedKey(plugin, "infected_mob");
        INFECTION_TIME = new NamespacedKey(plugin, "infection_time");
        TOOL_ITEM = new NamespacedKey(plugin, "infection_tool");

        GRIBOVIK_ITEM = new NamespacedKey(plugin, "gribovik_item");
        SPORE_ITEM = new NamespacedKey(plugin, "spore_item");
        ADAPTATION_POTION = new NamespacedKey(plugin, "adaptation_potion");
        ADAPTATION_CLOUD = new NamespacedKey(plugin, "adaptation_cloud");
    }
}
