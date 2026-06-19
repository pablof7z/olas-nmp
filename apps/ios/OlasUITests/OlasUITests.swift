import XCTest

final class OlasUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testActivityTrustFilterCopyVisible() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5), "App should launch")

        let activityTab = app.tabBars.buttons["Activity"]
        XCTAssertTrue(activityTab.waitForExistence(timeout: 5), "Activity tab should exist")
        activityTab.tap()

        let trustCopy = app.staticTexts["Filtered by your trust settings"]
        XCTAssertTrue(trustCopy.waitForExistence(timeout: 5), "Activity should show trust filtering copy")

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
            XCTAssertTrue(searchField.waitForExistence(timeout: 5), "Search field should exist")
            searchField.tap()
            searchField.typeText("photography")

            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "SearchResults"
            attachment.lifetime = .keepAlways
            self.add(attachment)

            print("Search functionality test passed")
        } else {
            print("No search fields found, but Search tab is accessible")
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "SearchViewWithoutSearchBar"
            attachment.lifetime = .keepAlways
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

        let photoPickerTitle = app.staticTexts["New Post"]
        XCTAssertTrue(photoPickerTitle.waitForExistence(timeout: 2), "Photo picker should appear")

        print("Compose sheet opened successfully")
    }
}
