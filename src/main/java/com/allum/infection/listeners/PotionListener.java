package com.allum.infection.listeners;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.brewing.CustomBrewManager;
import com.allum.infection.util.AdaptationItems;
import com.allum.infection.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Обрабатывает всё, что связано с грибной спорой и Зельем адаптации:
 * — поедание сырой споры заражает игрока;
 * — питьё/попадание Зелья адаптации выдаёт временный баф (иссушение от
 *   инфекции не действует, голод — по-прежнему действует);
 * — варка Зелья адаптации отслеживается через {@link CustomBrewManager}
 *   (полностью собственная логика, не зависящая от ванильного распознавания
 *   рецепта зельеварения — см. подробности в этом классе).
 */
public class PotionListener implements Listener {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;
    private final CustomBrewManager brewManager;

    public PotionListener(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
        this.brewManager = plugin.getCustomBrewManager();
    }

    // ------------------------------------------------------------- Варка
    //
    // Зельеварка сама по себе НЕ распознаёт нашу комбинацию (Грибная спора +
    // Мутное зелье) как валидный рецепт — она используется лишь как обычный
    // контейнер с топливом. Реальную варку полностью ведёт CustomBrewManager:
    // эти обработчики лишь сообщают ему, когда стоит пересчитать состояние
    // конкретной зельеварки (сразу после открытия/изменения содержимого),
    // а страхует всё периодический тик самого CustomBrewManager.

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Block block = brewerBlock(event.getInventory());
        if (block != null) {
            brewManager.watch(block);
            brewManager.recheck(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Block block = brewerBlock(event.getView().getTopInventory());
        if (block != null) {
            // содержимое инвентаря на момент события ещё не обновлено кликом —
            // пересчитываем на следующем тике, когда клик уже применится
            Bukkit.getScheduler().runTask(plugin, () -> brewManager.recheck(block));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Block block = brewerBlock(event.getView().getTopInventory());
        if (block != null) {
            Bukkit.getScheduler().runTask(plugin, () -> brewManager.recheck(block));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Block block = brewerBlock(event.getInventory());
        if (block != null) {
            brewManager.recheck(block);
        }
    }

    // Подача ингредиентов/зелий через воронку в автоматизированную зельеварку
    @EventHandler(ignoreCancelled = true)
    public void onHopperMoveItem(InventoryMoveItemEvent event) {
        Block block = brewerBlock(event.getDestination());
        if (block != null) {
            Bukkit.getScheduler().runTask(plugin, () -> brewManager.recheck(block));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewingStandBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.BREWING_STAND) {
            brewManager.forget(event.getBlock());
        }
    }

    private Block brewerBlock(Inventory inventory) {
        if (!(inventory instanceof BrewerInventory)) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BrewingStand standState) {
            return standState.getBlock();
        }
        return null;
    }

    // --------------------------------------------------------- Поедание/питьё

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (AdaptationItems.isSpore(item)) {
            infectPlayer(player);
            return;
        }
        if (AdaptationItems.isAdaptationPotion(item)) {
            grantBuff(player);
        }
    }

    // ------------------------------------------------------------ Сплэш

    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion thrown = event.getPotion();
        if (!AdaptationItems.isAdaptationPotion(thrown.getItem())) {
            return;
        }
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player player) {
                grantBuff(player);
            }
        }
    }

    // Помечаем облако долгоиграющего зелья, чтобы позже узнать его в AreaEffectCloudApplyEvent
    @EventHandler(ignoreCancelled = true)
    public void onLingeringSplash(LingeringPotionSplashEvent event) {
        if (!AdaptationItems.isAdaptationPotion(event.getEntity().getItem())) {
            return;
        }
        event.getAreaEffectCloud().getPersistentDataContainer()
                .set(Keys.ADAPTATION_CLOUD, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloudApply(AreaEffectCloudApplyEvent event) {
        Byte flag = event.getEntity().getPersistentDataContainer().get(Keys.ADAPTATION_CLOUD, PersistentDataType.BYTE);
        if (flag == null || flag != (byte) 1) {
            return;
        }
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player player) {
                grantBuff(player);
            }
        }
    }

    // ------------------------------------------------------------ Общее

    private void infectPlayer(Player player) {
        if (manager.isInfectedPlayer(player)) {
            return;
        }
        manager.markPlayerInfected(player);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.7f);
        player.sendMessage(Component.text("Вы заражены... грибница теперь часть вас.", NamedTextColor.DARK_PURPLE));
    }

    private void grantBuff(Player player) {
        long seconds = plugin.cfgInt("potion.adaptation-buff-minutes", 5) * 60L;
        manager.grantAdaptationBuff(player, seconds);
        player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, player.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        player.sendMessage(Component.text("Адаптация активна: иссушение от заражённой земли вам не страшно.",
                NamedTextColor.LIGHT_PURPLE));
    }
}
