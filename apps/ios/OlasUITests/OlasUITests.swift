import XCTest

@MainActor
final class OlasUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testActivityTrustFilterCopyVisible() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5), "App should launch")

        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 5), "Tab bar should exist")

        let activityTab = tabBar.buttons["Activity"]
        XCTAssertTrue(activityTab.waitForExistence(timeout: 2), "Activity tab should exist")
        activityTab.tap()

        let trustCopy = app.staticTexts["Filtered by your trust settings"]
        XCTAssertTrue(trustCopy.waitForExistence(timeout: 2), "Trust filtering copy should be visible")

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "ActivityTrustFiltering"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testSearchFunctionality() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5), "App should launch")

        let tabBars = app.tabBars
        XCTAssertTrue(tabBars.buttons.count >= 2, "Tab bar should have at least 2 buttons")

        let searchButton = tabBars.buttons.element(boundBy: 1)
        XCTAssertTrue(searchButton.exists, "Search tab button should exist")
        searchButton.tap()

        let searchFields = app.searchFields
        if searchFields.count > 0 {
            let searchField = searchFields.element(boundBy: 0)
            XCTAssertTrue(searchField.waitForExistence(timeout: 2), "Search field should exist")
            searchField.tap()
            searchField.typeText("photography")

            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "SearchResults"
            self.add(attachment)
        } else {
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "SearchViewWithoutSearchBar"
            self.add(attachment)
        }
    }

    func testComposePhotoPublishingFlow() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5))

        let tabBars = app.tabBars
        XCTAssertTrue(tabBars.buttons.count >= 3, "Tab bar should have at least 3 buttons")

        let composeButton = tabBars.buttons.element(boundBy: 2)
        XCTAssertTrue(composeButton.exists, "Compose button should exist")
        composeButton.tap()

        // Step 2: Wait for photo picker to appear
        let photoPickerTitle = app.staticTexts["New Post"]
        XCTAssertTrue(photoPickerTitle.waitForExistence(timeout: 2), "Photo picker should appear")
    }
}
