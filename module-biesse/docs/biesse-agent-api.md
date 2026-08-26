# Biesse agent API

**Movido a module-system** (`:8080`). Ver:

`../module-system/docs/biesse-agent-api.md`

## Este módulo (obras)

Solo datos XML/escaneo + APIs de integración usadas por system:

- `GET/POST /api/biesse/scan/integration/**` (`X-Internal-Token` o JWT)
- `GET /api/biesse/scan/trazabilidad` (portal)
- `GET /api/biesse/scan/ops`
- órdenes / partes / escaneo Android

Token interno compartido: `app.biesse.internal-token` / `APP_BIESSE_INTERNAL_TOKEN`.
