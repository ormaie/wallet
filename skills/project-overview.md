# Project Overview

Use when you need the repo layout, the layer split between Android and Core, or which guide owns an area.
Gem Wallet is an open-source, multi-chain cryptocurrency wallet. This fork maintains the native Android app backed by the shared Rust core (`ios/` has been removed).

```
wallet/
├── android/      # Kotlin/Compose app (Gradle, Hilt)
├── core/         # Shared Rust blockchain engine (JNI -> Kotlin via Gemstone)
└── justfile      # Root task runner
```

Product domains include chains, assets, wallets, transactions, staking, swaps, QR cold-wallet signing, and WalletConnect.

## Layers

| Layer | Android | Shared |
|-------|---------|--------|
| UI | Jetpack Compose + MVVM | — |
| DI | Hilt | — |
| Data | Room + Repositories | — |
| Blockchain and domain services | Gemstone (JNI) | Rust `core/` |

Domain decisions live in Core-owned Gemstone services; Android implements the store traits and maps the returned records to its UI. The reference is [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md); the migration backlog is [docs/TODO.md](../docs/TODO.md).

Read the repo root guide first, then load the relevant platform guides:

- [`android/AGENTS.md`](../android/AGENTS.md)
- [`core/AGENTS.md`](../core/AGENTS.md)
