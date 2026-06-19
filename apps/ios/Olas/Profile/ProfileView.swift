import SwiftUI

struct ProfileView: View {
    let pubkey: String?
    let isOwn: Bool

    @State private var profile = OlasProfile(pubkey: "")
    @State private var posts: [PhotoPost] = []
    @State private var followingCount = 0
    @State private var followerCount = 0
    @State private var selectedPost: PhotoPost?
    @State private var showSignIn = false

    private var resolvedPubkey: String {
        pubkey ?? NMPBridge.shared.activeAccountPubkey ?? ""
    }

    @State private var showSettings = false

    var body: some View {
        ZStack(alignment: .top) {
            Group {
                if isOwn && resolvedPubkey.isEmpty {
                    signInPrompt
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            Color.clear.frame(height: 44)
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
                                selectedPost = post
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.olasBackground)

            // Custom nav bar — no NavigationStack to avoid UIKitAdaptableTabView on iOS 26
            HStack {
                Spacer()
                Text("Profile")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.olasText1)
                Spacer()
                if isOwn && !resolvedPubkey.isEmpty {
                    Button { showSettings = true } label: {
                        Image(systemName: "gearshape")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.olasText1)
                    }
                    .padding(.trailing, OlasSpacing.md)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
            }
            .frame(height: 44)
            .background(.ultraThinMaterial)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Color.olasBorder).frame(height: 0.5)
            }
        }
        .background(Color.olasBackground)
        .onAppear { loadProfile() }
        .onDisappear {
            if !resolvedPubkey.isEmpty {
                NMPBridge.shared.releaseProfile(pubkey: resolvedPubkey)
            }
        }
        .sheet(isPresented: $showSignIn) {
            SignInSheet()
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack { SettingsView() }
        }
        .sheet(item: $selectedPost) { post in
            PostDetailView(post: post)
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
                    .font(OlasFont.title2())
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
        NMPBridge.shared.claimProfile(pubkey: pk)
        NMPBridge.shared.addEventHandler { [pk] json in
            handleProfileEvent(json, pubkey: pk)
        }
    }

    private func handleProfileEvent(_ json: String, pubkey: String) {
        guard let data = json.data(using: .utf8),
              let event = try? JSONDecoder().decode(NostrEvent.self, from: data) else { return }

        if event.kind == 0, event.author == pubkey {
            if let profileData = event.content.data(using: .utf8),
               var parsed = try? JSONDecoder().decode(OlasProfile.self, from: profileData) {
                parsed = OlasProfile(
                    pubkey: pubkey,
                    name: parsed.name,
                    displayName: parsed.displayName,
                    about: parsed.about,
                    picture: parsed.picture,
                    banner: parsed.banner,
                    nip05: parsed.nip05,
                    lud16: parsed.lud16
                )
                profile = parsed
            }
        }

        if event.kind == 20, event.author == pubkey {
            if let postJSON = NMPBridge.shared.decodeKind20Event(json),
               let postData = postJSON.data(using: .utf8),
               let post = try? JSONDecoder().decode(PhotoPost.self, from: postData),
               !posts.contains(where: { $0.id == post.id }) {
                posts.insert(post, at: 0)
            }
        }
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

// MARK: - Post Detail

struct PostDetailView: View {
    let post: PhotoPost
    @Environment(\.dismiss) private var dismiss
    @State private var vm = FeedViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                PostCardView(post: post, vm: vm)
            }
            .background(Color.olasBackground)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(Color.olasText2)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .preferredColorScheme(.dark)
    }
}
