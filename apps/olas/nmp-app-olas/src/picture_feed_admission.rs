use std::sync::Arc;

use nmp_wot::WotBootstrapRuntime;

const CLOSE_MINIMUM_SCORE: i32 = 10;

pub(super) fn network_allows(
    wot: &Option<Arc<WotBootstrapRuntime>>,
    has_active: bool,
    candidate: &str,
) -> bool {
    network_allows_for_preset(
        wot,
        has_active,
        candidate,
        &crate::extras_state::wot_preset(),
    )
}

pub(super) fn network_allows_for_preset(
    wot: &Option<Arc<WotBootstrapRuntime>>,
    has_active: bool,
    candidate: &str,
    preset: &str,
) -> bool {
    if preset == "open" || !has_active {
        return true;
    }
    let Some(runtime) = wot else {
        return false;
    };
    let Some(viewer) = runtime
        .current_snapshot()
        .and_then(|snapshot| snapshot.active_pubkey)
    else {
        return false;
    };
    let decision = match preset {
        "close" => runtime.score_with_minimum_score(&viewer, candidate, CLOSE_MINIMUM_SCORE),
        _ => runtime.score(&viewer, candidate),
    };
    decision.is_some_and(|decision| !decision.hide)
}
