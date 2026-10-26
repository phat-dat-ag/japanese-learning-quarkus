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
        Path compressed = file("application.log.2026-09-04.2.gz", cutoff.minusSeconds(1));
        Path dailyCompressed = file("application.log.2026-09-04.gz", cutoff.minusSeconds(1));
        Path boundary = file("application.log.2026-09-05", cutoff);
        Path recent = file("application.log.2026-10-01", cutoff.plusSeconds(1));
        Path other = file("other.log.2026-09-04", cutoff.minusSeconds(1));
        Path unknown = file("application.log.backup", cutoff.minusSeconds(1));
        Path nested = Files.createDirectory(directory.resolve("application.log.2026-09-03"));
        Path child = Files.writeString(nested.resolve("keep"), "keep");

        assertEquals(3, LogRetention.deleteExpiredArchives(active, cutoff));
        assertFalse(Files.exists(compressed));
        assertFalse(Files.exists(dailyCompressed));
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

    @Test
    void jsonStaysValidThroughCompressedSizeRotationAndRestart() throws Exception {
        Path active = directory.resolve("structured.log");
        var formatter = new io.quarkus.logging.json.runtime.JsonFormatter();
        int count = 0;
        for (int restart = 0; restart < 2; restart++) {
            var handler = new PeriodicSizeRotatingFileHandler();
            handler.setSuffix(".yyyy-MM-dd.gz");
            handler.setMaxBackupIndex(20);
            handler.setRotateSize(300);
            handler.setRotateOnBoot(false);
            handler.setAppend(true);
            handler.setFormatter(formatter);
            handler.setFile(active.toFile());
            try {
                for (int i = 0; i < 3; i++) {
                    handler.publish(new ExtLogRecord(Level.INFO, "escaped \\n line " + count++, getClass().getName()));
                }
                handler.flush();
            } finally {
                handler.close();
            }
        }
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        int records = 0;
        try (var files = Files.list(directory)) {
            var paths = files.toList();
            assertTrue(paths.stream().anyMatch(path -> path.toString().endsWith(".gz")));
            for (Path path : paths) {
                String content;
                if (path.toString().endsWith(".gz")) {
                    try (var input = new java.util.zip.GZIPInputStream(Files.newInputStream(path))) {
                        content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } else {
                    content = Files.readString(path);
                }
                for (String line : content.lines().toList()) {
                    assertTrue(json.readTree(line).isObject());
                    records++;
                }
            }
        }
        assertEquals(6, records, "Rotation and reopen preserve every event");
    }

    private Path file(String name, Instant modified) throws Exception {
        Path file = Files.writeString(directory.resolve(name), "keep");
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }
}
