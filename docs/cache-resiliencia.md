# Caché y resiliencia

## Problema
El portal de la ANT (`consultaweb.ant.gob.ec/PortalWEB/.../clp_grid_citaciones.jsp`) tiene muy baja disponibilidad: durante el desarrollo respondió **502 Bad Gateway** en todas sus páginas. La consulta de puntos de licencia no puede depender de que esté en línea.

## Patrón: Cache-Aside con respaldo (stale-if-error) + Retry + Circuit Breaker
Implementado en `LicenciaService` (ANT Service) sobre **Redis en la nube (Upstash)**.

```
1. ¿Hay copia en Redis de menos de 30 min?  ── sí ──► responder desde la caché (origen CACHE)
2. no → llamar a la ANT (Retry ×2, timeout 10 s, Circuit Breaker)
3.     respuesta adecuada ──► guardar en Redis (7 días) ──► leer de Redis y responder (origen ANT)
4.     falla ──► ¿hay copia (aunque sea vieja)? ── sí ──► responder con la copia + aviso (origen CACHE_RESPALDO)
5.                                             ── no ──► 503 ANT_NO_DISPONIBLE + Retry-After: 60
                                                        y la consulta queda en cola (ant:pendientes)
6. ReintentoAntJob (cada 2 min) reintenta la cola; cuando la ANT responde, guarda en Redis.
```

**Respuesta adecuada** = HTTP 2xx, con contenido, que no sea una página de error (502/503/504) y que contenga la grilla de citaciones (JSON, XML o tabla HTML de jqGrid). Solo esas respuestas se guardan; así nunca se guarda en caché una página de error.

## Cómo se avisa que el dato no es reciente
Cada respuesta incluye:
- `origen`: `ANT` (en vivo), `CACHE` (copia reciente) o `CACHE_RESPALDO` (la ANT falló).
- `obtenidoEn` y `antiguedadSegundos`: cuándo entregó la ANT ese dato.
- `aviso`: texto para el usuario.

La web muestra la etiqueta del origen, "Dato de la ANT hace X" y, en `CACHE_RESPALDO`, un recuadro **"⚠️ Este dato no es reciente"**.

## Tiempos de vida (TTL) y su justificación

| Parámetro | Valor | Variable | Justificación |
|---|---|---|---|
| Copia reciente (`fresh-ttl`) | **30 min** | `ANT_CACHE_FRESH_TTL` | Las citaciones y los puntos cambian como mucho unas pocas veces al día (una multa nueva, un pago). 30 min evita repetir consultas a un portal inestable durante una misma sesión, sin mostrar datos que puedan haber cambiado de forma relevante. |
| Respaldo (`ttl`) | **7 días** | `ANT_CACHE_TTL` | Las caídas de la ANT pueden durar horas o días. Una copia de hasta una semana, **marcada como no reciente**, es más útil que un error. Pasados 7 días el dato puede estar desactualizado (vencimiento de multas, nuevas citaciones) y se descarta. |
| Circuit breaker abierto | **60 s** | `wait-duration-in-open-state` | Si fallan ≥50 % de las últimas 10 llamadas (mínimo 4), se deja de llamar a la ANT por 60 s. Protege al portal y evita que el usuario espere timeouts de 10 s en cada consulta; luego 2 llamadas de prueba deciden si se cierra. |
| Retry | **2 intentos**, 500 ms | `resilience4j.retry` | Absorbe fallos transitorios sin multiplicar la carga sobre un servicio que ya está saturado. |
| Timeout ANT | **10 s** | `ant.timeout-ms` | El portal tarda varios segundos cuando responde; más espera no mejora la tasa de éxito. |
| Reintento en segundo plano | **2 min** | `ANT_REINTENTO_INTERVALO` | Recupera consultas sin copia tan pronto como la ANT vuelve, sin saturarla. |
| Caché del SRI | **6 h** | `SRI_CACHE_TTL` | El catastro del SRI y los datos del vehículo cambian rara vez; reduce latencia (3,3 s → 1 s medido) y dependencia del SRI. |
| Nombre vía Registro Civil | **7 días** | — | Los nombres casi nunca cambian y cada consulta a Didit tiene costo. |

## Verificación
- `FlujoCacheAntTest`: simula la ANT por HTTP → responde → se guarda → cae (502) → se devuelve desde la caché; y la cola + reintento.
- `LicenciaServiceTest`: los cuatro caminos del patrón.
- `AntContratoApiTest`: `CACHE_RESPALDO` incluye `obtenidoEn` y `aviso`; sin caché responde `503` con `Retry-After`.
- En vivo: **🧪 Modo demostración** de la web (simulador de la ANT con caída simulada).
