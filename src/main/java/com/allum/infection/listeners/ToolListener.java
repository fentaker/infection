package com.allum.infection.listeners;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.tasks.SpreadTask;
import com.allum.infection.util.InfectionRules;
import com.allum.infection.util.ToolItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ToolListener implements Listener {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;

    public ToolListener(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!ToolItem.isTool(event.getItem())) {
            return;
        }
        if (!event.getPlayer().hasPermission("allum.infection.admin")) {
            return;
        }
        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (!InfectionRules.canInfect(block.getType())) {
            event.getPlayer().sendMessage(Component.text("Этот блок нельзя заразить.", NamedTextColor.RED));
            return;
        }

        boolean wasActive = manager.isActive();
        boolean infected = manager.infectBlock(block);
        if (!infected) {
            event.getPlayer().sendMessage(Component.text("Этот блок уже заражён.", NamedTextColor.YELLOW));
            return;
        }

        event.getPlayer().sendMessage(Component.text("Очаг инфекции установлен.", NamedTextColor.GREEN));
        plugin.getDataStore().save(manager);

        if (!wasActive) {
            new SpreadTask(plugin).startIfNeeded();
        }
    }
}
