# ADR 0006 — Class 3 (STRONG) biometrics only for vault access

- **Status:** Accepted — **Class 2 (weak) biometrics are not supported; no device-specific exception will be defined (2026-11-20)**
- **Date:** 2026-11-20
- **Related:** [ADR 0002](0002-operational-security-hardening.md) (operational security hardening), [ADR 0005](0005-kek-rotation.md) (key hierarchy and local threat model)

> **Summary Decision.** The encrypted vault's biometric lock accepts only **Class 3 / `BIOMETRIC_STRONG`** biometrics, and every authentication is bound to a hardware-backed Keystore key via `BiometricPrompt.CryptoObject`. Class 2 / `BIOMETRIC_WEAK` (e.g. the 2D face unlock on the Samsung Galaxy Tab S10 Lite) is **deliberately rejected**; no relaxation is applied, including an "exception for these devices only". The enrollment offer after vault creation / password change appears **only when a strong biometric is actually enrolled**; otherwise setup stays silent. On devices without a usable strong biometric the settings row is not hidden: it stays visible but passive (grey), with an **info icon** that opens the reason dialog on demand. Android sends no broadcast for enrollment changes, so the state is re-evaluated in `SettingsActivity.onResume()`; leaving and re-entering Settings is enough to update the appearance. No popup is shown when biometrics become available, and the switch stays off by default.

---

## 1. Problem and Context

User observations showed two different device behaviours:

- On a phone with a fingerprint sensor, the "no biometrics enrolled" warning appeared after the enrolled biometric was removed.
- On a Samsung Galaxy Tab S10 Lite, the biometric setting **never appeared at all**, and no warning was shown either.

The cause was hardware, not application logic. The Tab S10 Lite has **no fingerprint sensor; only 2D (Class 2) face recognition**. The application's single gate, `canAuthenticate(BIOMETRIC_STRONG)`, returned `BIOMETRIC_ERROR_NO_HARDWARE` / `BIOMETRIC_ERROR_NONE_ENROLLED` on that device. With the old behaviour the row was set to `GONE` through `beVisibleIf(...)`, leaving nothing that could be tapped to produce the warning. The result was a silent hiding.

This ADR records both that user-experience defect and the **security decision behind it**.

---

## 2. Decision

| Layer | Rule |
|---|---|
| Device gate | `BiometricManager.canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS` |
| Key gate | `setUserAuthenticationRequired(true)`, `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` on API 30+ (API 26–29 uses `setUserAuthenticationValidityDurationSeconds(-1)`), `setUnlockedDeviceRequired(true)` on API 28+, `setInvalidatedByBiometricEnrollment(true)`, hardware-backed key (TEE/StrongBox) |
| Authentication | `BiometricPrompt.CryptoObject(cipher)` in every enrollment and unlock flow |
| Device PIN/password | `DEVICE_CREDENTIAL` is accepted for four **non-crypto device-owner confirmations** (disable biometrics, export vault, archive vault, unarchive vault); it is **never** an unlock or enrollment fallback for the vault. On API 28–29 these confirmations fall back to `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` because of an AndroidX restriction. |
| Weak biometrics | Not supported for vault unlock/enrollment, no silent or explicit fallback (API 28–29 device-owner confirmations excepted as above) |
| UI | Row is visible whenever a vault exists; when a usable strong biometric/key is missing the switch is passive (grey) with an info icon opening the reason dialog (not a toast, not an always-visible text) |
| Enrollment offer | Shown only if `canAuthenticate(BIOMETRIC_STRONG) == SUCCESS` and a usable hardware key exists; otherwise no note and no config rewrite during setup |
| Enrollment changes | No broadcast/callback is available; `SettingsActivity.onResume()` re-checks the state. No popup; the setting activates on returning to Settings and remains off by default |

`CryptoManagerBiometrics.kt` and `BiometricAvailability.kt` are the single implementation points for these rules. The `canAuthenticate` result (`NO_HARDWARE`, `NONE_ENROLLED`, `HW_UNAVAILABLE`, `SECURITY_UPDATE_REQUIRED`, key failure) is mapped to a specific user-facing message, localized into 9 audited languages plus English/Turkish.

