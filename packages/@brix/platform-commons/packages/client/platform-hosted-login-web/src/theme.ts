/**
 * @file Hosted login design tokens
 * @description The light design tokens the consoles render with (the native adapter's resolver),
 * so the hosted pages carry the platform's SHINWA look without loading a console theme runtime.
 */

import { NativeDesignTokenResolver } from '@brix-sdk/infra-adapter-ui-native';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';

export const hostedLoginTokens: DesignTokens = new NativeDesignTokenResolver().resolve('light');
