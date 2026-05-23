# Remote Module Update Plan

This document preserves the planned architecture for remote module/status updates.
It is a future implementation guide only. The current template should remain local,
safe, and placeholder-focused until these pieces are intentionally built.

## Goal

Allow module menus, game status, defaults, options, notices, and compatibility
metadata to update without requiring users to install a new APK.

The recommended default is remote JSON/data updates, not downloaded executable
logic. The APK should stay the trusted UI/runtime engine, while the backend
provides signed blueprints and values.

```text
APK/runtime engine
  - ImGui rendering
  - component library
  - dynamic menu renderer
  - manifest validation
  - config/profile storage
  - cache/recovery logic

Backend
  - game status
  - license entitlement metadata
  - module manifests
  - component defaults
  - dropdown options
  - notices/maintenance messages
  - supported app/template versions
```

## Security Model

The realistic target is not "uncrackable." The target is to make tampering,
MITM attacks, and casual cracking expensive enough that low-effort attackers
give up.

Use this rule:

```text
TLS protects transport.
Signatures protect trust.
Encryption protects privacy only after trust is already verified.
```

Do not put decryption keys in license metadata. If the app can read a key, a
cracker can extract it. License metadata should describe entitlement, not carry
secrets needed to trust downloaded content.

## Recommended First Version: Signed JSON Updates

Start with remote JSON manifests and statuses.

### Flow

```text
App starts
  -> loads global config/theme
  -> loads local game list fallback
  -> fetches signed remote index
  -> verifies signature with embedded public key
  -> updates game statuses/module manifest URLs
  -> caches last known good index

User selects online game
  -> fetches signed module manifest JSON
  -> verifies manifest signature
  -> validates schema/version/component data
  -> initializes ModuleState from defaults
  -> loads compatible local config/profile values
  -> renders menu through DynamicMenuRenderer
```

### Remote Game Index Example

```json
{
  "schemaVersion": 1,
  "createdAt": "2026-05-23T00:00:00Z",
  "minAppVersion": 1,
  "games": [
    {
      "gameId": "test_game_01",
      "displayName": "Test Game 01",
      "status": "online",
      "moduleId": "test_module",
      "manifestUrl": "https://example.com/modules/test_module.json",
      "message": "Available"
    },
    {
      "gameId": "test_game_02",
      "displayName": "Test Game 02",
      "status": "maintenance",
      "moduleId": "demo_module",
      "manifestUrl": "https://example.com/modules/demo_module.json",
      "message": "Temporarily unavailable"
    }
  ],
  "signature": "base64-ed25519-signature"
}
```

### Remote Module Manifest Example

```json
{
  "manifestVersion": 1,
  "minTemplateVersion": 1,
  "moduleId": "test_module",
  "gameId": "test_game_01",
  "moduleName": "Test Module",
  "version": "1.0.2",
  "tabs": [
    {
      "id": "main",
      "title": "Main",
      "icon": "Main",
      "sections": [
        {
          "id": "general",
          "title": "General",
          "description": "Placeholder module controls.",
          "defaultOpen": true,
          "components": [
            {
              "id": "test_switch",
              "label": "Test Switch",
              "type": "Switch",
              "defaultBool": true
            },
            {
              "id": "test_slider",
              "label": "Test Slider",
              "type": "Slider",
              "defaultFloat": 50,
              "minValue": 0,
              "maxValue": 100,
              "dependency": {
                "mode": "ShowWhen",
                "targetComponentId": "test_switch",
                "expectedBool": true
              }
            }
          ]
        }
      ]
    }
  ],
  "signature": "base64-ed25519-signature"
}
```

## License Entitlement Metadata

License metadata should answer: "What is this user allowed to see?"

It should not include module decryption keys.

```json
{
  "licenseId": "license_abc",
  "deviceIdHash": "optional-device-binding-hash",
  "allowedModules": ["test_module", "demo_module"],
  "channel": "stable",
  "expiresAt": "2026-06-23T00:00:00Z",
  "issuedAt": "2026-05-23T00:00:00Z",
  "signature": "base64-ed25519-signature"
}
```

Validation rules:

- Signature must be valid.
- Expiration must be valid.
- Requested module must exist in `allowedModules`.
- Channel must match the selected update channel.
- Device binding, if used, must match the current local device hash.

## Signature Verification

Embed only the public verification key in the APK.

Keep the private signing key off-device and outside the repository.

Recommended algorithm:

- Ed25519 for simple, fast signing.
- ECDSA P-256 is acceptable if Android platform support is easier.

Verification flow:

```text
1. Download JSON.
2. Extract payload without the signature field.
3. Canonicalize payload.
4. Verify signature with embedded public key.
5. Reject if invalid.
6. Validate schema/version/content.
7. Cache only after all checks pass.
```

Implementation note: choose one canonical JSON signing format before shipping.
Do not sign pretty-printed strings unless serialization is deterministic.

## Hashes and Downloaded Packages

If this project later supports downloaded packages, trust must still come from
signed metadata and hashes.

Package manifest example:

```json
{
  "moduleId": "test_module",
  "version": "1.0.4",
  "minAppVersion": 12,
  "url": "https://cdn.example.com/modules/test_module_1.0.4.pkg",
  "sha256": "expected-file-hash",
  "createdAt": "2026-05-23T00:00:00Z",
  "signature": "base64-ed25519-signature"
}
```

