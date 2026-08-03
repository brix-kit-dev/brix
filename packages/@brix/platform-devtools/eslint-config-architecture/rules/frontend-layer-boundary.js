/**
 * Enforces the ACTIVE frontend-1.1 source boundary:
 * Page/View -> Hook/ViewModel -> Repository -> HttpCapability.
 */

const TRANSPORT_IMPORTS = new Set([
  'axios',
  'got',
  'ky',
  'node-fetch',
  'superagent',
  'undici',
]);

const TOKEN_IMPORTS = new Set([
  'jose',
  'jsonwebtoken',
  'jwt-decode',
]);

const HTTP_METHODS = new Set([
  'delete',
  'get',
  'head',
  'options',
  'patch',
  'post',
  'put',
]);

function normalizeFilename(filename) {
  return String(filename || '').replace(/\\/g, '/');
}

function normalizeImport(value) {
  return String(value || '').replace(/\\/g, '/');
}

function hasSegment(filename, segments) {
  const parts = normalizeFilename(filename).split('/');
  return segments.some(segment => parts.includes(segment));
}

function isTestFile(filename) {
  return /(^|\/)(__tests__|test-fixtures|fixtures)(\/|$)/.test(filename)
    || /\.(spec|test)\.[cm]?[jt]sx?$/.test(filename);
}

function isRepositoryPath(value) {
  return /(^|\/|\.\.\/)(repositories?|repository)(\/|$)/.test(value);
}

function isBusinessServicePath(value) {
  return /(^|\/|\.\.\/)(services?|service)(\/|$)/.test(value);
}

function isGeneratedClientPath(value) {
  return /(^|\/|\.\.\/)(generated|__generated__|clients?)(\/|$)/.test(value);
}

function isRuntimeApiImport(value) {
  return value === '@brix-sdk/runtime-sdk-api-web'
    || value.startsWith('@brix-sdk/runtime-sdk-api-web/');
}

function importHasSpecifier(node, importedName) {
  return node.specifiers.some(specifier => {
    if (specifier.type !== 'ImportSpecifier') {
      return false;
    }
    const imported = specifier.imported;
    return imported && imported.name === importedName;
  });
}

function isIdentifierNamed(node, name) {
  return Boolean(node && node.type === 'Identifier' && node.name === name);
}

function isMemberNamed(node, objectName, propertyName) {
  return Boolean(
    node
      && node.type === 'MemberExpression'
      && !node.computed
      && isIdentifierNamed(node.object, objectName)
      && isIdentifierNamed(node.property, propertyName),
  );
}

