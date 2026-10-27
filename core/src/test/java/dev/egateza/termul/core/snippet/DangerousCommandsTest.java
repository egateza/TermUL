package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DangerousCommandsTest {

    @Test
    void polaBerbahayaTerdeteksi() {
        assertThat(DangerousCommands.detect("sudo rm -rf /var/cache/nginx/*")).contains("rm -rf");
        assertThat(DangerousCommands.detect("rm -fr build")).contains("rm -rf");
        assertThat(DangerousCommands.detect("dd if=/dev/zero of=/dev/sda")).contains("dd");
        assertThat(DangerousCommands.detect("sudo mkfs.ext4 /dev/sdb1")).contains("mkfs");
        assertThat(DangerousCommands.detect("sudo reboot")).contains("shutdown / reboot");
        assertThat(DangerousCommands.detect("chmod -R 777 /var/www")).contains("chmod -R 777");
        assertThat(DangerousCommands.detect("psql -c \"drop database app_dev;\"")).contains("DROP");
    }

    @Test
    void perintahBiasaTidakTerdeteksi() {
        for (String ok : new String[] {"rm file.txt", "df -h", "systemctl status nginx", "echo dropdown", "ls -rf"}) {
            assertThat(DangerousCommands.detect(ok)).as(ok).isEmpty();
        }
        assertThat(DangerousCommands.detect(null)).isEmpty();
    }
}
