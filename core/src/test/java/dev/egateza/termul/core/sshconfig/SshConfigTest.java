package dev.egateza.termul.core.sshconfig;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshConfigTest {

    @TempDir
    Path home;

    private List<SshConfigHost> parse(String text) {
        return SshConfigParser.parse(text.lines().toList(), home);
    }

    private static Map<String, SshConfigHost> byAlias(List<SshConfigHost> hosts) {
        return hosts.stream().collect(Collectors.toMap(SshConfigHost::alias, Function.identity()));
    }

    @Test
    void nilaiPertamaMenangDanHostBintangJadiDefault() {
        var hosts = byAlias(parse("""
                # komentar
                Host web
                    HostName 10.0.0.5
                    User deploy
                Host web db
                    Port 2201
                    User diabaikan
                Host *
                    User dev
                    Port 22
                """));

        assertThat(hosts).containsOnlyKeys("web", "db");
        assertThat(hosts.get("web")).extracting(SshConfigHost::hostName, SshConfigHost::user, SshConfigHost::port)
                .containsExactly("10.0.0.5", "deploy", 2201);
        assertThat(hosts.get("db")).extracting(SshConfigHost::effectiveHost, SshConfigHost::user, SshConfigHost::port)
                .containsExactly("db", "diabaikan", 2201);
    }

    @Test
    void polaWildcardDanNegasi() {
        assertThat(SshConfigParser.matches("prod-web", List.of("prod-*", "!prod-db"))).isTrue();
        assertThat(SshConfigParser.matches("prod-db", List.of("prod-*", "!prod-db"))).isFalse();
        assertThat(SshConfigParser.matches("app1", List.of("app?"))).isTrue();
        assertThat(SshConfigParser.matches("x", List.of("!y"))).isFalse(); // negasi saja tidak cocok
    }

    @Test
    void formatSamaDenganKutipTokenDanMatchDilewati() {
        var h = parse("""
                Host=gw
                  HostName="%h.example.com"
                  IdentityFile ~/.ssh/id_gw
                  ProxyJump user@bastion:2222,[fe80::1]:22
                Match host gw
                  User dariMatch
                """).getFirst();

        assertThat(h.hostName()).isEqualTo("gw.example.com");
        assertThat(h.user()).isNull();
        assertThat(h.identityFile()).isEqualTo(home + "/.ssh/id_gw");
        assertThat(h.proxyJump()).containsExactly("user@bastion:2222", "[fe80::1]:22");
    }

    @Test
    void includeDenganGlobDiInline(@TempDir Path ssh) throws Exception {
        Files.createDirectories(ssh.resolve("conf.d"));
        Files.writeString(ssh.resolve("conf.d/b.conf"), "Host b\n  HostName 10.0.0.2\n");
        Files.writeString(ssh.resolve("conf.d/a.conf"), "Host a\n  HostName 10.0.0.1\n");
        Files.writeString(ssh.resolve("config"), "Include conf.d/*.conf\nHost c\n  User root\n");

        var hosts = SshConfigParser.parse(ssh.resolve("config"), ssh, home);

        assertThat(hosts).extracting(SshConfigHost::alias).containsExactly("a", "b", "c");
    }

    @Test
    void imporMembuatProfilDanRantaiJump() throws Exception {
        Path key = Files.writeString(home.resolve("id_app"), "x");
        var hosts = parse("""
                Host bastion
                  HostName bastion.example.com
                Host app
                  HostName 10.1.0.10
                  IdentityFile %s
                  ProxyJump edge@edge.example.com,bastion
                Host legacy
                  ProxyCommand nc -X 5 -x proxy:1080 %%h %%p
                """.formatted(key.toString().replace('\\', '/')));

        var plan = SshConfigImport.plan(hosts, ProfileSnapshot.empty(), "dev", "Impor");
        var byName = plan.stream().collect(Collectors.toMap(c -> c.profile().name(), Function.identity()));

        assertThat(byName).containsOnlyKeys("bastion", "app", "legacy", "edge@edge.example.com");
        var app = byName.get("app").profile();
        var bastion = byName.get("bastion").profile();
        var edge = byName.get("edge@edge.example.com");
        assertThat(app.authMethod()).isEqualTo(AuthMethod.KEY);
        assertThat(app.username()).isEqualTo("dev");
        assertThat(app.group()).isEqualTo("Impor");
        assertThat(app.jumpHostId()).isEqualTo(bastion.id());
        assertThat(bastion.jumpHostId()).isEqualTo(edge.profile().id());
        assertThat(edge.fromJump()).isTrue();
        assertThat(edge.profile().username()).isEqualTo("edge");
        assertThat(byName.get("legacy").note()).contains("ProxyCommand");

        var toSave = SshConfigImport.toSave(plan, Set.of(app.id()));
        assertThat(toSave).extracting(HostProfile::name).containsExactly("edge@edge.example.com", "bastion", "app");
    }

    @Test
    void profilYangSudahAdaDipakaiUlangTanpaDiubah() {
        var old = new HostProfile(UUID.randomUUID(), "Server lama", "Produksi", "10.0.0.5", 22, "deploy",
                AuthMethod.PASSWORD, null, null, EnvironmentTag.PROD, null, null, false);
        var snapshot = new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of("Produksi"), List.of(old));
        var hosts = parse("""
                Host web
                  HostName 10.0.0.5
                  User deploy
                Host api
                  ProxyJump web
                """);

        var plan = SshConfigImport.plan(hosts, snapshot, "dev", "Impor");

        var web = plan.stream().filter(c -> c.profile().id().equals(old.id())).findFirst().orElseThrow();
        assertThat(web.existing()).isTrue();
        assertThat(web.profile()).isEqualTo(old);
        var api = plan.stream().filter(c -> c.profile().name().equals("api")).findFirst().orElseThrow();
        assertThat(api.profile().jumpHostId()).isEqualTo(old.id());
        assertThat(SshConfigImport.toSave(plan, Set.of(old.id(), api.profile().id())))
                .extracting(HostProfile::name).containsExactly("api");
    }

    @Test
    void identityFileYangTidakAdaJatuhKeDefault() {
        var plan = SshConfigImport.plan(parse("Host x\n  IdentityFile ~/.ssh/tidak-ada\n"),
                ProfileSnapshot.empty(), "dev", "Impor");

        assertThat(plan.getFirst().profile().authMethod()).isEqualTo(AuthMethod.AGENT);
        assertThat(plan.getFirst().note()).contains("tidak ditemukan");
    }
}
