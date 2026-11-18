# Sistema de Consulta Vehicular – C4 + IcePanel

[![CI](https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota/actions/workflows/ci.yml/badge.svg)](https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota/actions/workflows/ci.yml)

> 📘 **[Guía de entrega paso a paso](docs/GUIA-ENTREGA.md)** · 📑 [Contrato de la API](docs/api-contrato.md) · 🛟 [Caché y resiliencia (TTL justificados)](docs/cache-resiliencia.md) · 🧪 [Prueba de usabilidad](docs/prueba-usabilidad.md) · 🧩 [Modelo C4 (Mermaid)](docs/c4-model.md)

Sistema web que:

1. Recibe el **correo electrónico** y el **RUC** de una persona natural (o solo su **cédula**: se valida el dígito verificador y se consulta el RUC `cédula + 001`; si no tiene RUC, el flujo continúa con la licencia y el vehículo).
2. Verifica en el SRI que el RUC sea contribuyente (`existePorNumeroRuc`) y **persona natural** (`obtenerPorNumerosRuc`) y muestra su información.
3. Con la cédula (10 primeros dígitos del RUC) consulta los **puntos de la licencia** en la ANT (`clp_grid_citaciones.jsp`).
4. Pide la **placa** y muestra la información del vehículo (`BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn`).
5. Como la ANT tiene muy baja disponibilidad, usa el patrón **Cache-Aside con respaldo (stale-if-error) + Retry + Circuit Breaker** sobre una **caché Redis en la nube**.

| Capa | Tecnología | Carpeta |
|---|---|---|
| Front end | React 18 + Vite | [`frontend/`](frontend) |
| Microservicio SRI | Java 21, Spring Boot 3.3 | [`backend/sri-service/`](backend/sri-service) |
| Microservicio ANT | Java 21, Spring Boot 3.3, Resilience4j, Jsoup | [`backend/ant-service/`](backend/ant-service) |
| Caché Cloud | Redis en Upstash, usado por **los dos** microservicios (ANT: patrón de resiliencia; SRI: `@Cacheable`, 6 h) | – |

## Arquitectura C4

Modelo en **IcePanel** (los cuatro diagramas son vistas del mismo modelo, de lo general a lo detallado):

| Nivel C4 | Diagrama | Qué muestra |
|---|---|---|
| 1 – Contexto | [Diagrama de contexto](https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=5M5LMF8Q5X) | Usuario, el sistema y los sistemas externos SRI y ANT |
| 2 – Contenedores | [Contenedores - Sistema de Consulta Vehicular](https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=QkKuuBn6Rm) | Web App (React), SRI Service y ANT Service (Java) y Caché Cloud (Redis) |
| 3 – Componentes | [Componentes - ANT Service](https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=9tPsJvLUj0) | Patrón de caché: controlador, servicio, cliente ANT, parser, repositorio Redis, reintento y estado |
| 3 – Componentes | [Componentes - SRI Service](https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=vXnzr0lZ35) | Verificación de contribuyente, vehículo y estado del SRI |

- **ADR-001** (en IcePanel → Decisiones): Cache-Aside con respaldo + Circuit Breaker para la ANT.
- Cada objeto del modelo enlaza a su carpeta o archivo en este repositorio.
- Copia en Mermaid (se ve en GitHub): [`docs/c4-model.md`](docs/c4-model.md)

## Extras

| Extra | Dónde |
|---|---|
| **Panel de estado en vivo** (SRI, ANT, Circuit Breaker y Redis, se actualiza cada 15 s) | Encabezado de la web · `GET /api/sri/estado`, `GET /api/ant/estado` |
| **"Arquitectura en vivo"**: muestra de dónde salió el dato (caché, ANT, respaldo o cola) y métricas del patrón | Tarjeta en la web |
| **Reintento en segundo plano** de consultas sin caché (cola `ant:pendientes` en Redis) | `ReintentoAntJob` |
| **Documentación de APIs con Swagger/OpenAPI** | http://localhost:8081/swagger-ui.html · http://localhost:8082/swagger-ui.html |
| **Pruebas automáticas** del patrón con la ANT simulada por HTTP | `LicenciaServiceTest`, `FlujoCacheAntTest` |
| **Integración continua** (pruebas del backend y build del frontend en cada push) | [GitHub Actions](.github/workflows/ci.yml) |
| **Docker Compose** para levantar todo con un comando | [`docker-compose.yml`](docker-compose.yml) |
| **Modo demostración**: simulador de la ANT (datos marcados como simulados) con interruptor para simular su caída y ver el respaldo desde Redis | Botón 🧪 en la web · `POST /api/ant/demo` |
| **Cédula o RUC** con validación del dígito verificador (módulo 10) | `Identificacion.java` |
| **Nombre por cédula** aunque no tenga RUC (`Persona/obtenerPorTipoIdentificacion` del SRI) | `SriClient.java` |
| **Registro Civil vía Didit** (opcional) para cédulas que el SRI no conoce; solo con `DIDIT_API_KEY` y consentimiento del titular | `RegistroCivilClient.java` |
| **Caché cloud también en el SRI Service** (`@Cacheable` en Redis, claves `sri:*`, TTL 6 h) | `CacheConfig.java` |
| Notificaciones, confeti, contador animado de puntos, tema claro/oscuro, reporte en PDF y placa estilo Ecuador | Web |