---

## 3. Rationale

### 3.1. The platform contract

The AOSP biometric class table is explicit:

| Class | BiometricPrompt | Keystore (key release) | CryptoObject |
|---|---|---|---|
| Class 3 / STRONG | Yes | Yes | Yes |
| Class 2 / WEAK | Yes | **No** | **No** |
| DEVICE_CREDENTIAL | Yes | Yes (time-based) | Limited |

Android Keystore has **no** `AUTH_BIOMETRIC_WEAK` key authorization. AndroidX `BiometricPrompt.authenticate(info, crypto)` throws `IllegalArgumentException` when the allowed authenticators include Class 2. Unlocking a cryptographic key with a weak face is therefore impossible at the API level.

The platform does not do this arbitrarily: Class 2 has a high presentation-attack rate (AOSP: SAR 7–20%; 2D face can be fooled with a photo). Android does not allow such a result to unlock critical keys used for payments or encryption.

### 3.2. Why "but the TEE is there" is not sufficient

Class 2 biometric templates are also stored inside the TEE/Secure Element and matching also happens in the secure environment (as required by the Android CDD). This conflates **two separate gates**:

1. **Biometric gate (TEE):** stores the template and says "this is the user".
2. **Key gate (Keystore / StrongBox):** authorizes use of the cryptographic key.

The TEE provides the first gate for Class 2; Android deliberately does not open the second one. Adding an exception means writing *application code* that connects the first gate's result to the second one — the security boundary moves from hardware to software.

### 3.3. What CryptoObject adds

- **Authorization is enforced in hardware.** When the app calls `cipher.doFinal()`, Keystore/StrongBox checks whether strong authentication occurred for that operation; otherwise it returns `UserNotAuthenticatedException`. The app cannot fake the "authenticated" state.
- **Per-operation authorization.** With `setUserAuthenticationParameters(0, ...)`, authorization is bound to a single `Cipher` instance; an authentication from another moment cannot be reused.
- **The key never leaves secure hardware.** Key material never enters the application process and is non-exportable.
- **Enrollment changes kill the key.** `setInvalidatedByBiometricEnrollment(true)` is enforced by hardware; an attacker who enrolls a new biometric cannot use the vault key.

### 3.4. Risks if the binding is removed

| Scenario | With CryptoObject | Without CryptoObject |
|---|---|---|
| Modified APK | Even if the success callback is faked, the `Cipher` stays unauthorized and decryption fails. | The prompt is skipped and the key is used directly. |
| Root + Frida / LSPosed hook | The key is not in memory; hooking `doFinal` is not enough. | A fake "authenticated" result opens the vault silently. |
| Compromised app process | The attacker must wait for a genuine strong authentication (which the user notices). | The key/password is reachable in-process and can be read in the background. |
| Photo of the user | The system will not release a strong key for a Class 2 result. | A 2D face is accepted, so a photo is sufficient. |
| New biometric enrollment | Hardware invalidates the key; the password is required. | The key survives; the attacker enrolls their own face and unlocks. |

On a rooted device, content is visible in process memory *after* the user has legitimately unlocked the vault — that is identical in both designs. The extra guarantee CryptoObject provides is: **decryption cannot be triggered unless the user actually authenticated.**

### 3.5. Product integrity

This fork is defined by security: Argon2id target unlock time, screenshot blocking, hidden keypad, PIN dots, RAM zeroization, lock on vault exit. A weak-biometric exception weakens that integrity with a single decision and creates the misconception that "biometric = strong".

---

## 4. User Experience Decision

Independently of the security decision, the old behaviour was wrong: the settings row disappeared silently with no explanation. The new behaviour is:

