package com.allum.infection;

import com.allum.infection.model.BlockKey;
import com.allum.infection.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Хранит и изменяет всё состояние инфекции: заражённые блоки (с их
 * исходными материалами), паузу распространения и текущий интервал цикла.
 * Не является потокобезопасным — вызывается только из основного потока сервера.
 */
public class InfectionManager {

    private final InfectionPlugin plugin;
    private final Random random = new Random();

    // Список нужен для O(1) случайного выбора; сет — для O(1) проверки наличия.
    private final List<BlockKey> infectedList = new ArrayList<>();
    private final Set<BlockKey> infectedSet = new HashSet<>();
    private final Map<BlockKey, Material> originalMaterial = new HashMap<>();
    private final Map<BlockKey, Integer> indexInList = new HashMap<>();

    // Фронт заражения: блоки, чьи соседи в радиусе ещё не проверялись на
    // распространение. Каждый цикл SpreadTask забирает весь текущий фронт,
    // расширяет его на radius блоков во все стороны, и только что заражённые
    // блоки автоматически становятся новым фронтом для следующего цикла.
    private final Deque<BlockKey> frontier = new ArrayDeque<>();

    private boolean paused = false;
    private boolean resetting = false; // подавляет побочные эффекты во время /infection kill

    // Ручной оверрайд интервала цикла распространения через /infection time.
    // null = используется значение по умолчанию из config.yml.
    private Integer intervalOverrideMinutes = null;

    // Временный баф "адаптации" от Зелья адаптации: не персистентен между
    // рестартами (эффект и так рассчитан на несколько минут максимум).
    private final Map<UUID, Long> adaptationBuffUntil = new HashMap<>();

    public InfectionManager(InfectionPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- Блоки

    public boolean isInfected(Block block) {
        return infectedSet.contains(BlockKey.of(block));
    }

    public boolean isInfected(BlockKey key) {
        return infectedSet.contains(key);
    }

    public int getInfectedCount() {
        return infectedList.size();
    }

    public boolean isActive() {
        return !infectedList.isEmpty();
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isResetting() {
        return resetting;
    }

    /**
     * Заражает конкретный блок: запоминает исходный материал и ставит мицелий.
     * Возвращает false, если блок уже заражён либо не подходит под правила.
     */
    public boolean infectBlock(Block block) {
        BlockKey key = BlockKey.of(block);
        if (infectedSet.contains(key)) {
            return false;
        }
        Material original = block.getType();
        infectedSet.add(key);
        indexInList.put(key, infectedList.size());
        infectedList.add(key);
        originalMaterial.put(key, original);
        block.setType(Material.MYCELIUM, false);
        frontier.add(key);

        block.getWorld().spawnParticle(Particle.COMPOSTER, block.getLocation().add(0.5, 1.0, 0.5), 6, 0.3, 0.2, 0.3, 0.0);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.5f, 0.6f);
        return true;
    }

    /**
     * Восстанавливает блок в исходный материал и убирает его из инфекции.
     */
    public void revertBlock(BlockKey key) {
        if (!infectedSet.remove(key)) {
            return;
        }
        Material original = originalMaterial.remove(key);
        Integer idx = indexInList.remove(key);
        if (idx != null) {
            int lastIdx = infectedList.size() - 1;
            BlockKey lastKey = infectedList.get(lastIdx);
            infectedList.set(idx, lastKey);
            if (!lastKey.equals(key)) {
                indexInList.put(lastKey, idx);
            }
            infectedList.remove(lastIdx);
        }
        Block block = key.toBlock();
        if (block != null && original != null) {
            block.setType(original, false);
        }
    }

    public BlockKey randomInfectedKey() {
        if (infectedList.isEmpty()) {
            return null;
        }
        return infectedList.get(random.nextInt(infectedList.size()));
    }

    public Set<BlockKey> allInfectedKeysSnapshot() {
        return new HashSet<>(infectedSet);
    }

    public Material originalMaterialOf(BlockKey key) {
        return originalMaterial.get(key);
    }

    // ------------------------------------------------------------- Интервал

    /**
     * Интервал цикла распространения в минутах. Если задан оверрайд командой
     * {@code /infection time} — используется он, иначе значение из config.yml.
     */
    public int intervalMinutes() {
        return intervalOverrideMinutes != null ? intervalOverrideMinutes : plugin.cfgInt("spread.interval-minutes", 5);
    }

    /** Устанавливает ручной интервал (в минутах), задаётся командой /infection time. */
    public void setIntervalOverrideMinutes(int minutes) {
        this.intervalOverrideMinutes = minutes;
    }

    public Integer getIntervalOverrideMinutes() {
        return intervalOverrideMinutes;
    }

    /** Используется хранилищем при загрузке с диска. */
    public void loadIntervalOverrideMinutes(Integer minutes) {
        this.intervalOverrideMinutes = minutes;
    }

    // -------------------------------------------------------------- Фронт

    /** Забирает весь текущий фронт заражения и очищает очередь. */
    public List<BlockKey> drainFrontier() {
        List<BlockKey> snapshot = new ArrayList<>(frontier);
        frontier.clear();
        return snapshot;
    }

    /** Используется при старте сервера — весь загруженный очаг снова считается фронтом. */
    public void reseedFrontierFromLoaded() {
        frontier.addAll(infectedList);
    }

    // --------------------------------------------------------------- Мобы

    public boolean isInfected(LivingEntity entity) {
        Byte flag = entity.getPersistentDataContainer().get(Keys.INFECTED_MOB, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public void markInfected(Mob mob) {
        mob.getPersistentDataContainer().set(Keys.INFECTED_MOB, PersistentDataType.BYTE, (byte) 1);
        mob.getPersistentDataContainer().set(Keys.INFECTION_TIME, PersistentDataType.LONG, System.currentTimeMillis());
    }

    /** Считает живых заражённых мобов на сервере — используется только командой /infection info. */
    public int countInfectedMobs() {
        int count = 0;
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isInfected(entity)) {
                    count++;
                }
            }
        }
        return count;
    }

