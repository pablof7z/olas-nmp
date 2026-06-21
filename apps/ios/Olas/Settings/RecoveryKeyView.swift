import SwiftUI

// MARK: - Recovery Key View (P3-D)
//
// Shows the active account's Recovery Key behind a tap-to-reveal blur.
// CRITICAL: the word "nsec", "private key", or "seed" MUST NOT appear anywhere
// in this file. The user-facing term is always "Recovery Key".

struct RecoveryKeyView: View {
    @State private var revealed = false
    @State private var copied = false

    // Resolved once at appear — do not log or persist.
    @State private var recoveryKey: String? = nil

    var body: some View {
        List {
            if let key = recoveryKey {
                Section {
                    keyDisplayRow(key)
                    if revealed {
                        copyButton(key)
                    }
                } footer: {
                    Text("Anyone with this Recovery Key has full access to your account. Store it somewhere safe and never share it.")
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText3)
                }

                Section {
                    Label("Write it down offline — not in screenshots or cloud notes.", systemImage: "pencil.and.list.clipboard")
                    Label("Olas staff will never ask for your Recovery Key.", systemImage: "shield.checkmark")
                }
                .font(OlasFont.caption())
                .foregroundStyle(Color.olasText2)
            } else {
                Section {
                    Text("No local account found. Sign in with a local key to export a Recovery Key.")
                        .foregroundStyle(Color.olasText2)
                        .font(OlasFont.body())
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Back Up Account")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            // Fetch once and store — never re-fetch to avoid unnecessary exposure.
            recoveryKey = NMPBridge.shared.activeAccountRecoveryKey()
        }
        .onDisappear {
            // Clear the revealed state so the key is masked if the user navigates
            // back to this screen (e.g. via the navigation stack).
            revealed = false
        }
    }

    // MARK: - Key display cell

    @ViewBuilder
    private func keyDisplayRow(_ key: String) -> some View {
        ZStack {
            // Always render the key text so the layout is stable.
            Text(key)
                .font(.system(.caption, design: .monospaced))
                .foregroundStyle(revealed ? Color.olasText1 : Color.clear)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, OlasSpacing.xs)

            if !revealed {
                // Blur overlay: three rows of bullet dots to mimic key length.
                VStack(spacing: 4) {
                    ForEach(0..<3, id: \.self) { _ in
                        Text(String(repeating: "● ", count: 12))
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(Color.olasText3)
                    }
                }
                .blur(radius: 3)
                .frame(maxWidth: .infinity, alignment: .leading)

                // Tap-to-reveal affordance
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) { revealed = true }
                } label: {
                    Text("Tap to reveal")
                        .font(OlasFont.subheadline())
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.olasText1)
                        .padding(.horizontal, OlasSpacing.sm)
                        .padding(.vertical, OlasSpacing.xs)
                        .background(Color.olasSurface2, in: RoundedRectangle(cornerRadius: 8))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.olasSurface.opacity(0.7))
            }
        }
        .background(Color.olasBackground)
    }

    // MARK: - Copy button

    @ViewBuilder
    private func copyButton(_ key: String) -> some View {
        Button {
            // Use setItems with an expiration date so the key auto-clears from the
            // pasteboard after 60 seconds — it should not linger in clipboard history.
            UIPasteboard.general.setItems(
                [[UIPasteboard.typeAutomatic: key]],
                options: [.expirationDate: Date().addingTimeInterval(60)]
            )
            withAnimation { copied = true }
            Task {
                try? await Task.sleep(for: .seconds(2))
                withAnimation { copied = false }
            }
        } label: {
            HStack {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                Text(copied ? "Copied — clears in 60s" : "Copy Recovery Key")
            }
            .frame(maxWidth: .infinity)
            .foregroundStyle(copied ? Color.olasSuccess : Color.olasBlue)
        }
    }
}
