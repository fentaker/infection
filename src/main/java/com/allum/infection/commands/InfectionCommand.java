package com.allum.infection.commands;

import com.allum.infection.InfectionManager;
import com.allum.infection.InfectionPlugin;
import com.allum.infection.util.ToolItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class InfectionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("tool", "stop", "resume", "kill", "info", "time", "cure");

    private final InfectionPlugin plugin;
    private final InfectionManager manager;

    public InfectionCommand(InfectionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getInfectionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Использование: /infection <tool|stop|resume|kill|info|time|cure>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tool" -> handleTool(sender);
            case "stop" -> handleStop(sender);
            case "resume" -> handleResume(sender);
            case "kill" -> handleKill(sender);
            case "info" -> handleInfo(sender);
            case "time" -> handleTime(sender, args);
            case "cure" -> handleCure(sender, args);
            default -> sender.sendMessage(Component.text("Неизвестная подкоманда.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleTool(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Только для игроков.", NamedTextColor.RED));
            return;
        }
        player.getInventory().addItem(ToolItem.create());
        player.sendMessage(Component.text("Вы получили жезл заражения.", NamedTextColor.GREEN));
    }

    private void handleStop(CommandSender sender) {
        manager.setPaused(true);
        plugin.getDataStore().save(manager);
        sender.sendMessage(Component.text("Распространение инфекции по блокам приостановлено.", NamedTextColor.YELLOW));
    }

    private void handleResume(CommandSender sender) {
        // Цикл SpreadTask уже "спит" в режиме периодической проверки паузы
        // (см. SpreadTask.PAUSE_RECHECK_TICKS) и сам подхватит снятие паузы —
        // повторный запуск здесь создал бы параллельный дублирующий цикл.
        manager.setPaused(false);
        plugin.getDataStore().save(manager);
        sender.sendMessage(Component.text("Распространение инфекции возобновлено.", NamedTextColor.GREEN));
    }

    private void handleKill(CommandSender sender) {
        int count = manager.getInfectedCount();
        manager.killAll();
        plugin.getDataStore().save(manager);
        sender.sendMessage(Component.text("Инфекция полностью снята. Восстановлено блоков: " + count, NamedTextColor.GREEN));
    }

    private void handleInfo(CommandSender sender) {
        String status = !manager.isActive() ? "неактивна" : manager.isPaused() ? "на паузе" : "активна";
        sender.sendMessage(Component.text("== Статус инфекции ==", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Состояние: " + status, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Заражённых блоков: " + manager.getInfectedCount(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Интервал цикла распространения: " + manager.intervalMinutes() + " мин", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Живых заражённых мобов: " + manager.countInfectedMobs(), NamedTextColor.WHITE));
    }

    private void handleTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /infection time <минуты>", NamedTextColor.YELLOW));
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Нужно указать целое число минут.", NamedTextColor.RED));
            return;
        }
        if (minutes < 1) {
            sender.sendMessage(Component.text("Интервал должен быть не меньше 1 минуты.", NamedTextColor.RED));
            return;
        }
        manager.setIntervalOverrideMinutes(minutes);
        plugin.getDataStore().save(manager);
        sender.sendMessage(Component.text("Интервал цикла распространения установлен: " + minutes + " мин.", NamedTextColor.GREEN));
    }

    private void handleCure(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /infection cure <игрок>", NamedTextColor.YELLOW));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден или не в сети.", NamedTextColor.RED));
            return;
        }
        if (!manager.isInfectedPlayer(target)) {
            sender.sendMessage(Component.text(target.getName() + " не заражён.", NamedTextColor.YELLOW));
            return;
        }
        manager.curePlayer(target);
        sender.sendMessage(Component.text(target.getName() + " излечен от заражения.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Вы излечены от заражения.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("time")) {
            return List.of("5", "10", "20", "30");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cure")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
