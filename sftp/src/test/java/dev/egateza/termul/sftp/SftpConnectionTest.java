package dev.egateza.termul.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.egateza.termul.sftp.SftpConnection.State;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SFTP mengikuti sesi terminal: menunggu saat terminal menyambung, dibuka lagi saat tersambung, ditolak saat DOWN. */
class SftpConnectionTest {

    @TempDir
    Path tmp;

    private SftpFixture fx;
    private SftpLinks links;
    private SftpLinks.TerminalHandle terminal;
    private SftpConnection link;

    @BeforeEach
    void setUp() throws Exception {
        fx = new SftpFixture(tmp);
        Files.writeString(fx.remoteRoot.resolve("a.txt"), "isi");
        links = new SftpLinks(fx.sessions);
        link = links.acquire(fx.profile);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) {
            terminal.close();
        }
        links.release(fx.profile);
        fx.close();
    }

    private void dropSftpChannel() {
        link.service().close(); // koneksi SFTP mati (seperti koneksi SSH terputus)
    }

    @Test
    void withoutTerminalTabTheChannelIsReopenedDirectly() throws Exception {
        link.ensureLive();
        dropSftpChannel();

        long size = link.execute(() -> link.service().stat("/a.txt").size());

        assertThat(size).isEqualTo(3);
        assertThat(link.status().state()).isEqualTo(State.CONNECTED);
    }

    @Test
    void operationWaitsWhileTerminalReconnectsThenRunsAfterItIsConnected() throws Exception {
        terminal = links.registerTerminal(fx.profile);
        terminal.set(TerminalState.CONNECTED);
        link.ensureLive();
        dropSftpChannel();
        terminal.set(TerminalState.RECONNECTING);

        CompletableFuture<Long> op = CompletableFuture.supplyAsync(() -> {
            try {
                return link.execute(() -> link.service().stat("/a.txt").size());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        Thread.sleep(700);
        assertThat(op).isNotDone(); // belum boleh jalan: terminal belum tersambung
        assertThat(link.status().state()).isEqualTo(State.WAITING);

        terminal.set(TerminalState.CONNECTED);

        assertThat(op.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(link.status().state()).isEqualTo(State.CONNECTED);
    }

    @Test
    void operationIsRejectedWhenTerminalGaveUpAndWorksAgainAfterTerminalReconnects() throws Exception {
        terminal = links.registerTerminal(fx.profile);
        terminal.set(TerminalState.CONNECTED);
        link.ensureLive();
        dropSftpChannel();
        terminal.set(TerminalState.DOWN);

        assertThatThrownBy(() -> link.execute(() -> link.service().home()))
                .isInstanceOf(RemoteFileException.class).hasMessageContaining("Klik Reconnect");
        assertThat(link.status().state()).isEqualTo(State.DISCONNECTED);

        terminal.set(TerminalState.CONNECTED);

        // kanal dibuka lagi otomatis, tanpa ada operasi yang memintanya
        await().atMost(Duration.ofSeconds(10)).until(() -> link.status().state() == State.CONNECTED);
        assertThat(link.execute(() -> link.service().exists("/a.txt"))).isTrue();
    }

    @Test
    void statusListenersSeeWaitingThenReconnected() throws Exception {
        terminal = links.registerTerminal(fx.profile);
        terminal.set(TerminalState.CONNECTED);
        link.ensureLive();
        var seen = new CopyOnWriteArrayList<SftpConnection.Status>();
        link.addListener(seen::add);

        dropSftpChannel();
        terminal.set(TerminalState.RECONNECTING);
        terminal.set(TerminalState.CONNECTED);

        await().atMost(Duration.ofSeconds(10)).until(() -> link.status().state() == State.CONNECTED
                && !seen.isEmpty());
        assertThat(seen).extracting(SftpConnection.Status::state).containsSubsequence(State.WAITING, State.CONNECTED);
        assertThat(seen.getLast().message()).isEqualTo("Tersambung kembali");
    }

    @Test
    void firstOpenWaitsForTerminalStillConnecting() throws Exception {
        terminal = links.registerTerminal(fx.profile); // CONNECTING

        CompletableFuture<Object> open = CompletableFuture.supplyAsync(() -> {
            try {
                return link.ensureLive();
            } catch (RemoteFileException e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(500);
        assertThat(open).isNotDone();

        terminal.set(TerminalState.CONNECTED);

        assertThat(open.get(10, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void restoreReopensDeadChannelEvenWhileTerminalIsStillReconnecting() throws Exception {
        terminal = links.registerTerminal(fx.profile);
        terminal.set(TerminalState.CONNECTED);
        link.ensureLive();
        var deadChannel = link.service();
        dropSftpChannel();
        terminal.set(TerminalState.RECONNECTING);

        links.restoreSftp(fx.profile); // bagian dari percobaan reconnect terminal

        assertThat(link.service()).isNotSameAs(deadChannel);
        assertThat(link.service().isOpen()).isTrue();
        assertThat(link.status().state()).isEqualTo(State.CONNECTED);
    }

    @Test
    void restoreDoesNothingWhenSftpWasNeverOpened() throws Exception {
        links.restoreSftp(fx.profile);

        assertThat(link.status().state()).isEqualTo(State.IDLE);
    }

    @Test
    void genuineErrorOnLiveConnectionDoesNotWaitOrReopen() throws Exception {
        terminal = links.registerTerminal(fx.profile);
        terminal.set(TerminalState.CONNECTED);
        link.ensureLive();

        assertThatThrownBy(() -> link.execute(() -> link.service().stat("/tidak-ada")))
                .isInstanceOf(RemoteFileException.class);
        assertThat(link.status().state()).isEqualTo(State.CONNECTED);
    }

    @Test
    void linksShareOneConnectionPerProfileAndCloseAfterLastRelease() throws Exception {
        var second = links.acquire(fx.profile);
        assertThat(second).isSameAs(link);
        link.ensureLive();

        links.release(fx.profile);
        assertThat(link.status().state()).isEqualTo(State.CONNECTED);
        links.release(fx.profile);
        assertThat(link.status().state()).isEqualTo(State.CLOSED);

        link = links.acquire(fx.profile); // dilepas lagi di tearDown
        assertThat(link.status().state()).isEqualTo(State.IDLE);
    }
}
