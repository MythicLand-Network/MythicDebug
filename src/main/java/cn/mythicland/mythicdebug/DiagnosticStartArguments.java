package cn.mythicland.mythicdebug;

import cn.mythicland.framework.bukkit.diagnostics.DiagnosticOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

record DiagnosticStartArguments(List<String> targets, List<String> observers, DiagnosticOptions options, int historySeconds) {
    static final List<String> FLAGS = List.of("--packets", "--observers", "--duration", "--sample-ticks", "--details", "--window", "--after");
    static final Map<String, String> SHORT_FLAGS = Map.of(
            "-p", "--packets", "-o", "--observers", "-t", "--duration", "-s", "--sample-ticks", "-d", "--details");

    static final Map<String, String> PROFILES = Map.of(
            "display", "equipment,health,visibility",
            "combat", "combat,movement,latency",
            "movement", "movement",
            "network", "latency");

    static String flag(String argument) {
        return SHORT_FLAGS.getOrDefault(argument, argument);
    }

    static DiagnosticStartArguments parse(String[] arguments, int defaultDuration) {
        return parse(arguments, defaultDuration, false);
    }

    static DiagnosticStartArguments parse(String[] arguments, int defaultDuration, boolean watch) {
        List<String> positional = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index++) {
            String argument = flag(arguments[index]);
            if (!argument.startsWith("-")) {
                positional.add(argument);
                continue;
            }
            if (!FLAGS.contains(argument)) throw new IllegalArgumentException("Unknown option: " + argument);
            if (++index == arguments.length || arguments[index].startsWith("-"))
                throw new IllegalArgumentException("Missing value for " + argument);
            if (flags.putIfAbsent(argument, arguments[index]) != null)
                throw new IllegalArgumentException("Duplicate option: " + argument);
        }
        if (!watch && (flags.containsKey("--window") || flags.containsKey("--after")))
            throw new IllegalArgumentException("--window and --after require mdebug watch");
        if (flags.containsKey("--after")) {
            if (flags.putIfAbsent("--duration", flags.get("--after")) != null)
                throw new IllegalArgumentException("Use either --after or --duration");
        }
        int historySeconds = watch ? number(flags, "--window", 120) : 0;
        if (watch && (historySeconds < 1 || historySeconds > 1800))
            throw new IllegalArgumentException("window must be between 1 and 1800 seconds");
        List<String> targets = new ArrayList<>();
        String profile = null;
        String observerNames = flags.get("--observers");
        if (watch) {
            if (!positional.isEmpty()) targets.addAll(names(positional.removeFirst()));
            if (!positional.isEmpty() && !PROFILES.containsKey(positional.getFirst())) {
                if (observerNames != null) throw new IllegalArgumentException("Observers were specified twice");
                observerNames = positional.removeFirst();
            }
            if (!positional.isEmpty()) {
                profile = positional.removeFirst();
                if (!PROFILES.containsKey(profile)) throw new IllegalArgumentException("Unknown capture type: " + profile);
                if (flags.containsKey("--packets")) throw new IllegalArgumentException("Use either a capture type or --packets");
            }
            if (!positional.isEmpty()) throw new IllegalArgumentException("Use comma-separated player names");
        } else for (String argument : positional) targets.addAll(names(argument));
        if (targets.isEmpty() || targets.size() > 8)
            throw new IllegalArgumentException("Select between 1 and 8 target players");
        List<String> observers = observerNames == null ? List.of() : names(observerNames);
        if (observers.size() > 16) throw new IllegalArgumentException("Select at most 16 observers");
        String defaultPackets = watch ? PROFILES.get(profile == null ? "display" : profile) : "combat";
        Set<DiagnosticOptions.PacketGroup> packets = DiagnosticOptions.parsePackets(flags.getOrDefault("--packets", defaultPackets));
        int defaultSampleTicks = packets.contains(DiagnosticOptions.PacketGroup.COMBAT)
                || packets.contains(DiagnosticOptions.PacketGroup.MOVEMENT) ? 1 : 20;
        DiagnosticOptions options = new DiagnosticOptions(
                packets,
                DiagnosticOptions.parseDetails(flags.getOrDefault("--details", watch ? "protocol" : "none")),
                number(flags, "--duration", watch ? Math.min(60, defaultDuration) : defaultDuration),
                number(flags, "--sample-ticks", defaultSampleTicks)
        );
        return new DiagnosticStartArguments(List.copyOf(targets), observers, options, historySeconds);
    }

    private static List<String> names(String text) {
        List<String> names = List.of(text.split(",", -1));
        if (names.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Player names must not be empty");
        return names;
    }

    private static int number(Map<String, String> flags, String name, int defaultValue) {
        if (!flags.containsKey(name)) return defaultValue;
        try {
            return Integer.parseInt(flags.get(name));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " requires an integer");
        }
    }
}
