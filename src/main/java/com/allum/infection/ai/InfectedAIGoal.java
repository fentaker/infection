package com.allum.infection.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;

/**
 * ИИ заражённого моба: ищет ближайшее незаражённое живое существо и атакует его.
 * <p>
 * Публичный Paper Mob Goal API не позволяет создавать ванильные голы
 * (NearestAttackableTargetGoal/MeleeAttackGoal) для мобов, которые их не имеют
 * от природы (см. PaperMC/Paper#12942). Поэтому агрессия реализована как
 * собственный Goal (поиск цели + сближение + атака в ближнем бою).
 * <p>
 * Урон наносится напрямую через {@link LivingEntity#damage(double, org.bukkit.entity.Entity)},
 * а не через {@link Mob#attack(org.bukkit.entity.Entity)} — у мирных мобов
 * (корова, курица и т.д.) в ваниле нет собственной реализации атаки, и вызов
 * {@code attack()} на них может привести к необработанному исключению внутри
 * тика ИИ, из-за которого сервер тихо снимает сущность с обработки (моб
 * "исчезает" без сообщения о смерти и без дропа). Ручной damage() работает
 * одинаково предсказуемо для любого типа моба. Весь тик дополнительно обёрнут
 * в try/catch — при любой ошибке она попадёт в лог, а не убьёт сущность молча.
 */
public class InfectedAIGoal implements Goal<Mob> {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;
    private final Mob mob;
    private final GoalKey<Mob> key;

    private LivingEntity target;
    private int retargetCooldown = 0;
    private int attackCooldown = 0;

    public InfectedAIGoal(InfectionPlugin plugin, Mob mob) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
        this.mob = mob;
        this.key = GoalKey.of(Mob.class, plugin.key("infected_ai"));
    }

    @Override
    public boolean shouldActivate() {
        return manager.isInfected(mob);
    }

    @Override
    public boolean shouldStayActive() {
        return manager.isInfected(mob);
    }

    @Override
    public void start() {
        target = null;
        retargetCooldown = 0;
        attackCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        if (mob.isValid()) {
            mob.setTarget(null);
        }
    }

    @Override
    public void tick() {
        try {
            tickSafely();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка в ИИ заражённого моба (" + mob.getType() + ", uuid=" + mob.getUniqueId() + ")", e);
        }
    }

    private void tickSafely() {
        if (!mob.isValid() || mob.isDead()) {
            return;
        }

        if (target == null || target.isDead() || !target.isValid()
                || target.getWorld() != mob.getWorld()
                || manager.isInfected(target)) {
            if (retargetCooldown-- <= 0) {
                target = findTarget();
                retargetCooldown = 10; // не искать цель каждый тик
                mob.setTarget(target);
            }
        }
        if (target == null) {
            return;
        }

        double distanceSq = mob.getLocation().distanceSquared(target.getLocation());
        double reach = plugin.cfgDouble("mobs.attack-reach", 2.5);

        if (distanceSq <= reach * reach) {
            mob.lookAt(target);
            if (attackCooldown <= 0) {
                double damage = plugin.cfgDouble("mobs.attack-damage", 3.0);
                target.damage(damage, mob);
                attackCooldown = plugin.cfgInt("mobs.attack-cooldown-ticks", 20);
            } else {
                attackCooldown--;
            }
        } else {
            double speed = plugin.cfgDouble("mobs.move-speed", 1.0);
            mob.getPathfinder().moveTo(target, speed);
        }
    }

    private LivingEntity findTarget() {
        double radius = plugin.cfgDouble("mobs.ai-radius", 16.0);
        List<org.bukkit.entity.Entity> nearby = mob.getNearbyEntities(radius, radius, radius);

        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity e : nearby) {
            if (!(e instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (e instanceof Player player && (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }
            if (manager.isInfected(living)) {
                continue; // свои своих не трогают
            }
            double distSq = mob.getLocation().distanceSquared(living.getLocation());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = living;
            }
        }
        return best;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.TARGET);
    }
}
