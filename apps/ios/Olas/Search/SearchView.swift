import SwiftUI
import Combine

enum SearchResultTab: String, CaseIterable {
    case people = "People"
    case photos = "Photos"
    case tags = "Tags"
}

// MARK: - SearchViewModel

@Observable @MainActor final class SearchViewModel {
    var query: String = ""
    var profileResults: [OlasProfile] = []
    var postResults: [PhotoPost] = []
    var tagResults: [String] = []
    var isSearching: Bool = false

    private let searchSubject = PassthroughSubject<String, Never>()
    private var searchCancellable: AnyCancellable?
    private let consumer = "olas.search"

    // Collected raw events from the search feed, keyed by pubkey (kind:0)
    // or event id (kind:20).
    private var collectedProfiles: [String: OlasProfile] = [:]
    private var collectedPosts: [String: PhotoPost] = [:]
    private var lastSearchQuery: String?

    func startListening() {
        guard !isListening else { return }
        isListening = true

        // Wire up Combine debounce — fires 500 ms after the last keystroke.
        searchCancellable = searchSubject
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .sink { [weak self] debouncedQuery in
                self?.performSearch(query: debouncedQuery)
            }

        NMPBridge.shared.addEventHandler { [weak self] json in
            Task { @MainActor [weak self] in
                self?.handleSearchEvent(json)
            }
        }
    }
    private var isListening = false

    func onQueryChanged(_ newQuery: String) {
        if newQuery.isEmpty {
            closeSearch()
            profileResults = []
            postResults = []
            tagResults = []
            isSearching = false
            return
        }
        isSearching = true
        // Publish into the subject; the Combine pipeline handles debounce.
        searchSubject.send(newQuery)
    }

    private func performSearch(query: String) {
        collectedProfiles = [:]
        collectedPosts = [:]
        if let last = lastSearchQuery {
            NMPBridge.shared.closeSearchFeed(query: last, consumer: consumer)
        }
        lastSearchQuery = query
        NMPBridge.shared.openSearchFeed(query: query, consumer: consumer)
        // Results arrive asynchronously via handleSearchEvent; no sleep needed.
    }

    func closeSearch() {
        guard let last = lastSearchQuery else { return }
        NMPBridge.shared.closeSearchFeed(query: last, consumer: consumer)
        lastSearchQuery = nil
    }

    private func handleSearchEvent(_ json: String) {
        guard !query.isEmpty,
              let data = json.data(using: .utf8),
              // NMP-GAP(#11): Raw event decoding must be replaced by a typed Rust search projection.
              let event = try? JSONDecoder().decode(NostrEvent.self, from: data) else { return }

        switch event.kind {
        case 0:
            if let profileData = event.content.data(using: .utf8),
               var parsed = try? JSONDecoder().decode(OlasProfile.self, from: profileData) {
                parsed = OlasProfile(
                    pubkey: event.author,
                    name: parsed.name,
                    displayName: parsed.displayName,
                    about: parsed.about,
                    picture: parsed.picture,
                    banner: parsed.banner,
                    nip05: parsed.nip05,
                    lud16: parsed.lud16
                )
                collectedProfiles[event.author] = parsed
            }
        case 20:
            // NMP-GAP(#9): PhotoPostParser decodes kind:20 events in Swift. Must be replaced by a typed Rust snapshot projection.
            if let post = PhotoPostParser.parse(event) {
                collectedPosts[event.id] = post
            }
        default:
            break
        }

        // Apply results immediately as events arrive.
        isSearching = false
        applyFilteredResults(query: query)
    }

    private func applyFilteredResults(query: String) {
        let q = query.lowercased()
        profileResults = collectedProfiles.values.filter { profile in
            let name = (profile.name ?? "").lowercased()
            let displayName = (profile.displayName ?? "").lowercased()
            let about = (profile.about ?? "").lowercased()
            let nip05 = (profile.nip05 ?? "").lowercased()
            return name.contains(q) || displayName.contains(q) || about.contains(q) || nip05.contains(q)
        }.sorted { ($0.displayNameOrName) < ($1.displayNameOrName) }

        postResults = collectedPosts.values.filter { post in
            let caption = post.caption.lowercased()
            let tags = post.hashtags.map { $0.lowercased() }
            return caption.contains(q) || tags.contains { $0.contains(q) }
        }.sorted { $0.createdAt > $1.createdAt }

        // Build tag results from hashtags in collected posts
        var tagSet = Set<String>()
        for post in collectedPosts.values {
            for tag in post.hashtags where tag.lowercased().contains(q) {
                tagSet.insert(tag)
            }
        }
        tagResults = Array(tagSet).sorted()
    }
}

// MARK: - SearchView

struct SearchView: View {
    @State private var vm = SearchViewModel()
    @State private var selectedResultTab: SearchResultTab = .people

