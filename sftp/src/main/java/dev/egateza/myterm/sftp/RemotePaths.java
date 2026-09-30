package dev.egateza.myterm.sftp;

/** Utilitas path POSIX remote (selalu '/', tidak bergantung OS lokal). */
public final class RemotePaths {

    private RemotePaths() {
    }

    public static String join(String dir, String name) {
        if (name.startsWith("/")) {
            return name;
        }
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    /** Parent dari path absolut; parent dari "/" adalah "/". */
    public static String parent(String path) {
        String p = stripTrailingSlash(path);
        int idx = p.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return p.substring(0, idx);
    }

    public static String name(String path) {
        String p = stripTrailingSlash(path);
        int idx = p.lastIndexOf('/');
        return idx < 0 ? p : p.substring(idx + 1);
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