- The row is **always visible** when a vault exists and storage permission is granted;
- if no strong biometric is usable, the switch is **passive/grey** and an **info icon** appears at its right; no explanation text is forced into the row;
- tapping the info icon (or the passive row) shows the localized explanation in a **`VaultNoticeDialog`** (`cancelable = true`; a toast is not used because it truncates long text);
- the vault-creation / password-change enrollment note appears **only when a Class 3 biometric is enrolled**. With no enrollment there is no "you cannot add a fingerprint" message during setup — the user discovers the state later in Settings;
- Android does not notify apps when biometric enrollment changes. The state is therefore re-read in `onResume()`. **Leaving and re-entering Settings is the refresh trigger.** If the user enables biometrics in Android settings, no dialog pops up: the info icon disappears, the switch becomes active and remains **off by default**;
- a key generated while nothing was enrolled would be invalidated by a later enrollment (`setInvalidatedByBiometricEnrollment(true)`). `ensureBiometricKeyAvailable()` detects this and clears the stale key/config, so re-enrollment leaves the setting off instead of leaving a dead configuration behind;
- the child row **"Automatically show biometric prompt when returning to app"** is visible only while biometrics are enabled and usable. Because it changes vault-wide security behaviour, **both enabling and disabling it re-verify the vault** through `requireVaultPassword(verifyOnly = true)`. The prompt accepts the vault password/PIN *or* a Class 3 biometric, matching the sibling switches (block screenshots, hide key presses). It is not a setup prerequisite: the row is hidden until biometric enrollment is complete, so there is no biometric catch-22.

The message varies by cause; in particular, "weak face unlock is not accepted; enrol a fingerprint or a Class 3 face unlock" gives the user both the hardware limit and the remedy. This is presented as a **device security-feature limit**, not as an error.

### 4.1. Setup card (3b) and dismissal rules

- Order is strict: **3a (password saved acknowledgement) → 3b (biometric setup card)**. The one-time offer is triggered from the 3a acknowledgement, never directly from the credential callback, so 3b can no longer appear before 3a.
- 3b appears **only when a Class 3 biometric is enrolled**. On devices without one, 3b is skipped and 3a is the last message of the setup flow.
- The card uses the approved **"İz dalgası"** glyph: seven fingerprint ridges are drawn with 90 ms stagger (450 ms each), darken from 30 % to full opacity at 850 ms, then a single background circle fades in with a Gaussian glow (**18 dp blur / 5 dp spread at 38 % alpha** at 35 % progress, settling at **8 dp blur / 1 dp spread at 16 % alpha**). With `ANIMATOR_DURATION_SCALE == 0` the final state is drawn immediately.
- **Dismissal:** only the recovery dialog (6) is **not cancelable**. 3a, the reminder dialog (4) and the 3b card are cancelable with the Back button; tapping outside remains disabled throughout. The old separate platform "offer" dialog was **removed**: `VaultBiometricOfferDialog.showIfNeeded()` now goes straight to the themed 3b card.
- **Enrollment state:** until 3b is completed, `isVaultBiometricEnabled` stays **false**. Closing 3b does not re-show the offer; the user must later open Settings, verify the password and enable biometrics explicitly.
- **Dismissing 3a/3b does not affect reminder scheduling.** Completing 3b does: `completeBiometricSetup()` calls `markVaultPasswordEntered(context.config)` and starts the reminder window at that moment. Dismissing 4/5/6 or closing the app there also does: the notice timestamp was already written when 4 was shown, so the reminder returns after the next full interval (e.g. two weeks).

---

## 5. Modality Selection Is Not the App's Decision

When two strong biometrics — Class 3 3D face unlock **and** fingerprint — are enrolled on the same device, the application does not decide which one is used:

