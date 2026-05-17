# @brix-sdk/infra-adapter-biometric-mobile

Biometric authentication adapter for Brix Runtime SDK Mobile platform.

## Overview

This package provides a standalone biometric authentication capability for mobile applications built on the Brix Runtime SDK. It was extracted from `@brix-sdk/infra-adapter-device-mobile` to follow the Single Responsibility Principle and provide better modularity.

## Features

- **Multi-platform support**: Works with iOS (Face ID, Touch ID) and Android (Fingerprint, Face Unlock, Iris)
- **Unified API**: Platform-agnostic interface for biometric authentication
- **Availability detection**: Check biometric hardware and enrollment status
- **Error handling**: Standardized error codes for all failure scenarios
- **Fallback support**: Optional device credential fallback

## Installation

```bash
pnpm add @brix-sdk/infra-adapter-biometric-mobile
```

## Usage

```typescript
import {
  BiometricCapabilityAdapter,
  BiometricCapability,
} from '@brix-sdk/infra-adapter-biometric-mobile';

// Create adapter instance
const biometric: BiometricCapability = new BiometricCapabilityAdapter();

// Check availability
const availability = await biometric.checkAvailability();
console.log('Biometric available:', availability.available);
console.log('Biometric type:', availability.biometricType);
console.log('Enrolled:', availability.enrolled);

// Authenticate
if (availability.available && availability.enrolled) {
  const result = await biometric.authenticate({
    promptMessage: 'Verify your identity to continue',
    title: 'Authentication Required',
    allowDeviceCredential: true,
  });

  if (result.success) {
    console.log('Authentication successful!');
  } else {
    console.log('Authentication failed:', result.error);
    console.log('Error code:', result.errorCode);
  }
}
```

## API Reference

### BiometricCapability Interface

| Method | Description |
|--------|-------------|
| `checkAvailability()` | Check if biometric authentication is available |
| `getBiometricType()` | Get the type of biometric available |
| `authenticate(options)` | Perform biometric authentication |
| `isEnrolled()` | Check if biometrics are enrolled |

### BiometricType

- `fingerprint` - Fingerprint authentication (Touch ID, Android Fingerprint)
- `face` - Face recognition (Face ID, Android Face Unlock)
- `iris` - Iris recognition (Samsung devices)
- `none` - No biometric available

### BiometricErrorCode

- `user_cancel` - User cancelled authentication
- `user_fallback` - User chose fallback (password/PIN)
- `system_cancel` - System cancelled (app backgrounded)
- `not_available` - Biometric not available
- `not_enrolled` - No biometrics enrolled
- `lockout` - Too many failed attempts
- `lockout_permanent` - Permanent lockout
- `no_hardware` - No biometric hardware
- `passcode_not_set` - Device passcode not set
- `unknown` - Unknown error

## Architecture

```
┌─────────────────────────────────────────────────────────────�?
�?                   Mobile Plugin Layer                      �?
�?   ┌─────────────�? ┌─────────────�? ┌─────────────�?      �?
�?   �? Booking    �? �? Identity   �? �? Payments   �?      �?
�?   �? Plugin     �? �? Plugin     �? �? Plugin     �?      �?
�?   └──────┬──────�? └──────┬──────�? └──────┬──────�?      �?
�?          �?               �?               �?             �?
�?          �?               �?               �?             �?
�?   ┌─────────────────────────────────────────────────�?    �?
�?   �?        BiometricCapability Interface            �?    �?
�?   �? - checkAvailability()                          �?    �?
�?   �? - authenticate()                               �?    �?
�?   �? - getBiometricType()                           �?    �?
�?   └─────────────────────────────────────────────────�?    �?
�?                          �?                               �?
�?                          �?                               �?
�?   ┌─────────────────────────────────────────────────�?    �?
�?   �?     BiometricCapabilityAdapter (this package)   �?    �?
�?   �? - Native module abstraction                    �?    �?
�?   �? - Platform-specific handling                   �?    �?
�?   �? - Error normalization                          �?    �?
�?   └─────────────────────────────────────────────────�?    �?
�?                          �?                               �?
�?                          �?                               �?
�?   ┌─────────────────────────────────────────────────�?    �?
�?   �?          React Native Native Modules            �?    �?
�?   �?  (react-native-biometrics, LocalAuthentication) �?    �?
�?   └─────────────────────────────────────────────────�?    �?
└─────────────────────────────────────────────────────────────�?
```

## License

Apache-2.0