Download validation:

```text
1. Verify signed package manifest.
2. Confirm module entitlement.
3. Download over HTTPS.
4. Hash downloaded file.
5. Compare SHA-256 with signed manifest.
6. Reject if mismatched.
7. Store as pending.
8. Promote to active only after validation succeeds.
9. Keep last known good version for rollback.
```

## Executable Update Boundary

Downloading executable code, native libraries, or runtime scripts is not
recommended for the normal template path.

Risks:

- Larger attack surface.
- Harder crash/debug reports.
- Android compatibility and policy problems.
- Easier to abuse if server/CDN/update path is compromised.
- More version mismatch cases between APK runtime and downloaded logic.

If executable updates are ever considered, gate them behind a separate design
review and require:

- Signed package manifests.
- File hash validation.
- Strict app/module ABI compatibility.
- Rollback protection.
- Last-known-good recovery.
- Crash quarantine.
- No keys stored in license metadata.
- No hidden behavior.

For the current template, remote JSON manifests should be enough.

## Backend Components

Minimal backend:

```text
GET /v1/index
  returns signed game/module index

GET /v1/licenses/{licenseId}
  returns signed entitlement metadata

GET /v1/modules/{moduleId}/manifest
  returns signed module manifest JSON
```

Optional later:

```text
POST /v1/licenses/activate
  binds a license to a device hash

GET /v1/modules/{moduleId}/package
  returns short-lived package download URL

GET /v1/notices
  returns signed global notices/maintenance messages
```

## Client Systems To Add Later

Suggested native/Kotlin systems:

```text
remote/
  RemoteIndexClient
  RemoteModuleManifestClient
  LicenseEntitlementClient
  SignatureVerifier
  CertificatePinningConfig

modules/
  RemoteManifestModuleSource
  ModuleManifestCache
  ModuleEntitlementGate

runtime/
  UpdateRecoveryManager
  LastKnownGoodStore
```

Suggested responsibilities:

- Fetch remote JSON with timeouts.
- Verify signatures.
- Validate schema and template compatibility.
- Merge remote status with local fallback game data.
- Cache last known good manifests.
- Fall back safely when offline or invalid.
- Show toast/error panel for invalid or unavailable modules.

## Cache and Recovery

Cache only verified content.

Recommended cache states:

```text
pending
  downloaded but not fully validated

active
  verified and currently used

last_known_good
  previous verified version kept for recovery

rejected
  invalid signature/hash/schema/version
```

Recovery rules:

- If remote fetch fails, use active cached manifest.
- If active cache is invalid, try last known good.
- If no safe cache exists, fall back to local demo module or show error panel.
- Never render unverified remote content.
- Never overwrite last known good with failed content.

## Anti-Tamper Speed Bumps

These are meant to slow low-effort cracking, not guarantee protection:

- Signed license metadata.
- Signed module manifests.
- SHA-256 validation for any downloaded file.
- HTTPS everywhere.
- Certificate pinning for controlled domains.
- Short-lived download URLs.
- Per-license entitlement checks.
- Version rollback protection.
- R8/ProGuard obfuscation for Kotlin/Java.
- Strip native symbols in release builds.
- Avoid obvious string flags such as `license_valid=true`.
- Keep checks distributed but understandable.
- Rate-limit suspicious licenses/devices server-side.

## Implementation Phases

### Phase 1: Remote Status Index

- Add `GameStatus` enum values: Online, Offline, Maintenance, Updating,
  Unsupported.
- Fetch signed remote index.
- Verify signature.
- Update Games screen statuses.
- Cache last known good index.
- Keep local placeholder status fallback.

### Phase 2: Remote Module Manifests

- Add remote manifest source next to local demo modules.
- Fetch signed module manifest JSON on game selection.
- Validate manifest version, template version, tabs, sections, and components.
- Initialize `ModuleState` from remote defaults.
- Render through existing `DynamicMenuRenderer`.
- Cache last known good manifest per module.

### Phase 3: License Entitlements

- Fetch signed entitlement metadata.
- Gate module visibility/loading by `allowedModules`.
- Add channel support: stable, beta, dev.
- Add expiration handling and clean error UI.

### Phase 4: Operational Hardening

- Add certificate pinning.
- Add rollback protection.
- Add update telemetry/status in the developer debug panel.
- Add cache repair to RecoveryManager.
- Add backend admin workflow outside the app.

### Phase 5: Optional Package Updates

Only consider this if JSON manifests are not enough.

- Require signed package manifest.
- Require hash validation.
- Add pending/active/last-known-good promotion flow.
- Add crash quarantine and rollback.
- Keep executable loading out of the normal template path unless absolutely
  necessary.

## Implementation Checklist

- Remote JSON never trusted until signature passes.
- Server private key never ships in the APK.
- License metadata never contains decryption keys.
- Unknown fields ignored safely.
- Unknown component types render disabled fallback.
- Unsupported template versions fail cleanly.
- Missing/corrupt cache does not crash.
- Offline mode falls back to local/cached data.
- Debug panel shows active manifest source and verification status.
- Toasts explain load failures without exposing sensitive internals.

