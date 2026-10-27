package cn.mythicland.mythicdebug;

import cn.mythicland.framework.bukkit.BukkitRegistration;
import cn.mythicland.framework.bukkit.diagnostics.CombatDiagnostics;
import cn.mythicland.framework.command.BukkitCommandRegistrar;
import cn.mythicland.framework.diagnostics.DiagnosticRecorder;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;

public final class MythicDebug extends JavaPlugin {
    private CombatDiagnostics diagnostics;
    private BukkitRegistration commands;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        DiagnosticRecorder.Limits limits = new DiagnosticRecorder.Limits(
                Duration.ofSeconds(this.getConfig().getLong("duration-seconds", 1800)),
                this.mebibytes("segment-mib", 32),
                this.mebibytes("session-mib", 512),
                this.mebibytes("directory-mib", 2048),
                this.mebibytes("minimum-free-mib", 512),
                this.getConfig().getInt("queue-capacity", 256)
        );
        this.diagnostics = new CombatDiagnostics(this, this.getDataFolder().toPath().resolve("records"), limits);
        MythicDebugCommand command = new MythicDebugCommand(this, this.diagnostics);
        this.commands = BukkitCommandRegistrar.register(
                this,
                List.of(command),
                null
        );
        var root = this.getCommand("mdebug");
        var completer = root.getTabCompleter();
        root.setTabCompleter((sender, ignored, alias, arguments) -> {
            if (arguments.length >= 2 && (arguments[0].equalsIgnoreCase("start") || arguments[0].equalsIgnoreCase("watch")))
                return command.completeStart(sender, arguments);
            return completer.onTabComplete(sender, ignored, alias, arguments);
        });
    }

    private long mebibytes(String key, long defaultValue) {
        return Math.multiplyExact(this.getConfig().getLong(key, defaultValue), 1024L * 1024L);
    }

    @Override
    public void onDisable() {
        try {
            if (this.commands != null) this.commands.close();
        } finally {
            if (this.diagnostics != null) this.diagnostics.close();
        }
    }
}
