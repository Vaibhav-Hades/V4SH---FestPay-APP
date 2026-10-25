# V4SH

A modern interface for *99#.

## Overview

V4SH provides a modern Android interface for cellular USSD-based UPI payment initiation, simplifying the interaction normally performed through raw `*99#` USSD menus. 

V4SH is **NOT**:
- a UPI network
- a bank
- a wallet
- a PSP/TPAP
- a merchant platform
- a replacement for the underlying payment service

---

## Problem

Interacting with NPCI's `*99#` USSD service natively requires navigating text-heavy, unformatted carrier menus, manually entering service codes, and remembering exact menu hierarchy steps. This makes quick festival or merchant payments error-prone and tedious.

---

## Solution

V4SH provides a graceful Warm Editorial visual interface (scanning standard UPI QRs, entering amounts, and reviewing payment details) while keeping the underlying payment execution mechanism securely anchored to the existing cellular `*99#` USSD flow.

---

## Key Features

- QR scanning via CameraX
- Standard UPI QR parsing (`pa`, `pn`, `am`, `cu`, `mc`)
- Amount entry with decimal validation & quick shortcuts
- Payment review
- `*99#` payment initiation
- Hard authentication boundary (`AUTHENTICATION_REQUIRED`)
- Safe payment states (`SUCCESS`, `FAILED`, `UNKNOWN`, `CANCELLED`)
- Transaction history / local ledger (`TransactionRepository`)
- Receive Money / UPI QR generator
- Dual-SIM payment selection awareness
- Network awareness & no-network state handling
- Duplicate-payment protection (blocking rapid taps)
- Local-first operation
- Premium V4SH Warm Editorial interface (28+ polished screens)

---

## Payment Flow

```text
User
 ↓
V4SH
 ↓
Scan UPI QR / enter payment details
↓
Review payment
↓
*99# (Telephony / Dialer)
↓
Non-sensitive USSD automation (FestPayAccessibilityService)
↓
Authentication required boundary (STOP before PIN entry)
↓
User authentication (User enters UPI PIN in carrier interface)
↓
Payment service / Bank processing
↓
SUCCESS / FAILED / UNKNOWN (Safe terminal state resolution)
```

### Authentication Boundary

V4SH stops automation immediately upon reaching the carrier UPI PIN prompt (`AUTHENTICATION_REQUIRED`). The user completes authentication personally within the carrier prompt. V4SH never automates, reads, stores, or submits the UPI PIN.

---

## Payment Safety

- **SUCCESS** is shown only when definitive success evidence is observed from the payment flow/provider.
- **FAILED** is shown only when definitive failure evidence is observed.
- **UNKNOWN** is used when payment initiation may have occurred but the underlying USSD channel does not provide enough information for V4SH to safely determine final settlement status (e.g. carrier dialog disappearance post-authentication).
- **No Automatic Retry**: V4SH does not automatically retry `UNKNOWN` payments to protect the user against accidental double debiting.

---

## Security

- **UPI PIN**: Never captured, stored, generated, or logged by V4SH.
- **OTP / Passwords**: Never captured, stored, or logged.
- **Card PIN / CVV**: Never captured, stored, or logged.
- **Accessibility Nodes**: Sensitive accessibility screen contents are never persisted or logged.
- **Mock Isolation**: Production mock payment mode is strictly isolated behind developer controls.
- **Release Signing**: Production release uses a dedicated signing key stored outside the repository.

---

## Architecture

V4SH is built on a robust native Android stack following clean MVVM architecture:

```text
┌────────────────────────────────────────────────────────┐
│                        UI Layer                        │
│   (Jetpack Compose, Material 3, Navigation Compose)    │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│                     ViewModel Layer                    │
│                 (PaymentViewModel, State)              │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│                 Payment Engine Abstraction             │
│        (RealUssdPaymentEngine / LocalMockEngine)       │
└──────┬───────────────────────────────────┬─────────────┘
       │                                   │
       ▼                                   ▼
┌──────────────┐                 ┌───────────────────────┐
│  Telephony   │                 │ AccessibilityService  │
│  (*99# Dial) │                 │ (Non-sensitive Menus) │
└──────────────┘                 └───────────────────────┘
```

- **Core Tech**: Native Android, Kotlin 2.2.10, Jetpack Compose, Material 3
- **Architecture**: MVVM, Navigation Compose 2.7.6
- **Camera & Barcode**: AndroidX CameraX 1.4.1, Google ML Kit Barcode Scanning 17.3.0
- **Telephony & Accessibility**: Android Telephony APIs (`*99#`), `FestPayAccessibilityService`
- **Persistence**: Local transaction repository (`TransactionRepository` via SharedPreferences)

