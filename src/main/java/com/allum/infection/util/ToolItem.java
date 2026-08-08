package com.allum.infection.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ToolItem {

    private ToolItem() {
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Жезл заражения", NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("ПКМ по блоку — поставить очаг инфекции", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(Keys.TOOL_ITEM, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(Keys.TOOL_ITEM, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
