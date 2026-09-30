package dev.egateza.termul.sftp;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.TestSshServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/** Server SFTP in-process (root = {@link #remoteRoot}) + {@link RemoteFileService} yang sudah terhubung. */
public final class SftpFixture implements AutoCloseable {

    public final Path remoteRoot;
    public final TestSshServer server;
    public final SessionManager sessions;
    public final HostProfile profile;
    public final RemoteFileService files;

    public SftpFixture(Path tmp) throws Exception {
        remoteRoot = Files.createDirectories(tmp.resolve("remote"));
        server = new TestSshServer(tmp.resolve("hostkey.ser"), s -> {
            s.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
            s.setFileSystemFactory(new VirtualFileSystemFactory(remoteRoot));
        });
        var trust = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile p, int attempt) {
                return TestSshServer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        sessions = new SessionManager(new KnownHostsStore(tmp.resolve("known_hosts")), trust, creds,
                new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                        Duration.ZERO, 0, Duration.ZERO));
        profile = new HostProfile(UUID.randomUUID(), "sftp", "", "127.0.0.1", server.port(), TestSshServer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        files = RemoteFileService.open(sessions.acquire(profile));
    }

    @Override
    public void close() throws Exception {
        files.close();
        sessions.close();
        server.close();
    }
}