- Android does not let third-party apps select a modality. `BiometricManager.Authenticators` offers only `BIOMETRIC_STRONG`, `BIOMETRIC_WEAK`, `DEVICE_CREDENTIAL` (and `IDENTITY_CHECK` on API 36); there is no `FACE` / `FINGERPRINT` bit. The platform constants `TYPE_FINGERPRINT`, `TYPE_FACE`, `TYPE_IRIS` are `@hide`, so non-system apps cannot access them.
- The app therefore only sends the `BIOMETRIC_STRONG` + `CryptoObject` request. The earlier observation "it added a fingerprint without asking me which one" was also the result of the **system enrollment flow**, not the app.
- The system prompt accepts both modalities. On stable Samsung One UI versions the dialog shows **fingerprint / face tabs** at the top that the user taps to switch; which one is shown first depends on the OEM implementation and the user's **Settings → Biometrics** preference (in practice, fingerprint is usually first). AOSP guidance says that if multiple biometrics are supported, the user should be able to set a default in settings, and Class 3 should be the default otherwise.
- As of 18 November 2026, **One UI 9.5 open beta** removes those tabs and listens for both modalities in a single unified prompt; the first successful one is used without an explicit choice. This is Samsung-specific and still in beta.
- Because a `CryptoObject` is requested, only Class 3 modalities are eligible. A Class 2 (2D) face, even if enrolled, never appears in the prompt — for example, on the Galaxy S8 the crypto prompt showed fingerprint only.
- The app **cannot tell** which modality was used: `AuthenticationResult.getAuthenticationType()` (API 30) only distinguishes biometric from device credential, not face from fingerprint. Consequently there is no modality indicator in the UI; the existing fingerprint-oriented strings/icons should be read as a general "biometric" label.

**Conclusion:** On a device with both Class 3 face and Class 3 fingerprint, either modality can unlock the vault; the choice belongs to the user or to the device's system setting. Forcing a modality in the app is neither possible nor desirable for security reasons, so no code change is made.

> **Test note:** No device with Class 3 3D face unlock was available, so this scenario was **not verified on a physical device**. The behaviour described above is based on Android/AOSP and OEM documentation (see §7).

---

## 6. Rejected Alternatives

1. **Weak biometrics + application-level verification:** rejected because it moves the security boundary from the TEE to the app (§3.3–3.4).
2. **Exception only for Tab S10 Lite / specific devices:** rejected because a hardware exception ties the security model to device identity, expands with each new device and spreads the same weakness.
3. **Silent hiding:** rejected and fixed because it gives the user no information (§4).
4. **Short toast warning / always-visible explanation text:** rejected because the toast truncates long text and permanent text clutters the row; an on-demand info icon plus dialog was adopted instead.
5. **Enrollment note on every vault creation / password change:** rejected because it tells the user they "cannot add biometrics" on devices where the feature is irrelevant; the note is now conditional on an actually enrolled strong biometric.
6. **Popup when Android biometrics become available:** rejected; the user asked for a purely visual change discovered in Settings, so only `onResume()` re-evaluation is used.
7. **Using `DEVICE_CREDENTIAL` for general vault unlock:** rejected for now because it offers no extra convenience (a PIN is still entered) and is out of scope. It may be evaluated in a separate ADR later, but must remain Keystore-backed and must not become an application-level gate.

---

## 7. Verification and Status

- `VaultLockPolicyTest` verifies, at source level, the error-code → message mapping, that the row is not re-hidden by `setupVaultBiometric()`, that the switch is passive when unusable, that the info icon exists, and that the enrollment offer stays silent when no strong biometric is enrolled. `biometricUnavailableReasonDistinguishesWeakFaceHardwareAndKeyFailures` is a direct unit test and does not yet assert `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED`.
- **Password-change enrollment:** `promptChangeVaultPassword()` now defers `lockVaultIfOutsideSession()` until `showVaultPasswordSavedDialog`'s `onDone` (after 3b or a Back dismissal). Wiping the key before 3a caused `completeBiometricSetup()` to fail silently (`unlockedMasterKey ?: return false`).
- The 1:1 comparison tables for the 7 new keys in 9 audited locales (az, de, es, es-419, es-US, ja, nb-NO, nn, zh-TW) were updated under `translation-blind-tests/`.
- `:app:testFossDebugUnitTest` passes; resource merging is error-free.
- **Test coverage limit:** No device with Class 3 3D face-unlock hardware was available, so this modality was **not tested on a physical device**. The behaviour described in §5 is based on Android/AOSP and OEM documentation and still needs verification on such a device.

---

## 8. Conditions for Future Revision

This decision may be reopened only if:

- Android provides a Keystore-backed authorization mechanism for Class 2 biometrics; or
- the product's threat model changes.

In either case the change must be off by default, explicitly confirmed, visibly warned, and still limited to a hardware-backed key. **Silent fallback is never acceptable.**
