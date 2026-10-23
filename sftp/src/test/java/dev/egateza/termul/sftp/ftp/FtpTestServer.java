package dev.egateza.termul.sftp.ftp;

import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.Protocol;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

/** Server FTP/FTPS in-process (Apache FtpServer) dengan satu user; root = {@link #root}. */
final class FtpTestServer implements AutoCloseable {

    static final String USER = "tester";
    static final String PASSWORD = "rahasia-ftp";
    static final String KEYSTORE_PASSWORD = "keystore-pw";

    final Path root;
    final int port;
    private final FtpServer server;

    /**
     * @param keystore PKCS12 berisi sertifikat server untuk FTPS eksplisit, atau null untuk FTP biasa
     */
    FtpTestServer(Path tmp, Path keystore) throws IOException, FtpException {
        root = Files.createDirectories(tmp.resolve("ftp-root"));
        port = freePort();
        var serverFactory = new FtpServerFactory();
        var listener = new ListenerFactory();
        listener.setServerAddress("127.0.0.1");
        listener.setPort(port);
        var data = new DataConnectionConfigurationFactory();
        data.setPassiveAddress("127.0.0.1");
        if (keystore != null) {
            var ssl = new SslConfigurationFactory();
            ssl.setKeystoreFile(keystore.toFile());
            ssl.setKeystoreType("PKCS12");
            ssl.setKeystorePassword(KEYSTORE_PASSWORD);
            ssl.setKeyPassword(KEYSTORE_PASSWORD);
            var config = ssl.createSslConfiguration();
            listener.setSslConfiguration(config);
            listener.setImplicitSsl(false);
            data.setSslConfiguration(config);
        }
        listener.setDataConnectionConfiguration(data.createDataConnectionConfiguration());
        serverFactory.addListener("default", listener.createListener());

        var users = new PropertiesUserManagerFactory().createUserManager();
        var user = new BaseUser();
        user.setName(USER);
        user.setPassword(PASSWORD);
        user.setHomeDirectory(root.toString());
        user.setAuthorities(List.of(new WritePermission()));
        users.save(user);
        serverFactory.setUserManager(users);

        server = serverFactory.createServer();
        server.start();
    }

    HostProfile profile(Protocol protocol) {
        return new HostProfile(UUID.randomUUID(), "ftp", "", "127.0.0.1", port, USER, null, null, null,
                EnvironmentTag.DEV, null, null, false, null, null, null, protocol);
    }

    /** Keystore PKCS12 self-signed (CN=localhost, SAN 127.0.0.1) dibuat dengan keytool JDK. */
    static Path selfSignedKeystore(Path dir, String alias) throws IOException, InterruptedException {
        Path file = dir.resolve(alias + ".p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        var process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", alias, "-keyalg", "EC",
                "-groupname", "secp256r1", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", file.toString(), "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD)
                .redirectErrorStream(true)
                .start();
        String out = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IOException("keytool gagal: " + out);
        }
        return file;
    }

    private static int freePort() throws IOException {
        try (var s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Override
    public void close() {
        server.stop();
    }
}
