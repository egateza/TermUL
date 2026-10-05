package dev.egateza.termul.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RemotePathsTest {

    @Test
    void joinParentName() {
        assertThat(RemotePaths.join("/home/user", "a.txt")).isEqualTo("/home/user/a.txt");
        assertThat(RemotePaths.join("/", "etc")).isEqualTo("/etc");
        assertThat(RemotePaths.join("/x", "/abs")).isEqualTo("/abs");
        assertThat(RemotePaths.parent("/home/user/a.txt")).isEqualTo("/home/user");
        assertThat(RemotePaths.parent("/etc")).isEqualTo("/");
        assertThat(RemotePaths.parent("/")).isEqualTo("/");
        assertThat(RemotePaths.parent("/srv/app/")).isEqualTo("/srv");
        assertThat(RemotePaths.name("/srv/app/")).isEqualTo("app");
        assertThat(RemotePaths.name("/etc/nginx/nginx.conf")).isEqualTo("nginx.conf");
    }

    @Test
    void longNameOwnerGroup() {
        assertThat(LongName.ownerGroup("-rw-r--r--    1 dev      www-data     1234 Jan  1 00:00 index.php"))
                .containsExactly("dev", "www-data");
        assertThat(LongName.ownerGroup("drwxr-xr-x 2 root root 4096 Sep 30 10:00 etc")).containsExactly("root", "root");
        assertThat(LongName.ownerGroup("index.php")).isNull();
        assertThat(LongName.ownerGroup(null)).isNull();
    }

    @Test
    void modeString() {
        assertThat(RemoteEntry.modeString(0644)).isEqualTo("rw-r--r--");
        assertThat(RemoteEntry.modeString(0755)).isEqualTo("rwxr-xr-x");
        assertThat(RemoteEntry.modeString(04755)).isEqualTo("rwsr-xr-x");
        assertThat(RemoteEntry.modeString(01777)).isEqualTo("rwxrwxrwt");
        assertThat(RemoteEntry.modeString(02640)).isEqualTo("rw-r-S---");
    }
}
