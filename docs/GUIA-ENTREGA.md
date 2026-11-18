# Guía de entrega paso a paso (rúbrica Arquitectura C4)

Esta guía recorre cada criterio de la rúbrica: qué hay en el proyecto, cómo mostrarlo y qué falta hacer **a mano** (marcado con ✋).

Repositorio: https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota

---

## 0. Preparar el entorno (una sola vez)

1. Instalar **Java 21** y **Node.js 18+** (`winget install OpenJS.NodeJS.LTS`). Maven no hace falta: el proyecto trae `mvnw`.
2. Clonar el repositorio y entrar a la carpeta.
3. Crear la caché en la nube: base gratis en https://console.upstash.com → copiar la URL `rediss://...`.
4. Copiar `.env.example` como `.env` y pegar la URL en `REDIS_URL=`.
5. (Opcional) `DIDIT_API_KEY=` para obtener el nombre de cédulas sin RUC desde el Registro Civil.

## 1. Ejecutar el sistema

```bash
./iniciar.sh
```
(en PowerShell: `.\iniciar.cmd`). Se abren tres ventanas: SRI Service (8081), ANT Service (8082) y Web App (5173). Abrir http://localhost:5173.

Pruebas automáticas (33 casos):
```bash
cd backend && ./mvnw test
```

---

## Criterio 1 · Modelo C4 en IcePanel (2.5 pts)

**Qué hay:** un landscape con 4 diagramas coherentes con el código:

| Nivel | Diagrama | Contenido |
|---|---|---|
| 1 Contexto | Diagrama de contexto | Usuario, Sistema de Consulta Vehicular, SRI, ANT, Registro Civil (opcional) |
| 2 Contenedores | Contenedores – Sistema de Consulta Vehicular | Web App (React), SRI Service y ANT Service (Java), Caché Cloud (Redis/Upstash) |
| 3 Componentes | Componentes – ANT Service | Controller, LicenciaService (patrón), AntClient (Retry + CB), Parser, Repositorio Redis, ReintentoAntJob, Estado, Simulador |
| 3 Componentes | Componentes – SRI Service | Controller, ContribuyenteService, VehiculoService, SriClient (@Cacheable), RegistroCivilClient, Estado |

Cada objeto tiene en su descripción el enlace a su carpeta o archivo en GitHub, y hay un ADR (ADR-001) con la decisión del patrón de caché.

✋ **Para tener el puntaje completo hay que hacer dos cosas en IcePanel con tu cuenta:**

**a) Enlazar los objetos con GitHub (sección *Links*)**
1. Abrir el diagrama de **Contenedores** → seleccionar **Web App** → en el panel derecho, sección **Links**, pulsar **+** → pegar
   `https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota/tree/main/frontend`
2. Repetir con:
   - **SRI Service** → `https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota/tree/main/backend/sri-service`
   - **ANT Service** → `https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota/tree/main/backend/ant-service`
   - **Sistema de Consulta Vehicular** (diagrama de contexto) → `https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota`
3. (Opcional) **Settings → Integrations → GitHub → Connect** para que IcePanel muestre el ícono de GitHub y el estado del repositorio.

**b) Crear el enlace de lectura (sin iniciar sesión)**
1. En el landscape, botón **Share** (arriba a la derecha).
2. Activar el **enlace público / de solo lectura** (*Share link* / *Public link*) y copiarlo.
3. Abrirlo en una ventana de incógnito para comprobar que se ve **sin iniciar sesión**.
4. Pegar ese enlace en el README (sección *Arquitectura C4*) y en la entrega.

> ✅ Enlace público ya creado y verificado sin sesión: https://s.icepanel.io/rIEoRmDe7NOiiG/bx5d

---

## Criterio 2 · Integraciones y contrato de la API (2.5 pts)

**Qué hay:**
- Verbos y rutas REST: `GET /api/sri/contribuyentes/{cedulaORuc}?email=`, `GET /api/sri/vehiculos/{placa}`, `GET /api/ant/licencias/{cedula}?placa=`, `GET .../estado`, `POST /api/ant/demo`.
- Códigos HTTP correctos: 200, 400, 404, 405, 422, 500, 502, 503 (con `Retry-After`).
- Errores estructurados con `codigo` + `mensaje` + `status` + `instance` + `timestamp` (RFC 9457).
- Validación de entradas: correo obligatorio y con formato válido, cédula con dígito verificador, RUC con `001`, placa.
- Documento del contrato: [`docs/api-contrato.md`](api-contrato.md). Swagger: http://localhost:8081/swagger-ui.html y http://localhost:8082/swagger-ui.html
- Pruebas de contrato: `SriContratoApiTest` (12) y `AntContratoApiTest` (5).

