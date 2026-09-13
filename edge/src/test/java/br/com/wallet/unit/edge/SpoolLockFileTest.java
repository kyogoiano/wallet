package br.com.wallet.unit.edge;

import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Spool Lock File Tests (REQ-PRC-010 & I-JOURNAL-001)")
class SpoolLockFileTest {

    private Path spoolDir;
    private SegmentedFileJournal journal1;
    private SegmentedFileJournal journal2;

    @BeforeEach
    void setUp() throws IOException {
        spoolDir = Files.createTempDirectory("edge-spool-lock-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (journal1 != null) {
            journal1.close();
        }
        if (journal2 != null) {
            journal2.close();
        }
        if (spoolDir != null && Files.exists(spoolDir)) {
            try (var stream = Files.walk(spoolDir)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("REQ-PRC-010: Should prevent concurrent writers on the same spool directory")
    void shouldPreventConcurrentWriters() throws Exception {
        journal1 = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 10, 1);
        journal1.start();

        // Second journal attempting to open the same spool directory concurrently MUST fail fast
        journal2 = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 10, 1);
        assertThatThrownBy(() -> journal2.start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already locked");
    }

    @Test
    @DisplayName("REQ-PRC-010: Should allow new writer to acquire lock after previous writer closes cleanly")
    void shouldAllowNewInstanceAfterCleanClose() throws Exception {
        journal1 = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 10, 1);
        journal1.start();
        journal1.close();
        journal1 = null;

        // Second journal starts cleanly after journal1 released the lock
        journal2 = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 10, 1);
        journal2.start();
        assertThat(Files.exists(spoolDir.resolve(".spool.lock"))).isTrue();
    }

    @Test
    @DisplayName("REQ-PRC-010 & I-JOURNAL-001: Independent Edge instances with distinct spool directories must run concurrently")
    void shouldAllowIndependentWritersForDifferentDirectories() throws Exception {
        Path spoolDir2 = Files.createTempDirectory("edge-spool-lock-test-2-");
        try {
            journal1 = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 10, 1);
            journal2 = new SegmentedFileJournal(spoolDir2, 64 * 1024L, 10 * 1024 * 1024L, 10, 1);

            journal1.start();
            journal2.start();

            assertThat(Files.exists(spoolDir.resolve(".spool.lock"))).isTrue();
            assertThat(Files.exists(spoolDir2.resolve(".spool.lock"))).isTrue();
        } finally {
            if (journal2 != null) journal2.close();
            try (var stream = Files.walk(spoolDir2)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }
}
