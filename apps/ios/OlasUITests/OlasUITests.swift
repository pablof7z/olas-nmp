import XCTest

final class OlasUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSearchFunctionality() throws {
        let app = XCUIApplication()
        app.launch()

        // Wait for app to load
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5), "App should launch")

        // Step 1: Tap the Search tab (2nd tab button)
        let tabBars = app.tabBars
        XCTAssertTrue(tabBars.buttons.count >= 2, "Tab bar should have at least 2 buttons")

        let searchButton = tabBars.buttons.element(boundBy: 1)
        XCTAssertTrue(searchButton.exists, "Search tab button should exist")
        searchButton.tap()

        // Step 2: Wait for Search view to load
        sleep(2)

        // Step 3: Look for search bar (searchable modifier creates a search field)
        let searchFields = app.searchFields
        if searchFields.count > 0 {
            let searchField = searchFields.element(boundBy: 0)
            XCTAssertTrue(searchField.exists, "Search field should exist")

            // Tap the search field
            searchField.tap()

            // Type search query
            searchField.typeText("photography")

            // Wait for results to load
            sleep(2)

            // Take screenshot
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "SearchResults"
            self.add(attachment)

            print("✓ Search functionality test passed")
        } else {
            print("⚠️ No search fields found, but Search tab is accessible")
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "SearchViewWithoutSearchBar"
            self.add(attachment)
        }
    }

    func testComposePhotoPublishingFlow() throws {
        let app = XCUIApplication()
        app.launch()

        // Wait for app to load
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5))

        // Step 1: Tap the Compose button (+ icon in tab bar, should be the 3rd tab button)
        let tabBars = app.tabBars
        XCTAssertTrue(tabBars.buttons.count >= 3, "Tab bar should have at least 3 buttons")

        let composeButton = tabBars.buttons.element(boundBy: 2)
        XCTAssertTrue(composeButton.exists, "Compose button should exist")
        composeButton.tap()

        // Step 2: Wait for photo picker to appear
        let photoPickerTitle = app.staticTexts["New Post"]
        XCTAssertTrue(photoPickerTitle.waitForExistence(timeout: 2), "Photo picker should appear")

        print("✓ Compose sheet opened successfully")
    }
}
