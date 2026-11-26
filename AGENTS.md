# Calypso Gallery — Repository & Modularity Rules (`AGENTS.md`)

This guide defines functional file categories, hard line-count ceilings, modular splitting standards, security invariants, and verification workflows for **Calypso Gallery** (Android gallery & encrypted vault fork of Fossify Gallery, plus the offline desktop companion CLI and native Rust Argon2id crate).

---

## 1. Repository Layout & Fork Boundaries

| Area | Path | Purpose |
| :--- | :--- | :--- |
| Android app Kotlin sources | `app/src/main/kotlin/org/fossify/gallery/` | Activities, fragments, adapters, dialogs, extensions, helpers, Room DAOs/models, receivers, services, custom views |
| Android unit tests | `app/src/test/kotlin/org/fossify/gallery/` | JVM unit tests, synthetic container tests, and source-text security/policy contract tests |
| Desktop companion CLI | `tools/calypso-cli/` | Cross-platform encrypt-only offline CLI (`calypso_vault.py` & modular submodules), launchers, fixture generator, unit tests |
| Native KDF library | `rust/calypso-argon2/` | Native Argon2id JNI/FFI crate built via Cargo (`lib.rs`, desktop + Android targets) |
| Build & static analysis | `app/build.gradle.kts`, `detekt.yml`, `app/detekt-baseline.xml`, `app/lint-baseline.xml` | Gradle tasks (including Rust builds), Detekt rules (`maxIssues: 0`), and baselines |
| Fork architecture docs | `DOCS.md`, `FORK_CHANGELOG.md`, `Gallery Audit.md` | Storage/container format specs (`FGVLTENC` v1), fork release history, security hardening checklist |

---

## 2. File Classification & Hard Line-Count Ceilings

Every source file belongs to a functional category below. **A file may never exceed its hard ceiling (700–1,000 lines depending on role).** Whenever an edit would push a file past its soft target toward the ceiling—or whenever a file exceeds its hard ceiling—**modular splitting into focused sibling files is mandatory before merging.**

| Functional Category | Directory / Pattern | Soft Target | Hard Ceiling | Mandatory Split Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **Activities & Fragments** | `activities/*.kt`, `fragments/*.kt` | ≤ 700 lines | **1,000 lines** | Extract feature tabs/controllers/sections as `internal` extensions on the Activity/Fragment (e.g., `SettingsActivityVault.kt`, `SettingsActivityBackup.kt`, `SettingsActivitySearchUi.kt`) or helper controllers. Never allow a single activity/fragment file past 1,000 lines for new or fork-owned UI. |
| **Adapters & Custom Views** | `adapters/*.kt`, `views/*.kt` | ≤ 600 lines | **800 lines** | Extract item binders, ViewHolders, gesture/zoom controllers, and DiffUtil/selection callbacks into dedicated helper files in `adapters/` or `views/`. |
| **Dialogs** | `dialogs/*.kt` | ≤ 500 lines | **800 lines** | Extract input validators, custom view setup, or conflict preview formatters into `helpers/` or cohesive dialog helper files; keep dialog classes orchestration-only. |
| **Extension Files** | `extensions/*.kt` | ≤ 600 lines | **700–1,000 lines** *(hard max 1,000; new fork files ≤ 750)* | Split by domain capability (`ActivityVaultSetup.kt`, `ActivityVaultEncrypt.kt`, `ActivityVaultDecrypt.kt`, `ActivityTransferActions.kt`, `ActivityTransferConflicts.kt`, `ActivityRecycleBin.kt`, `ContextFolderVisibility.kt`, `ContextMediaCache.kt`, `ContextDirectoryHierarchy.kt`, `VirtualAlbumReferences.kt`). |
| **Helpers, Managers, Engines & Policies** | `helpers/**/*.kt` | ≤ 600 lines | **700–800 lines** *(split required > 700)* | Separate crypto primitives/KDF/header parsing/file streaming (`CryptoManagerArgon2.kt`, `CryptoManagerHeader.kt`, `CryptoManagerFileCrypto.kt`, `CryptoManagerFileDecrypt.kt`), crash recovery phases (`OperationReconciler.kt`, `OperationResumeExecutor.kt`), indexing vs scoring (`MediaIdentityIndex.kt`, `MediaIdentityScoring.kt`), and UI menu builders (`CalypsoOverflowMenuIcons.kt`, `CalypsoToolbarMenuController.kt`). |
| **Models, Interfaces, DAOs, Databases, Receivers, Services & App** | `models/*.kt`, `interfaces/*.kt`, `databases/*.kt`, `receivers/*.kt`, `services/*.kt`, `App.kt` | ≤ 300 lines | **500 lines** | Keep one domain aggregate/entity group per file; keep receivers/services thin delegators into `helpers/operations/`. |
| **Desktop CLI Python Modules** | `tools/calypso-cli/*.py` | ≤ 500 lines | **700 lines** *(test suites ≤ 800)* | Separate protocol constants/i18n (`calypso_i18n.py`), crypto/header/Tink/meta (`calypso_crypto.py`), media/EXIF/thumbnail/encryption (`calypso_media.py`), encrypt/staging workflow (`calypso_workflow.py`), and CLI discovery/entrypoint (`calypso_vault.py`). |
| **Native Rust Crate** | `rust/calypso-argon2/src/*.rs` | ≤ 400 lines | **700 lines** | Split JNI bindings, C-ABI FFI exports, and core Argon2id derivation into submodules under `rust/calypso-argon2/src/`. |
| **Unit & Instrumentation Tests** | `app/src/test/**/*.kt`, `app/src/androidTest/**/*.kt` | ≤ 700 lines | **900–1,000 lines** *(never > 1,000)* | Group tests by behavioral subdomain (e.g., `CustomVaultLocationTest.kt`, `VaultTransferCryptoTest.kt`, `VaultConflictAndSyncPolicyTest.kt`, `VaultMergeAndSkippedFilesTest.kt`) and move reusable mocks/contexts/synthetic media builders into `VaultTestBase.kt`, `VaultLockPolicyTestBase.kt`, or `SeekIndexTestSupport.kt`. |
| **XML Layouts & Generated Baselines** | `app/src/main/res/layout*/*.xml`, `values*/strings.xml`, `*-baseline.xml` | ≤ 500 lines (layouts) | **700 lines** (new layouts) | Split large layouts with `<include>` partials. Translated `strings.xml`, `AndroidManifest.xml`, `detekt-baseline.xml`, and `lint-baseline.xml` are excluded from manual splitting, though new feature strings/layouts must avoid bloating a single custom layout. |

