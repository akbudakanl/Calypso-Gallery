# ADR 0008 — Per-digit PIN dot indicator: mathematical entropy analysis, metadata trade-offs, and conditional persistence

- **Status:** Accepted — **Opt-in alternative design retained with conditional on-disk persistence (2026-11-22)**
- **Date:** 2026-11-22
- **Related:** [ADR 0002](0002-operational-security-hardening.md) (operational security hardening), [ADR 0004](0004-argon2-memory-and-cross-device-kdf.md) (Argon2id brute-force cost), [ADR 0006](0006-strong-biometrics-only.en.md) (strong biometrics and product security posture)

> **Summary Decision.** The optional **"Show a dot for each PIN digit"** (`show_vault_pin_dots`) setting is retained as a legitimate opt-in UX alternative (**default: off**). While rendering fixed empty rings for each digit requires storing the PIN digit count (`vault_pin_length`) in private `SharedPreferences`, a mathematical analysis of the PIN search space shows that knowing the exact length of an $N$-digit PIN reduces total brute-force combinations by only **~10%** ($\approx 0.15$ bits of entropy). However, because knowing the exact length still leaks structural metadata—both visually to shoulder-surfers and forensically to an attacker with access to app data—the PIN length must **never** be persisted on disk when the setting is off. Enabling the setting requires explicit PIN verification (at which point the length is recorded), and disabling it immediately purges `vault_pin_length` from `SharedPreferences`. Furthermore, users should be informed of the visual and metadata implications before enabling the option, and the unlock light-sweep animation is suppressed while the rectangular dot-slot container is active to avoid visual collision.

---

## 1. Context and Problem

The encrypted vault supports two secret types:

1. **Alphanumeric password** (`PROTECTION_PASSWORD`), and
2. **Numeric PIN** (`PROTECTION_PIN`, minimum 4 digits, uncapped length; per-digit dot indicator supported up to 14 digits via `VAULT_PIN_DOTS_MAX_LENGTH`).

For PIN entry, the application offers two visual presentations:

- **Default presentation (`showVaultPinDots == false`):** No pre-allocated slots are shown. Each key press appends a simple bullet (`•`) in an unframed text area (`vaultPinDotsLegacy`), revealing nothing about the total expected PIN length prior to submission.
- **Opt-in slot indicator (`showVaultPinDots == true`):** A rounded rectangular card (`VaultPinDotsView`) renders $N$ slots up front—solid circles for entered digits and thin outlined rings for remaining digits.

To draw $N$ empty rings before the user starts typing on a locked screen, the UI must know $N$ (`vault_pin_length`) before the `.vault` header is decrypted. Because `.vault` intentionally does not store the PIN length, $N$ must be stored in the app's private `SharedPreferences`.

Two questions arose from this design:

1. **UI / Motion interaction:** When the vault unlocks, `VaultShutterAnimator` plays a horizontal light sweep (`vault_unlock_sweep`) across the credential corridor. In the default borderless bullet view, this sweep travels across open space. When `showVaultPinDots` is enabled, however, `VaultPinDotsView` draws a bordered rectangular box and the sweep line cuts across the box, creating a visual defect.
2. **Security / Metadata leakage:** Does persisting `vault_pin_length` on disk—and displaying $N$ slots on screen—compromise vault security enough that the feature should be removed altogether?

---

## 2. Mathematical Analysis: Why the Option Is Legitimate

To evaluate whether leaking the exact PIN length $N$ breaks the cryptographic strength of a PIN-protected vault, we compare the exhaustive search space of:

- **Attacker A (Unknown Length, Bounded by $N$):** Knows the minimum PIN length is $4$ and tests all possible numeric PINs up to length $N$.
- **Attacker B (Known Exact Length $N$):** Reads `vault_pin_length = N` from `SharedPreferences` (or counts the $N$ rings on screen) and tests only PINs of exact length $N$.

### 2.1. Exhaustive Search Space Comparison

For base-10 digits ($0\text{–}9$), the number of distinct PINs of exact length $k$ is $10^k$.

- **Attacker A's search space ($S_{\le N}$):**
  $$S_{\le N} = \sum_{k=4}^{N} 10^k = 10^4 + 10^5 + \dots + 10^N = \frac{10^{N+1} - 10^4}{9}$$

- **Attacker B's search space ($S_{= N}$):**
  $$S_{= N} = 10^N$$

- **Ratio of the two search spaces:**
  $$\frac{S_{= N}}{S_{\le N}} = \frac{10^N}{\frac{10^{N+1} - 10^4}{9}} \approx \frac{9}{10} = 0.90 \quad (\text{for } N \ge 6)$$

