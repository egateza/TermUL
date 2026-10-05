# TermUL (Terminal Utility)

SSH client desktop pribadi untuk Windows dan macOS, ala MobaXterm:

- Host tree + profil SSH per server
- Terminal SSH multi-tab (JediTerm)
- Inject password sudo/su dari vault terenkripsi (hotkey / auto-trigger dengan guard)
- Remote directory (SFTP) + edit file remote dengan editor lokal, auto-upload saat disimpan

**Status:** Fase 1–4 selesai (terminal SSH multi-tab, vault + inject sudo, panel SFTP, edit remote dengan editor lokal). Fase 5–6 menyusul. Progres detail: [plan-recap.md](plan-recap.md).

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
