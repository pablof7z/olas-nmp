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
        -o apps/android/app/src/main/jniLibs \
        build --release

# ── iOS ───────────────────────────────────────────────────────────────────────

gen-ios:
    xcodegen generate --spec apps/ios/project.yml

build-ios-sim: rust-ios-sim gen-ios
    xcodebuild \
        -project apps/ios/Olas.xcodeproj \
        -scheme Olas \
        -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
        -derivedDataPath apps/ios/DerivedData \
        RUST_TARGET=aarch64-apple-ios-sim \
        build

build-ios-device: rust-ios-device gen-ios
    xcodebuild \
        -project apps/ios/Olas.xcodeproj \
        -scheme Olas \
        -destination 'platform=iOS,id=3C438D9B-2021-5A30-93DB-910F7754F9A2' \
        -derivedDataPath apps/ios/DerivedData \
        RUST_TARGET=aarch64-apple-ios \
        build

# ── Android ───────────────────────────────────────────────────────────────────

build-android: rust-android
    cd apps/android && ./gradlew assembleDebug

# ── Relay ─────────────────────────────────────────────────────────────────────

relay-run:
    cd apps/relay && go run .

relay-build:
    cd apps/relay && go build -o olas-relay .

relay-deploy:
    cd apps/relay && ssh pablo@157.180.102.242 'cd /opt/olas-relay && git pull && go build -o olas-relay . && systemctl restart olas-relay'

# ── Combined ──────────────────────────────────────────────────────────────────

build-all: build-ios-sim build-android
