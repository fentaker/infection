package com.allum.infection.storage;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.model.BlockKey;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Сохранение/загрузка состояния инфекции в infection-data.yml в папке плагина.
 * Формат хранит только bookkeeping (позиции + исходные материалы + пауза) —
 * сами блоки в мире уже сохранены обычным сохранением чанков.
 */
public class DataStore {

    private final InfectionPlugin plugin;
    private final File file;

    public DataStore(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "infection-data.yml");
    }

    public void save(InfectionManager manager) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("paused", manager.isPaused());
        yaml.set("interval-override-minutes", manager.getIntervalOverrideMinutes());

        List<String> lines = new ArrayList<>();
        for (BlockKey key : manager.allInfectedKeysSnapshot()) {
            Material original = manager.originalMaterialOf(key);
            if (original == null) {
                continue;
            }
            lines.add(key.serialize() + ";" + original.name());
        }
        yaml.set("blocks", lines);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить состояние инфекции", e);
        }
    }

    public void load(InfectionManager manager) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        manager.setPaused(yaml.getBoolean("paused", false));
        if (yaml.contains("interval-override-minutes")) {
            manager.loadIntervalOverrideMinutes(yaml.getInt("interval-override-minutes"));
        }

        List<String> lines = yaml.getStringList("blocks");
        int loaded = 0;
        for (String line : lines) {
            try {
                String[] parts = line.split(";");
                BlockKey key = new BlockKey(java.util.UUID.fromString(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                Material original = Material.valueOf(parts[4]);
                manager.loadEntry(key, original);
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().warning("Пропущена повреждённая запись инфекции: " + line);
            }
        }
        plugin.getLogger().info("Загружено заражённых блоков: " + loaded);
    }
}
