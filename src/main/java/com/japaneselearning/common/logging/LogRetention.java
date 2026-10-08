package com.japaneselearning.common.logging;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@ApplicationScoped
public class LogRetention {
    private static final Logger LOG = Logger.getLogger(LogRetention.class);
    private static final String DAILY_SUFFIX = ".yyyy-MM-dd";
    private final boolean enabled;
    private final Path logFile;
    private final Duration retention;
    private final String suffix;
    private ScheduledExecutorService executor;

    public LogRetention(
            @ConfigProperty(name = "quarkus.log.file.enabled") boolean enabled,
            @ConfigProperty(name = "quarkus.log.file.path") Path logFile,
            @ConfigProperty(name = "application.logging.retention-days") int retentionDays,
            @ConfigProperty(name = "quarkus.log.file.rotation.file-suffix") String suffix
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("application.logging.retention-days must be positive");
        }

        this.enabled = enabled;
        this.logFile = logFile.toAbsolutePath().normalize();
        this.retention = Duration.ofDays(retentionDays);
        this.suffix = suffix;
    }

    void start(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }

        if (!DAILY_SUFFIX.equals(suffix)) {
            throw new IllegalArgumentException("Log retention requires the .yyyy-MM-dd rotation suffix");
        }

        // File I/O runs only on this dedicated worker, never on a reactive request thread.
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "application-log-retention");
            thread.setDaemon(true);
            return thread;
        });

        executor.scheduleWithFixedDelay(this::cleanup, 0, 1, TimeUnit.HOURS);
        LOG.infof("Application log retention started retentionDays=%d", retention.toDays());
    }

    void stop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
            LOG.info("Application log retention stopped");
        }
    }

    private void cleanup() {
        try {
            int removed = deleteExpiredArchives(logFile, Instant.now().minus(retention));
            if (removed > 0) {
                LOG.debugf("Expired application log archives removed count=%d", removed);
            }
        } catch (IOException | RuntimeException failure) {
            // Filesystem exceptions can contain paths or other environment-specific data.
            LOG.warnf("Application log retention failed failureType=%s; retrying in one hour",
                    failure.getClass().getName());
        }
    }

    static int deleteExpiredArchives(Path logFile, Instant cutoff) throws IOException {
        Path directory = logFile.toAbsolutePath().normalize().getParent();

        if (!Files.exists(directory)) {
            return 0;
        }

        Pattern archiveName = Pattern.compile(
                Pattern.quote(logFile.getFileName().toString()) + "\\.\\d{4}-\\d{2}-\\d{2}");
        int removed = 0;

        // Never recurse, follow symlinks, or match the active file or another application's files.
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (archiveName.matcher(file.getFileName().toString()).matches()
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)
                        && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        }

        return removed;
    }
}