    public long infectionTime(LivingEntity entity) {
        Long time = entity.getPersistentDataContainer().get(Keys.INFECTION_TIME, PersistentDataType.LONG);
        return time == null ? 0L : time;
    }

    // ----------------------------------------------------------------- Kill

    /**
     * Полный сброс: все заражённые блоки восстанавливаются, все заражённые мобы умирают.
     */
    public void killAll() {
        resetting = true;
        try {
            List<BlockKey> snapshot = new ArrayList<>(infectedList);
            for (BlockKey key : snapshot) {
                revertBlock(key);
            }
            infectedList.clear();
            infectedSet.clear();
            originalMaterial.clear();
            indexInList.clear();
            frontier.clear();

            for (var world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (isInfected(entity)) {
                        entity.remove();
                    }
                }
            }
        } finally {
            resetting = false;
        }
    }

    /**
     * Пытается поставить мицелий в точке смерти существа (свой блок либо
     * блок под ногами, если существо стояло в воздухе). Не действует на паузе.
     */
    public boolean tryPlaceDeathMycelium(org.bukkit.Location loc) {
        if (paused || loc.getWorld() == null) {
            return false;
        }
        Block at = loc.getBlock();
        Block target = com.allum.infection.util.InfectionRules.canInfect(at.getType())
                ? at : at.getRelative(0, -1, 0);
        if (isInfected(target) || !com.allum.infection.util.InfectionRules.canInfect(target.getType())) {
            return false;
        }
        return infectBlock(target);
    }

    // ---------------------------------------------------- Заражение игроков

    /**
     * Заражает игрока (после поедания грибной споры). Статус хранится прямо
     * в PersistentDataContainer игрока — переживает рестарт сервера и смерть
     * (кроме смерти от огня — см. {@code CombatListener#onPlayerDeath}), т.к.
     * это тот же ключ, что и у заражённых мобов, а {@link #isInfected(LivingEntity)} общий.
     */
    public void markPlayerInfected(Player player) {
        player.getPersistentDataContainer().set(Keys.INFECTED_MOB, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isInfectedPlayer(Player player) {
        return isInfected((LivingEntity) player);
    }

    /** Снимает заражение (команда /infection cure). */
    public void curePlayer(Player player) {
        player.getPersistentDataContainer().remove(Keys.INFECTED_MOB);
    }

    // ------------------------------------------------------- Баф адаптации

    /** Выдаёт/продлевает баф адаптации от Зелья адаптации на N секунд от текущего момента. */
    public void grantAdaptationBuff(Player player, long seconds) {
        adaptationBuffUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean hasAdaptationBuff(Player player) {
        Long until = adaptationBuffUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Оставшееся время бафа в секундах, либо 0 если не активен. */
    public long adaptationBuffRemainingSeconds(Player player) {
        Long until = adaptationBuffUntil.get(player.getUniqueId());
        if (until == null) {
            return 0L;
        }
        long remaining = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    /** Чистит просроченные записи бафа — вызывается периодически, чтобы карта не росла бесконечно. */
    public void purgeExpiredAdaptationBuffs() {
        long now = System.currentTimeMillis();
        adaptationBuffUntil.values().removeIf(until -> until <= now);
    }

    // ------------------------------------------------------------- Загрузка

    /**
     * Используется хранилищем при загрузке с диска — восстанавливает bookkeeping
     * без повторной установки блоков в мире (они уже сохранены как мицелий).
     */
    public void loadEntry(BlockKey key, Material original) {
        if (infectedSet.add(key)) {
            indexInList.put(key, infectedList.size());
            infectedList.add(key);
            originalMaterial.put(key, original);
        }
    }
}
