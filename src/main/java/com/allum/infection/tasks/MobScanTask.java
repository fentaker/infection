package com.allum.infection.tasks;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.ai.InfectedAIGoal;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Раз в секунду одним проходом по всем живым существам:
 * заражает тех, кто стоит на заражённом блоке, и умерщвляет мобов,
 * чьё время жизни после заражения истекло. Никаких задач на отдельную сущность.
 */
public class MobScanTask extends BukkitRunnable {

    private final InfectionPlugin plugin;
    private final InfectionManager manager;

    public MobScanTask(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    @Override
    public void run() {
        if (!manager.isActive()) {
            return;
        }
        long lifetimeMillis = plugin.cfgInt("mobs.lifetime-seconds", 300) * 1000L;
        long now = System.currentTimeMillis();

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player) {
                    continue; // игроки обрабатываются в PlayerEffectTask
                }

                boolean infected = manager.isInfected(entity);

                if (infected) {
                    if (now - manager.infectionTime(entity) >= lifetimeMillis) {
                        killInfectedMob(entity);
                    }
                    continue;
                }

                Block below = entity.getLocation().getBlock().getRelative(0, -1, 0);
                if (manager.isInfected(below) && entity instanceof Mob mob) {
                    try {
                        infectMob(mob);
                    } catch (Exception e) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Ошибка при заражении моба (" + mob.getType() + ")", e);
                    }
                }
            }
        }
    }

    private void infectMob(Mob mob) {
        manager.markInfected(mob);
        mob.customName(net.kyori.adventure.text.Component.text(
                com.allum.infection.util.InfectionRules.infectedDisplayName(mob.getType())));
        mob.setCustomNameVisible(true);

        // Убираем ВСЕ ванильные голы (в т.ч. атаки/агрессию у изначально
        // враждебных мобов вроде зомби/скелета/паука/крипера) — иначе моб
        // продолжит атаковать по старой ванильной логике параллельно с нашим
        // ИИ, из-за чего заражённый зомби мог бы атаковать заражённого игрока
        // в обход правила "заражённые не трогают заражённых".
        Bukkit.getMobGoals().removeAllGoals(mob);
        mob.setTarget(null);
        Bukkit.getMobGoals().addGoal(mob, 1, new InfectedAIGoal(plugin, mob));

        mob.getWorld().spawnParticle(Particle.WITCH, mob.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.0);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.6f, 0.8f);
    }

    private void killInfectedMob(LivingEntity entity) {
        org.bukkit.Location deathLoc = entity.getLocation();
        entity.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, deathLoc.clone().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.0);
        // естественная смерть — обычный дроп моба + гриб обрабатываются в CombatListener
        entity.setHealth(0.0);
        manager.tryPlaceDeathMycelium(deathLoc);
    }
}
