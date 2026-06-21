import SwiftUI
import CoreLocation

struct CaptionView: View {
    let images: [UIImage]
    let filteredPreview: UIImage
    let onDone: () -> Void

    @State private var caption = ""
    @State private var altTexts: [Int: String] = [:]
    @State private var locationEnabled = false
    @State private var showServerPicker = false
    @FocusState private var captionFocused: Bool

    // P3-C: @mention autocomplete
    @State private var mentionQuery: String? = nil
    @State private var mentionResults: [ProfileWire] = []

    // Location
    @State private var locationManager = LocationOnce()

    private var serverURL: String {
        NMPBridge.shared.blossomServerURL
    }

    private var serverName: String {
        URL(string: serverURL)?.host ?? serverURL
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                imageStrip

                ZStack(alignment: .bottom) {
                    TextEditor(text: $caption)
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText1)
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: 80, maxHeight: 160)
                        .padding(.horizontal, OlasSpacing.sm)
                        .focused($captionFocused)
                        .onChange(of: caption) { _, newValue in
                            updateMentionQuery(newValue)
                        }

                    // @mention autocomplete dropdown
                    if let _ = mentionQuery, !mentionResults.isEmpty {
                        mentionDropdown
                    }
                }

                Divider().background(Color.olasBorder)

                VStack(alignment: .leading, spacing: OlasSpacing.sm) {
                    ForEach(Array(images.enumerated()), id: \.offset) { idx, img in
                        altTextRow(index: idx, image: img)
                    }
                }
                .padding(.horizontal, OlasSpacing.md)
                .padding(.vertical, OlasSpacing.sm)

                Divider().background(Color.olasBorder)

                Toggle(isOn: $locationEnabled) {
                    HStack(spacing: OlasSpacing.xs) {
                        Image(systemName: locationEnabled ? "location.fill" : "location")
                            .foregroundStyle(locationEnabled ? Color.olasBlue : Color.olasText2)
                        Text("Add location")
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                        if locationEnabled, let loc = locationManager.geohash {
                            Text("· \(loc)")
                                .font(OlasFont.caption())
                                .foregroundStyle(Color.olasText3)
                        } else if locationEnabled && locationManager.geohash == nil {
                            Text("· Locating…")
                                .font(OlasFont.caption())
                                .foregroundStyle(Color.olasText3)
                        }
                    }
                }
                .tint(Color.olasBlue)
                .padding(.horizontal, OlasSpacing.md)
                .padding(.vertical, OlasSpacing.sm)
                .onChange(of: locationEnabled) { _, on in
                    if on { locationManager.request() }
                }

                Divider().background(Color.olasBorder)

                Button { showServerPicker = true } label: {
                    HStack(spacing: OlasSpacing.xs) {
                        Image(systemName: "server.rack")
                            .foregroundStyle(Color.olasText2)
                        Text("Posting to \(serverName)")
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundStyle(Color.olasText3)
                            .font(.system(size: 13))
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, OlasSpacing.md)
                .padding(.vertical, OlasSpacing.sm)

                Divider().background(Color.olasBorder)
            }
        }
        .background(Color.olasBackground)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Share") {
                    captionFocused = false
                    // P0-B: enqueue ALL selected images with per-image alt text.
                    // filteredPreview replaces images[0] (filter was applied).
                    let gh = locationEnabled ? locationManager.geohash : nil
                    var entries: [UploadQueue.ImageEntry] = []
                    for (idx, img) in images.enumerated() {
                        let src = idx == 0 ? filteredPreview : img
                        entries.append(UploadQueue.ImageEntry(image: src, altText: altTexts[idx]))
                    }
                    UploadQueue.shared.enqueue(images: entries, caption: caption, geohash: gh)
                    onDone()
                }
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasBlue)
            }
        }
        .onAppear { captionFocused = true }
    }

    // MARK: - @mention autocomplete

    private var mentionDropdown: some View {
        VStack(spacing: 0) {
            ForEach(mentionResults.prefix(5), id: \.pubkey) { profile in
                Button {
                    insertMention(profile)
                } label: {
                    HStack(spacing: OlasSpacing.xs) {
                        NostrAvatar(profile: profile, size: 28)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(profile.displayName ?? profile.npubShort)
                                .font(OlasFont.subheadline())
                                .foregroundStyle(Color.olasText1)
                            if let nip05 = profile.nip05, !nip05.isEmpty {
                                Text(nip05)
                                    .font(OlasFont.caption())
                                    .foregroundStyle(Color.olasText3)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                    }
                    .padding(.horizontal, OlasSpacing.md)
                    .padding(.vertical, OlasSpacing.xs)
                }
                .buttonStyle(.plain)
                if profile.pubkey != (mentionResults.prefix(5).last?.pubkey ?? "") {
                    Divider().background(Color.olasBorder)
                }
            }
        }
        .background(Color.olasSurface2)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
        .padding(.horizontal, OlasSpacing.sm)
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    private func updateMentionQuery(_ text: String) {
        // Extract the last whitespace-delimited token that begins with '@'
        let tokens = text.components(separatedBy: .whitespacesAndNewlines)
        let last = tokens.last ?? ""
        guard last.hasPrefix("@"), last.count > 1 else {
            mentionQuery = nil
            mentionResults = []
            return
        }
        let query = String(last.dropFirst()).lowercased()
        mentionQuery = query
        let cache = NMPBridge.shared.profileCache
        mentionResults = cache.values.filter { profile in
            let name = (profile.displayName ?? "").lowercased()
            return name.contains(query)
        }.sorted { a, b in
            let na = (a.displayName ?? "").lowercased()
            let nb = (b.displayName ?? "").lowercased()
            let aPre = na.hasPrefix(query)
            let bPre = nb.hasPrefix(query)
            if aPre != bPre { return aPre }
            return na < nb
        }
    }

    private func insertMention(_ profile: ProfileWire) {
        // Replace the trailing '@query' token with 'nostr:<npub>'
        guard !profile.npub.isEmpty else { return }
        var tokens = caption.components(separatedBy: .whitespacesAndNewlines)
        if tokens.last?.hasPrefix("@") == true { tokens.removeLast() }
        let replacement = "nostr:\(profile.npub)"
        tokens.append(replacement)
        caption = tokens.joined(separator: " ") + " "
        mentionQuery = nil
        mentionResults = []
    }

    private var imageStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: OlasSpacing.xs) {
                ForEach(Array(images.enumerated()), id: \.offset) { idx, img in
                    Image(uiImage: idx == 0 ? filteredPreview : img)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 72, height: 72)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
            .padding(OlasSpacing.md)
        }
    }

    private func altTextRow(index: Int, image: UIImage) -> some View {
        HStack(spacing: OlasSpacing.xs) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 32, height: 32)
                .clipShape(RoundedRectangle(cornerRadius: 4))

            TextField("Add alt text (accessibility)", text: Binding(
                get: { altTexts[index] ?? "" },
                set: { altTexts[index] = $0 }
            ))
            .font(OlasFont.caption())
            .foregroundStyle(Color.olasText1)
        }
    }
}

// MARK: - One-shot location + geohash

@Observable @MainActor
final class LocationOnce: NSObject, CLLocationManagerDelegate {
    var geohash: String?
    private let mgr = CLLocationManager()

    override init() {
        super.init()
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func request() {
        guard CLLocationManager.locationServicesEnabled() else { return }
        switch mgr.authorizationStatus {
        case .notDetermined: mgr.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways: mgr.requestLocation()
        default: break
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let status = m.authorizationStatus
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            m.requestLocation()  // safe: called on delegate thread before Task
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        guard let loc = locs.first else { return }
        let lat = loc.coordinate.latitude
        let lon = loc.coordinate.longitude
        m.stopUpdatingLocation()  // called on delegate thread
        Task { @MainActor in
            // Coarse precision 4 (~20km) — canonical across iOS/Android for privacy;
            // matches Android currentCoarseGeohash4 / Rust location_geohash4.
            geohash = NMPBridge.shared.computeGeohash(lat: lat, lon: lon, precision: 4)
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {}
}
