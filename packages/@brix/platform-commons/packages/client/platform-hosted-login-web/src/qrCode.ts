/**
 * @file QR code of a TOTP enrollment
 * @description Rendered in the page itself as a PNG data URI, which the hosted page CSP allows
 * (`img-src 'self' data:`); the enrollment never goes to another service.
 */

import QRCode from 'qrcode';

export type QrCodeRenderer = (text: string) => Promise<string>;

export const renderQrCode: QrCodeRenderer = (text) =>
  QRCode.toDataURL(text, { errorCorrectionLevel: 'M', margin: 1, width: 192 });
