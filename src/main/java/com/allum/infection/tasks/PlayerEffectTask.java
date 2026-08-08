package com.allum.infection.tasks;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Раз в секунду обрабатывает эффекты на игроках. Две независимые ветки:
 * <p>
 * — Обычный (незаражённый) игрок: как и раньше — Голод I сразу на заражённой
 * земле, Иссушение I через N секунд, эффекты держатся ещё M секунд после
 * выхода (лингер).
 * <p>
 * — Заражённый игрок (съел грибную спору): на мицелии — Сытость I + Скорость I
 * ВСЕГДА, без дебафов. Вне заражённой земли — сразу (без лингера) снимаются
 * положительные эффекты и выдаются Слабость II + Голод II, через 60 сек
 * добавляется Иссушение II.
 * <p>
 * В обеих ветках Иссушение (визуал и реальный урон) подавляется, пока у
 * игрока активен баф "адаптации" от Зелья адаптации — голод при этом всё
 * равно действует.
 * <p>
 * Урон от Иссушения не полагается на внутренний тик ванильного эффекта (см.
 * историю багфиксов в ТЗ) — ведётся собственный таймер на игрока.
 */
public class PlayerEffectTask extends BukkitRunnable {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;

    // --- Обычные (незаражённые) игроки ---
    private final Map<UUID, Long> enteredAt = new HashMap<>();
    private final Map<UUID, Long> lastSeenOnInfection = new HashMap<>();
    private final Map<UUID, Long> lastWitherDamageAt = new HashMap<>();

    // --- Заражённые игроки ---
    private final Map<UUID, Long> infectedOffZoneSince = new HashMap<>();
    private final Map<UUID, Long> infectedLastWitherDamageAt = new HashMap<>();

    public PlayerEffectTask(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    @Override
    public void run() {
        manager.purgeExpiredAdaptationBuffs();

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean onInfection = manager.isActive() && isOnInfectedGround(player);
            boolean infected = manager.isInfectedPlayer(player);
            boolean adapted = manager.hasAdaptationBuff(player);

            if (infected) {
                handleInfectedPlayer(player, onInfection, adapted);
            } else {
                handleNormalPlayer(player, onInfection, adapted);
            }

            if (adapted) {
                showAdaptationActionBar(player);
            }
        }

        cleanupOfflinePlayers();
    }

    // ------------------------------------------------------- Заражённые

    private void handleInfectedPlayer(Player player, boolean onInfection, boolean adapted) {
        UUID id = player.getUniqueId();

        if (onInfection) {
            infectedOffZoneSince.remove(id);
            infectedLastWitherDamageAt.remove(id);

            removeEffectsSilently(player, PotionEffectType.WEAKNESS, PotionEffectType.HUNGER, PotionEffectType.WITHER);
            applyEffect(player, PotionEffectType.SATURATION, 1);
            applyEffect(player, PotionEffectType.SPEED, 1);
            return;
        }

        // Вне заражённой земли — сразу (без лингера) снимаем положительные
        // эффекты и выдаём дебафы.
        removeEffectsSilently(player, PotionEffectType.SATURATION, PotionEffectType.SPEED);
        applyEffect(player, PotionEffectType.WEAKNESS, 2);
        applyEffect(player, PotionEffectType.HUNGER, 2);

        long witherDelayMs = plugin.cfgInt("player-effects.wither-delay-seconds", 60) * 1000L;
        long now = System.currentTimeMillis();
        long offSince = infectedOffZoneSince.computeIfAbsent(id, k -> now);

        if (now - offSince >= witherDelayMs) {
            if (adapted) {
                player.removePotionEffect(PotionEffectType.WITHER);
                infectedLastWitherDamageAt.remove(id);
            } else {
                applyEffect(player, PotionEffectType.WITHER, 2);
                tickWitherDamage(player, id, now, infectedLastWitherDamageAt,
                        plugin.cfgInt("player-infection.off-zone-wither-damage-interval-seconds", 2) * 1000L,
                        plugin.cfgDouble("player-infection.off-zone-wither-damage-amount", 1.5));
            }
        }
    }

