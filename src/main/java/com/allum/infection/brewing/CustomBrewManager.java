package com.allum.infection.brewing;

import com.allum.infection.InfectionPlugin;
import com.allum.infection.model.BlockKey;
import com.allum.infection.util.AdaptationItems;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Полностью собственная (не зависящая от ванильного PotionMix/BrewingStartEvent)
 * реализация варки Зелья адаптации: Грибная спора (верхний слот) + Мутное
 * зелье (любой из трёх нижних слотов) в зельеварке с топливом.
 * <p>
 * Причина отказа от ванильных механизмов: ни {@code PotionMix} (даже через
 * {@code PotionMix.createPredicateChoice}), ни time-stretching через
 * {@code BrewingStartEvent} не дали гарантированно рабочего результата —
 * зельеварка либо вовсе не распознавала комбинацию как валидный рецепт и не
 * запускала варку, либо поведение зависело от тонкостей внутреннего
 * сравнения зелий по NBT, непроверяемых без живого сервера. Этот класс
 * полностью обходит вопрос "распознает ли ваниль наш рецепт" — зельеварка
 * используется только как обычный контейнер (инвентарь + топливо), а вся
 * логика варки (проверка условий, отсчёт времени, результат) выполняется
 * самим плагином.
 * <p>
 * Работа: слушатели в {@code PotionListener} по событиям инвентаря помечают
 * зельеварку как "отслеживаемую" ({@link #watch}) и просят пересчитать её
 * состояние ({@link #recheck}); отдельная периодическая задача
 * ({@link #tick()}, раз в секунду) дополнительно перепроверяет все
 * отслеживаемые зельеварки (страхует от пропущенных событий, например от
 * автоматической подачи предметов через воронку) и продвигает уже идущие
 * варки к завершению.
 */
public class CustomBrewManager {

    private final InfectionPlugin plugin;

    /** Зельеварки, за которыми когда-либо наблюдали (открывали/кликали) — периодически перепроверяются. */
    private final Set<BlockKey> watched = new HashSet<>();
    /** Активные собственные варки: ключ блока -> сколько тиков осталось. */
    private final Map<BlockKey, Integer> sessions = new HashMap<>();

    public CustomBrewManager(InfectionPlugin plugin) {
        this.plugin = plugin;
    }

    /** Начинает отслеживать зельеварку (вызывается при открытии/установке). */
    public void watch(Block block) {
        if (block.getType() == Material.BREWING_STAND) {
            watched.add(BlockKey.of(block));
        }
    }

    /** Немедленно пересчитывает состояние конкретной зельеварки (после клика в инвентаре и т.п.). */
    public void recheck(Block block) {
        if (block == null || block.getType() != Material.BREWING_STAND) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        watched.add(key);
        evaluate(key, block);
    }

    /** Снимает отслеживание и отменяет активную варку (например, при разрушении блока). */
    public void forget(Block block) {
        if (block == null) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        watched.remove(key);
        sessions.remove(key);
    }

    /** Периодический тик (раз в секунду): страхует все отслеживаемые зельеварки и продвигает активные варки. */
    public void tick() {
        if (watched.isEmpty()) {
            return;
        }
        List<BlockKey> snapshot = new ArrayList<>(watched);
        for (BlockKey key : snapshot) {
            Block block = key.toBlock();
            if (block == null || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue; // мир/чанк не загружен — просто пропускаем этот тик, не удаляем из наблюдения
            }
            if (block.getType() != Material.BREWING_STAND) {
                watched.remove(key);
                sessions.remove(key);
                continue;
            }
            evaluate(key, block);
        }
    }

    // ------------------------------------------------------------- Внутреннее

    private void evaluate(BlockKey key, Block block) {
        BlockState state = block.getState();
        if (!(state instanceof BrewingStand standState)) {
            sessions.remove(key);
            return;
        }
        BrewerInventory inv = standState.getInventory();

        Integer remaining = sessions.get(key);
        if (remaining == null) {
            // варка ещё не идёт — проверяем, можно ли начать
            if (isEligibleToStart(inv, standState)) {
                startSession(key, standState);
            }
            return;
        }

        // варка уже идёт — проверяем, что условия всё ещё выполняются
        if (!isEligibleToContinue(inv)) {
            sessions.remove(key); // ингредиент/зелья убрали — варка прерывается без результата
            return;
        }

        remaining -= 20; // tick() вызывается раз в секунду (20 тиков)
        if (remaining <= 0) {
            finishSession(inv, standState);
            sessions.remove(key);
        } else {
            sessions.put(key, remaining);
        }
    }

    private boolean isEligibleToStart(BrewerInventory inv, BrewingStand standState) {
        return standState.getFuelLevel() > 0 && hasSpore(inv) && hasAnyMundanePotion(inv);
    }

    private boolean isEligibleToContinue(BrewerInventory inv) {
        return hasSpore(inv) && hasAnyMundanePotion(inv);
    }

    private boolean hasSpore(BrewerInventory inv) {
        return AdaptationItems.isSpore(inv.getIngredient());
    }

    private boolean hasAnyMundanePotion(BrewerInventory inv) {
        for (int slot = 0; slot < 3; slot++) {
            if (AdaptationItems.isMundanePotion(inv.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private void startSession(BlockKey key, BrewingStand standState) {
        int brewMinutes = plugin.cfgInt("potion.brew-time-minutes", 10);
        sessions.put(key, brewMinutes * 60 * 20);

        // расходуем 1 единицу топлива за варку — как в ванили (1 заряд бластер-порошка = 20 варок)
        standState.setFuelLevel(Math.max(0, standState.getFuelLevel() - 1));
        standState.update();

        Block block = key.toBlock();
        if (block != null) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 0.8f);
            block.getWorld().spawnParticle(Particle.WITCH,
                    block.getLocation().add(0.5, 0.9, 0.5), 8, 0.2, 0.1, 0.2, 0.0);
        }
    }

    private void finishSession(BrewerInventory inv, BrewingStand standState) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack potion = inv.getItem(slot);
            if (!AdaptationItems.isMundanePotion(potion)) {
                continue;
            }
            Material container = potion.getType();
            int amount = potion.getAmount();
            ItemStack result = AdaptationItems.createAdaptationPotion(container);
            result.setAmount(amount);
            inv.setItem(slot, result);
        }

        ItemStack ingredient = inv.getIngredient();
        if (AdaptationItems.isSpore(ingredient)) {
            if (ingredient.getAmount() <= 1) {
                inv.setIngredient(null);
            } else {
                ingredient.setAmount(ingredient.getAmount() - 1);
                inv.setIngredient(ingredient);
            }
        }

        standState.setBrewingTime(0);
        standState.update();

        Block block = standState.getBlock();
        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        block.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR,
                block.getLocation().add(0.5, 1.0, 0.5), 20, 0.3, 0.4, 0.3, 0.0);
    }
}
