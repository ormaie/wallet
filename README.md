# Gem Wallet (Android Fork)

[![Core CI](https://github.com/ormaie/wallet/actions/workflows/core-ci.yml/badge.svg)](https://github.com/ormaie/wallet/actions/workflows/core-ci.yml)
[![Android CI](https://github.com/ormaie/wallet/actions/workflows/android-ci.yml/badge.svg)](https://github.com/ormaie/wallet/actions/workflows/android-ci.yml)
[![Repo Checks](https://github.com/ormaie/wallet/actions/workflows/repo-checks.yml/badge.svg)](https://github.com/ormaie/wallet/actions/workflows/repo-checks.yml)
[![License](https://badgen.net/github/license/ormaie/wallet)](https://github.com/ormaie/wallet/blob/main/LICENSE)

An Android-only fork of [gemwalletcom/wallet](https://github.com/gemwalletcom/wallet) built on the shared Rust core, adding **BC-UR air-gapped QR cold wallet signing**, Jdenticon/Blockies wallet avatars, and Android UI refinements.

## Download

Download the signed `gem_wallet_universal_<version>.apk` (and `.sha256` checksum) from the latest [GitHub Release](https://github.com/ormaie/wallet/releases/latest). Unreleased `main` builds are also available as `gem-wallet-universal-release.apk` from [Android CI](https://github.com/ormaie/wallet/actions/workflows/android-ci.yml), or you can build from source below.

## Air-Gapped Cold Wallet Support

Pair offline signers (**AirGap Vault**, **BitTool Signer**) via single-frame or animated `UR:...` fountain codes (`crypto-hdkey`, `crypto-account`, `crypto-multi-accounts`, `bittool-signer-wallet`) so private keys never leave the offline device.

| Network | UR Request / Response | Supported Flows |
| --- | --- | --- |
| EVM chains | `eth-sign-request` / `eth-signature` | EIP-1559 transfers, EIP-712 typed data, personal messages, Permit2 + swaps |
| HyperCore | `eth-sign-request` / `eth-signature` | Multi-step EIP-712 action signing |
| Bitcoin | `crypto-psbt` / `crypto-psbt` | PSBT construction and signed transaction extraction |
| Solana | `sol-sign-request` / `sol-signature` | Transactions and off-chain messages |
| TRON | `tron-sign-request` / `tron-signature` | Transactions and personal messages |

## Layout & Development

- `android/` — Kotlin / Jetpack Compose app
- `core/` — Rust blockchain engine and UniFFI bindings (`gemstone`)

```bash
git clone https://github.com/ormaie/wallet.git
cd wallet

just install        # install Rust toolchain, Android targets, NDK, and JDK
just build          # assemble debug APK
just test           # run Android unit tests
just core test      # run Rust workspace unit tests
just sync-upstream  # merge gemwalletcom/wallet:main while stripping iOS paths
```

To build an unsigned universal release APK locally, run `android/reproducible/docker.sh universal` (or `BUILD_MODE=release ./gradlew :app:assembleUniversalRelease -Pchannel=universal -PuseLegacyPackaging=true` in `android/`).

## Security & License

Upstream third-party security audits ([CertiK](https://static.gemwallet.com/audits/Gem-Wallet-CertiK-Security-Audit-April-2026.pdf), [Cure53](https://static.gemwallet.com/audits/Gem-Wallet-Cure53-Audit-Report-October-2026.pdf)) cover the `gemwalletcom/wallet` baseline only and do not cover custom modules added in this fork.

Licensed under [GPL-3.0](LICENSE).
