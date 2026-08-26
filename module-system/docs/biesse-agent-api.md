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
| POST | `/events` | Eventos Event.log + labels ZPL |
| POST | `/print-ack` | Ack de impresión local |

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
