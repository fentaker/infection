package com.allum.infection.listeners;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.Set;

public class CombatListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA
    );

    private final InfectionManager manager;
    private final Random random = new Random();

    public CombatListener(InfectionPlugin plugin) {
        this.manager = plugin.getInfectionManager();
    }

    /**
     * Заражённый игрок, умерший от огня/лавы, излечивается от заражения —
     * после респауна статус снят. Другие причины смерти заражение не лечат.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isInfectedPlayer(player)) {
            return;
        }
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage != null && FIRE_CAUSES.contains(lastDamage.getCause())) {
            manager.curePlayer(player);
            player.sendMessage(Component.text("Огонь выжег инфекцию из вашего тела — вы больше не заражены.",
                    NamedTextColor.GOLD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (manager.isResetting()) {
            return;
        }
        LivingEntity dead = event.getEntity();

        if (manager.isInfected(dead)) {
            // обычный дроп уже в event.getDrops(); добавляем грибы
            Material mushroom = random.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM;
            event.getDrops().add(new ItemStack(mushroom, 1 + random.nextInt(2)));
            return;
        }

        if (dead instanceof Player) {
            return; // мицелий на месте смерти появляется только от мобов/животных
        }

        EntityDamageEvent lastDamage = dead.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof LivingEntity killer
                && manager.isInfected(killer)) {
            // Заражённый убийца — будь то заражённый моб или заражённый игрок —
            // оставляет мицелий под жертвой (прямой урон в ближнем бою).
            manager.tryPlaceDeathMycelium(dead.getLocation());
        }
    }
}