    // -------------------------------------------------------- Обычные

    private void handleNormalPlayer(Player player, boolean onInfection, boolean adapted) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long witherDelayMs = plugin.cfgInt("player-effects.wither-delay-seconds", 60) * 1000L;
        long lingerMs = plugin.cfgInt("player-effects.linger-seconds", 10) * 1000L;
        long witherDamageIntervalMs = plugin.cfgInt("player-effects.wither-damage-interval-seconds", 2) * 1000L;
        double witherDamageAmount = plugin.cfgDouble("player-effects.wither-damage-amount", 1.0);

        if (onInfection) {
            lastSeenOnInfection.put(id, now);
            enteredAt.putIfAbsent(id, now);

            applyEffect(player, PotionEffectType.HUNGER, 1);
            if (now - enteredAt.get(id) >= witherDelayMs) {
                applyWitherOrSuppress(player, id, now, adapted, witherDamageIntervalMs, witherDamageAmount);
            }
        } else {
            Long lastSeen = lastSeenOnInfection.get(id);
            if (lastSeen == null) {
                return;
            }
            if (now - lastSeen >= lingerMs) {
                clearEffects(player);
                lastSeenOnInfection.remove(id);
                enteredAt.remove(id);
                lastWitherDamageAt.remove(id);
            } else {
                applyEffect(player, PotionEffectType.HUNGER, 1);
                if (now - enteredAt.getOrDefault(id, now) >= witherDelayMs) {
                    applyWitherOrSuppress(player, id, now, adapted, witherDamageIntervalMs, witherDamageAmount);
                }
            }
        }
    }

    private void applyWitherOrSuppress(Player player, UUID id, long now, boolean adapted,
                                        long intervalMs, double amount) {
        if (adapted) {
            player.removePotionEffect(PotionEffectType.WITHER);
            lastWitherDamageAt.remove(id);
            return;
        }
        applyEffect(player, PotionEffectType.WITHER, 1);
        tickWitherDamage(player, id, now, lastWitherDamageAt, intervalMs, amount);
    }

    // ------------------------------------------------------------ Общее

    private void tickWitherDamage(Player player, UUID id, long now, Map<UUID, Long> tracker,
                                   long intervalMs, double amount) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        Long last = tracker.get(id);
        if (last == null) {
            tracker.put(id, now);
            return;
        }
        if (now - last >= intervalMs) {
            player.damage(amount);
            tracker.put(id, now);
        }
    }

    private void showAdaptationActionBar(Player player) {
        long remaining = manager.adaptationBuffRemainingSeconds(player);
        String time = String.format("%d:%02d", remaining / 60, remaining % 60);
        player.sendActionBar(Component.text("Адаптация: " + time, NamedTextColor.LIGHT_PURPLE));
    }

    private boolean isOnInfectedGround(Player player) {
        Block below = player.getLocation().getBlock().getRelative(0, -1, 0);
        return manager.isInfected(below);
    }

    private void applyEffect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, 60, amplifier - 1, true, false, true));
    }

    private void removeEffectsSilently(Player player, PotionEffectType... types) {
        for (PotionEffectType type : types) {
            if (player.hasPotionEffect(type)) {
                player.removePotionEffect(type);
            }
        }
    }

    private void clearEffects(Player player) {
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.WITHER);
    }

    private void cleanupOfflinePlayers() {
        cleanupMap(lastSeenOnInfection);
        cleanupMap(enteredAt);
        cleanupMap(lastWitherDamageAt);
        cleanupMap(infectedOffZoneSince);
        cleanupMap(infectedLastWitherDamageAt);
    }

    private void cleanupMap(Map<UUID, Long> map) {
        Iterator<UUID> it = map.keySet().iterator();
        while (it.hasNext()) {
            if (Bukkit.getPlayer(it.next()) == null) {
                it.remove();
            }
        }
    }
}
