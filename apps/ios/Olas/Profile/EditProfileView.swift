import SwiftUI

struct EditProfileView: View {
    let profile: OlasProfile
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var displayName: String
    @State private var about: String
    @State private var nip05: String
    @State private var lud16: String
    @State private var isSaving = false
    @State private var saveError: String?

    init(profile: OlasProfile) {
        self.profile = profile
        _name = State(initialValue: profile.name ?? "")
        _displayName = State(initialValue: profile.displayName ?? "")
        _about = State(initialValue: profile.about ?? "")
        _nip05 = State(initialValue: profile.nip05 ?? "")
        _lud16 = State(initialValue: profile.lud16 ?? "")
    }

    var body: some View {
        Form {
            Section("Profile") {
                labeledField("Display name", text: $displayName)
                labeledField("Name", text: $name)
            }

            Section("About") {
                ZStack(alignment: .topLeading) {
                    TextEditor(text: $about)
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText1)
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: 80)
                        .onChange(of: about) { _, new in
                            if new.count > 300 { about = String(new.prefix(300)) }
                        }
                    if about.isEmpty {
                        Text("Bio")
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText3)
                            .padding(.top, 8)
                            .padding(.leading, 5)
                            .allowsHitTesting(false)
                    }
                }
                HStack {
                    Spacer()
                    Text("\(about.count)/300")
                        .font(OlasFont.caption())
                        .foregroundStyle(about.count >= 300 ? Color.olasDestructive : Color.olasText3)
                }
            }

            Section("Identity") {
                labeledField("NIP-05 username", text: $nip05)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
                labeledField("Lightning address", text: $lud16)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
            }

            if let error = saveError {
                Section {
                    Text(error)
                        .foregroundStyle(Color.olasDestructive)
                        .font(OlasFont.subheadline())
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Edit Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { save() }
                    .foregroundStyle(Color.olasBlue)
                    .disabled(isSaving)
            }
        }
    }

    private func labeledField(_ label: String, text: Binding<String>) -> some View {
        TextField(label, text: text)
            .font(OlasFont.body())
            .foregroundStyle(Color.olasText1)
    }

    private func save() {
        isSaving = true
        saveError = nil

        let profileDict: [String: String?] = [
            "name": name.isEmpty ? nil : name,
            "display_name": displayName.isEmpty ? nil : displayName,
            "about": about.isEmpty ? nil : about,
            "nip05": nip05.isEmpty ? nil : nip05,
            "lud16": lud16.isEmpty ? nil : lud16,
            "picture": profile.picture,
            "banner": profile.banner
        ]

        // Dispatch the canonical nmp.publish PublishProfile action. The kernel
        // serializes the flat field map into the kind:0 content, stamps
        // created_at, signs with the active account, and routes via NIP-65 — no
        // event JSON or signing is built natively (AGENTS.md: Rust owns logic).
        let filteredDict = profileDict.compactMapValues { $0 }
        guard let fieldsData = try? JSONEncoder().encode(filteredDict),
              let fieldsStr = String(data: fieldsData, encoding: .utf8) else {
            saveError = "Couldn't prepare profile data."
            isSaving = false
            return
        }
        let actionJSON = #"{"PublishProfile":{"fields":\#(fieldsStr)}}"#

        Task {
            let terminal = await NMPBridge.shared.dispatchAndAwaitResult(
                namespace: "nmp.publish", json: actionJSON
            )
            await MainActor.run {
                isSaving = false
                if terminal?.succeeded == true {
                    dismiss()
                } else {
                    saveError = "Couldn't publish profile update."
                }
            }
        }
    }
}
