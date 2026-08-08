package com.allum.infection.util;

import com.allum.infection.InfectionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Регистрация обычных верстачных рецептов грибной цепочки:
 * 9 грибов -&gt; Грибовик -&gt; (9 Грибовиков) -&gt; Грибная спора.
 * <p>
 * Дальнейший шаг (Грибная спора + Мутное зелье -&gt; Зелье адаптации)
 * НЕ регистрируется как ванильный рецепт зельеварения (ни через
 * {@code PotionMix}, ни через {@code BrewingStartEvent}) — на практике оба
 * подхода оказались ненадёжны: зельеварка либо не распознавала комбинацию
 * как валидный рецепт вообще (варка не начиналась), либо поведение зависело
 * от тонкостей внутреннего сравнения NBT зелий, которые невозможно
 * достоверно проверить без живого сервера. Вместо этого варка полностью
 * реализована собственным кодом плагина — см.
 * {@link com.allum.infection.brewing.CustomBrewManager}: он сам следит за
 * содержимым зельеварок, ведёт свой таймер и подставляет результат,
 * полностью независимо от того, что вообще думает по этому поводу ванильная
 * система рецептов зельеварения.
 */
public final class AdaptationRecipes {

    private AdaptationRecipes() {
    }

    public static void register(InfectionPlugin plugin) {
        registerGribovikRecipe(plugin);
        registerSporeRecipe(plugin);
    }

    // Крафт: 9x (красный/бурый гриб, в любой комбинации) -> Грибовик
    private static void registerGribovikRecipe(InfectionPlugin plugin) {
        NamespacedKey key = plugin.key("gribovik");
        ShapedRecipe recipe = new ShapedRecipe(key, AdaptationItems.createGribovik());
        recipe.shape("MMM", "MMM", "MMM");
        RecipeChoice mushroomChoice = new RecipeChoice.MaterialChoice(Material.RED_MUSHROOM, Material.BROWN_MUSHROOM);
        recipe.setIngredient('M', mushroomChoice);
        Bukkit.addRecipe(recipe);
    }

    // Крафт: 9x Грибовик -> Грибная спора
    private static void registerSporeRecipe(InfectionPlugin plugin) {
        NamespacedKey key = plugin.key("spore");
        ShapedRecipe recipe = new ShapedRecipe(key, AdaptationItems.createSpore());
        recipe.shape("GGG", "GGG", "GGG");
        // ExactChoice — принимает только предмет с нашей меткой, а не любой
        // ванильный блок-ножку гриба, найденный в мире (например, в биоме
        // грибных полей). Для обычных предметов крафта (не зелий) ExactChoice
        // работает надёжно.
        RecipeChoice gribovikChoice = new RecipeChoice.ExactChoice(AdaptationItems.createGribovik());
        recipe.setIngredient('G', gribovikChoice);
        Bukkit.addRecipe(recipe);
    }
}
