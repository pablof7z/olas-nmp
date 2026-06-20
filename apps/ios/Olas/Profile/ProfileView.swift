import SwiftUI

struct ProfileView: View {
    let pubkey: String?
    let isOwn: Bool

    @State private var profile = OlasProfile(pubkey: "")
    @State private var posts: [PhotoPost] = []
    @State private var followingCount = 0
    @State private var followerCount = 0
    @State private var showSignIn = false
    @Environment(PhotoLiftState.self) private var photoLift

    private var resolvedPubkey: String {
        pubkey ?? NMPBridge.shared.activeAccountPubkey ?? ""
    }

    @State private var showSettings = false

    var body: some View {
        // Own profile is a tab root and owns its NavigationStack; when pushed
        // from another screen (e.g. Search) it inherits the pusher's stack and
        // must NOT nest a second one.
        if isOwn {
            NavigationStack { profileContent }
        } else {
            profileContent
        }
    }

    private var navigationTitleText: String {
        if isOwn { return "Profile" }
        let name = profile.displayNameOrName
        return name.isEmpty ? "Profile" : name
    }

    private var profileContent: some View {
        Group {
            if isOwn && resolvedPubkey.isEmpty {
                signInPrompt
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ProfileHeaderView(
                            profile: profile,
                            isOwn: isOwn,
                            followingCount: followingCount,
                            followerCount: followerCount
                        )
                        Rectangle()
                            .fill(Color.olasBorder)
                            .frame(height: 1)
                            .padding(.vertical, OlasSpacing.sm)
                        ProfileGridView(posts: posts) { post in
                            // Open via zoom transition to the same fullscreen viewer as the feed.
                            withAnimation(.olasStandard) {
                                photoLift.open(post: post, index: 0, context: "profile")
                            }
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.olasBackground)
        .navigationTitle(navigationTitleText)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isOwn && !resolvedPubkey.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showSettings = true } label: {
                        Image(systemName: "gearshape")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.olasText1)
                    }
                }
            }
        }
        .onAppear { loadProfile() }
        .onChange(of: NMPBridge.shared.activeAccountPubkey) { _, newValue in
            // Re-load when sign-in completes so the handler sees the correct pubkey.
            if isOwn { loadProfile() }
            // Logout: account cleared → close Settings so we land on the sign-in prompt.
            if isOwn, newValue == nil { showSettings = false }
        }
        .onDisappear {
            let pk = resolvedPubkey
            if !pk.isEmpty {
                NMPBridge.shared.releaseProfile(pubkey: pk)
                NMPBridge.shared.closeAuthorPhotoFeed(pubkey: pk)
            }
        }
        .sheet(isPresented: $showSignIn) {
            SignInSheet()
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack { SettingsView() }
        }
    }

    private var signInPrompt: some View {
        VStack(spacing: OlasSpacing.xl) {
            Spacer()
            Image(systemName: "person.circle")
                .font(.system(size: 64, weight: .thin))
                .foregroundStyle(Color.olasText3)
            VStack(spacing: OlasSpacing.xs) {
                Text("Sign in to Olas")
                    .font(OlasFont.title1())
                    .foregroundStyle(Color.olasText1)
                Text("Use your Nostr key to access your profile and post photos.")
                    .font(OlasFont.body())
                    .foregroundStyle(Color.olasText2)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, OlasSpacing.xxl)
            }
            Button {
                showSignIn = true
            } label: {
                Text("Sign In")
                    .font(OlasFont.headline())
                    .foregroundStyle(Color.olasBackground)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(OlasPressedButtonStyle())
            .padding(.horizontal, OlasSpacing.xl)
            Spacer()
        }
    }

    private func loadProfile() {
        let pk = resolvedPubkey
        guard !pk.isEmpty else { return }
        profile = OlasProfile(pubkey: pk)
        posts = []
        NMPBridge.shared.claimProfile(pubkey: pk)
        NMPBridge.shared.openAuthorPhotoFeed(pubkey: pk)
        // Apply cached profile immediately if available (from snapshot).
        if let cached = NMPBridge.shared.profileCache[pk] {
            applyProfileWire(cached, pubkey: pk)
        }
        NMPBridge.shared.addEventHandler { [pk] json in
            handleProfileEvent(json, pubkey: pk)
        }
        NMPBridge.shared.addProfileUpdateHandler { [pk] cache in
            guard let cached = cache[pk] else { return }
            applyProfileWire(cached, pubkey: pk)
        }
        // NMP event observer only fires for events NEW to the local EventStore.
        // Published events from earlier sessions are already in LMDB and won't
        // re-fire. Load them directly from publish_intents/ as a reliable fallback.
        Task { loadPostsFromPublishIntents(pubkey: pk) }
    }

    private func handleProfileEvent(_ json: String, pubkey: String) {
        guard let data = json.data(using: .utf8),
              let event = try? JSONDecoder().decode(NostrEvent.self, from: data) else { return }

        if event.kind == 0, event.author == pubkey {
            // Use the Rust decoder for consistent key casing and pubkey injection.
            if let profileJSON = NMPBridge.shared.decodeKind0Event(json),
               let profileData = profileJSON.data(using: .utf8),
               let parsed = try? JSONDecoder().decode(OlasProfile.self, from: profileData) {
                profile = parsed
            }
        }

        if event.kind == 20, event.author == pubkey {
            if let postJSON = NMPBridge.shared.decodeKind20Event(json),
               let postData = postJSON.data(using: .utf8),
               let post = try? JSONDecoder().decode(PhotoPost.self, from: postData) {
                if !posts.contains(where: { $0.id == post.id }) {
                    posts.insert(post, at: 0)
                }
            }
        }
    }

    private func loadPostsFromPublishIntents(pubkey: String) {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let dir = docs.appendingPathComponent("publish_intents")
        guard let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) else { return }

        let decoder = JSONDecoder()
        var loaded: [PhotoPost] = []
        for fileURL in files where fileURL.pathExtension == "json" {
            guard let data = try? Data(contentsOf: fileURL),
                  let intent = try? decoder.decode(PublishIntentFile.self, from: data),
                  intent.event.unsigned.kind == 20,
                  intent.event.unsigned.pubkey == pubkey else { continue }

            let u = intent.event.unsigned
            let kernel = KernelEventPayload(
                id: intent.event.id,
                author: u.pubkey,
                kind: u.kind,
                createdAt: u.createdAt,
                tags: u.tags,
                content: u.content
            )
            guard let kernelData = try? JSONEncoder().encode(kernel),
                  let kernelJSON = String(data: kernelData, encoding: .utf8),
                  let postJSON = NMPBridge.shared.decodeKind20Event(kernelJSON),
                  let postData = postJSON.data(using: .utf8),
                  let post = try? decoder.decode(PhotoPost.self, from: postData) else { continue }
            loaded.append(post)
        }

        guard !loaded.isEmpty else { return }
        loaded.sort { $0.createdAt > $1.createdAt }
        Task { @MainActor in
            for post in loaded where !posts.contains(where: { $0.id == post.id }) {
                posts.append(post)
            }
            posts.sort { $0.createdAt > $1.createdAt }
        }
    }

    private func applyProfileWire(_ wire: ProfileWire, pubkey: String) {
        if let name = wire.displayName, !name.isEmpty { profile.displayName = name }
        if let pic = wire.pictureUrl, !pic.isEmpty { profile.picture = pic }
    }
}

