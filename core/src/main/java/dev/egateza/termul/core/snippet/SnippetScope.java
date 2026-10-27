package dev.egateza.termul.core.snippet;

import dev.egateza.termul.core.profile.HostProfile;

/**
 * Host tempat snippet berlaku: semua host, satu grup host (beserta subgrupnya), atau satu host (berdasarkan nama).
 * Di file, grup ditulis sebagai {@code appliesTo.folders} dan host sebagai {@code appliesTo.hosts}.
 */
public sealed interface SnippetScope {

    SnippetScope ALL = new All();

    /** true kalau snippet berlaku untuk host bernama {@code hostName} di grup {@code hostGroup}. */
    boolean appliesTo(String hostName, String hostGroup);

    record All() implements SnippetScope {
        @Override
        public boolean appliesTo(String hostName, String hostGroup) {
            return true;
        }
    }

    /** @param path grup host, mis. {@code Prod/DB}; berlaku juga untuk subgrupnya */
    record Group(String path) implements SnippetScope {
        public Group {
            path = HostProfile.normalizeGroup(path);
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Grup snippet wajib diisi.");
            }
        }

        @Override
        public boolean appliesTo(String hostName, String hostGroup) {
            String host = HostProfile.normalizeGroup(hostGroup == null ? "" : hostGroup);
            return host.equals(path) || host.startsWith(path + "/");
        }
    }

    /** @param name nama host (profil), dibandingkan tanpa membedakan huruf besar/kecil */
    record Host(String name) implements SnippetScope {
        public Host {
            name = name == null ? "" : name.strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Nama host snippet wajib diisi.");
            }
        }

        @Override
        public boolean appliesTo(String hostName, String hostGroup) {
            return hostName != null && name.equalsIgnoreCase(hostName.strip());
        }
    }

    /** Grup lama ({@code ""} = semua host). */
    static SnippetScope ofGroup(String group) {
        String g = HostProfile.normalizeGroup(group == null ? "" : group);
        return g.isEmpty() ? ALL : new Group(g);
    }
}
