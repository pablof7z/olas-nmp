use std::ffi::CString;
use std::os::raw::c_char;

const GEOHASH_ALPHABET: &str = "0123456789bcdefghjkmnpqrstuvwxyz";

#[no_mangle]
pub extern "C" fn olas_location_geohash4(latitude: f64, longitude: f64) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if !latitude.is_finite()
            || !longitude.is_finite()
            || !(-90.0..=90.0).contains(&latitude)
            || !(-180.0..=180.0).contains(&longitude)
        {
            return std::ptr::null_mut();
        }
        CString::new(encode_geohash(latitude, longitude, 4))
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
fn is_valid_geohash4(value: &str) -> bool {
    value.len() == 4 && value.chars().all(|ch| GEOHASH_ALPHABET.contains(ch))
}

fn encode_geohash(lat: f64, lon: f64, precision: usize) -> String {
    let chars: Vec<char> = GEOHASH_ALPHABET.chars().collect();
    let mut min_lat = -90.0;
    let mut max_lat = 90.0;
    let mut min_lon = -180.0;
    let mut max_lon = 180.0;
    let mut even = true;
    let mut bit = 0;
    let mut ch = 0;
    let mut out = String::new();

    while out.len() < precision {
        if even {
            let mid = (min_lon + max_lon) / 2.0;
            if lon >= mid {
                ch |= 1 << (4 - bit);
                min_lon = mid;
            } else {
                max_lon = mid;
            }
        } else {
            let mid = (min_lat + max_lat) / 2.0;
            if lat >= mid {
                ch |= 1 << (4 - bit);
                min_lat = mid;
            } else {
                max_lat = mid;
            }
        }
        even = !even;
        if bit < 4 {
            bit += 1;
        } else {
            out.push(chars[ch]);
            bit = 0;
            ch = 0;
        }
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn geohash4_uses_expected_precision() {
        assert_eq!(encode_geohash(37.7749, -122.4194, 4), "9q8y");
    }

    #[test]
    fn geohash4_validation_rejects_wrong_precision() {
        assert!(is_valid_geohash4("9q8y"));
        assert!(!is_valid_geohash4("9q8"));
        assert!(!is_valid_geohash4("9q8!"));
    }
}
