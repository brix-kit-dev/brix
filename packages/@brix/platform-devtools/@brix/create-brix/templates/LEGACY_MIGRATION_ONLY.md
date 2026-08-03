# Legacy Migration-Only Templates

Runtime Shell v3.0.10 Phase 0 freezes the current `create-brix` templates.

These templates are retained only as migration inventory. The generator fails
before rendering them so new projects cannot be created from legacy structures
such as `module-manifest.yaml`, `startup-order`, direct Spring controller
publication, or backend `RuntimeContext` access.
