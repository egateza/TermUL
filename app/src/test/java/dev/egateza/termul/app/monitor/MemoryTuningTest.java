package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.HotSpotDiagnosticMXBean;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.monitor.MemoryTuning.Mode;
import dev.egateza.termul.core.config.AppConfig;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryTuningTest {

    private final HotSpotDiagnosticMXBean vm = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
    private MemoryTuning tuning;
    private String minBefore;
    private String maxBefore;

    @BeforeEach
    void setUp() {
        minBefore = flag(MemoryTuning.MIN_FREE);
        maxBefore = flag(MemoryTuning.MAX_FREE);
        tuning = new MemoryTuning(vm);
    }

    @AfterEach
    void tearDown() {
        tuning.apply(Mode.NORMAL); // JVM test dipakai bersama: kembalikan seperti semula
    }

    @Test
    void hematMengecilkanCadanganHeapDanNormalMengembalikanNilaiAwal() {
        assertThat(tuning.apply(Mode.SAVER)).isTrue();
        assertThat(flag(MemoryTuning.MIN_FREE)).isEqualTo(String.valueOf(MemoryTuning.SAVER_MIN_FREE));
        assertThat(flag(MemoryTuning.MAX_FREE)).isEqualTo(String.valueOf(MemoryTuning.SAVER_MAX_FREE));

        assertThat(tuning.apply(Mode.NORMAL)).isTrue();
        assertThat(flag(MemoryTuning.MIN_FREE)).isEqualTo(minBefore);
        assertThat(flag(MemoryTuning.MAX_FREE)).isEqualTo(maxBefore);
    }

    @Test
    void bolakBalikBerulangTidakDitolakJvm() {
        // JVM menolak MinHeapFreeRatio > MaxHeapFreeRatio; urutan set harus benar di kedua arah
        for (int i = 0; i < 3; i++) {
            assertThat(tuning.apply(Mode.SAVER)).isTrue();
            assertThat(tuning.apply(Mode.NORMAL)).isTrue();
        }
    }

    @Test
    void gcBerkalaHanyaKalauG1() {
        tuning.apply(Mode.SAVER);
        if (Boolean.parseBoolean(flag("UseG1GC"))) {
            assertThat(flag(MemoryTuning.PERIODIC_GC)).isEqualTo(String.valueOf(MemoryTuning.SAVER_PERIODIC_GC_MS));
        }
    }

    @Test
    void idCocokDenganConfigDanPunyaLabel() {
        assertThat(Mode.fromId(AppConfig.MEMORY_SAVER)).isEqualTo(Mode.SAVER);
        assertThat(Mode.fromId(AppConfig.MEMORY_NORMAL)).isEqualTo(Mode.NORMAL);
        assertThat(Mode.fromId("aneh")).isEqualTo(Mode.SAVER);
        for (var mode : Mode.values()) {
            for (var language : I18n.SUPPORTED) {
                for (String key : new String[] {"main.menu.settings.memory." + mode.id(),
                        "main.menu.settings.memory." + mode.id() + ".tooltip"}) {
                    assertThat(I18n.tIn(language.tag(), key)).isNotEqualTo(key);
                }
            }
        }
    }

    private String flag(String name) {
        return vm.getVMOption(name).getValue();
    }
}
