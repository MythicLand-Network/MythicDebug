package cn.mythicland.mythicdebug;

import cn.mythicland.framework.bukkit.diagnostics.DiagnosticOptions;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class DiagnosticStartArgumentsTest {
    @Test
    public void watch_shouldDefaultToDisplayHistoryAndAllowWindowAndPostTriggerDuration() {
        var defaults = DiagnosticStartArguments.parse(new String[]{"Baozha_1194"}, 1800, true);
        assertEquals(120, defaults.historySeconds());
        assertEquals(60, defaults.options().durationSeconds());
        assertEquals(Set.of(DiagnosticOptions.PacketGroup.EQUIPMENT, DiagnosticOptions.PacketGroup.HEALTH, DiagnosticOptions.PacketGroup.VISIBILITY), defaults.options().packets());
        assertEquals(Set.of(DiagnosticOptions.Detail.PROTOCOL), defaults.options().details());
        var selected = DiagnosticStartArguments.parse(new String[]{"Baozha_1194", "ShanguanLinG", "network", "--window", "300", "--after", "30"}, 1800, true);
        assertEquals(List.of("ShanguanLinG"), selected.observers());
        assertEquals(Set.of(DiagnosticOptions.PacketGroup.LATENCY), selected.options().packets());
        assertEquals(300, selected.historySeconds());
        assertEquals(30, selected.options().durationSeconds());
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStartArguments.parse(new String[]{"Baozha_1194", "--window", "0"}, 1800, true));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStartArguments.parse(new String[]{"Baozha_1194", "--window", "300"}, 1800));
    }

    @Test
    public void displayCapture_shouldSeparateObserverAndOnlyEnableRequestedDetails() {
        DiagnosticStartArguments arguments = DiagnosticStartArguments.parse(new String[]{
                "Windyqing", "-p", "equipment,health,visibility", "-o", "ShanguanLinG,S4tHout",
                "-t", "60", "-d", "protocol", "-s", "5"
        }, 1800);

        assertEquals(List.of("Windyqing"), arguments.targets());
        assertEquals(List.of("ShanguanLinG", "S4tHout"), arguments.observers());
        assertEquals(Set.of(DiagnosticOptions.PacketGroup.EQUIPMENT, DiagnosticOptions.PacketGroup.HEALTH,
                DiagnosticOptions.PacketGroup.VISIBILITY), arguments.options().packets());
        assertEquals(Set.of(DiagnosticOptions.Detail.PROTOCOL), arguments.options().details());
        assertEquals(60, arguments.options().durationSeconds());
        assertEquals(5, arguments.options().sampleTicks());
    }

    @Test
    public void invalidOptions_shouldRejectUnknownMissingDuplicateAndOutOfRangeValues() {
        for (String[] arguments : List.of(
                new String[]{"Windyqing", "--packets", "unknown"},
                new String[]{"Windyqing", "--observers"},
                new String[]{"Windyqing", "--duration", "60", "--duration", "30"},
                new String[]{"Windyqing", "--sample-ticks", "0"},
                new String[]{"Windyqing", "--duration", "1801"},
                new String[]{"Windyqing", "--packets", "health", "--details", "core"})) {
            assertThrows(IllegalArgumentException.class, () -> DiagnosticStartArguments.parse(arguments, 1800));
        }
    }
}
