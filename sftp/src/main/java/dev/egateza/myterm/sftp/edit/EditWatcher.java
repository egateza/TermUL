package dev.egateza.myterm.sftp.edit;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mengawasi file cache edit. Setiap perubahan (modify/create, termasuk atomic save lewat rename)
 * di-debounce per file lalu memanggil {@code onChanged} satu kali di thread scheduler.
 * Konten yang sama (save tanpa perubahan) disaring oleh hash di {@link RemoteEditSession}.
 */
public final class EditWatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EditWatcher.class);

    private final WatchService watchService;
    private final Duration debounce;
    private final Consumer<Path> onChanged;
    private final ScheduledExecutorService scheduler;
    private final Thread thread;
    private final Object lock = new Object();
    private final Set<Path> files = new HashSet<>();                 // guarded by lock
    private final Map<Path, WatchKey> dirKeys = new HashMap<>();     // guarded by lock
    private final Map<Path, ScheduledFuture<?>> pending = new HashMap<>(); // guarded by lock

    public EditWatcher(Duration debounce, Consumer<Path> onChanged) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.debounce = debounce;
        this.onChanged = onChanged;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("edit-debounce").daemon().factory());
        this.thread = Thread.ofPlatform().name("edit-watcher").daemon().start(this::loop);
    }

    public void watch(Path file) throws IOException {
        Path f = file.toAbsolutePath().normalize();
        Path dir = f.getParent();
        synchronized (lock) {
            files.add(f);
            if (!dirKeys.containsKey(dir)) {
                dirKeys.put(dir, dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY));
            }
        }
    }

    public void unwatch(Path file) {
        Path f = file.toAbsolutePath().normalize();
        Path dir = f.getParent();
        synchronized (lock) {
            files.remove(f);
            var future = pending.remove(f);
            if (future != null) {
                future.cancel(false);
            }
            boolean dirStillUsed = files.stream().anyMatch(p -> p.getParent().equals(dir));
            if (!dirStillUsed) {
                var key = dirKeys.remove(dir);
                if (key != null) {
                    key.cancel();
                }
            }
        }
    }

    private void loop() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        // event hilang: anggap semua file di direktori ini mungkin berubah
                        synchronized (lock) {
                            files.stream().filter(p -> p.getParent().equals(dir)).toList().forEach(this::schedule);
                        }
                        continue;
                    }
                    Path changed = dir.resolve((Path) event.context()).toAbsolutePath().normalize();
                    synchronized (lock) {
                        if (files.contains(changed)) {
                            schedule(changed);
                        }
                    }
                }
                key.reset();
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // berhenti
        }
    }

    // guarded by lock
    private void schedule(Path file) {
        var previous = pending.get(file);
        if (previous != null) {
            previous.cancel(false);
        }
        pending.put(file, scheduler.schedule(() -> {
            synchronized (lock) {
                pending.remove(file);
                if (!files.contains(file)) {
                    return;
                }
            }
            try {
                onChanged.accept(file);
            } catch (RuntimeException e) {
                log.warn("Handler perubahan {} gagal", file, e);
            }
        }, debounce.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        try {
            watchService.close();
        } catch (IOException e) {
            log.debug("Menutup WatchService: {}", e.toString());
        }
        thread.interrupt();
        scheduler.shutdownNow();
    }
}
