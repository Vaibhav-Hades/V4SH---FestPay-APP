# Compatibility and dual-SIM qualification

Evidence cut-off: 3 November 2026. VERIFIED is limited to the observed device/account/menu and tested operation. It is not production settlement certification.

| Category | Current evidence | Status / qualification boundary |
| --- | --- | --- |
| Redmi Note 13 Pro+ | User-observed offline balance, QR/repeat/manual Review, Receive opening, profile/history and Help/share chooser; synthetic recovery/tests | VERIFIED for recorded checkpoints only. |
| Airtel | Actual *99# balance navigation and displayed result | VERIFIED for tested balance path, not all payments/accounts/regions. |
| Tested bank/account | User's existing bank-linked route works for balance; bank identity not collected | Limited evidence only; no bank-wide authorization/support claim. |
| Other banks | No physical matrix | NOT QUALIFIED. |
| Other carriers | No physical matrix | NOT QUALIFIED. |
| Dual-SIM routing/switching | Source/public-API analysis; no physical two-SIM qualification | NOT QUALIFIED. |
| SIM removal/lock | Synthetic readiness-policy cases only | NOT QUALIFIED physically. |
| Coverage loss / Wi-Fi-only with no cellular service | Source checks and simulated unavailable conditions, no actual loss test | NOT QUALIFIED physically. Wi-Fi is not a cellular substitute. |
| Mobile data/Wi-Fi off, airplane off | Metadata checked and user confirmed actual balance and preparation | VERIFIED for tested Redmi route. |
| Airplane mode | Synthetic known-unavailable preflight test | TEST VERIFIED policy; NOT QUALIFIED physically. |
| Accessibility/Phone permission | Actual binding/grant observed; consent and unavailable preflight tests | VERIFIED/TEST VERIFIED within recorded scope; OEM reconnect remains user-controlled. |
| Android 16 / API 36 | Connected Redmi and instrumented suite | VERIFIED tested platform; minimum API 26 is an implementation setting, not tested coverage of API 26–35. |
| Signed release runtime / debug-to-release upgrade | Signed shrunk APK inspected; public signing-digest comparison shows debug/release identities differ | NOT QUALIFIED at runtime. Physical checks use the installed debug build. Replacing it would require an approved signing/data migration strategy; no uninstall, data clear or destructive release install was performed. |
| Other Android OEMs/versions/form factors | No device matrix | NOT QUALIFIED. Compile SDK 37 is not Android 17 runtime evidence. |
| English carrier menu | Tested option-3/menu/result route | Limited VERIFIED evidence; unexpected layouts halt. |
| Other languages/localized menus | Not tested | NOT QUALIFIED; no universal language/parser claim. |
| 16 KB binary packaging | Final release 64-bit ELF and ZIP alignment checks | TEST VERIFIED packaging; not runtime certification. |
| 16 KB runtime | No successful 16 KB app execution | NOT TESTED / NOT QUALIFIED; details below. |
| Cloud/device transfer and true low-memory-killer policy | Exclusions/fault/process-loss tests only | NOT TESTED physically; no all-OEM restore/recovery certification. |

## Current dual-SIM control: exact boundary

RealUssdPaymentEngine and startBalanceCheck launch ACTION_CALL with encoded *99# and NEW_TASK. They attach no subscription ID, PhoneAccountHandle or SIM-routing extra. The initialization broadcast only initializes V4SH's observation session; it cannot select a network subscription. CellularReadinessReader uses the default TelephonyManager view and does not enumerate or verify each SIM/account. There is no fake selector or persisted claim that a bank SIM was selected.

Android documents [TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE](https://developer.android.com/reference/android/telecom/TelecomManager#EXTRA_PHONE_ACCOUNT_HANDLE) for specifying a phone account on calls, and [TelephonyManager.createForSubscriptionId](https://developer.android.com/reference/android/telephony/TelephonyManager#createForSubscriptionId(int)) for a subscription-scoped manager. These are genuine public mechanisms; claiming Android has no selection APIs would be incorrect. The existing V4SH path uses neither, and the contracts alone do not qualify interactive *99# behavior, OEM routing or bank registration. Changing to sendUssdRequest also does not supply settlement evidence or establish equivalent interactive PIN behavior. No new permission/API was added just to produce a selector.

User must check Android's default calling SIM or choose the bank-linked SIM if Android prompts. V4SH cannot guarantee the chosen/default subscription is bank-registered, that a picker appears, or that a carrier supports this flow. Readiness, SIM guidance and Help explicitly state this. Before any selector is implemented, qualify public routing with real dual-SIM devices/carriers and document permissions, failure and binding behavior; do not initiate a real payment to probe routing behind an unresolved guard.

## 16 KB runtime investigation

The connected Redmi reports 4096-byte pages. Installed API-37 Google Play x86_64 image is not the experimental ps16k image. Google's [official 16 KB testing guide](https://developer.android.com/guide/practices/page-sizes) documents dedicated emulator images. The official SDK catalogue lists system-images;android-36;google_apis_ps16k;x86_64, revision 7 (archive x86_64-ps16k-36_r07.zip, 1,849,428,145 bytes; advertised SHA-1 dd783282e84bf475a02eba6777c79fc5695e1583).

The installed emulator's -accel-check returned code 6 and requested ensuring Hyper-V/Windows Hypervisor Platform are enabled; it did not establish usable acceleration. A download to ignored D:\FestPay\build\qualification16k was started and stopped after a partial transfer when this host prerequisite was identified. C: has very little space; no SDK download was directed there and no existing AVD/system image/personal data was deleted. The partial image is not installed or checksum-certified and is not a test result.

To close this gap, provide a host with usable emulator acceleration (verify successful acceleration/launch), finish and validate the official image on a drive with space, create an isolated 16 KB AVD, verify getconf PAGE_SIZE=16384, then test actual startup, bundled decoding/camera path, profile/history/Receive and recovery while retaining no-live-payment rules. A software/emulated page-size claim without checking the running guest is insufficient. No native binary was modified and no 16 KB runtime PASS is claimed.

## Manual-stop Accessibility limitation

Following user-confirmed app clearing, V4SH was enabled but unbound; exit metadata included user-requested force-stop. Re-enabling restored actual binding, verified without another test/restart. Service enablement is not binding, and this device may require manual re-enable after clearing the app. Current preflight blocks cellular launch when disconnected. General OEM/background recovery remains NOT QUALIFIED; no independent application crash was established from the service lifecycle label alone. Full diagnostic scope is recorded in GAP_CLOSURE_VALIDATION.md.
