/**
 * @file Hosted page frame
 * @description The card every hosted login, setup and invitation page renders in.
 */

import type { ReactNode } from 'react';
import { useI18n, useUI } from '@brix-sdk/runtime-sdk-react';
import { HOSTED_LOGIN_NAMESPACE } from '../i18n/messages';

export function Frame({ title, subtitle, children }: {
  readonly title?: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
}) {
  const { Paper, Stack, Typography } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const shownSubtitle = subtitle ?? (title ? undefined : t('subtitle'));
  return (
    <Paper elevation={1} style={{ width: '100%', maxWidth: 400, padding: 32 }}>
      <Stack spacing={3}>
        <Stack spacing={0.5}>
          <Typography variant="h5" component="h1">{title ?? t('title')}</Typography>
          {shownSubtitle ? <Typography variant="body2" color="textSecondary">{shownSubtitle}</Typography> : null}
        </Stack>
        {children}
      </Stack>
    </Paper>
  );
}
