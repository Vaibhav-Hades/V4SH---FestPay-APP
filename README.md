# V4SH — Warm Editorial Fintech Interface

V4SH is a lightweight, non-custodial fintech payment interface designed for seamless festival and merchant payments using standard UPI payment rails.

---

## Key Architecture & Features

- **Warm Editorial UI System**: Custom design system built with Jetpack Compose, featuring Bodoni Moda headlines, IBM Plex Serif body, and Libre Caslon Text labels on a Warm Cream (`#FFF8F0`) and Deep Burgundy (`#3D0C1A`) surface.
- **29 Authoritative Screens**: Complete product flow covering Home, Activity Ledger, Receive Money, Profile, Account & Security settings, Privacy controls, Help Center, Contact Support, First-Time Setup, CameraX Scanner, and Payment Terminal States.
- **Real UPI QR Scanning**: Integrated CameraX Live Preview with Google ML Kit Barcode Scanning for instant NPCI UPI URI (`upi://pay?...`) extraction and validation.
- **Manual Amount Entry**: Full decimal keyboard support, real-time numeric validation (`amount > 0`), and editable quick preset shortcuts.
- **Real USSD Payment Engine Foundation**: Clean `PaymentEngine` abstraction backing both `LocalMockPaymentEngine` and `RealUssdPaymentEngine` (`*99#` carrier execution).
- **Hard Authentication Boundary**: Complete security handoff (`AUTHENTICATION_REQUIRED`) halting automation before any PIN or password prompt. V4SH never asks for, enters, captures, or logs your UPI PIN, OTP, card PIN, CVV, or banking passwords.
- **Local Transaction Persistence**: In-device `TransactionRepository` persisting activity records across app restarts and process recreation.
- **Non-Custodial Design**: V4SH does not hold user funds, operate a wallet, or replace your bank account. Money moves directly through your registered UPI payment provider.

---

## Tech Stack & Dependencies

- **Language & Framework**: Kotlin 2.2.10, Jetpack Compose, Material 3
- **Navigation**: Navigation Compose 2.7.6
- **Camera & Barcode**: AndroidX CameraX 1.4.1, Google ML Kit Barcode Scanning 17.3.0
- **Build System**: Android Gradle Plugin 9.4.1, Android SDK 35 (minSdk 26)

---

## Build & Release Instructions

### Build Commands

```bash
# Assemble Debug APK
./gradlew clean assembleDebug

# Assemble Production Signed Release APK
./gradlew clean assembleRelease

# Assemble Production Signed Release Android App Bundle (AAB)
./gradlew bundleRelease
```

### Release Signing Setup

Production release builds require a `keystore.properties` file in the project root or equivalent `KEYSTORE_*` environment variables.

To configure local release signing:
1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill in your release keystore path and passwords:
   ```properties
   storeFile=C\:/path/to/v4sh-release.jks
   storePassword=YourKeystorePassword
   keyAlias=v4sh_release_key
   keyPassword=YourKeyPassword
   ```

*Note: `keystore.properties` and keystore files (`*.jks`, `*.keystore`) are strictly ignored by Git.*

---

## Known Limitations

1. **Cellular Network Requirement**: USSD payment execution requires an active mobile cellular connection.
2. **Carrier User Interaction**: Modern Android devices require explicit user confirmation for dialer USSD prompts.
3. **Non-Custodial Scope**: V4SH does not store bank account statements; transaction history represents local V4SH payment attempts.
4. **Upstream 4 KB Native Alignment**: Native binaries in official Google ML Kit (`libbarhopper_v3.so`) and CameraX (`libimage_processing_util_jni.so`) Maven releases ship 4 KB aligned by default, which operates with 100% stability on ARM64 devices.
