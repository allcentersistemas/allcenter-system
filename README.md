# AllCenter Backend

Backend AllCenter en **monolito modular** (`module-system`) más integración **Biesse** (`module-biesse`).

## Módulos Maven

| Módulo | Puerto | Descripción |
|--------|--------|-------------|
| `module-system` | 8080 | API principal: empleados, auth, pales, transporte, órdenes, ubicaciones, inventario, RM, portal clientes |
| `module-biesse` | 8086 | Escaneo OSI / piezas (BD `obras`) |
| `com.allcenter.security` (en `module-system` y `module-biesse`) | — | CORS, cabeceras HTTP, validación JWT en APIs |

## Arranque local

```bash
# Desde esta carpeta (allcenter-system)
mvn -pl module-system -am package -DskipTests
java -jar module-system/target/module-system-0.0.1-SNAPSHOT.jar

# Biesse (otra terminal)
mvn -pl module-biesse -am package -DskipTests
java -jar module-biesse/target/module-biesse-0.0.1-SNAPSHOT.jar
```

Variables habituales: `SPRING_DATASOURCE_*` (PostgreSQL `app_db`), `BIESSE_DATASOURCE_*` (BD `obras`), `JWT_SECRET`.

## Auth

| Audiencia | Base path |
|-----------|-----------|
| Empleados (app + Android) | `/api/auth/*` |
| Portal clientes | `/api/client/auth/*` |

## Docker (monorepo)

Backends + frontends. Postgres externo. Los build context de `frontend` / `frontend-client` están en el padre (`../frontend`).

```bash
# Desde allcenter-system/ (con carpeta hermana frontend/ y frontend-client/)
cp .env.example .env   # POSTGRES_*, JWT_SECRET, APP_BIESSE_INTERNAL_TOKEN
docker compose up -d --build
```

| Servicio | Puerto interno | Health / rol |
|----------|----------------|--------------|
| `modulesystem` | 8080 | `/actuator/health` |
| `modulebiesse` | 8086 | `/actuator/health` |
| `frontend` | 80 | portal empleados (nginx + proxy API) |
| `frontend-client` | 80 | app clientes |

Alternativa con Caddy en un solo host: `appscanner/docker-compose.yml` en la raíz del monorepo.

## Coolify

1. App sobre el **monorepo** (debe incluir `frontend/` y `frontend-client/` al lado de `allcenter-system/`).
2. **Build Pack: Docker Compose**. **Compose Location:** `/allcenter-system/docker-compose.yml`.
3. Environment: keys de `.env.example` (`POSTGRES_*`, `JWT_SECRET`, `APP_BIESSE_INTERNAL_TOKEN`).
4. **Domains** (puerto interno):
   - `frontend` → `https://portal.tudominio.com:80`
   - `frontend-client` → `https://app.tudominio.com:80`
   - `modulesystem` / `modulebiesse` solo si expones API directa (`:8080` / `:8086`)
5. Deploy. Sin `ports:` en el host: Coolify enruta por dominio.

`SERVICE_URL_*` en el compose permite que Coolify asigne FQDN automáticamente.

## IntelliJ IDEA

1. **File → Open** → carpeta `allcenter-system` (o el `pom.xml` raíz).
2. Clic derecho en el proyectoOptimizacion → **Maven → Reload project**.
3. Run configuration: `ModuleSystemApplication` (módulo `module-system`).

Si ves módulos fantasma de la estructura antigua, borra caché: **File → Invalidate Caches**.

## Estructura de código (`module-system`)

```
com.allcenter.modulesystem
├── model/       # Entidades JPA
├── dto/         # Request/response
├── repository/
├── service/
├── controller/  # REST
├── config/      # Seguridad, bootstrap, properties
└── security/    # JWT
```
