# Development Commands

Use the root `justfile` to drive common repo workflows.

## Common Commands

```bash
just                    # list all commands
just install            # install Rust and Android dependencies
just build              # build Android app
just clean              # clean Android and Rust build caches
just generate           # regenerate Android models + Gemstone bindings
just localize           # update Android localization from localization/app/*.ftl
just sync-upstream      # fetch upstream/main, merge, and strip iOS paths automatically
just start-emulator     # start the Android emulator
just run-android        # build, install, and run Android
just bump patch         # bump the repo patch version
just bump minor         # bump the repo minor version
just bump major         # bump the repo major version
```

## Platform Entry Points

```bash
just android build          # build Android
just android start-emulator # start the Android emulator
just android test           # run Android unit tests
just core test              # run Core unit tests
just core lint              # run Core clippy lints
```

Load the platform guide before dropping into platform-specific commands or tooling details.
