# API Agente Biesse (`module-biesse`)

Base: `http://SERVIDOR:8086/api/biesse/agent`  
Auth: header `X-Agent-Token`

## Endpoints

| Método | Path | Uso |
|--------|------|-----|
| GET | `/me` | Validar token → `machine_id` / `machine_name` |
| POST | `/heartbeat` | Alive (5–10s) |
| POST | `/status` | Snapshot máquina; `RUN` + job → obra `PRODUCCION` |
| POST | `/events` | Eventos OSI; responde `labels[].zpl` para Part |
| POST | `/print-ack` | Resultado impresión local |

## Producción y tiempos

Al `Start program` o status `RUN` con `job_name` que matchea una obra:

1. `ordenes.estado_escaneo = PRODUCCION`
2. `op_trazabilidad` con `CORTE_INICIO` (+ timestamp)
3. Al pasar a Idle: `CORTE_FIN` con duración en segundos
4. Cada `PRODUCT INFO Part`: `PIEZA_CORTADA` + etiqueta ZPL

Match de job: `ordername` / `bookingcode` / `op_codigo` / prefijo fuzzy.

## Frontend (portal empleados)

Inventario → pestaña **Monitor CNC**:
- Tarjetas de máquina (online, RUN/IDLE, job, tiempo de corte)
- Eventos OSI recientes
- Piezas cortadas / stickers

Inventario → **Órdenes Biesse**:
- Estados **Optimizado** / **Producción**
- En el detalle: panel **Trazabilidad OP** (CORTE_INICIO, CORTE_FIN, PIEZA_CORTADA, XML_SUBIDO)

APIs JWT (mismo permiso que órdenes):
- `GET /api/biesse/scan/agent/machines`
- `GET /api/biesse/scan/agent/events`
- `GET /api/biesse/scan/agent/cut-pieces`
- `GET /api/biesse/scan/agent/trazabilidad?orderId=`

