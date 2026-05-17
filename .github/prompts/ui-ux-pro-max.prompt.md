---
name: ui-ux-pro-max
description: Describe when to use this prompt
---

<!-- Tip: Use /create-prompt in chat to generate content with agent assistance -->

Please load and follow the UI-UX Pro Max instructions from this file:

[UI-UX Pro Max Original Prompt](./ui-ux-pro-max/PROMPT.md)

Apply those UI/UX rules to the current task.

Additional project constraints for Brix:

- This project is Brix, an enterprise-grade plugin-based platform.
- Explanations to me should be in Chinese.
- Code comments should be in English.
- Do not introduce a new UI framework unless explicitly requested.
- Reuse the existing frontend stack, layout, routing, API layer, types, hooks, and shared components.
- Do not hardcode API URLs, tenant IDs, role names, permission strings, or mock business rules.
- Do not bypass Brix architecture boundaries.
- Before modifying code, first inspect the existing project structure and list affected files.
- Prefer the smallest production-quality implementation.
- Avoid temporary hacks, fake architecture, and unrelated rewrites.

When I ask for UI/UX work:

1. First analyze the existing frontend structure.
2. Then give the component decomposition.
3. Then list the files that need to be changed.
4. Then implement only after the scope is clear.
5. Finally provide test steps.