package dev.egateza.termul.ssh.tunnel;

import dev.egateza.termul.core.tunnel.Tunnel;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.ForwardingFilter;

/**
 * Filter forwarding sisi client. Server hanya boleh membuka channel {@code forwarded-tcpip} untuk port remote yang
 * diminta TermUL sendiri (MINA mencocokkannya dengan daftar forward aktif; port lain tidak punya tujuan), dan tujuannya
 * hanya relay loopback milik {@link TunnelManager}. Agent forwarding, X11, dan permintaan lain dari server ditolak.
 */
public final class ClientForwardingFilter implements ForwardingFilter {

    public static final ClientForwardingFilter INSTANCE = new ClientForwardingFilter();

    private ClientForwardingFilter() {
    }

    @Override
    public boolean canConnect(Type type, SshdSocketAddress address, Session session) {
        return type == Type.Forwarded && address != null && Tunnel.LOOPBACK.equals(address.getHostName());
    }

    @Override
    public boolean canListen(SshdSocketAddress address, Session session) {
        return false;
    }

    @Override
    public boolean canForwardAgent(Session session, String requestType) {
        return false;
    }

    @Override
    public boolean canForwardX11(Session session, String requestType) {
        return false;
    }
}
