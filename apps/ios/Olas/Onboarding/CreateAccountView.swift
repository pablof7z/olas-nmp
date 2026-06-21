import SwiftUI

struct CreateAccountView: View {
    @Bindable var vm: OnboardingViewModel
    @State private var showImagePicker = false

    var body: some View {
        VStack(spacing: 0) {
            // Progress dots
            HStack(spacing: 6) {
                progressDot(filled: true)
                progressDot(filled: false)
            }
            .padding(.top, 56)

            Spacer()

            VStack(spacing: OlasSpacing.xxl) {
                // Avatar circle
                Button { showImagePicker = true } label: {
                    ZStack {
                        Circle()
                            .strokeBorder(
                                style: StrokeStyle(lineWidth: 2, dash: [6, 4])
                            )
                            .foregroundStyle(Color.olasBorder)
                            .frame(width: 88, height: 88)

                        Image(systemName: "camera.fill")
                            .font(.system(size: 24, weight: .medium))
                            .foregroundStyle(Color.olasText3)
                    }
                }
                .buttonStyle(OlasPressedButtonStyle())

                VStack(spacing: OlasSpacing.sm) {
                    // Display name
                    VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                        Text("Display name")
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)

                        TextField("Your name", text: $vm.createdName)
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                            .padding(.horizontal, OlasSpacing.md)
                            .padding(.vertical, OlasSpacing.sm)
                            .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                    }

                    // Username
                    VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                        Text("Username")
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)

                        HStack(spacing: 0) {
                            Text("@")
                                .foregroundStyle(Color.olasText3)
                                .padding(.leading, OlasSpacing.md)

                            TextField("username", text: $vm.createdUsername)
                                .font(OlasFont.body())
                                .foregroundStyle(Color.olasText1)
                                .autocapitalization(.none)
                                .autocorrectionDisabled()
                                .padding(.vertical, OlasSpacing.sm)
                                .padding(.horizontal, OlasSpacing.xs)

                            Text(".olas.app")
                                .foregroundStyle(Color.olasText3)
                                .padding(.trailing, OlasSpacing.md)
                        }
                        .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(.horizontal, OlasSpacing.xl)
            }

            Spacer()

            Button {
                vm.createAndContinue()
            } label: {
                Text("Continue")
                    .font(OlasFont.headline())
                    .foregroundStyle(Color.olasBackground)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(
                        Color.olasText1.opacity(canContinue ? 1.0 : 0.4),
                        in: RoundedRectangle(cornerRadius: 12)
                    )
            }
            .buttonStyle(OlasPressedButtonStyle())
            .disabled(!canContinue)
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.bottom, 48)
        }
        .background(Color.olasBackground)
    }

    private var canContinue: Bool {
        !vm.createdName.trimmingCharacters(in: .whitespaces).isEmpty &&
        !vm.createdUsername.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func progressDot(filled: Bool) -> some View {
        Circle()
            .fill(filled ? Color.olasText1 : Color.olasBorder)
            .frame(width: 8, height: 8)
    }
}