---

## Project Structure

```text
D:/FestPay/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/festpay/app/
│       │   ├── MainActivity.kt
│       │   ├── FestPayAccessibilityService.kt
│       │   ├── data/
│       │   │   └── TransactionRepository.kt
│       │   └── ui/
│       │       ├── navigation/
│       │       │   ├── MainNavHost.kt
│       │       │   └── BottomTab.kt
│       │       ├── screens/
│       │       │   └── ScreenPlaceholders.kt
│       │       ├── theme/
│       │       │   ├── Theme.kt
│       │       │   ├── Color.kt
│       │       │   └── Type.kt
│       │       └── viewmodel/
│       │           └── PaymentViewModel.kt
│       └── res/
├── mcp/                      # Authoritative V4SH specifications & schema
├── design/                   # Approved Stitch design sets
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## UI / Design

- **Branding**: V4SH (Premium V-symbol monogram)
- **Primary Burgundy**: `#3D0C1A`
- **Background Cream**: `#FFF8F0`
- **Secondary Umber**: `#8C6A58`
- **Style**: Warm Editorial, premium fintech utility, restrained cards, thin borders, soft shadows
- **Typography**: Headlines: Bodoni Moda, Body: IBM Plex Serif, Labels: Libre Caslon Text
- **Bottom Navigation**: Home | Transactions | Receive | Profile

---

## Testing

The project has undergone rigorous physical-device validation on the Redmi Note 13 Pro+ (Android 36):
- Fresh installations & setup flow
- Live QR scanning & parsing
- Payment state machine transitions (`SUCCESS`, `FAILED`, `UNKNOWN`, `CANCELLED`, `AUTHENTICATION_REQUIRED`)
- Duplicate-payment protection (rapid tap blocking)
- Lifecycle & process restart recovery
- Network-loss handling & no-network states
- Authentication boundary enforcement (zero PIN capture)
- Transaction ledger persistence
- Security & Logcat sensitive data audits
- Final UAT

---

## Known Limitations

1. **Cellular Network Dependency**: USSD payment execution (`*99#`) requires active mobile cellular connectivity.
2. **Post-Authentication Settlement Visibility**: V4SH cannot guarantee definitive post-authentication settlement confirmation when the underlying USSD channel dismisses without exposing a receipt string.
3. **UNKNOWN Guidance**: Unclear outcomes resolve to `UNKNOWN` (`"Payment status unclear"`), requiring the user to verify their bank confirmation before re-initiating.
4. **AccessibilityService Policy**: AccessibilityService usage requires careful Google Play Store policy review and disclosure compliance prior to public distribution.
5. **Non-Custodial Scope**: V4SH does not integrate with official PSP/TPAP banking APIs in this open-source build; transaction records represent local V4SH payment attempts rather than bank statements.
6. **Upstream Native Alignment**: Official Google ML Kit (`libbarhopper_v3.so`) and CameraX (`libimage_processing_util_jni.so`) dependencies contain upstream 4 KB-aligned native binaries that operate with 100% stability on ARM64 devices.

---

## Installation

1. Clone or open the project in Android Studio (Jellyfish / Koala or newer).
2. Ensure Android SDK 35 and build-tools are installed.
3. Sync project with Gradle files.
4. To build production release APKs locally, create a `keystore.properties` file in the root directory (refer to `keystore.properties.example` for structure).

---

## Running the Project

```bash
# Clean and build debug APK
./gradlew clean assembleDebug

# Clean and build production signed release APK
./gradlew clean assembleRelease

# Build Android App Bundle (AAB)
./gradlew bundleRelease
```

To install the debug build on a connected physical Android device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Release

V4SH includes a production-signed release build configuration (`app-release.apk` and `app-release.aab`) secured using a dedicated production keystore stored outside version control.

---

## Screenshots

*(Screenshots of the Warm Editorial UI, QR Scanner, Review Payment, and Transaction Ledger can be added here once captured.)*

---

## Future Scope

- Official UPI/PSP integration if formally authorized and partnered with a sponsor bank.
- Enhanced automated confirmation mechanisms where supported by provider APIs.
- Broader device and carrier compatibility testing.
- Additional accessibility enhancements and localized screen support.

---

## License

Licensing details can be added separately upon project distribution.
