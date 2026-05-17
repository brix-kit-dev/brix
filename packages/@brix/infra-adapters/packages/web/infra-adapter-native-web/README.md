# @brix-sdk/infra-adapter-native-web

> Native component adapter for Brix Platform

## Overview

This package provides native web component integration for the Brix Runtime platform. It enables plugins built with vanilla JavaScript or Web Components to integrate with the Brix runtime.

## Features

- Web Component support
- Custom Element registration
- Shadow DOM encapsulation
- Attribute/property bridging
- Event forwarding

## Installation

```bash
npm install @brix-sdk/infra-adapter-native-web
```

## Usage

```typescript
import { NativeAdapter, registerNativePlugin } from '@brix-sdk/infra-adapter-native-web';

// Register a native Web Component plugin
registerNativePlugin({
  name: 'my-widget',
  element: 'my-custom-widget',
  capabilities: ['config', 'eventbus']
});

// Create adapter instance
const adapter = new NativeAdapter();

// Mount native component
adapter.mount(container, {
  element: 'my-custom-widget',
  props: { theme: 'dark' }
});
```

## Web Component Example

```typescript
class MyWidget extends HTMLElement {
  connectedCallback() {
    this.innerHTML = '<div>My Native Widget</div>';
  }
}

customElements.define('my-custom-widget', MyWidget);
```

## License

Apache-2.0
