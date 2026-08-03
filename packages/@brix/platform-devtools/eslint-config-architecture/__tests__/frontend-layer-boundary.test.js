/**
 * Unit tests for frontend-layer-boundary.
 */

import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/frontend-layer-boundary');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('frontend-layer-boundary', () => {
  it('enforces frontend-1.1 source boundaries', () => {
    ruleTester.run('frontend-layer-boundary', rule, {
      valid: [
        {
          filename: '/repo/app-booking/booking-ui-web/src/pages/BookingPage.tsx',
          code: `
            import { useUI } from '@brix-sdk/runtime-sdk-react';
            import { useBookingPageModel } from '../hooks/useBookingPageModel';
            export function BookingPage() {
              const ui = useUI();
              const model = useBookingPageModel();
              return ui.Box({ children: model.title });
            }
          `,
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/hooks/useBookingPageModel.ts',
          code: `
            import { useBookingRepository } from './useBookingRepository';
            export function useBookingPageModel() {
              return useBookingRepository().list();
            }
          `,
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/repositories/BookingRepository.ts',
          code: `
            import { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
            export function createBookingRepository(http) {
              return { list: () => http.get('/v1/bookings') };
            }
          `,
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/repositories/GeneratedBookingRepository.ts',
          code: `
            import { bookingClient } from '../generated/booking-client';
            export function createBookingRepository() {
              return { list: () => bookingClient.listBookings() };
            }
          `,
        },
        {
          filename: '/repo/enterprise-host/host-shell-standalone-web/src/bootstrap.tsx',
          code: `
            import { bootstrapFrontendHost } from '@brix-sdk/runtime-orchestrator-web';
            import { standaloneWebComposition } from './composition/standalone-web.composition';
            bootstrapFrontendHost({ composition: standaloneWebComposition });
          `,
        },
      ],

      invalid: [
        {
          filename: '/repo/app-booking/booking-ui-web/src/pages/BookingPage.tsx',
          code: `import { createBookingRepository } from '../repositories/BookingRepository';`,
          errors: [{ messageId: 'noPageRepositoryImport' }],
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/pages/BookingPage.tsx',
          code: `import { BookingService } from '../services/BookingService';`,
          errors: [{ messageId: 'noPageServiceImport' }],
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/hooks/useBookingPageModel.ts',
          code: `
            import axios from 'axios';
            export function useBookingPageModel() {
              return axios.get('/api/bookings');
            }
          `,
          errors: [{ messageId: 'noHookTransport' }],
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/hooks/useBookingPageModel.ts',
          code: `
            export function useBookingPageModel() {
              return fetch('/api/bookings');
            }
          `,
          errors: [{ messageId: 'noHookTransport' }],
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/hooks/useBookingPageModel.ts',
          code: `
            export function useBookingPageModel() {
              return localStorage.getItem('token');
            }
          `,
          errors: [{ messageId: 'noHookTransport' }],
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/repositories/BookingRepository.ts',
          code: `
            import { useHttp } from '@brix-sdk/runtime-sdk-react';
            export function createBookingRepository() {
              const http = useHttp();
              return { list: () => http.get('/v1/bookings') };
            }
          `,
          errors: [
            { messageId: 'noRepositoryRuntimeHook' },
            { messageId: 'repositoryHttpCapabilityMissing' },
          ],
        },
        {
          filename: '/repo/app-booking/booking-ui-web/src/repositories/BookingRepository.ts',
          code: `
            export function createBookingRepository(http) {
              return { list: () => http.get('/v1/bookings') };
            }
          `,
          errors: [{ messageId: 'repositoryHttpCapabilityMissing' }],
        },
        {
          filename: '/repo/enterprise-host/host-shell-standalone-web/src/bootstrap.tsx',
          code: `import { PlatformLoginPage } from '@brix-sdk/platform-admin-web/src/pages';`,
          errors: [{ messageId: 'noHostBusinessLayer' }],
        },
        {
          filename: '/repo/enterprise-host/host-shell-standalone-web/src/bootstrap.tsx',
          code: `import { createPlatformAuth } from '@brix-sdk/platform-auth-web';`,
          errors: [{ messageId: 'noHostProviderPolicy' }],
        },
      ],
    });
  });
});
