set shell := ["zsh", "-cu"]

# ── Rust ──────────────────────────────────────────────────────────────────────

rust-test:
    cargo test -p nmp-app-olas

rust-ios-sim:
    cargo build -p nmp-app-olas --features lmdb-backend --target aarch64-apple-ios-sim

rust-ios-device:
    cargo build -p nmp-app-olas --features lmdb-backend --target aarch64-apple-ios

rust-android:
    cargo ndk \
        --manifest-path apps/olas/nmp-app-olas/Cargo.toml \
        -t arm64-v8a -t x86_64 \
        -o android/app/src/main/jniLibs \
        build --release

# ── iOS ───────────────────────────────────────────────────────────────────────

gen-ios:
    xcodegen generate --spec ios/Olas/project.yml

build-ios-sim: rust-ios-sim gen-ios
    xcodebuild \
        -project ios/Olas/Olas.xcodeproj \
        -scheme Olas \
        -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
        -derivedDataPath ios/DerivedData \
        build

# ── Android ───────────────────────────────────────────────────────────────────

build-android: rust-android
    cd android && ./gradlew assembleDebug

# ── Relay ─────────────────────────────────────────────────────────────────────

relay-run:
    cd relay && go run .

relay-build:
    cd relay && go build -o olas-relay .

# ── Combined ──────────────────────────────────────────────────────────────────

build-all: build-ios-sim build-android
