package com.japaneselearning.common.logging;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class LogRetentionTest {
    @TempDir
    Path directory;

    @Test
    void removesOnlyExpiredDailyArchives() throws Exception {
        Instant cutoff = Instant.parse("2026-09-05T12:00:00Z");
        Path active = file("application.log", cutoff.minusSeconds(1));
        Path expired = file("application.log.2026-09-04", cutoff.minusSeconds(1));
        Path boundary = file("application.log.2026-09-05", cutoff);
        Path recent = file("application.log.2026-10-01", cutoff.plusSeconds(1));
        Path other = file("other.log.2026-09-04", cutoff.minusSeconds(1));
        Path unknown = file("application.log.backup", cutoff.minusSeconds(1));
        Path nested = Files.createDirectory(directory.resolve("application.log.2026-09-03"));
        Path child = Files.writeString(nested.resolve("keep"), "keep");

        assertEquals(1, LogRetention.deleteExpiredArchives(active, cutoff));
        assertFalse(Files.exists(expired));
        for (Path kept : new Path[]{active, boundary, recent, other, unknown, child}) {
            assertTrue(Files.exists(kept), kept.toString());
        }
        assertEquals(0, LogRetention.deleteExpiredArchives(active, cutoff));
    }

    @Test
    void missingDirectoryIsHarmlessAndInvalidRetentionIsRejected() throws Exception {
        assertEquals(0, LogRetention.deleteExpiredArchives(directory.resolve("missing/app.log"), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new LogRetention(true, directory.resolve("app.log"), 0, ".yyyy-MM-dd"));
        var retention = new LogRetention(true, directory.resolve("app.log"), 30, ".yyyy-MM");
        assertThrows(IllegalArgumentException.class, () -> retention.start(null));
        var disabled = new LogRetention(false, directory.resolve("app.log"), 30, ".yyyy-MM-dd");
        assertDoesNotThrow(() -> {
            disabled.start(null);
            disabled.stop(null);
        });
    }

    @Test
    void dailyRotationWorksWithoutCountBasedDeletionAndAppendsOnRestart() throws Exception {
        Instant yesterday = Instant.now().minus(2, ChronoUnit.DAYS);
        Path active = file("application.log", yesterday);
        for (int i = 0; i < 2; i++) {
            var handler = new PeriodicSizeRotatingFileHandler();
            handler.setSuffix(".yyyy-MM-dd");
            handler.setMaxBackupIndex(0);
            handler.setRotateOnBoot(false);
            handler.setAppend(true);
            handler.setFormatter(new PatternFormatter("%s%n"));
            handler.setFile(active.toFile());
            try {
                handler.publish(new ExtLogRecord(Level.INFO, "new-record-" + i, getClass().getName()));
                handler.flush();
            } finally {
                handler.close();
            }
        }
        try (var files = Files.list(directory)) {
            var archives = files.filter(path -> !path.equals(active)).toList();
            assertEquals(1, archives.size());
            assertEquals("keep", Files.readString(archives.get(0)));
        }
        assertTrue(Files.readString(active).contains("new-record-0"));
        assertTrue(Files.readString(active).contains("new-record-1"));
    }

    private Path file(String name, Instant modified) throws Exception {
        Path file = Files.writeString(directory.resolve(name), "keep");
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }
}
