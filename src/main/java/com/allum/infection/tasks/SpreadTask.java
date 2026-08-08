package com.allum.infection.tasks;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.model.BlockKey;
import com.allum.infection.util.InfectionRules;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Отвечает за рост инфекции по блокам. Каждый цикл (фиксированный интервал,
 * без роста фаз) забирает весь текущий фронт заражения и расширяет его на
 * {@code spread.radius} блоков во все стороны (по кубу, включая вертикаль).
 * Вновь заражённые блоки автоматически становятся фронтом следующего цикла —
 * см. {@link InfectionManager#infectBlock(Block)}.
 * <p>
 * Не использует фиксированный repeating-таймер: каждый запуск сам планирует
 * следующий через runTaskLater — так интервал можно менять "на лету" без
 * пересоздания задачи (хотя теперь интервал и не меняется автоматически).
 */
public class SpreadTask extends BukkitRunnable {

    private static final int PAUSE_RECHECK_TICKS = 20 * 20; // 20 сек, пока на паузе
    private final InfectionPlugin plugin;
    private final InfectionManager manager;

    public SpreadTask(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    public void startIfNeeded() {
        if (manager.isActive()) {
            runTaskLater(plugin, 20L);
        }
    }

    @Override
    public void run() {
        if (!manager.isActive()) {
            return; // очага ещё нет — ждём /infection tool
        }

        if (manager.isPaused()) {
            // фронт не трогаем: он копится и будет расширен после /infection resume
            new SpreadTask(plugin).runTaskLater(plugin, PAUSE_RECHECK_TICKS);
            return;
        }

        int radius = plugin.cfgInt("spread.radius", 2);
        List<BlockKey> currentFrontier = manager.drainFrontier();
        for (BlockKey sourceKey : currentFrontier) {
            expandFrom(sourceKey, radius);
        }

        long nextIntervalTicks = manager.intervalMinutes() * 60L * 20L;
        new SpreadTask(plugin).runTaskLater(plugin, nextIntervalTicks);
    }

    /**
     * Заражает все подходящие блоки в кубе {@code radius} вокруг source —
     * полное расширение фронта, а не случайная выборка.
     */
    private void expandFrom(BlockKey sourceKey, int radius) {
        Block source = sourceKey.toBlock();
        if (source == null || !source.getWorld().isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)) {
            return;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block candidate = source.getRelative(dx, dy, dz);
                    if (!candidate.getWorld().isChunkLoaded(candidate.getX() >> 4, candidate.getZ() >> 4)) {
                        continue;
                    }
                    if (manager.isInfected(candidate)) {
                        continue;
                    }
                    Material type = candidate.getType();
                    if (!InfectionRules.canInfect(type)) {
                        continue;
                    }
                    manager.infectBlock(candidate);
                }
            }
        }
    }
}