function isHttpCapabilityCall(node) {
  if (!node || node.type !== 'CallExpression') {
    return false;
  }
  const callee = node.callee;
  return Boolean(
    callee
      && callee.type === 'MemberExpression'
      && !callee.computed
      && callee.property
      && HTTP_METHODS.has(callee.property.name)
      && callee.object
      && callee.object.type === 'Identifier',
  );
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Enforce frontend-1.1 Page/Hook/Repository/HttpCapability and Host source boundaries.',
    },
    messages: {
      noPageRepositoryImport:
        '[BRX-FE-LAYER-001] Page/View/Component must not import Repository directly. Route data through a Hook/ViewModel.',
      noPageServiceImport:
        '[BRX-FE-LAYER-001] Page/View/Component must not import business service modules. Route behavior through a Hook/ViewModel.',
      noHookTransport:
        '[BRX-FE-LAYER-002] Hook/ViewModel must not use transport clients, fetch, browser storage, or token parsing. Call Repository methods instead.',
      noRepositoryRuntimeHook:
        '[BRX-FE-LAYER-003] Repository must not depend on React runtime hooks. Accept an injected HttpCapability or generated typed client.',
      repositoryHttpCapabilityMissing:
        '[BRX-FE-LAYER-003] Repository HTTP calls must be backed by an injected HttpCapability or generated typed client.',
      noHostBusinessLayer:
        '[BRX-FE-12] Frontend Host source must not import business Page/Hook/Repository/Service modules.',
      noHostProviderPolicy:
        '[BRX-FE-12] Frontend Host source must not import provider policy packages or infra adapters.',
    },
    schema: [],
  },

  create(context) {
    const filename = normalizeFilename(context.getFilename());
    if (!filename || filename === '<input>' || isTestFile(filename)) {
      return {};
    }

    const isPageLayer = hasSegment(filename, ['pages', 'views', 'components']);
    const isHookLayer = hasSegment(filename, ['hooks']);
    const isRepositoryLayer = hasSegment(filename, ['repositories', 'repository']);
    const isHostSource = /host-shell-[^/]+-web\/src\//.test(filename);
    let repositoryHasHttpCapabilityImport = false;
    let repositoryHasGeneratedClientImport = false;
    let repositoryUsesHttp = false;

    function reportHookTransport(node) {
      if (isHookLayer) {
        context.report({ node, messageId: 'noHookTransport' });
      }
    }

    return {
      ImportDeclaration(node) {
        const source = normalizeImport(node.source && node.source.value);

        if (isPageLayer && isRepositoryPath(source)) {
          context.report({ node, messageId: 'noPageRepositoryImport' });
        }

        if (isPageLayer && isBusinessServicePath(source)) {
          context.report({ node, messageId: 'noPageServiceImport' });
        }

        if (isHookLayer && (TRANSPORT_IMPORTS.has(source) || TOKEN_IMPORTS.has(source))) {
          context.report({ node, messageId: 'noHookTransport' });
        }

        if (isRepositoryLayer && source === '@brix-sdk/runtime-sdk-react') {
          context.report({ node, messageId: 'noRepositoryRuntimeHook' });
        }

        if (isRepositoryLayer && isRuntimeApiImport(source) && importHasSpecifier(node, 'HttpCapability')) {
          repositoryHasHttpCapabilityImport = true;
        }

        if (isRepositoryLayer && isGeneratedClientPath(source)) {
          repositoryHasGeneratedClientImport = true;
        }

        if (isHostSource && (
          isRepositoryPath(source)
          || isBusinessServicePath(source)
          || /(^|\/|\.\.\/)(pages|views|hooks|components)(\/|$)/.test(source)
        )) {
          context.report({ node, messageId: 'noHostBusinessLayer' });
        }

        if (isHostSource && (
          source.startsWith('@brix-sdk/infra-adapter-')
          || source.startsWith('@brix-sdk/platform-auth-web')
          || source.startsWith('@brix-sdk/platform-tenant-web')
        )) {
          context.report({ node, messageId: 'noHostProviderPolicy' });
        }
      },

      CallExpression(node) {
        if (isHookLayer && isIdentifierNamed(node.callee, 'fetch')) {
          reportHookTransport(node);
        }

        if (isHookLayer && isMemberNamed(node.callee, 'window', 'fetch')) {
          reportHookTransport(node);
        }

        if (isRepositoryLayer && isHttpCapabilityCall(node)) {
          repositoryUsesHttp = true;
        }
      },

      MemberExpression(node) {
        if (!isHookLayer || node.computed) {
          return;
        }

        if (isIdentifierNamed(node.object, 'localStorage')
          || isIdentifierNamed(node.object, 'sessionStorage')
          || isMemberNamed(node, 'window', 'localStorage')
          || isMemberNamed(node, 'window', 'sessionStorage')) {
          reportHookTransport(node);
        }
      },

      'Program:exit'(node) {
        if (
          isRepositoryLayer
          && repositoryUsesHttp
          && !repositoryHasHttpCapabilityImport
          && !repositoryHasGeneratedClientImport
        ) {
          context.report({
            node,
            messageId: 'repositoryHttpCapabilityMissing',
          });
        }
      },
    };
  },
};
