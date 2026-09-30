# MyTerm (nama kerja)

SSH client desktop pribadi untuk Windows, ala MobaXterm:

- Host tree + profil SSH per server
- Terminal SSH multi-tab (JediTerm)
- Inject password sudo/su dari vault terenkripsi (hotkey / auto-trigger dengan guard)
- Remote directory (SFTP) + edit file remote dengan editor lokal, auto-upload saat disimpan

**Status:** Fase 1 (kode) selesai: host tree, profil, terminal SSH multi-tab, TOFU, keep-alive. Progres detail: [plan-recap.md](plan-recap.md).

## Dokumen
- [CLAUDE.md](CLAUDE.md): konteks project & aturan kode
- [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/SECURITY.md](docs/SECURITY.md)
- [docs/PLAN.md](docs/PLAN.md)
- [docs/SETUP.md](docs/SETUP.md)
- [docs/adr/](docs/adr/)

## Stack
Java 25 (JDK 25 LTS) · Maven · Apache MINA SSHD · JediTerm · Swing + FlatLaf