// MARK: - Publish Intent decoding (for LMDB-bypass own-post loading)

private struct PublishIntentFile: Decodable {
    struct EventWrapper: Decodable {
        let id: String
        let unsigned: UnsignedEvent
    }
    struct UnsignedEvent: Decodable {
        let pubkey: String
        let kind: Int
        let tags: [[String]]
        let content: String
        let createdAt: Int64
        enum CodingKeys: String, CodingKey {
            case pubkey, kind, tags, content
            case createdAt = "created_at"
        }
    }
    let event: EventWrapper
}

private struct KernelEventPayload: Encodable {
    let id: String
    let author: String
    let kind: Int
    let createdAt: Int64
    let tags: [[String]]
    let content: String
    let relayProvenance: [String] = []
    enum CodingKeys: String, CodingKey {
        case id, author, kind, tags, content
        case createdAt = "created_at"
        case relayProvenance = "relay_provenance"
    }
}

// MARK: - Sign In Sheet

struct SignInSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showManualKey = false
    @State private var nsec = ""
    @State private var isSigningIn = false

    var body: some View {
        NavigationStack {
            VStack(spacing: OlasSpacing.xl) {
                Spacer()
                NostrLoginBlock(
                    onSignerSelected: { signer in
                        // NIP-55 / NIP-46 handshake via signer URL scheme — placeholder
                        _ = signer
                    },
                    onManualKey: {
                        showManualKey = true
                    }
                )
                .padding(.horizontal, OlasSpacing.xl)
                Spacer()
            }
            .background(Color.olasBackground)
            .navigationTitle("Sign in to Olas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.foregroundStyle(Color.olasText2)
                }
            }
            .sheet(isPresented: $showManualKey) {
                ManualKeySheet()
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Manual Key Sheet

struct ManualKeySheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var nsec = ""
    @State private var isSigningIn = false
    @State private var error: String?

    private var canSubmit: Bool {
        nsec.hasPrefix("nsec1") && nsec.count > 10 && !isSigningIn
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: OlasSpacing.xl) {
                Spacer()
                VStack(spacing: OlasSpacing.xxs) {
                    Text("Enter your key")
                        .font(OlasFont.title1())
                        .foregroundStyle(Color.olasText1)
                    Text("Paste your nsec or hex private key.")
                        .font(OlasFont.subheadline())
                        .foregroundStyle(Color.olasText2)
                }
                VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                    TextField("nsec1...", text: $nsec)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText1)
                        .padding(OlasSpacing.md)
                        .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.olasBorder, lineWidth: 1))
                        .padding(.horizontal, OlasSpacing.xl)
                        .submitLabel(.go)
                        .onSubmit {
                            guard canSubmit else { return }
                            isSigningIn = true
                            NMPBridge.shared.signInNsec(nsec)
                            isSigningIn = false
                            dismiss()
                        }
                    if let error {
                        Text(error)
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasDestructive)
                            .padding(.horizontal, OlasSpacing.xl)
                    }
                }
                Button {
                    isSigningIn = true
                    NMPBridge.shared.signInNsec(nsec)
                    isSigningIn = false
                    dismiss()
                } label: {
                    Group {
                        if isSigningIn {
                            ProgressView().tint(Color.olasBackground)
                        } else {
                            Text("Sign in")
                                .font(OlasFont.headline())
                                .foregroundStyle(Color.olasBackground)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(canSubmit ? Color.olasText1 : Color.olasText3, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(OlasPressedButtonStyle())
                .disabled(!canSubmit)
                .padding(.horizontal, OlasSpacing.xl)
                .accessibilityIdentifier("signInButton")
                Spacer()
            }
            .background(Color.olasBackground)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.foregroundStyle(Color.olasText2)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .preferredColorScheme(.dark)
    }
}
