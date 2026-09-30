package dev.egateza.termul.ssh;

/** Quoting argumen untuk shell POSIX (sh/bash) di sisi remote. */
public final class ShellQuote {

    private ShellQuote() {
    }

    /** Membungkus dengan single quote; {@code '} di dalamnya menjadi {@code '\''}. */
    public static String quote(String arg) {
        if (!arg.isEmpty() && arg.chars().allMatch(c -> Character.isLetterOrDigit(c) || "/._-~+=:@,".indexOf(c) >= 0)
                && !arg.startsWith("~")) {
            return arg;
        }
        return "'" + arg.replace("'", "'\\''") + "'";
    }
}
