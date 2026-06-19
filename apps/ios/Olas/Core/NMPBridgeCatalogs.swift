import Foundation
import OlasFFI

extension NMPBridge {
    func filterCatalogJSON() -> String? {
        guard let res = olas_filter_catalog_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func mediaUploadConfigJSON() -> String? {
        guard let res = olas_media_upload_config_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func pickerConfigJSON() -> String? {
        guard let res = olas_picker_config_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func settingsCatalogJSON() -> String? {
        guard let res = olas_settings_catalog_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func onboardingStepsJSON() -> String? {
        guard let res = olas_onboarding_steps_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func composeStepsJSON() -> String? {
        guard let res = olas_compose_steps_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }
}
