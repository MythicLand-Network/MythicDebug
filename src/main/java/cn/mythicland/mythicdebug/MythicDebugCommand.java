package cn.mythicland.mythicdebug;

import cn.mythicland.framework.bukkit.CommandComponent;
import cn.mythicland.framework.bukkit.diagnostics.CombatDiagnostics;
import cn.mythicland.framework.command.AbstractCommand;
import cn.mythicland.framework.command.CommandContext;
import cn.mythicland.framework.command.CommandRoute;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Duration;
import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@CommandComponent
public final class MythicDebugCommand extends AbstractCommand {
    private final JavaPlugin plugin;
    private final CombatDiagnostics diagnostics;

    public MythicDebugCommand(JavaPlugin plugin, CombatDiagnostics diagnostics) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
    }

    @CommandRoute(
            path = "mdebug",
            permission = "mythicdebug.admin",
            executable = false
    )
    private void group(CommandContext context) {
    }

    @CommandRoute(
            path = {"mdebug", "start"},
            permission = "mythicdebug.admin",
            usages = "/mdebug start <players> [--packets groups] [--observers players] [--duration seconds] [--sample-ticks tick] [--details details]",
            minArguments = 1,
            maxArguments = 32
    )
    private void start(CommandContext context) {
        this.start(context, false);
    }

    @CommandRoute(
            path = {"mdebug", "watch"},
            permission = "mythicdebug.admin",
            usages = "/mdebug watch <targets> [observers] [display|combat|movement|network] [--window seconds] [--after seconds]",
            minArguments = 1,
            maxArguments = 32
    )
    private void watch(CommandContext context) {
        this.start(context, true);
    }

    private void start(CommandContext context, boolean watch) {
        CombatDiagnostics.Status status = this.diagnostics.status();
        if (status.recording()) {
            context.sender().sendMessage("§c请先关闭上一个记录。");
            return;
        }
        if (status.stopping()) {
            context.sender().sendMessage("§c记录正在关闭, 请稍后重试。");
            return;
        }
        try {
            DiagnosticStartArguments arguments = DiagnosticStartArguments.parse(
                    context.arguments().toArray(String[]::new), this.plugin.getConfig().getInt("duration-seconds", 1800), watch);
            LinkedHashSet<Player> players = this.onlinePlayers(arguments.targets(), context.sender());
            if (players == null) return;
            LinkedHashSet<Player> observers;
            if (arguments.observers().isEmpty()) {
                observers = new LinkedHashSet<>(players);
                if (context.sender() instanceof Player player) observers.add(player);
            } else observers = this.onlinePlayers(arguments.observers(), context.sender());
            if (observers == null) return;
            if (watch) {
                long historyBytes = Math.multiplyExact(this.plugin.getConfig().getLong("history-mib", 16), 1024L * 1024L);
                this.diagnostics.watch(players, observers, arguments.options(),
                        new DiagnosticRecorder.History(Duration.ofSeconds(arguments.historySeconds()), historyBytes));
            } else this.diagnostics.start(players, observers, arguments.options());
            String names = players.stream()
                    .map(Player::getName)
                    .collect(Collectors.joining(", "));
            context.sender().sendMessage(watch
                    ? "§a已开始回溯采集: " + names + "。发现异常后使用 /mdebug mark <label> 保存。"
                    : "§a已开始记录: " + names + "。");
        } catch (IOException failure) {
            this.fail(context.sender(), "记录文件无法写入", failure);
        } catch (ReflectiveOperationException failure) {
            this.fail(context.sender(), "诊断探针无法安装", failure);
        } catch (IllegalStateException | IllegalArgumentException failure) {
            this.fail(context.sender(), "无法开启记录", failure);
        } catch (LinkageError failure) {
            this.fail(context.sender(), "诊断依赖不兼容", failure);
        }
    }

    @CommandRoute(
            path = {"mdebug", "help"},
            permission = "mythicdebug.admin",
            usages = "/mdebug help [advanced]",
            maxArguments = 1
    )
    private void help(CommandContext context) {
        if (context.arguments().isEmpty()) {
            context.sender().sendMessage(new String[]{
                    "§a/mdebug watch <targets> [observers] [type] §e持续回溯, 多个名字用逗号分隔",
                    "§edisplay: 装备/血量/可见性 (默认); combat: 战斗; movement: 移动; network: 网络时序",
                    "§a--window <seconds> §e保留最近多久, 默认 120 秒; §a--after <seconds> §e标记后继续多久, 默认 60 秒",
                    "§a/mdebug mark <label> §e保存回溯并记录后续; §a/mdebug stop §e保存并停止",
                    "§a/mdebug status §e查看状态; §a/mdebug help advanced §e即时采集及细粒度参数"
            });
            return;
        }
        if (!context.arguments().getFirst().equalsIgnoreCase("advanced")) {
            context.sender().sendMessage("§c用法: /mdebug help [advanced]");
            return;
        }
        context.sender().sendMessage(new String[]{
                "§a/mdebug start <players> §e目标玩家, 最多 8 人",
                "§a-p / --packets <groups> §ecombat/movement/equipment/health/visibility/latency, 默认 combat",
                "§a-o / --observers <players> §e接收画面的玩家, 最多 16 人; 默认目标玩家及执行者",
                "§a-t / --duration <seconds> §e1–1800, 不超过配置时长",
                "§a-s / --sample-ticks <tick> §e状态采样间隔 1–1200; 战斗/移动默认 1, 其余默认 20",
                "§a-d / --details <details> §eprotocol/frames/events/listeners/core/nbt/runtime, 默认 none",
                "§eprotocol: 包拦截前后; frames: 入站原始帧; events: 事件边界; listeners: 逐监听器调用",
                "§ecore: 原生战斗分支; nbt: 完整物品标签; runtime: 堆及 GC",
                "§a/mdebug mark <label> §e标记异常; §a/mdebug status §e查看状态; §a/mdebug stop §e停止"
        });
    }

    private LinkedHashSet<Player> onlinePlayers(Collection<String> names, CommandSender sender) {
        LinkedHashSet<Player> players = new LinkedHashSet<>();
        for (String name : names) {
            Player player = Bukkit.getPlayerExact(name);
            if (player == null) {
                sender.sendMessage("§c玩家不在线: " + name + "。");
                return null;
            }
            players.add(player);
        }
        return players;
    }

    List<String> completeStart(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission("mythicdebug.admin")) return List.of();
        String current = arguments[arguments.length - 1];
        String previous = arguments.length > 2 ? DiagnosticStartArguments.flag(arguments[arguments.length - 2]) : "";
        List<String> values;
        if (previous.equals("--packets")) values = List.of("combat", "movement", "equipment", "health", "visibility", "latency");
        else if (previous.equals("--details")) values = List.of("none", "protocol", "frames", "events", "listeners", "core", "nbt", "runtime");
        else if (previous.equals("--duration") || previous.equals("--after")) values = List.of("30", "60", "300", "1800");
        else if (previous.equals("--window")) values = List.of("60", "120", "300", "600");
        else if (previous.equals("--sample-ticks")) values = List.of("1", "5", "20", "100");
        else {
            values = new ArrayList<>();
            boolean watch = arguments[0].equalsIgnoreCase("watch");
            int positional = 0;
            for (int i = 1; i < arguments.length - 1; i++) {
                if (arguments[i].startsWith("-")) i++;
                else positional++;
            }
            if (!watch || positional < 2 || previous.equals("--observers"))
                for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
            if (watch && positional >= 1 && positional <= 2 && !previous.equals("--observers"))
                values.addAll(DiagnosticStartArguments.PROFILES.keySet());
            if (!previous.equals("--observers")) {
                for (String flag : DiagnosticStartArguments.FLAGS) {
                    if (watch && !current.startsWith("-") && !flag.equals("--window") && !flag.equals("--after")) continue;
                    if ((flag.equals("--window") || flag.equals("--after")) && !arguments[0].equalsIgnoreCase("watch")) continue;
                    if (Arrays.stream(arguments).map(DiagnosticStartArguments::flag).noneMatch(flag::equals)) {
                        values.add(flag);
                        DiagnosticStartArguments.SHORT_FLAGS.forEach((shortFlag, fullFlag) -> {
                            if (fullFlag.equals(flag)) values.add(shortFlag);
                        });
                    }
                }
            }
        }
        int comma = current.lastIndexOf(',') + 1;
        String prefix = current.substring(0, comma);
        String typed = current.substring(comma).toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(typed))
                .filter(value -> !Arrays.asList(prefix.split(",")).contains(value))
                .map(value -> prefix + value)
                .toList();
    }

    @CommandRoute(
            path = {"mdebug", "stop"},
            permission = "mythicdebug.admin",
            usages = "/mdebug stop"
    )
    private void stop(CommandContext context) {
        CombatDiagnostics.Status status = this.diagnostics.status();
        if (status.stopping()) {
            context.sender().sendMessage("§c记录正在关闭, 请稍后重试。");
            return;
        }
        if (!status.recording()) {
            context.sender().sendMessage("§c记录未开启。");
            return;
        }
        try {
            this.diagnostics.stop();
            context.sender().sendMessage("§a已停止采集。");
        } catch (IllegalStateException | IllegalArgumentException | LinkageError failure) {
            this.fail(context.sender(), "无法关闭记录", failure);
        }
    }

    @CommandRoute(
            path = {"mdebug", "status"},
            permission = "mythicdebug.admin",
            usages = "/mdebug status"
    )
    private void status(CommandContext context) {
        CombatDiagnostics.Status status = this.diagnostics.status();
        String state = status.reason().equals("not_started") ? "未开启"
                : status.buffering() ? "回溯采集中" : status.recording() ? "记录中" : status.stopping() ? "正在关闭" : "已停止";
        CommandSender sender = context.sender();
        sender.sendMessage("§a-------------§e记录状态§a-------------");
        sender.sendMessage("  §a状态: §e" + state);
        sender.sendMessage("  §a记录数量: §e" + status.records() + " 条");
        sender.sendMessage("  §a数据大小: §e" + String.format(Locale.ROOT, "%.2f MiB", status.bytes() / (1024D * 1024D)));
        this.sendDirectory(sender, status.directory().toString());
        if (!status.reason().isEmpty() && !status.reason().equals("not_started")) {
            sender.sendMessage("  §a停止原因: §e" + this.stopReason(status.reason()));
        }
        if (!status.error().isEmpty()) sender.sendMessage("  §a采集异常: §e" + status.error());
    }

    @CommandRoute(
            path = {"mdebug", "mark"},
            permission = "mythicdebug.admin",
            usages = "/mdebug mark <label>",
            minArguments = 1,
            maxArguments = Integer.MAX_VALUE
    )
    private void mark(CommandContext context) {
        if (!this.diagnostics.status().recording()) {
            context.sender().sendMessage("§c记录未开启, 无法添加标记。");
            return;
        }
        String label = String.join(" ", context.arguments());
        if (label.isBlank() || label.length() > 512) {
            context.sender().sendMessage("§c标记需为 1 至 512 个字符。");
            return;
        }
        try {
            boolean buffering = this.diagnostics.status().buffering();
            this.diagnostics.mark(context.sender().getName(), label);
            context.sender().sendMessage(buffering ? "§a已冻结回溯窗口, 正在保存并记录后续过程。" : "§a已添加标记。");
        } catch (IllegalStateException failure) {
            if (!this.diagnostics.status().recording()) {
                context.sender().sendMessage("§c记录未开启, 无法添加标记。");
            } else this.fail(context.sender(), "无法读取标记状态", failure);
        } catch (IllegalArgumentException | LinkageError failure) {
            this.fail(context.sender(), "无法保存标记", failure);
        }
    }

    private void sendDirectory(CommandSender sender, String directory) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("  §a目录: §e" + directory);
            return;
        }
        TextComponent message = new TextComponent("");
        for (BaseComponent component : TextComponent.fromLegacyText("  §a目录: §e[点击复制]")) {
            message.addExtra(component);
        }
        message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, directory));
        message.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText("§e" + directory)
        ));
        player.spigot().sendMessage(message);
    }

    private String stopReason(String reason) {
        return switch (reason) {
            case "command" -> "手动关闭";
            case "duration_limit" -> "达到时长上限";
            case "session_size_limit" -> "达到单次容量上限";
            case "directory_size_limit" -> "达到目录容量上限";
            case "free_space_limit" -> "磁盘空间不足";
            case "queue_overflow" -> "写入队列已满";
            case "capture_error" -> "采集异常";
            case "writer_error" -> "记录写入异常";
            case "plugin_disable" -> "插件关闭";
            case "stopped", "writer_closed" -> "记录已关闭";
            default -> reason;
        };
    }

    private void fail(CommandSender sender, String message, Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        sender.sendMessage("§c" + message + ": " + detail + "。");
        this.plugin.getLogger().warning("Diagnostic command failed: " + failure);
    }
}
