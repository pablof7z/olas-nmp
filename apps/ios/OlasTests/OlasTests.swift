import XCTest
@testable import Olas

@MainActor
final class OlasTests: XCTestCase {
    func testDefaultRelaysComeFromRustBridge() {
        let relays = NMPBridge.shared.defaultRelays()

        XCTAssertEqual(relays.map(\.id), ["damus", "nos", "primal", "purplepages"])
        XCTAssertEqual(
            relays.map(\.url),
            [
                "wss://relay.damus.io",
                "wss://nos.lol",
                "wss://relay.primal.net",
                "wss://purplepag.es",
            ]
        )
        XCTAssertEqual(relays.map(\.role), ["both", "both", "both", "indexer"])
    }

    func testPhotoFeedJSONPreservesRepostAttribution() throws {
        let json = """
        [
          {
            "event_id": "picture-event",
            "author": "picture-author",
            "images": [
              {
                "url": "https://example.test/photo.jpg",
                "sha256": null,
                "mime": "image/jpeg",
                "dimensions": { "width": 1200, "height": 800 },
                "blurhash": null,
                "alt": "Test photo"
              }
            ],
            "content": "A photo from Rust",
            "created_at": 1234,
            "repostedBy": {
              "authorPubkey": "repost-author",
              "repostEventId": "repost-wrapper",
              "repostCreatedAt": 1300
            }
          }
        ]
        """

        let posts = try JSONDecoder().decode([PhotoPost].self, from: Data(json.utf8))

        XCTAssertEqual(posts.count, 1)
        XCTAssertEqual(posts[0].id, "picture-event")
        XCTAssertEqual(posts[0].authorPubkey, "picture-author")
        XCTAssertEqual(posts[0].caption, "A photo from Rust")
        XCTAssertEqual(posts[0].images[0].dimensions?.width, 1200)
        XCTAssertEqual(posts[0].hashtags, [])
        XCTAssertEqual(posts[0].repostedBy?.authorPubkey, "repost-author")
        XCTAssertEqual(posts[0].repostedBy?.repostEventId, "repost-wrapper")
        XCTAssertEqual(posts[0].repostedBy?.repostCreatedAt, 1300)
    }

    func testFeedViewModelAcceptsOnlyActiveFeedKey() throws {
        let post = try XCTUnwrap(
            try JSONDecoder().decode(
                [PhotoPost].self,
                from: Data(
                    """
                    [{
                      "event_id": "network-picture",
                      "author": "network-author",
                      "images": [{ "url": "https://example.test/network.jpg" }],
                      "content": "Network photo",
                      "created_at": 99
                    }]
                    """.utf8
                )
            ).first
        )
        let vm = FeedViewModel()
        vm.start(mode: .network)

        vm.handlePhotoFeedSnapshot(key: "olas.following_feed", posts: [post])
        XCTAssertTrue(vm.posts.isEmpty)
        XCTAssertTrue(vm.isLoading)

        vm.handlePhotoFeedSnapshot(key: "olas.network_feed", posts: [post])
        XCTAssertEqual(vm.posts.map(\.id), ["network-picture"])
        XCTAssertFalse(vm.isLoading)
    }
}
