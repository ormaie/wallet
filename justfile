mod android 'android/justfile'
mod core 'core/justfile'

default:
    @just --list

install:
    @just core install-rust
    @just android install

build:
    @echo "==> Building Android app"
    @just android build

clean:
    @echo "==> Cleaning Android build folders"
    @just android clean
    @echo "==> Cleaning Rust build folders"
    @just core clean

run-android:
    @just android run

start-emulator:
    @just android start-emulator

test:
    @echo "==> Test Android app"
    @just android test

lint:
    @echo "==> Lint Android app"
    @just android lint

test-integration:
    @echo "==> Test Android app integration"
    @just android test-integration

generate: generate-models generate-android-stone

generate-models:
    @just android generate-models

generate-android-stone:
    @just android generate-stone

localize:
    @just android localize

sync-upstream:
    @bash ./scripts/sync-upstream.sh

check-mappers:
    @python3 ./scripts/check-mapper-parity.py

check-docs:
    @python3 ./scripts/check-docs-links.py

check-ffi:
    @python3 ./scripts/check-ffi-surface.py

check-boundaries:
    @python3 ./scripts/check-boundaries.py

bump TARGET="patch":
    @bash ./scripts/bump.sh {{TARGET}}

maestro-smoke PLATFORM="android":
    @bash ./scripts/maestro-smoke.sh {{PLATFORM}}