| PIN Length ($N$) | Unknown Length ($S_{\le N}$, from $4..N$) | Known Exact Length ($S_{= N}$) | Search Space Reduction | Entropy Loss ($\Delta H = \log_2(S_{\le N} / S_{= N})$) |
|---|---:|---:|---:|---:|
| 4 digits | $10,000$ | $10,000$ | $0.00\%$ | $0.000\text{ bits}$ |
| 6 digits | $1,110,000$ | $1,000,000$ | $9.91\%$ | $0.151\text{ bits}$ |
| 8 digits | $111,110,000$ | $100,000,000$ | $10.00\%$ | $0.152\text{ bits}$ |
| 10 digits | $11,111,110,000$ | $10,000,000,000$ | $10.00\%$ | $0.152\text{ bits}$ |
| 12 digits | $1,111,111,110,000$ | $1,000,000,000,000$ | $10.00\%$ | $0.152\text{ bits}$ |

### 2.2. Interpretation

Because every additional decimal digit multiplies the keyspace by $10$, the sum of **all** shorter PINs from length $4$ to $N-1$ combined ($10^4 + \dots + 10^{N-1}$) equals at most **11.11%** of the combinations at length $N$ alone.

- Knowing the exact length $N$ reduces the upper-bound exhaustive brute-force work by at most **~10%** ($\approx 0.152\text{ bits}$ of Shannon entropy).
- Under the vault's calibrated Argon2id KDF (e.g. $500\text{ ms}\text{–}2,000\text{ ms}$ per guess, see [ADR 0004](0004-argon2-memory-and-cross-device-kdf.md)), a $0.15\text{-bit}$ difference does not change the feasibility class of an exhaustive offline attack.

**Conclusion:** From a pure combinatorial and information-theoretic standpoint, retaining "Show a dot for each PIN digit" as an **opt-in** usability feature is mathematically legitimate. It provides clear visual pacing for users who prefer fixed slot feedback without collapsing the underlying keyspace.

---

## 3. Explicit Security Risks and Threat Model Nuances

Despite the small ~10% reduction in exhaustive keyspace, leaking $N$ introduces **non-uniform (heuristic) and operational risks** that must be stated explicitly:

### 3.1. Visual Leakage (Shoulder Surfing / Casual Observation)

When `showVaultPinDots` is enabled, anyone who glances at the lock screen—even before a single key is pressed—can immediately count the empty rings in `VaultPinDotsView`.

- A casual observer learns whether the user chose a standard 4-digit PIN, a 6-digit PIN, or a longer 8–12 digit PIN.
- Combined with partial observation of hand movement (even with `hideVaultKeyPresses` enabled), knowing the exact number of digits makes it easier to reconstruct the entry cadence.

### 3.2. Heuristic & Targeted Brute-Force Prioritization

Real-world attackers do not necessarily search sequentially from $4$ digits upward ($0000 \dots 99999999$).

- **Without knowing $N$:** An attacker with a high-cost Argon2id target must divide their computational budget across common 4-digit PINs, common 6-digit PINs, 8-digit dates (`DDMMYYYY` / `YYYYMMDD`), phone numbers, etc. Many attackers abandon brute-force attempts after exhausting 4- and 6-digit dictionaries.
- **With $N$ leaked:** If an attacker inspects `SharedPreferences` (via a rooted device, ADB backup/extraction, or forensic image) and sees `vault_pin_length = 8`, they will **skip** 4-, 5-, 6-, and 7-digit candidates entirely and immediately focus 100% of their Argon2id budget on 8-digit patterns (e.g., birthdates, anniversaries, repeated 4-digit pairs). Conversely, if they see `vault_pin_length = 4`, they know a complete exhaustive search of all $10,000$ combinations is guaranteed to succeed in bounded time.

### 3.3. On-Disk Forensic Footprint

The portable `.vault` container header deliberately omits the PIN length; an attacker who only obtains an exported vault folder (`.vault` + `.bin` files) learns only that `protectionType == PROTECTION_PIN`. Persisting `vault_pin_length` in `SharedPreferences` creates a discrepancy where local app storage leaks more structural metadata about the secret than the encrypted vault header itself.

---

## 4. Decision

To balance user choice against the risks in §3, the following rules are enforced:

### 4.1. Default-Off and Zero On-Disk Footprint When Disabled

1. **Default state:** `showVaultPinDots` is **false** by default.
2. **No unconditional persistence:** `setupOrUpdateCredentials` and `unlockFromHeaderFile` must **never** write `vault_pin_length` to `SharedPreferences` when `showVaultPinDots` is `false`.
3. **Immediate purge on disable:** Setting `config.showVaultPinDots = false` (or `config.vaultPinLength = 0`) explicitly removes the `vault_pin_length` key from `SharedPreferences` (`editor.remove(VAULT_PIN_LENGTH)`).
4. **Legacy cleanup:** Entering Vault Settings while `showVaultPinDots` is `false` purges any leftover `vault_pin_length` value written by earlier builds, and vault reset / deletion / location switch resets `showVaultPinDots` to `false`.

