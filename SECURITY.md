# Security Policy

Security fixes target the latest release (`1.0.x`) and `tools/calypso-cli`.

> [!IMPORTANT]
> Do not open public GitHub issues, discussions, or PRs for security vulnerabilities.

## Reporting a Vulnerability

**Preferred — Encrypted e-mail:** Send your PGP-encrypted report to the contact address on the maintainer's GitHub profile.

- **PGP Fingerprint:** [F46C 0F0B D707 0F79 907E 4D85 25D5 F85B 8362 7D9D](https://keys.openpgp.org/search?q=security.unplanned639@passinbox.com)

**Fallback — GitHub Private Security Advisory:** Only if encrypted e-mail is not an option (GitHub advisories are private, not end-to-end encrypted), use **Security → Report a vulnerability**.

| Milestone | Target |
| --- | --- |
| Acknowledgement | Within 7 days |
| Triage & severity | Within 14 days |
| Disclosure timeline | Agreed within 30 days |

*Best-effort targets for an individual maintainer; follow up once via the same channel if there is no reply after 7 days.*

## Scope

- **Vault & Cryptography (`FGVLTENC` v1 / `FGMETA`):** Argon2id, HKDF-SHA256, AES-256-GCM header, Tink streaming AEAD, fail-closed integrity checks, vault export/import/recovery.
- **Isolation & Leaks:** Standard vs. Secret Vault mode boundaries (`CopyMovePolicy`), unintended plaintext writes, RAM zeroization, `FLAG_SECURE`, clipboard/overlay hardening, EXIF scrubbing, and offline guarantees (`INTERNET` / `ACCESS_NETWORK_STATE` removed).
- **Desktop CLI (`tools/calypso-cli`):** Key handling, `CLI/<vault-id>/` bridge path handling, and `bwrap` / `firejail` / `sys.addaudithook` sandbox bypasses.

## Out of Scope

- Vulnerabilities solely in third-party libraries or unmodified upstream [Fossify Gallery](https://github.com/FossifyOrg/Gallery) code.
- Host-OS forensic traces on disk-backed computers (journals, swap, SSD wear-leveling, cloud-synced folders) documented in [tools/calypso-cli/README.md](tools/calypso-cli/README.md).
- Best-practice suggestions without a demonstrable weakness.

## What to Include

- **Description & impact** — What the issue is and why it matters.
- **Affected component & version** — Android app or `calypso-cli` version, Android version/device (or OS).
- **Reproduction** — Step-by-step instructions, PoC, or crafted container/logs.
- **Suggested fix (optional)** — Welcome, but not required.

## Disclosure & Credit

Reports are handled via coordinated disclosure: once a fix is ready, an advisory is published with credit by name, handle, or anonymously per your preference (and noted in [FORK_CHANGELOG.md](FORK_CHANGELOG.md)). If no response is received within 14 days after an agreed disclosure date, you may disclose at your discretion.
