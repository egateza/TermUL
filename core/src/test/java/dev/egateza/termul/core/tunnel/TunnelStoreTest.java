package dev.egateza.termul.core.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TunnelStoreTest {

    @TempDir
    Path tmp;

    private final UUID db = UUID.randomUUID();

    private Path file() {
        return tmp.resolve("tunnels.json");
    }

    private TunnelStore loaded() {
        var store = new TunnelStore(file());
        store.load();
        return store;
    }

    @Test
    void fileBelumAdaBerartiKosong() {
        assertThat(loaded().snapshot()).isEmpty();
    }

    @Test
    void simpanUbahHapusTersimpanKeDisk() {
        var store = loaded();
        var pg = Tunnel.create("PostgreSQL", TunnelType.LOCAL, db, 5433, "localhost", 5432, false, true);
        var socks = Tunnel.create("Proxy", TunnelType.DYNAMIC, db, 1080, "", 0, true, false);
        var changes = new ArrayList<List<Tunnel>>();
        store.addListener(changes::add);

        store.save(pg);
        store.save(socks);
        store.save(pg.withSourcePort(5434));

        assertThat(loaded().snapshot()).containsExactly(pg.withSourcePort(5434), socks); // urutan dibuat
        store.delete(pg.id());
        store.delete(UUID.randomUUID()); // tidak ada: tidak menulis

        assertThat(loaded().snapshot()).containsExactly(socks);
        assertThat(store.find(socks.id())).contains(socks);
        assertThat(changes).hasSize(4);
    }

    @Test
    void formatFileTanpaSecretDanTipeHurufKecil() throws Exception {
        var store = loaded();
        store.save(Tunnel.create("Webhook", TunnelType.REMOTE, db, 9000, "localhost", 8080, false, false));

        String json = Files.readString(file());
        assertThat(json).contains("\"version\" : 1", "\"type\" : \"remote\"", "\"host\" : \"" + db + "\"");
    }

    @Test
    void entriTidakValidDilewati() throws Exception {
        Files.writeString(file(), """
                {"version":1,"tunnels":[
                  {"id":"%s","name":"Ok","type":"local","host":"%s","sourcePort":5433,
                   "destinationHost":"localhost","destinationPort":5432},
                  {"id":"%s","name":"Port salah","type":"local","host":"%s","sourcePort":0,
                   "destinationHost":"localhost","destinationPort":5432},
                  {"id":"bukan-uuid","name":"Rusak","type":"dynamic","host":"%s","sourcePort":1080}
                ]}""".formatted(UUID.randomUUID(), db, UUID.randomUUID(), db, db), StandardCharsets.UTF_8);

        assertThat(loaded().snapshot()).extracting(Tunnel::name).containsExactly("Ok");
    }

    @Test
    void fileRusakDisisihkan() throws Exception {
        Files.writeString(file(), "{ bukan json");

        assertThat(loaded().snapshot()).isEmpty();
        try (var files = Files.list(tmp)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .anyMatch(n -> n.startsWith("tunnels.json.corrupt-"));
        }
    }
}