    var body: some View {
        VStack(spacing: 0) {
            // Custom search bar header — no NavigationStack
            HStack(spacing: OlasSpacing.sm) {
                HStack(spacing: OlasSpacing.xs) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(Color.olasText3)
                        .font(.system(size: 15))
                    TextField("Search people, photos, tags", text: $vm.query)
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText1)
                        .autocorrectionDisabled()
                        .autocapitalization(.none)
                        .submitLabel(.search)
                    if !vm.query.isEmpty {
                        Button { vm.query = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(Color.olasText3)
                        }
                    }
                }
                .padding(.horizontal, OlasSpacing.sm)
                .padding(.vertical, 8)
                .background(Color.olasSurface2, in: RoundedRectangle(cornerRadius: 10))
            }
            .padding(.horizontal, OlasSpacing.md)
            .padding(.vertical, OlasSpacing.xs)
            .background(.ultraThinMaterial)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Color.olasBorder).frame(height: 0.5)
            }

            if vm.query.isEmpty {
                DiscoverView()
            } else {
                searchResults
            }
        }
        .background(Color.olasBackground)
        .onChange(of: vm.query) { _, newQuery in
            vm.onQueryChanged(newQuery)
        }
        .onAppear { vm.startListening() }
        .onDisappear {
            vm.closeSearch()
        }
    }

    private var searchResults: some View {
        VStack(spacing: 0) {
            // Tab bar
            HStack(spacing: 0) {
                ForEach(SearchResultTab.allCases, id: \.self) { tab in
                    Button {
                        withAnimation(.olasStandard) { selectedResultTab = tab }
                    } label: {
                        Text(tab.rawValue)
                            .font(OlasFont.subheadline())
                            .foregroundStyle(selectedResultTab == tab ? Color.olasText1 : Color.olasText3)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .overlay(alignment: .bottom) {
                                if selectedResultTab == tab {
                                    Rectangle().fill(Color.olasText1).frame(height: 1)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .overlay(alignment: .bottom) {
                Rectangle().fill(Color.olasBorder).frame(height: 1)
            }

            if vm.isSearching {
                Spacer()
                ProgressView().tint(Color.olasText2)
                Spacer()
            } else {
                tabResultContent
            }
        }
    }

    @ViewBuilder
    private var tabResultContent: some View {
        switch selectedResultTab {
        case .people:
            if vm.profileResults.isEmpty {
                emptyState("No people found")
            } else {
                List(vm.profileResults, id: \.pubkey) { profile in
                    NavigationLink(destination: ProfileView(pubkey: profile.pubkey, isOwn: false)) {
                        profileRow(profile)
                    }
                    .listRowBackground(Color.olasBackground)
                    .listRowSeparatorTint(Color.olasBorder)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }

        case .photos:
            if vm.postResults.isEmpty {
                emptyState("No photos found")
            } else {
                let columns = Array(repeating: GridItem(.flexible(), spacing: 1), count: 3)
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 1) {
                        ForEach(vm.postResults) { post in
                            NavigationLink(destination: Text(post.id)) {
                                AsyncImage(url: URL(string: post.images.first?.url ?? "")) { img in
                                    img.resizable().scaledToFill()
                                } placeholder: {
                                    Rectangle().fill(Color.olasSurface2)
                                }
                                .aspectRatio(1, contentMode: .fill)
                                .clipped()
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

        case .tags:
            if vm.tagResults.isEmpty {
                emptyState("No tags found")
            } else {
                List(vm.tagResults, id: \.self) { tag in
                    NavigationLink(destination: Text("#\(tag)")) {
                        HStack {
                            Text("#\(tag)")
                                .font(OlasFont.body())
                                .foregroundStyle(Color.olasText1)
                            Spacer()
                        }
                        .padding(.vertical, OlasSpacing.xs)
                    }
                    .listRowBackground(Color.olasBackground)
                    .listRowSeparatorTint(Color.olasBorder)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }

    private func profileRow(_ profile: OlasProfile) -> some View {
        HStack(spacing: OlasSpacing.md) {
            AsyncImage(url: URL(string: profile.picture ?? "")) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                Circle().fill(Color.olasSurface2)
            }
            .frame(width: 44, height: 44)
            .clipShape(Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(profile.displayNameOrName)
                    .font(OlasFont.feedUsername())
                    .foregroundStyle(Color.olasText1)
                if let nip05 = profile.nip05 {
                    Text(nip05)
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText2)
                }
            }
        }
        .padding(.vertical, OlasSpacing.xs)
    }

    private func emptyState(_ message: String) -> some View {
        VStack {
            Spacer()
            Text(message)
                .font(OlasFont.body())
                .foregroundStyle(Color.olasText3)
            Spacer()
        }
    }
}
