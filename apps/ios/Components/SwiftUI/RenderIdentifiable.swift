import SwiftUI

/// A value type whose render-relevant fields can be compared cheaply so
/// SwiftUI `.equatable()` row wrappers skip body re-evaluation at idle.
public protocol RenderIdentifiable {
    func rendersIdentically(_ other: Self) -> Bool
}

struct EquatableRow<Model: RenderIdentifiable, Content: View>: View, @preconcurrency Equatable {
    let model: Model
    @ViewBuilder let content: (Model) -> Content
    @MainActor var body: some View { content(model) }
    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.model.rendersIdentically(rhs.model)
    }
}
