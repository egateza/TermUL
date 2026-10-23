package dev.egateza.termul.sftp.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.SSLSocketUtils;

/**
 * Subclass {@link FTPClient}/{@link FTPSClient} dengan dua tambahan:
 * <ul>
 *   <li>login dengan password {@code char[]}: perintah {@code PASS} ditulis langsung ke stream kontrol, tanpa pernah
 *       menjadi {@code String} (API commons-net hanya menerima String) dan tanpa lewat listener perintah (log);</li>
 *   <li>FTPS: kanal data memakai ulang sesi TLS kanal kontrol. Banyak server (mis. vsftpd {@code require_ssl_reuse},
 *       default aktif) menolak kanal data tanpa resumption. Socket data dibuka plain lalu di-layer TLS dengan host:port
 *       kanal kontrol ({@link SessionKeySocket}), sehingga cache sesi JDK menemukan sesi kontrol.</li>
 * </ul>
 */
final class FtpClients {

    private FtpClients() {
    }

    /** Client yang bisa login dengan password {@code char[]}. */
    interface PasswordLogin {
        /** @return reply code akhir (230 = berhasil) */
        int login(String user, char[] password) throws IOException;
    }

    static final class Plain extends FTPClient implements PasswordLogin {
        @Override
        public int login(String user, char[] password) throws IOException {
            return FtpClients.login(this, _controlOutput_, user, password);
        }
    }

    static final class Secure extends FTPSClient implements PasswordLogin {
        private final SSLContext context;
        private boolean privateData; // PROT P aktif

        Secure(boolean implicit, SSLContext context) {
            super(implicit, context);
            this.context = context;
            setEndpointCheckingEnabled(true); // hostname diperiksa oleh trust manager JDK
        }

        @Override
        public int login(String user, char[] password) throws IOException {
            return FtpClients.login(this, _controlOutput_, user, password);
        }

        /** {@code PBSZ 0} + {@code PROT P}: kanal data dienkripsi. */
        void protectData() throws IOException {
            execPBSZ(0);
            execPROT("P");
            setSocketFactory(null); // socket data dibuat plain; TLS dipasang di _openDataConnection_ (resumption)
            privateData = true;
        }

        @Override
        protected Socket _openDataConnection_(String command, String arg) throws IOException {
            Socket plain = super._openDataConnection_(command, arg);
            if (plain == null || !privateData) {
                return plain;
            }
            SSLSocket ssl;
            try {
                ssl = (SSLSocket) context.getSocketFactory()
                        .createSocket(new SessionKeySocket(plain, getRemotePort()), _hostname_, getRemotePort(), true);
            } catch (IOException e) {
                plain.close();
                throw e;
            }
            try {
                ssl.setUseClientMode(true);
                SSLSocketUtils.enableEndpointNameVerification(ssl);
                ssl.startHandshake();
                return ssl;
            } catch (IOException e) {
                ssl.close();
                throw e;
            }
        }
    }

    /**
     * Socket data plain yang melaporkan port kanal kontrol. Cache sesi TLS klien JDK memakai kunci host + port dari
     * socket di bawahnya ({@code getPort()}); argumen port di {@code SSLSocketFactory.createSocket(Socket, host, port,
     * autoClose)} diabaikan. Tanpa pembungkus ini kanal data (port passive) tidak pernah menemukan sesi kontrol,
     * dan server yang mewajibkan reuse menolaknya ({@code 522 session reuse required}).
     * Semua operasi lain diteruskan ke socket asli.
     */
    static final class SessionKeySocket extends Socket {
        private final Socket socket;
        private final int port;

        SessionKeySocket(Socket socket, int port) {
            this.socket = socket;
            this.port = port;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public InetAddress getInetAddress() {
            return socket.getInetAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return socket.getLocalAddress();
        }

        @Override
        public int getLocalPort() {
            return socket.getLocalPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return socket.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return socket.getLocalSocketAddress();
        }

        @Override
        public SocketChannel getChannel() {
            return socket.getChannel();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public void setTcpNoDelay(boolean on) throws SocketException {
            socket.setTcpNoDelay(on);
        }

        @Override
        public boolean getTcpNoDelay() throws SocketException {
            return socket.getTcpNoDelay();
        }

        @Override
        public void setSoLinger(boolean on, int linger) throws SocketException {
            socket.setSoLinger(on, linger);
        }

        @Override
        public int getSoLinger() throws SocketException {
            return socket.getSoLinger();
        }

        @Override
        public void setSoTimeout(int timeout) throws SocketException {
            socket.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws SocketException {
            return socket.getSoTimeout();
        }

        @Override
        public void setSendBufferSize(int size) throws SocketException {
            socket.setSendBufferSize(size);
        }

        @Override
        public int getSendBufferSize() throws SocketException {
            return socket.getSendBufferSize();
        }

        @Override
        public void setReceiveBufferSize(int size) throws SocketException {
            socket.setReceiveBufferSize(size);
        }

        @Override
        public int getReceiveBufferSize() throws SocketException {
            return socket.getReceiveBufferSize();
        }

        @Override
        public void setKeepAlive(boolean on) throws SocketException {
            socket.setKeepAlive(on);
        }

        @Override
        public boolean getKeepAlive() throws SocketException {
            return socket.getKeepAlive();
        }

        @Override
        public void setTrafficClass(int tc) throws SocketException {
            socket.setTrafficClass(tc);
        }

        @Override
        public int getTrafficClass() throws SocketException {
            return socket.getTrafficClass();
        }

        @Override
        public void setReuseAddress(boolean on) throws SocketException {
            socket.setReuseAddress(on);
        }

        @Override
        public boolean getReuseAddress() throws SocketException {
            return socket.getReuseAddress();
        }

        @Override
        public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
            socket.setPerformancePreferences(connectionTime, latency, bandwidth);
        }

        @Override
        public <T> Socket setOption(SocketOption<T> name, T value) throws IOException {
            socket.setOption(name, value);
            return this;
        }

        @Override
        public <T> T getOption(SocketOption<T> name) throws IOException {
            return socket.getOption(name);
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return socket.supportedOptions();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        @Override
        public void shutdownInput() throws IOException {
            socket.shutdownInput();
        }

        @Override
        public void shutdownOutput() throws IOException {
            socket.shutdownOutput();
        }

        @Override
        public boolean isConnected() {
            return socket.isConnected();
        }

        @Override
        public boolean isBound() {
            return socket.isBound();
        }

        @Override
        public boolean isClosed() {
            return socket.isClosed();
        }

        @Override
        public boolean isInputShutdown() {
            return socket.isInputShutdown();
        }

        @Override
        public boolean isOutputShutdown() {
            return socket.isOutputShutdown();
        }

        @Override
        public String toString() {
            return socket.toString();
        }
    }

    private static int login(FTPClient client, Writer control, String user, char[] password) throws IOException {
        int reply = client.user(user);
        if (FTPReply.isPositiveCompletion(reply) || !FTPReply.isPositiveIntermediate(reply)) {
            return reply; // tanpa password (230) atau ditolak
        }
        char[] line = new char[5 + password.length + 2];
        try {
            "PASS ".getChars(0, 5, line, 0);
            System.arraycopy(password, 0, line, 5, password.length);
            line[line.length - 2] = '\r';
            line[line.length - 1] = '\n';
            control.write(line);
            control.flush();
        } finally {
            Arrays.fill(line, '\0');
        }
        return client.getReply();
    }
}
