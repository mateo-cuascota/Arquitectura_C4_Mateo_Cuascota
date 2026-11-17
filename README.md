# Sistema de Consulta Vehicular – C4 + IcePanel

Sistema web que:

1. Recibe el **correo electrónico** y el **RUC** de una persona natural.
2. Verifica en el SRI que el RUC sea contribuyente (`existePorNumeroRuc`) y **persona natural** (`obtenerPorNumerosRuc`) y muestra su información.
3. Con la cédula (10 primeros dígitos del RUC) consulta los **puntos de la licencia** en la ANT (`clp_grid_citaciones.jsp`).
4. Pide la **placa** y muestra la información del vehículo (`BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn`).
5. Como la ANT tiene muy baja disponibilidad, usa el patrón **Cache-Aside con respaldo (stale-if-error) + Retry + Circuit Breaker** sobre una **caché Redis en la nube**.

| Capa | Tecnología | Carpeta |
|---|---|---|
| Front end | React 18 + Vite | [`frontend/`](frontend) |
| Microservicio SRI | Java 21, Spring Boot 3.3 | [`backend/sri-service/`](backend/sri-service) |
| Microservicio ANT | Java 21, Spring Boot 3.3, Resilience4j, Jsoup | [`backend/ant-service/`](backend/ant-service) |
| Caché Cloud | Redis (Redis Cloud / Azure Cache for Redis / Upstash) | – |

## Arquitectura C4

- **IcePanel**: https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=5M5LMF8Q5X
  - Nivel 1 – *Context Diagram*
  - Nivel 2 – *Contenedores - Sistema de Consulta Vehicular*
  - Nivel 3 – *Componentes - ANT Service* y *Componentes - SRI Service*
  - ADR-001 – Cache-Aside con respaldo + Circuit Breaker para la ANT
- Copia en Mermaid (se ve en GitHub): [`docs/c4-model.md`](docs/c4-model.md)

### Patrón de caché (ANT Service)

```
Front ──► LicenciaController ──► LicenciaService
                                   │ 1. find(key) en Redis ── copia < 30 min ──► devuelve (origen CACHE)
                                   │ 2. AntClient (Retry + Circuit Breaker) ──► ANT
                                   │      ok  ──► 3. save(key) TTL 7 días ──► devuelve (origen ANT)
                                   │      falla ─► 4. última copia válida ──► devuelve (origen CACHE_RESPALDO)
                                   │                 sin copia ──► 503
```

## Ejecutar en local

Requisitos: **Java 21** y **Node.js 18+** (`winget install OpenJS.NodeJS.LTS`). Maven **no** hace falta: el proyecto trae Maven Wrapper (`backend/mvnw.cmd`).

### 1. Caché Cloud (Redis) – una sola vez

1. Crear una base gratuita en **Upstash** (https://console.upstash.com → *Create Database*) o **Redis Cloud** (https://redis.io/try-free).
2. Copiar la URL de conexión `rediss://default:<password>@<host>:<puerto>`.
3. En la raíz del proyecto copiar `.env.example` como `.env` (los scripts de inicio lo crean solos si no existe) y pegarla:

```bash
cp .env.example .env
```

(En PowerShell/CMD: `copy .env.example .env`.)

```properties
REDIS_URL=rediss://default:<password>@<host>:<puerto>
```

El archivo `.env` está en `.gitignore` (la contraseña no se sube a GitHub). El ANT Service lo lee automáticamente.
Si Redis no está disponible el sistema sigue funcionando, solo que sin caché.

### 2. Opción rápida: un solo comando

Según la terminal:

| Terminal | Comando |
|---|---|
| bash (Git Bash / MSYS2, terminal por defecto de VS Code en este equipo) | `./iniciar.sh` |
| PowerShell | `.\iniciar.cmd` |
| Explorador de Windows | doble clic en `iniciar.cmd` |

El script compila el backend, instala el frontend y abre tres ventanas: SRI Service (8081), ANT Service (8082) y Web App (5173). Luego abrir http://localhost:5173.

### 3. Opción manual

```bash
cd backend
.\mvnw.cmd package
```

```bash
java -jar backend/sri-service/target/sri-service-1.0.0.jar
```

```bash
java -jar backend/ant-service/target/ant-service-1.0.0.jar
```

- SRI Service → http://localhost:8081
  - `GET /api/sri/contribuyentes/{ruc}?email=correo@dominio.com`
  - `GET /api/sri/vehiculos/{placa}`
- ANT Service → http://localhost:8082
  - `GET /api/ant/licencias/{cedula}?placa=`
  - `GET /actuator/circuitbreakers` (estado del circuito)

Front end:

```bash
cd frontend
npm install
npm run dev
```

Abrir http://localhost:5173 (Vite redirige `/api/sri` → 8081 y `/api/ant` → 8082).

### Variables de entorno

| Variable | Servicio | Por defecto |
|---|---|---|
| `REDIS_URL` | ant-service | `redis://localhost:6379` |
| `ANT_CACHE_FRESH_TTL` | ant-service | `PT30M` |
| `ANT_CACHE_TTL` | ant-service | `P7D` |
| `CORS_ORIGINS` | ambos | `http://localhost:5173` |
| `PORT` | ambos | 8081 / 8082 |
| `VITE_SRI_URL`, `VITE_ANT_URL` | frontend (build) | vacío (usa el proxy) |

## Enlazar GitHub con IcePanel

1. Subir este proyecto a GitHub (ver comandos abajo).
2. En IcePanel: **Settings → Integrations → GitHub → Connect** y autorizar el repositorio.
3. En el diagrama de contenedores, seleccionar cada objeto y en **Links → Add link** elegir la ruta del repositorio:
   - *Web App* → `frontend/`
   - *SRI Service* → `backend/sri-service/`
   - *ANT Service* → `backend/ant-service/`
   - *Sistema de Consulta Vehicular* → raíz del repositorio
   - En los diagramas de componentes: `LicenciaService.java`, `AntClient.java`, `LicenciaCacheRepository.java`, `SriClient.java`, etc.

```bash
git init
git add .
git commit -m "Sistema de consulta vehicular: C4, React, Spring Boot y Redis"
git branch -M main
git remote add origin https://github.com/<usuario>/<repositorio>.git
git push -u origin main
```

## Pruebas

```bash
cd backend
./mvnw test
```

`LicenciaServiceTest` cubre los cuatro casos del patrón: respuesta en vivo que se guarda en caché, caché reciente, caché de respaldo con la ANT caída y 503 sin caché.

> Nota: el formato exacto de la grilla de la ANT no está documentado (durante el desarrollo respondía 502). `AntResponseParser` acepta XML de jqGrid o tabla HTML, suma la columna de puntos si existe y calcula los puntos disponibles sobre 30.
