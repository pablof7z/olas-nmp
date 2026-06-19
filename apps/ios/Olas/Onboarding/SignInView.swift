import SwiftUI

struct SignInView: View {
    let vm: OnboardingViewModel

    @State private var nsec: String = ""
    @State private var selectedMode: SignInMode = .nsec

    private enum SignInMode: String, CaseIterable {
        case nsec   = "Recovery key"
        case bunker = "Remote signer"
    }

    private var canSubmit: Bool {
        switch selectedMode {
        case .nsec:   return nsec.hasPrefix("nsec1") && !vm.isSigningIn
        case .bunker: return nsec.hasPrefix("bunker://") && !vm.isSigningIn
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Back button
            HStack {
                Button {
                    vm.advance(to: .welcome)
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.olasText2)
                        .padding(OlasSpacing.sm)
                }
                Spacer()
            }
            .padding(.horizontal, OlasSpacing.md)
            .padding(.top, OlasSpacing.md)

            Spacer()

            VStack(spacing: OlasSpacing.lg) {
                // Title
                VStack(spacing: OlasSpacing.xxs) {
                    Text("I have an account")
                        .font(OlasFont.title1())
                        .foregroundStyle(Color.olasText1)
                    Text("Sign in with your recovery key or a remote signer.")
                        .font(OlasFont.subheadline())
                        .foregroundStyle(Color.olasText2)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, OlasSpacing.xxl)
                }

                // Mode picker
                Picker("Sign-in method", selection: $selectedMode) {
                    ForEach(SignInMode.allCases, id: \.self) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, OlasSpacing.xl)

                // Input field
                VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                    let placeholder = selectedMode == .nsec
                        ? "nsec1..."
                        : "bunker://..."

                    TextField(placeholder, text: $nsec)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText1)
                        .padding(OlasSpacing.md)
                        .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.olasBorder, lineWidth: 1)
                        )
                        .padding(.horizontal, OlasSpacing.xl)

                    if let error = vm.signInError {
                        Text(error)
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasDestructive)
                            .padding(.horizontal, OlasSpacing.xl)
                    }
                }

                // Submit button
                Button {
                    switch selectedMode {
                    case .nsec:   vm.signInNsec(nsec)
                    case .bunker: vm.signInBunker(nsec)
                    }
                } label: {
                    Group {
                        if vm.isSigningIn {
                            ProgressView().tint(Color.olasBackground)
                        } else {
                            Text("Sign in")
                                .font(OlasFont.headline())
                                .foregroundStyle(Color.olasBackground)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(
                        canSubmit ? Color.olasText1 : Color.olasText3,
                        in: RoundedRectangle(cornerRadius: 12)
                    )
                }
                .buttonStyle(OlasPressedButtonStyle())
                .disabled(!canSubmit)
                .padding(.horizontal, OlasSpacing.xl)
            }

            Spacer()
        }
        .background(Color.olasBackground)
    }
}
