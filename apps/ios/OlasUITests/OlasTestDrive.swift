import XCTest

class OlasTestDrive: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        // Give NMP time to start up and connect to relays
        sleep(5)
    }

    func testFullFlow() {
        // Screenshot: initial feed state
        let feedAttachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        feedAttachment.name = "01_initial_feed"
        feedAttachment.lifetime = .keepAlways
        add(feedAttachment)

        // Profile tab
        let tabBar = app.tabBars.firstMatch
        let profileTab = tabBar.buttons.element(boundBy: 4)
        if profileTab.exists {
            profileTab.tap()
            sleep(2)

            let profileScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            profileScreenshot.name = "02_profile_tab"
            profileScreenshot.lifetime = .keepAlways
            add(profileScreenshot)

            // Sign in if button visible
            let signInButton = app.buttons["Sign In"]
            if signInButton.exists {
                signInButton.tap()
                sleep(1)

                let nsecField = app.textFields["nsec1..."]
                if nsecField.exists {
                    nsecField.tap()
                    nsecField.typeText("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5")

                    let sheetSignInButton = app.buttons["Sign in"]
                    if sheetSignInButton.exists {
                        sheetSignInButton.tap()
                        sleep(3)
                    }
                }

                let signedInScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                signedInScreenshot.name = "03_after_signin"
                signedInScreenshot.lifetime = .keepAlways
                add(signedInScreenshot)
            }
        }

        // Home tab
        let homeTab = tabBar.buttons.element(boundBy: 0)
        homeTab.tap()
        sleep(2)

        let homeScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        homeScreenshot.name = "04_home_feed"
        homeScreenshot.lifetime = .keepAlways
        add(homeScreenshot)

        // Compose (+) tab
        let composeTab = tabBar.buttons.element(boundBy: 2)
        composeTab.tap()
        sleep(2)

        let composeScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        composeScreenshot.name = "05_compose_picker"
        composeScreenshot.lifetime = .keepAlways
        add(composeScreenshot)

        // Select first photo in grid
        let firstPhoto = app.cells.firstMatch
        if firstPhoto.exists {
            firstPhoto.tap()
            sleep(2)

            let photoSelectedScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            photoSelectedScreenshot.name = "06_photo_selected"
            photoSelectedScreenshot.lifetime = .keepAlways
            add(photoSelectedScreenshot)

            // Tap Next
            let nextButton = app.buttons["Next"]
            if nextButton.exists {
                nextButton.tap()
                sleep(1)

                let filterScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                filterScreenshot.name = "07_filter_view"
                filterScreenshot.lifetime = .keepAlways
                add(filterScreenshot)

                // Apply a filter - tap the second filter item
                let filterItems = app.collectionViews.cells
                if filterItems.count > 1 {
                    filterItems.element(boundBy: 1).tap()
                    sleep(1)
                }

                let filterAppliedScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                filterAppliedScreenshot.name = "08_filter_applied"
                filterAppliedScreenshot.lifetime = .keepAlways
                add(filterAppliedScreenshot)

                // Tap Next again for caption
                let nextButton2 = app.buttons["Next"]
                if nextButton2.exists {
                    nextButton2.tap()
                    sleep(1)

                    let captionScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                    captionScreenshot.name = "09_caption_view"
                    captionScreenshot.lifetime = .keepAlways
                    add(captionScreenshot)

                    // Add caption
                    let captionField = app.textViews.firstMatch
                    if captionField.exists {
                        captionField.tap()
                        captionField.typeText("Testing Olas with real filters! #nostr #photography")
                    }

                    let captionEnteredScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                    captionEnteredScreenshot.name = "10_caption_entered"
                    captionEnteredScreenshot.lifetime = .keepAlways
                    add(captionEnteredScreenshot)

                    // Tap Post
                    let postButton = app.buttons["Post"]
                    if postButton.exists {
                        postButton.tap()
                        sleep(5) // Wait for upload
                    }

                    let finalScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                    finalScreenshot.name = "11_after_post"
                    finalScreenshot.lifetime = .keepAlways
                    add(finalScreenshot)
                }
            }
        }

        // Search tab
        let searchTab = tabBar.buttons.element(boundBy: 1)
        searchTab.tap()
        sleep(2)

        let searchScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        searchScreenshot.name = "12_search_tab"
        searchScreenshot.lifetime = .keepAlways
        add(searchScreenshot)
    }
}
