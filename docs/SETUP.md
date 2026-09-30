# Setup & Kebutuhan Build

## Tools di mesin dev (Windows)

| Tool | Keterangan |
|---|---|
| JDK 25 (Eclipse Temurin, LTS) | Target runtime & compile. `jpackage` dan `jlink` sudah termasuk di JDK. |
| IntelliJ IDEA | IDE utama, buka `pom.xml` root sebagai Maven project |
| Maven 3.9+ / Maven Wrapper | Wrapper dibuat saat Fase 1 (`mvn wrapper:wrapper`). Setelah itu cukup pakai `mvnw` / `mvnw.cmd`, tidak perlu install Maven global. |
| Git | Version control |
| Docker Desktop (atau VM Linux) | Integration test dengan Testcontainers + server uji |
| WiX Toolset | Dibutuhkan `jpackage` untuk membuat `.msi`/`.exe` di Windows. JDK 25 mendukung WiX v4/v5 (selain v3). |
| VS Code | Editor default untuk fitur remote edit (`code --wait`) |

## Dependensi (Maven Central)

> Versi sengaja tidak dikunci di sini. Kunci di `<properties>` parent `pom.xml` saat Fase 1, dengan mengambil versi stabil terbaru dari Maven Central.

| Library | Artifact | Modul |
|---|---|---|
| Apache MINA SSHD | `org.apache.sshd:sshd-core`, `sshd-sftp` | ssh, sftp |
| EdDSA/ed25519 | `net.i2p.crypto:eddsa` (kalau MINA versi terpilih masih memerlukannya) atau Bouncy Castle | ssh |
| JediTerm | `org.jetbrains.jediterm:jediterm-core`, `jediterm-ui` | terminal, app |
| FlatLaf | `com.formdev:flatlaf` (+ `flatlaf-extras` untuk SVG icon) | app |
| Jackson | `com.fasterxml.jackson.core:jackson-databind` | core |
| JNA | `net.java.dev.jna:jna-platform` (DPAPI `Crypt32Util`) | vault |
| Bouncy Castle | `org.bouncycastle:bcprov-jdk18on` (Argon2id) | vault |
| SLF4J + Logback | `org.slf4j:slf4j-api`, `ch.qos.logback:logback-classic` | semua / app |
| JUnit 5, AssertJ | test | semua |
| Testcontainers | `org.testcontainers:testcontainers`, `junit-jupiter` | ssh, sftp (integration test) |

Catatan:
- JediTerm dipublikasikan oleh JetBrains. Kalau artifact tidak ditemukan di Maven Central, tambahkan `<repository>` `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies` di parent POM.
- MINA mendukung SSH agent Windows (OpenSSH agent / Pageant) melalui modul/konektor tambahan. Cek dokumentasi versi terpilih.

## Catatan JDK 25

- Tambahkan `--enable-native-access=ALL-UNNAMED` untuk JNA di: `exec:java` (via `MAVEN_OPTS` atau `.mvn/jvm.config`), argumen Surefire/Failsafe (`argLine`), dan `--java-options` jpackage.
- Kalau muncul warning `sun.misc.Unsafe` dari library pihak ketiga, catat library-nya dan cek versi terbaru. Jangan disembunyikan dengan flag.

## Plugin Maven

| Plugin | Fungsi |
|---|---|
| `maven-compiler-plugin` | `release=25` (pakai versi plugin terbaru yang mendukung JDK 25) |
| `maven-enforcer-plugin` | Wajib Java 25, Maven ≥ 3.9, `dependencyConvergence` |
| `maven-surefire-plugin` | Unit test (`*Test`) |
| `maven-failsafe-plugin` | Integration test (`*IT`, Testcontainers) |
| `exec-maven-plugin` | `exec:java` untuk menjalankan app dari CLI |
| `maven-dependency-plugin` | `copy-dependencies` ke `target/libs` sebagai input jpackage |
| `maven-jar-plugin` | Manifest `Main-Class` + `Class-Path` |
| `exec-maven-plugin` / `jpackage-maven-plugin` | Memanggil `jlink` + `jpackage` di profile `package-win` |
| `versions-maven-plugin` (opsional) | `mvnw versions:display-dependency-updates` untuk cek update library |

## Server uji

Jangan uji fitur baru langsung ke server produksi. Siapkan target uji:

**Opsi cepat: container**
```bash
docker run -d --name myterm-sshd -p 2222:2222 \
  -e USER_NAME=dev -e USER_PASSWORD=devpass \
  -e PASSWORD_ACCESS=true -e SUDO_ACCESS=true \
  lscr.io/linuxserver/openssh-server
```
Container ini dipakai untuk menguji sudo prompt, SFTP, dan remote edit.

**Opsi realistis: VM Ubuntu Server** (Proxmox/Hyper-V), dengan nginx terpasang untuk menguji edit file root + `nginx -t` + rollback, dan PAM faillock aktif untuk menguji perilaku "Sorry, try again".

## Struktur repo (target setelah Fase 1)

```
my-personal-terminal/
├─ CLAUDE.md
├─ README.md
├─ pom.xml                     parent (packaging pom): versi, dependencyManagement, pluginManagement
├─ mvnw, mvnw.cmd, .mvn/wrapper/
├─ core/ vault/ ssh/ terminal/ sftp/ app/
│   ├─ pom.xml                  tanpa versi (diwarisi dari parent)
│   └─ src/{main,test}/java/...
└─ docs/
```

## Perintah (setelah Fase 1)

```bash
mvnw.cmd clean verify                       # compile + unit test (Surefire) + integration test (Failsafe, butuh Docker)
mvnw.cmd verify -DskipITs                   # tanpa integration test
mvnw.cmd install -DskipTests                # install modul ke local repo
mvnw.cmd -pl app exec:java                  # jalankan aplikasi (setelah install)
mvnw.cmd install -DskipTests && mvnw.cmd -pl app -Ppackage-win -DskipTests package   # app-image portable → app/target/dist/TermUL/TermUL.exe
mvnw.cmd -pl app -Ppackage-win -DskipTests package -Djpackage.type=msi        # installer .msi (butuh WiX di PATH)
```

## Distribusi (profile `package-win`)

- Runtime ramping dibuat `jpackage` lewat jlink dengan modul di property `jpackage.modules` (`app/pom.xml`), hasil
  `jdeps --print-module-deps` ditambah `jdk.unsupported` dan `jdk.localedata`. Kalau menambah library, cek ulang:
  `jdeps --ignore-missing-deps --print-module-deps --multi-release 25 --class-path "libs/*" termul-app-*.jar libs/*.jar`
  (dari folder hasil `dependency:copy-dependencies`). Opsi jlink: `--strip-debug --no-man-pages --no-header-files --include-locales=en,id`.
- Default `app-image` (folder portable ±100 MB, tanpa WiX). `-Djpackage.type=msi` mengaktifkan profile `package-win-msi`
  (menu Start, shortcut desktop, pilih folder, install per user, `--win-upgrade-uuid` tetap supaya upgrade menimpa versi lama).
- Data user tetap di `%APPDATA%\TermUL` / `%LOCALAPPDATA%\TermUL` (bukan di folder instalasi).
- Ikon aplikasi (`app/src/main/packaging/termul.ico` + `icons/app-*.png` untuk window) dibuat dengan
  `java tools/MakeAppIcon.java`.

## Ikon aplikasi (Font Awesome + Material Symbols)

Setiap ikon (`AppIcon`) tersedia di dua set yang bisa dipilih user di **Pengaturan → Set ikon**:
Font Awesome Free 7.3.1 (CC BY 4.0) dan Material Symbols Rounded (Apache 2.0, repo resmi Google, commit dikunci).
SVG ada di `app/src/main/resources/dev/egateza/termul/app/icons/{fa,material}/`, sumbernya dicatat di `icons.properties`.
Jangan edit manual, pakai tool (JDK 25, dari root project, butuh internet):

```bash
java tools/AddIcon.java TERMINAL terminal terminal    # tambah/ganti: NAMA  nama-fontawesome  nama_material
java tools/AddIcon.java FOLDER regular/folder folder  # gaya regular Font Awesome (default solid)
java tools/AddIcon.java --remove TERMINAL             # hapus
java tools/AddIcon.java --all                         # unduh ulang semua dari icons.properties
```

Cari nama ikon di https://fontawesome.com/search?ic=free dan https://fonts.google.com/icons. Tool mengunduh kedua SVG,
membuat `viewBox` persegi, lalu memperbarui `icons.properties` dan daftar konstanta di `AppIcon.java`. Pakai di kode:
`AppIcon.TERMINAL.icon()`. `AppIconTest` gagal kalau ada ikon yang hanya punya satu set.
