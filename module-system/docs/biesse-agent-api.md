# Agente Biesse CNC — API (module-system)

Base: `http://SERVIDOR:8080/api/biesse/agent`  
Auth: header `X-Agent-Token`

Orquestación en **module-system** (`app_db`). Las obras/XML viven en **module-biesse** (`obras`); system las actualiza vía APIs internas (`X-Internal-Token`).

## Endpoints agente

| Método | Path | Uso |
|--------|------|-----|
| GET | `/me` | Salud + identidad máquina |
| POST | `/heartbeat` | Heartbeat / cola / impresora |
| POST | `/status` | Estado OSI (RUN → PRODUCCION) |
| GET | `/order-manifest?job=` | Manifiesto por job OSI |
| GET | `/order-manifest?orderId=` | Manifiesto por id (selección manual) |
| GET | `/orders?q=&limit=` | Búsqueda de obras (diálogo agente) |
| POST | `/events` | Eventos Event.log + labels ZPL |
| POST | `/print-ack` | Ack de impresión local |

Si el match de obra falla, `/order-manifest?job=` responde **404/409** con JSON `{ message, job, candidates:[{orderId,orderName,bookingCode,nParts}] }` para que el agente WinForms muestre el selector.

## Monitor (JWT empleado)

Base: `/api/biesse/monitor`

- `GET /machines`
- `POST /machines` — crea token (mostrar una vez)
- `POST /machines/{id}/rotate-token`
- `DELETE /machines/{id}` — elimina seccionador (cascada eventos/planchas)
- `GET /events`
- `GET /cut-pieces`
- `GET /boards/live` — planchas en vivo por máquina + `total_live` (online RUN) / `total_today`
- `GET /boards/history?from=&to=&machineId=` — historial de planchas (`biesse_agent_board_cut`)
- `GET /boards/summary?from=&to=` — totales por máquina y gran total
- `GET /trazabilidad?orderId=` — proxy a obras

Plancha = board OSI (`Boards done` / `boards_done`), no pieza. Se registra al evento (idempotente por `event_uid`) o por delta de `boards_done` en status si no hubo evento reciente.

## Config

```properties
app.biesse.base-url=http://module-biesse:8086
app.biesse.internal-token=<mismo valor en ambos módulos>
app.biesse.agent.bootstrap-token=dev-biesse-agent-token
```

## URL del agente Win10

`http://IP-SERVIDOR:8080` (añade `/api/biesse/agent` automáticamente).