**Cómo mostrarlo:**
1. Abrir Swagger del SRI → `GET /api/sri/contribuyentes/{identificacion}` → *Try it out* con `1726617748` y un correo → ver `400 CEDULA_INVALIDA`.
2. Repetir sin correo → `400 CORREO_REQUERIDO`; con un RUC de sociedad (`1790016919001`) → `422 NO_ES_PERSONA_NATURAL`.
3. En el ANT, `GET /api/ant/licencias/1726617747` con la ANT caída → `503 ANT_NO_DISPONIBLE` con `Retry-After: 60`.

---

## Criterio 3 · Caché y resiliencia (2 pts)

**Qué hay:** patrón Cache-Aside con respaldo + Retry + Circuit Breaker + reintento en segundo plano, sobre Redis en Upstash. Documento: [`docs/cache-resiliencia.md`](cache-resiliencia.md) (incluye la **justificación de cada TTL**).
- Cuando la ANT falla responde con **el último dato bueno**.
- **Avisa que no es reciente:** etiqueta *Caché de respaldo*, "Dato de la ANT hace X" y recuadro *⚠️ Este dato no es reciente*.
- **Tiempo de vida definido y justificado:** copia reciente 30 min, respaldo 7 días, circuito 60 s (visibles en la tarjeta y en `/api/ant/estado`).

**Cómo mostrarlo (el portal real de la ANT está caído, se usa el simulador):**
1. Botón **🧪 Modo demostración** → activar **Usar simulador de la ANT**.
2. Consultar una cédula → **⚡ En vivo desde la ANT** (se guarda en Redis).
3. Volver a consultar antes de 10 s → **🗄️ Desde la caché**.
4. Activar **Simular caída de la ANT**, esperar 10 s y consultar → **🛟 Caché de respaldo** + *⚠️ Este dato no es reciente*.
5. Mostrar la tarjeta **Arquitectura en vivo** (camino del dato y métricas) y el indicador **🛡️ Protección activa** del circuit breaker.
6. Desactivar el modo demostración.

---

## Criterio 4 · Funcionamiento de React y Java (1.5 pts)

Recorrido de principio a fin en http://localhost:5173:
1. Escribir correo + cédula (o RUC) → marcar el consentimiento → **Consultar**.
2. Se muestra la persona (SRI: contribuyente, persona natural, datos).
3. Se consultan automáticamente los puntos de la licencia (ANT).
4. Escribir la placa → **Consultar vehículo** → datos del vehículo (SRI).
5. Panel de estado arriba: SRI, ANT, circuit breaker y Redis en vivo.

CI: cada push ejecuta las pruebas del backend y el build del frontend en GitHub Actions (insignia en el README).

---

## Criterio 5 · Prueba de usabilidad (1.5 pts)

✋ **Se debe hacer con una persona real.** Todo está preparado:
1. Leer el protocolo: [`docs/prueba-usabilidad.md`](prueba-usabilidad.md) (objetivo, participante, guion, 5 tareas con criterio de éxito y tiempo objetivo).
2. Con la persona frente al computador, abrir **📋 Prueba de usabilidad** (abajo a la derecha).
3. Escribir el alias (P1), la fecha y el perfil.
4. Para cada tarea: leer la consigna → **▶ Iniciar** → **＋ Error** por cada equivocación → **✓ Éxito / 🟡 Con ayuda / ✗ No completó** → facilidad 1–5 y comentario.
5. Escribir observaciones y **mejoras concretas** (qué cambiar y por qué).
6. **⬇ Descargar resultados (.md)** y pegar el contenido en la sección *7. Resultados* de `docs/prueba-usabilidad.md`; completar la tabla de *8. Mejoras*.
7. Subir el documento al repositorio: `git add docs/prueba-usabilidad.md && git commit -m "Resultados de la prueba de usabilidad" && git push`.

Recomendación: hacerla con 2 o 3 personas y, si alguna mejora es rápida, aplicarla y mencionarla.

---

## Lista final antes de entregar

- [ ] `./iniciar.sh` levanta todo y la web consulta SRI, licencia y vehículo.
- [ ] `./mvnw test` en verde (33 pruebas) y CI en verde en GitHub.
- [ ] IcePanel: objetos con **Links** a GitHub ✋.
- [ ] IcePanel: **enlace público de lectura** probado en incógnito y pegado en el README ✋.
- [ ] Prueba de usabilidad hecha con al menos una persona y resultados en `docs/prueba-usabilidad.md` ✋.
- [ ] Demostración del modo demo practicada (caída de la ANT → respaldo).
