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

## Docker (este repo)

Solo backend (`modulesystem` + `modulebiesse`). Postgres externo.

```bash
cp .env.example .env   # editar JDBC, JWT_SECRET, APP_BIESSE_INTERNAL_TOKEN
docker compose up -d --build
```

| Servicio | Puerto interno | Health |
|----------|----------------|--------|
| `modulesystem` | 8080 | `/actuator/health` |
| `modulebiesse` | 8086 | `/actuator/health` |

Stack completo (frontends + Caddy) sigue en el monorepo `appscanner/` con su propio `docker-compose.yml`.

## Coolify

1. Nueva aplicación → repo `allcenter-system` → **Build Pack: Docker Compose**.
2. **Docker Compose Location:** `/docker-compose.yml` (Base Directory `/`).
3. En **Environment Variables**, rellena las keys de `.env.example` (obligatorias: `POSTGRES_*`, `JWT_SECRET`, `APP_BIESSE_INTERNAL_TOKEN`). No dejes literales `${POSTGRES_HOST}` en ningún valor.
4. **Domains** (importante el puerto interno):
   - `modulesystem` → `https://api.tudominio.com:8080`
   - `modulebiesse` → solo si lo expones públicamente: `https://biesse.tudominio.com:8086`  
     (entre contenedores ya se hablan por `http://modulesystem:8080` / `http://modulebiesse:8086`).
5. Deploy. No uses `ports:` en el host: el proxy de Coolify enruta por dominio.

`SERVICE_URL_MODULESYSTEM_8080` / `SERVICE_URL_MODULEBIESSE_8086` en el compose permiten que Coolify asigne FQDN y puerto del proxy automáticamente si usas wildcard domain.

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
