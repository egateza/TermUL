package dev.egateza.termul.core.sshconfig;

import java.util.List;

/**
 * Satu alias {@code Host} dari {@code ~/.ssh/config} setelah semua blok yang cocok diterapkan.
 *
 * @param alias           nama di baris {@code Host}
 * @param hostName        {@code HostName}, atau null (= alias)
 * @param user            {@code User}, atau null
 * @param port            {@code Port}, atau null (= 22)
 * @param identityFile    {@code IdentityFile} pertama (path sudah di-expand), atau null
 * @param proxyJump       hop {@code ProxyJump} berurutan ({@code [user@]host[:port]} atau alias); kosong = langsung
 * @param hasProxyCommand ada {@code ProxyCommand} (tidak didukung; hanya diperingatkan)
 */
public record SshConfigHost(String alias, String hostName, String user, Integer port, String identityFile,
                            List<String> proxyJump, boolean hasProxyCommand) {

    public String effectiveHost() {
        return hostName != null ? hostName : alias;
    }
}
