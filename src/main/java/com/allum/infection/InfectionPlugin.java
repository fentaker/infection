package com.allum.infection;

import com.allum.infection.brewing.CustomBrewManager;
import com.allum.infection.commands.InfectionCommand;
import com.allum.infection.listeners.CombatListener;
import com.allum.infection.listeners.FireListener;
import com.allum.infection.listeners.PotionListener;
import com.allum.infection.listeners.ToolListener;
import com.allum.infection.storage.DataStore;
import com.allum.infection.tasks.MobScanTask;
import com.allum.infection.tasks.MushroomTask;
import com.allum.infection.tasks.PlayerEffectTask;
import com.allum.infection.tasks.SpreadTask;
import com.allum.infection.util.AdaptationRecipes;
import com.allum.infection.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class InfectionPlugin extends JavaPlugin {

    private InfectionManager infectionManager;
    private DataStore dataStore;
    private CustomBrewManager customBrewManager;

    private BukkitTask mobScanTask;
    private BukkitTask mushroomTask;
    private BukkitTask playerEffectTask;
    private BukkitTask autosaveTask;
    private BukkitTask customBrewTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        infectionManager = new InfectionManager(this);
        dataStore = new DataStore(this);
        dataStore.load(infectionManager);
        infectionManager.reseedFrontierFromLoaded();
        customBrewManager = new CustomBrewManager(this);

        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(new FireListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PotionListener(this), this);

        AdaptationRecipes.register(this);

        InfectionCommand commandHandler = new InfectionCommand(this);
        var infectionCmd = getCommand("infection");
        if (infectionCmd != null) {
            infectionCmd.setExecutor(commandHandler);
            infectionCmd.setTabCompleter(commandHandler);
        }

        int scanTicks = cfgInt("mobs.scan-interval-ticks", 20);
        mobScanTask = new MobScanTask(this).runTaskTimer(this, 20L, scanTicks);
        mushroomTask = new MushroomTask(this).runTaskTimer(this, 20L, 20L);
        playerEffectTask = new PlayerEffectTask(this).runTaskTimer(this, 20L, 20L);

        new SpreadTask(this).startIfNeeded();

        customBrewTask = getServer().getScheduler().runTaskTimer(this, () -> customBrewManager.tick(), 20L, 20L);

        long autosaveTicks = cfgInt("autosave-minutes", 5) * 60L * 20L;
        autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> dataStore.save(infectionManager), autosaveTicks, autosaveTicks);

        getLogger().info("AllumInfection включён. Заражённых блоков: " + infectionManager.getInfectedCount());
    }

    @Override
    public void onDisable() {
        if (dataStore != null && infectionManager != null) {
            dataStore.save(infectionManager);
        }
        cancelIfRunning(mobScanTask);
        cancelIfRunning(mushroomTask);
        cancelIfRunning(playerEffectTask);
        cancelIfRunning(autosaveTask);
        cancelIfRunning(customBrewTask);
        getServer().getScheduler().cancelTasks(this);
    }

    private void cancelIfRunning(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public InfectionManager getInfectionManager() {
        return infectionManager;
    }

    public CustomBrewManager getCustomBrewManager() {
        return customBrewManager;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    public int cfgInt(String path, int def) {
        return getConfig().getInt(path, def);
    }

    public double cfgDouble(String path, double def) {
        return getConfig().getDouble(path, def);
    }
}