---

## 3. Legacy Upstream No-Growth Rule

The following upstream Fossify Gallery files pre-date fork modularization and remain tightly coupled to Android lifecycle, ActionMode, ExoPlayer, or RecyclerView callbacks:

- `activities/MainActivity.kt`
- `activities/ViewPagerActivity.kt`
- `fragments/VideoFragment.kt`
- `adapters/DirectoryAdapter.kt`
- `activities/MediaActivity.kt`
- `adapters/MediaAdapter.kt`
- `helpers/MediaFetcher.kt`
- `helpers/Config.kt`
- `fragments/PhotoFragment.kt`
- `activities/EditActivity.kt`

**Rule:**
1. **No Net Growth:** Do **not** add new fork features, multi-method workflows, dialogs, or crypto logic inline inside these 10 files.
2. **Thin Hooks Only:** New fork integrations touching these files may add at most a tiny delegation call (typically 1–10 lines) invoking a dedicated helper/extension file under `helpers/` or `extensions/`.
3. **Boy Scout Split:** When modifying an existing fork feature inside one of these legacy monoliths, extract the touched block out into its own modular file whenever feasible without destabilizing upstream ActionMode/lifecycle state.

---

## 4. Modular Splitting Standards

When splitting a Kotlin file:
1. **Preserve Package & Public API:** Keep the original `package` declaration and public/`internal` top-level or `object` entrypoints compatible so existing callers and tests do not require import churn.
2. **Use `internal` State Carefully:** Split extension files in the same package (e.g., `SettingsActivity*.kt`, `ActivityVault*.kt`, `Context*.kt`, `OperationRecovery.kt`) should expose shared helper state or cross-file collaborators as `internal` rather than `public`.
3. **Avoid Spaghetti Dependencies:** Follow one-way layering:
   - `activities/` & `fragments/` & `dialogs/` → call `extensions/` & `helpers/`
   - `extensions/` → orchestrate UI prompts/Toasts/Dialogs and delegate heavy work to `helpers/`
   - `helpers/` → depend on `helpers/vault/`, `helpers/operations/`, `models/`, `interfaces/`, `Config`; must **never** hold strong references to Activities or UI widgets outside explicit short-lived callbacks/dialogs.
