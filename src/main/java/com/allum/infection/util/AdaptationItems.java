package com.allum.infection.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;

/**
 * Предметы грибной цепочки крафта: Грибовик -&gt; Грибная спора -&gt; (через
 * зельеварку, полностью на собственной логике плагина — см.
 * {@link com.allum.infection.brewing.CustomBrewManager}) -&gt; Зелье адаптации.
 * Все на ванильных материалах и текстурах — различаются только именем/лором/
 * меткой в PersistentDataContainer, поэтому полностью корректно отображаются
 * на Bedrock через Geyser.
 * <p>
 * Материалы Грибовика и Грибной споры выбраны так, чтобы у них не было
 * своего ванильного рецепта крафта (иначе наш рецепт конфликтовал бы с
 * существующим), а сопоставление в обычных крафтах идёт по
 * {@link org.bukkit.inventory.RecipeChoice.ExactChoice} — "найденный в мире"
 * ванильный предмет того же материала без нашей метки не подойдёт.
 */
public final class AdaptationItems {

    private AdaptationItems() {
    }

    // ------------------------------------------------------------ Грибовик

    public static ItemStack createGribovik() {
        ItemStack item = new ItemStack(Material.MUSHROOM_STEM);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Грибовик", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Спрессованная грибница.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Из девяти таких получается грибная спора.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(Keys.GRIBOVIK_ITEM, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isGribovik(ItemStack item) {
        return hasFlag(item, Keys.GRIBOVIK_ITEM);
    }

    // -------------------------------------------------------- Грибная спора

    /**
     * Материал должен быть съедобным ({@link Material#isEdible()}) — иначе
     * ПКМ по предмету не запускает поедание вообще (было на SPORE_BLOSSOM —
     * он не еда, а декоративный блок). POISONOUS_POTATO съедобен, не имеет
     * своего рецепта крафта (не конфликтует с нашей цепочкой) и по вкусу
     * подходит теме "опасно есть" — лёгкий шанс ванильного отравления при
     * поедании как раз к месту.
     */
    public static ItemStack createSpore() {
        ItemStack item = new ItemStack(Material.POISONOUS_POTATO);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Грибная спора", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Съешьте, чтобы заразиться.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Или добавьте в зельеварку к мутному зелью.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(Keys.SPORE_ITEM, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSpore(ItemStack item) {
        return hasFlag(item, Keys.SPORE_ITEM);
    }

    // ---------------------------------------------------- Зелье адаптации

    /** Собирает предмет "Зелье адаптации" для указанного вида бутылки. */
    public static ItemStack createAdaptationPotion(Material container) {
        ItemStack item = new ItemStack(container);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.MUNDANE);
        meta.setColor(Color.fromRGB(150, 80, 190));
        meta.displayName(Component.text("Зелье адаптации", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Даёт временную адаптацию к заражённой земле:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("иссушение не действует, но голод — по-прежнему.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(Keys.ADAPTATION_POTION, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isAdaptationPotion(ItemStack item) {
        return hasFlag(item, Keys.ADAPTATION_POTION);
    }

    /**
     * Мутное зелье (в любом из 3 видов бутылки) — валидный вход для варки
     * Зелья адаптации. Явно исключает уже готовое Зелье адаптации (у него
     * тот же базовый тип MUNDANE, иначе оно засчиталось бы как вход для
     * повторной варки самого себя).
     */
    public static boolean isMundanePotion(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (item.getType() != Material.POTION
                && item.getType() != Material.SPLASH_POTION
                && item.getType() != Material.LINGERING_POTION) {
            return false;
        }
        if (isAdaptationPotion(item)) {
            return false;
        }
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        return meta.getBasePotionType() == PotionType.MUNDANE;
    }

    // ------------------------------------------------------------- Общее

    private static boolean hasFlag(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
