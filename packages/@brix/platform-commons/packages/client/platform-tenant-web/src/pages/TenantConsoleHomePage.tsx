/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file Tenant console home
 * @description First page of the staff console behind the BFF: who is signed in, in which
 * tenant, and the effective permissions of that context. Everything shown comes from the Auth
 * capability, whose session the BFF provides (frontend BRX-FE-AUTH-002, multi-tenant
 * BRX-TENANT-AUTHZ-009); the page holds no token and no credential field.
 */

import { useAuth, useRuntimeContext, useUI } from '@brix-sdk/runtime-sdk-react';
import { AuthCapabilityType, type AuthCapability } from '@brix-sdk/runtime-sdk-api-web';
import { useActorContexts } from '../hooks/useActorContexts';

const MEMBER_TYPE_LABELS: Readonly<Record<string, string>> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
};

export function TenantConsoleHomePage() {
  const auth = useAuth();
  const runtime = useRuntimeContext();
  const { Card, Stack, Typography, Tag, Button, Alert } = useUI();
  const contexts = useActorContexts();
  const capability = runtime.getCapability<AuthCapability>(AuthCapabilityType);
  const tenant = capability?.getCurrentTenant?.() ?? null;
  const actor = capability?.getVerifiedActorContext() ?? null;
  const permissions = [...(actor?.permissions ?? [])].sort();

  return (
    <Stack spacing={3} style={{ padding: 24, maxWidth: 960 }}>
      <Typography variant="h5" component="h1">{tenant?.name ?? '员工控制台'}</Typography>
      <Card>
        <Stack spacing={1} style={{ padding: 16 }}>
          <Typography variant="subtitle2">当前身份</Typography>
          <Typography variant="body2">{auth.user?.email ?? auth.user?.username ?? '—'}</Typography>
          <Typography variant="body2" color="textSecondary">
            成员类型：{auth.user?.roles?.join(', ') || '—'}
          </Typography>
        </Stack>
      </Card>
      <Card>
        <Stack spacing={1} style={{ padding: 16 }}>
          <Typography variant="subtitle2">有效权限（{permissions.length}）</Typography>
          {permissions.length === 0 ? (
            <Typography variant="body2" color="textSecondary">当前上下文没有可用权限。</Typography>
          ) : (
            <Stack direction="row" spacing={1} wrap="wrap">
              {permissions.map((permission) => <Tag key={permission}>{permission}</Tag>)}
            </Stack>
          )}
        </Stack>
      </Card>
      {contexts.others.length > 0 ? (
        <Card>
          <Stack spacing={1} style={{ padding: 16 }}>
            <Typography variant="subtitle2">切换租户</Typography>
            {contexts.error ? <Alert severity="error">切换失败，请刷新页面后重试。</Alert> : null}
            {contexts.others.map((context) => (
              <Stack key={context.contextId} direction="row" spacing={1} align="center" justify="space-between">
                <Typography variant="body2">
                  {context.tenant.name}（{MEMBER_TYPE_LABELS[context.roleType] ?? context.roleType}）
                </Typography>
                <Button
                  variant="secondary"
                  size="small"
                  loading={contexts.switching}
                  disabled={contexts.switching}
                  onClick={() => void contexts.switchTo(context.contextId)}
                >
                  切换
                </Button>
              </Stack>
            ))}
          </Stack>
        </Card>
      ) : null}
    </Stack>
  );
}