4. **Update Source-Text Contract Tests Explicitly:** Unit tests such as `AppPasswordLeakTest`, `CalypsoOverflowMenuTest`, `CliBridgeCleanupTest`, `SearchBarCursorTest`, `ShareMetadataPolicyTest`, `VaultLockPolicyTest`, `VaultBiometricAndHardeningTest`, and `VaultSelectionPolicyTest` inspect Kotlin source files directly via `readText()`. When moving code between split files, update those test file lookups to target the exact new file(s) (or combined split set) without weakening any assertion.

When splitting Python files in `tools/calypso-cli/`:
- Keep `calypso_vault.py` backward-compatible by re-exporting public and test-referenced symbols from submodules, and keep test-patched discovery hooks (`get_desktop_dir`, `_external_mount_roots`, `get_platform_kind`, `find_vault_files`, `find_vault_file`) accessible on the `calypso_vault` module namespace (`unittest.mock.patch.object(cv, ...)`).

---

## 5. Static Analysis & Security Invariants

1. **Detekt (`detekt.yml`):**
   - `maxIssues: 0` (baseline only for legacy suppressed findings—do not add new baseline entries).
   - `MaxLineLength`: `120`
   - `LongMethod`: threshold `120` lines
   - `LongParameterList`: function threshold `10`, constructor threshold `8`
   - `ReturnCount`: max `4`
   - Keep functions focused with shallow nesting and early returns; avoid `MagicNumber` violations in new code by declaring named `const val`s.
2. **Cryptographic & Vault Safety Invariants (Non-Negotiable):**
   - **Canonical Format:** File containers must strictly conform to `FGVLTENC` v1 (AES-256-GCM encrypted metadata header with `HKDF-SHA256` key derivation + Tink `AES256_GCM_HKDF` 256 KiB streaming segments).
   - **Buffer Zeroization:** Wipe sensitive key material (`ByteArray`, `CharArray`) in `finally` blocks; avoid immutable `String` secrets for KDF/authentication paths where `CharArray` is supported.
   - **Randomness:** Use unseeded `java.security.SecureRandom` only; never seed `SecureRandom` manually.
   - **Background Session Protection:** Any foreground crypto, sync, export, or secure-wipe batch must run under `ForegroundOperationRegistry` so screen-off/background lock policies defer RAM key clearing cleanly until the active operation completes or cancels.
   - **Desktop CLI Encrypt-Only Policy:** `tools/calypso-cli/*.py` must remain **strictly encrypt-only** (never add decryption streams/modes or forbidden legacy formats checked by `test_calypso_vault.py`).

---

## 6. Verification Commands

Run all checks relevant to the areas you modified:

- **Android Kotlin compilation & JVM unit tests** (set `JAVA_HOME` to Android Studio JBR on Windows when `java` is not on `PATH`):
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
  .\gradlew.bat :app:testFossDebugUnitTest
  ```
- **Detekt & Android Lint** (when Kotlin/resource structure changes):
  ```powershell
  .\gradlew.bat :app:detektFossDebug :app:lintFossDebug
  ```
- **Desktop CLI syntax & unit tests** (requires Python 3.10–3.13 with `cryptography`, `argon2-cffi`, `tink` installed):
  ```powershell
  py -3.12 -m py_compile tools\calypso-cli\calypso_*.py tools\calypso-cli\test_*.py tools\calypso-cli\make_fixtures.py
  py -3.12 -m unittest discover -s tools\calypso-cli -v
  ```
- **Native Rust crate & release tooling tests** (Linux / macOS / CI):
  ```sh
  cargo test --manifest-path rust/calypso-argon2/Cargo.toml --locked
  python3 -m unittest discover -s .github/scripts/tests -v
  ```

---

## 7. Release, Fastlane & F-Droid Invariants

- **Changelog parity:** Every `VERSION_CODE` in `gradle.properties` must have a matching `fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt` (≤ 500 chars), generated via `.github/scripts/fork_changelog.py`.
- **Fastlane limits:** Keep `title.txt` ≤ 30 chars, `short_description.txt` ≤ 80 chars, and `full_description.txt` ≤ 4,000 chars across all locales.
- **Package ID vs Namespace:** Release `APPLICATION_ID` is `com.calypso.gallery` (`gradle.properties`, `fastlane/Appfile`, `AndroidManifest.xml` `<queries>`), while Kotlin `namespace` (`APP_ID`) intentionally stays `org.fossify.gallery`.
- **F-Droid / FOSS compliance:** Never re-enable `INTERNET`/`ACCESS_NETWORK_STATE`, proprietary SDKs, or `dependenciesInfo { includeInApk = true }`. Keep `rust/calypso-argon2/Cargo.lock` committed and synchronized with `Cargo.toml` for `--locked` offline builds.