### Patrón de caché (ANT Service)

Se invoca **después de ingresar la cédula** (los 10 primeros dígitos del RUC) y de nuevo al ingresar la placa.

```
Front ──► LicenciaController ──► LicenciaService
                                   │ 1. find(key) en Redis ── copia < 30 min ──► devuelve desde caché (origen CACHE)
                                   │ 2. AntClient (Retry + Circuit Breaker) ──► ANT clp_grid_citaciones.jsp
                                   │      respuesta adecuada ──► 3. save(key) en Redis (TTL 7 días)
                                   │                              └─► se lee de Redis y se devuelve (origen ANT)
                                   │      falla ─► 4. última copia válida en Redis ──► (origen CACHE_RESPALDO)
                                   │                 sin copia ──► encola en ant:pendientes + 503
ReintentoAntJob (cada 2 min) ──► reintenta ant:pendientes ──► cuando la ANT responde, guarda en Redis
```

Una respuesta es **adecuada** si es HTTP 2xx, tiene contenido, no es una página de error (502/503/504) y contiene la grilla de citaciones (JSON, XML o tabla HTML de jqGrid). Solo esas respuestas se guardan.

`FlujoCacheAntTest` simula la ANT por HTTP y verifica el flujo completo: la ANT responde → se guarda → la ANT cae (502) → se devuelve desde la caché; y también que una consulta sin caché queda en cola y el job la guarda cuando la ANT vuelve.

> Al momento de entregar, `consultaweb.ant.gob.ec` responde **502 Bad Gateway** en todas sus páginas (incluso la de inicio), mientras `www.ant.gob.ec` sigue enlazando a esa misma URL. Por eso el sistema muestra "ANT no disponible" y deja la consulta en cola hasta que el portal vuelva.

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

`LicenciaServiceTest` (4 casos del patrón) y `FlujoCacheAntTest` (flujo completo con la ANT simulada por HTTP, cola de pendientes y reintento). Las mismas pruebas corren en GitHub Actions en cada push.

## Datos por cédula (personas sin RUC)

1. Primero se usa el SRI: `Persona/obtenerPorTipoIdentificacion` devuelve el nombre de cualquier persona que el SRI conozca, aunque no tenga RUC.
2. Si el SRI no la conoce (responde 204), se puede consultar el **Registro Civil a través de [Didit](https://docs.didit.me/api-reference/database-validation/ecuador/cedula)** (servicio `ecu_cedula`, 500 consultas gratis al mes):
   - crea una cuenta en https://business.didit.me, copia tu API key y ponla en `.env` como `DIDIT_API_KEY=...`;
   - solo se consulta si el usuario marca el **consentimiento** del formulario (Ley Orgánica de Protección de Datos Personales).
3. La vía oficial directa es el web service demográfico del Registro Civil, que exige un convenio de interoperabilidad.

> No se consulta el **propietario de un vehículo por placa**: no existe una fuente oficial pública y los servicios que lo ofrecen no son oficiales. Revelar el dueño de cualquier placa permitiría identificar a desconocidos, así que el sistema solo muestra los datos del vehículo que entrega el SRI.

## Modo demostración (para presentar)

El portal real de la ANT responde **502**, así que en vivo solo se ve el aviso y la cola. Para mostrar el patrón completo:

1. Abre **🧪 Modo demostración** (abajo a la izquierda) y activa **Usar simulador de la ANT**.
2. Consulta una cédula → la tarjeta muestra **⚡ En vivo desde la ANT** y los datos quedan en Redis (prefijo `demo:`).
3. Vuelve a consultar antes de 10 s → **🗄️ Desde la caché**.
4. Activa **Simular caída de la ANT**, espera 10 s y consulta → **🛟 Caché de respaldo** con los mismos datos.
5. Desactiva el modo demostración para volver al portal real.

Los datos simulados llevan el sello **🧪 Datos simulados** y nunca se mezclan con la caché real.

> El indicador **🛡️ Protección activa** (circuit breaker abierto) no es un error: tras varios fallos de la ANT el circuito deja de llamarla durante 60 s y responde desde la caché; luego vuelve a probar sola.

## Docker (opcional)

Con Docker instalado y el archivo `.env` configurado:

```bash
docker compose up --build
```

Abre http://localhost:8080 (nginx sirve el frontend y redirige `/api/sri` y `/api/ant` a cada microservicio).

> Nota: el formato exacto de la grilla de la ANT no está documentado (durante el desarrollo respondía 502). `AntResponseParser` acepta XML de jqGrid o tabla HTML, suma la columna de puntos si existe y calcula los puntos disponibles sobre 30.
