# AllCenter Backend

Backend AllCenter en **monolito modular** (`module-system`) más integración **Biesse** (`module-biesse`).

## Módulos Maven

| Módulo | Puerto | Descripción |
|--------|-------|-------------|
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

## Docker

Desde la raíz del repo (`appscanner/`):

```bash
cp .env.example .env   # editar POSTGRES_PASSWORD
docker compose up -d --build
```

Un solo `.env` y `docker-compose.yml` en la raíz. Producción: `https://app.allcenter.pe` (Caddy + Let's Encrypt).

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