### 4.2. Enabling Flow Requires PIN Entry

Because `vault_pin_length` is not stored on disk while the setting is off, turning `showVaultPinDots` **on** in Settings requires the user to authenticate with their **actual PIN** (`allowBiometrics = false` when enabling):

- Upon successful PIN verification, the authenticated PIN's length is captured in memory and persisted to `config.vaultPinLength` (provided it is within `1..VAULT_PIN_DOTS_MAX_LENGTH`).
- Turning the setting **off** still permits biometric or PIN confirmation and immediately deletes `vault_pin_length` from disk.

### 4.3. User Notification Before Enabling (Informed Consent)

Because enabling `showVaultPinDots` intentionally trades metadata privacy for visual convenience (§3.1–3.3), the user should be informed before the setting is activated:

- When the user taps **"Show a dot for each PIN digit"** to turn it on, an informational confirmation dialog (`VaultNoticeDialog` / `ConfirmationDialog`) should explain:
  1. **Visual exposure:** Fixed dot rings will be displayed on all vault PIN screens, allowing anyone who sees the screen to tell how many digits your PIN has.
  2. **On-disk metadata:** To render those slots while the vault is locked, the digit count (not the PIN itself) is stored in local app preferences for as long as this setting remains enabled, and is deleted immediately when you turn the setting off.
- Only after the user confirms this notice does the app prompt for the PIN and enable the setting.

### 4.4. Unlock Animation Adjustment (Sweep Suppression)

When `showVaultPinDots` is enabled on a PIN-protected vault:

- `VaultPinDotsView` renders a bordered rectangular surface (`54dp` height, `16dp` corner radius) that occupies the entire PIN indicator frame.
- Running the horizontal light sweep (`vault_unlock_sweep`) across this bordered rectangle causes a visual collision where the line passes through a solid card rather than open space.
- Therefore, in both `VaultSecurityDialog` and `VaultLockActivity`, `sweep` is set to `null` inside `VaultShutterAnimator` whenever `currentType == PROTECTION_PIN && config.showVaultPinDots`.
- All other parts of the shutter unlock animation (shackle lift, emblem glow, staggered per-dot confirmation pulse, and upward panel exit) remain active. When `showVaultPinDots` is turned off (or when an alphanumeric password is used, which has its own dedicated 24dp open corridor above the text field), the horizontal light sweep plays normally.

---

## 5. Summary of Alternatives Considered

| Option | Description | Verdict |
|---|---|---|
| **Option A: Complete Removal** | Remove `showVaultPinDots`, `vaultPinLength`, and `VaultPinDotsView` entirely; always show growing `•` bullets. | **Deferred.** Eliminates all length metadata leakage and UI complexity, and remains a valid future hardening step if product policy tightens. |
| **Option B: Conditional Persistence + Sweep Fix + User Notice (Chosen)** | Keep the feature opt-in (default off), store `vault_pin_length` on disk **only** while enabled, purge it immediately when disabled, suppress the overlapping sweep line when active, and inform the user of the metadata trade-off before enabling. | **Accepted.** Preserves the UX option (justified by the ~10% mathematical search-space bound) while eliminating silent on-disk leakage for default users. |
| **Option C: Always Persist `vault_pin_length` (Previous Behaviour)** | Write `vault_pin_length` to `SharedPreferences` on every PIN setup/change even when `showVaultPinDots` is off. | **Rejected.** Leaks PIN length metadata on disk for all PIN users without their consent or knowledge. |

**Rationale for deferring Option A.** Complete removal would pre-empt a risk assessment that belongs to the user. A governing design principle of this fork is that security policy is chosen by the user rather than imposed silently by the implementation: a control that trades some protection for usability may remain available when it is opt-in and default-off (§4.1), introduced with informed consent that states both the visual and the on-disk consequences (§4.3), bounded by `VAULT_PIN_DOTS_MAX_LENGTH`, and immediately reversible with the derived metadata purged on disable (§4.1). With those safeguards in place, eliminating the option would withdraw a preference-driven choice from users who deliberately accept the documented trade-off, without reducing the exposure of users who never enable it. Option A is therefore deferred rather than rejected, subject to the revision triggers in §6.

---

## 6. Conditions for Future Revision

This ADR may be revisited to adopt **Option A (Complete Removal)** if:

- Future security audits determine that even opt-in on-screen/on-disk PIN length disclosure conflicts with the app's zero-metadata-leakage goals; or
- Maintaining two parallel PIN indicator layouts (`VaultPinDotsView` vs. `vaultPinDotsLegacy`) is no longer justified by user adoption.
