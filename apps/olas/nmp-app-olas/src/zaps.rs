use std::ffi::CStr;
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn olas_bolt11_amount_sats(bolt11: *const c_char) -> u64 {
    let result = std::panic::catch_unwind(|| -> u64 {
        if bolt11.is_null() {
            return 0;
        }
        let Ok(invoice) = (unsafe { CStr::from_ptr(bolt11) }).to_str() else {
            return 0;
        };
        bolt11_amount_sats(invoice).unwrap_or(0)
    });
    result.unwrap_or(0)
}

pub(crate) fn bolt11_amount_sats(invoice: &str) -> Option<u64> {
    nmp_nip57::bolt11::amount_msats(invoice).map(|msats| msats / 1_000)
}

#[cfg(test)]
mod tests {
    use std::ffi::CString;

    use super::bolt11_amount_sats;
    use super::olas_bolt11_amount_sats;

    #[test]
    fn decodes_sat_amount_from_bolt11_hrp() {
        assert_eq!(bolt11_amount_sats("lnbc210n1pvjluez000"), Some(21));
    }

    #[test]
    fn amountless_invoice_is_unknown() {
        assert_eq!(bolt11_amount_sats("lnbc1pvjluez000"), None);
    }

    #[test]
    fn c_abi_decodes_sat_amount_from_bolt11_hrp() {
        let invoice = CString::new("lnbc210n1pvjluez000").unwrap();
        assert_eq!(olas_bolt11_amount_sats(invoice.as_ptr()), 21);
    }
}
