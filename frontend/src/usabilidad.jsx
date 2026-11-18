import { useEffect, useRef, useState } from 'react';

/**
 * Registrador de la prueba de usabilidad.
 * El facilitador inicia cada tarea, cuenta errores, marca éxito/fracaso y anota observaciones.
 * Los resultados se guardan en el navegador (localStorage) y se exportan a Markdown (docs/prueba-usabilidad.md).
 */

export const TAREAS = [
  {
    id: 'T1',
    titulo: 'Verificar a la persona',
    consigna: 'Ingresa tu correo y tu cédula y averigua si eres contribuyente del SRI.',
    exito: 'Ve la tarjeta "Contribuyente verificado" o "Cédula verificada" con su nombre.',
  },
  {
    id: 'T2',
    titulo: 'Puntos de la licencia',
    consigna: '¿Cuántos puntos tiene tu licencia y de dónde salió esa información?',
    exito: 'Dice el número de puntos (o que la ANT no está disponible) y el origen (ANT, caché o respaldo).',
  },
  {
    id: 'T3',
    titulo: 'Datos del vehículo',
    consigna: 'Consulta tu vehículo con la placa y di de qué año es.',
    exito: 'Ingresa la placa y lee correctamente el año del vehículo.',
  },
  {
    id: 'T4',
    titulo: 'ANT caída',
    consigna: 'Con el modo demostración, simula que la ANT se cae y vuelve a consultar. ¿El dato es reciente?',
    exito: 'Activa la caída, consulta de nuevo y reconoce el aviso "Este dato no es reciente".',
  },
  {
    id: 'T5',
    titulo: 'Reporte',
    consigna: 'Guarda un reporte en PDF de tu consulta.',
    exito: 'Abre el diálogo de impresión desde "Descargar reporte (PDF)".',
  },
];

const CLAVE = 'prueba-usabilidad-v1';

function nuevaSesion() {
  return {
    participante: '',
    perfil: '',
    fecha: new Date().toISOString().slice(0, 10),
    tareas: Object.fromEntries(TAREAS.map((t) => [t.id, {
      inicio: null, segundos: null, errores: 0, resultado: null, facilidad: null, notas: '',
    }])),
    observaciones: '',
    mejoras: '',
  };
}

function cargar() {
  try {
    return JSON.parse(localStorage.getItem(CLAVE)) ?? nuevaSesion();
  } catch {
    return nuevaSesion();
  }
}

function formatoTiempo(s) {
  if (s === null || s === undefined) return '—';
  const m = Math.floor(s / 60);
  return `${m}:${String(s % 60).padStart(2, '0')}`;
}

export function aMarkdown(sesion) {
  const filas = TAREAS.map((t) => {
    const r = sesion.tareas[t.id];
    const res = r.resultado === 'exito' ? '✅ Éxito' : r.resultado === 'ayuda' ? '🟡 Con ayuda' : r.resultado === 'fracaso' ? '❌ No completó' : '—';
    return `| ${t.id} | ${t.titulo} | ${res} | ${formatoTiempo(r.segundos)} | ${r.errores} | ${r.facilidad ?? '—'} | ${(r.notas || '').replace(/\n/g, ' ')} |`;
  });
  const hechas = TAREAS.filter((t) => sesion.tareas[t.id].resultado);
  const exitos = hechas.filter((t) => sesion.tareas[t.id].resultado === 'exito').length;
  const tiempos = hechas.map((t) => sesion.tareas[t.id].segundos ?? 0);
  const errores = hechas.reduce((a, t) => a + sesion.tareas[t.id].errores, 0);
  const prom = tiempos.length ? Math.round(tiempos.reduce((a, b) => a + b, 0) / tiempos.length) : null;
  return `## Sesión: ${sesion.participante || 'Participante'} (${sesion.fecha})

- **Perfil:** ${sesion.perfil || '—'}
- **Tasa de éxito:** ${exitos}/${hechas.length} tareas sin ayuda
- **Tiempo promedio por tarea:** ${formatoTiempo(prom)}
- **Errores totales:** ${errores}

| Tarea | Descripción | Resultado | Tiempo (m:ss) | Errores | Facilidad (1-5) | Observaciones |
|---|---|---|---|---|---|---|
${filas.join('\n')}

### Observaciones generales
${sesion.observaciones || '—'}

### Mejoras propuestas
${sesion.mejoras || '—'}
`;
}

export function PanelUsabilidad() {
  const [abierto, setAbierto] = useState(false);
  const [sesion, setSesion] = useState(cargar);
  const [ahora, setAhora] = useState(Date.now());
  const [copiado, setCopiado] = useState(false);
  const ref = useRef(null);

  useEffect(() => localStorage.setItem(CLAVE, JSON.stringify(sesion)), [sesion]);
  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 500);
    return () => clearInterval(t);
  }, []);

  const enCurso = TAREAS.find((t) => sesion.tareas[t.id].inicio && sesion.tareas[t.id].segundos === null);

  function actualizar(id, cambios) {
    setSesion((s) => ({ ...s, tareas: { ...s.tareas, [id]: { ...s.tareas[id], ...cambios } } }));
  }

  function iniciar(id) {
    actualizar(id, { inicio: Date.now(), segundos: null, resultado: null, errores: 0 });
  }

  function terminar(id, resultado) {
    const r = sesion.tareas[id];
    const segundos = r.inicio ? Math.round((Date.now() - r.inicio) / 1000) : r.segundos ?? 0;
    actualizar(id, { resultado, segundos });
  }

  function descargar() {
    const blob = new Blob([`# Prueba de usabilidad – resultados\n\n${aMarkdown(sesion)}`], { type: 'text/markdown;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `usabilidad-${sesion.participante || 'participante'}-${sesion.fecha}.md`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  async function copiar() {
    await navigator.clipboard.writeText(aMarkdown(sesion));
    setCopiado(true);
    setTimeout(() => setCopiado(false), 2000);
  }

  function reiniciar() {
    if (window.confirm('¿Borrar los datos de esta sesión y empezar con otro participante?')) setSesion(nuevaSesion());
  }

  return (
    <div className={`panel-usabilidad ${abierto ? 'abierto' : ''}`} ref={ref}>
      <button className="panel-usabilidad-boton" onClick={() => setAbierto(!abierto)}>
        📋 {enCurso ? `${enCurso.id} en curso · ${formatoTiempo(Math.round((ahora - sesion.tareas[enCurso.id].inicio) / 1000))}` : 'Prueba de usabilidad'}
      </button>
      {abierto && (
        <div className="panel-usabilidad-cuerpo entrar">
          <div className="pu-cabecera">
            <h3>Prueba de usabilidad</h3>
            <button className="pu-cerrar" onClick={() => setAbierto(false)} aria-label="Cerrar">✕</button>
          </div>
          <p className="pu-ayuda">
            Para el facilitador: lee la consigna, pulsa <b>Iniciar</b>, suma un error cada vez que la persona se equivoque y
            marca el resultado. Protocolo completo en <code>docs/prueba-usabilidad.md</code>.
          </p>
          <div className="pu-fila">
            <input placeholder="Participante (alias, p. ej. P1)" value={sesion.participante}
                   onChange={(e) => setSesion({ ...sesion, participante: e.target.value })} />
            <input type="date" value={sesion.fecha} onChange={(e) => setSesion({ ...sesion, fecha: e.target.value })} />
          </div>
          <input className="pu-perfil" placeholder="Perfil (edad, uso de tecnología, ¿tiene vehículo?)" value={sesion.perfil}
                 onChange={(e) => setSesion({ ...sesion, perfil: e.target.value })} />

          <ol className="pu-tareas">
            {TAREAS.map((t) => {
              const r = sesion.tareas[t.id];
              const corriendo = r.inicio && r.segundos === null;
              const seg = corriendo ? Math.round((ahora - r.inicio) / 1000) : r.segundos;
              return (
                <li key={t.id} className={`pu-tarea ${r.resultado ?? ''} ${corriendo ? 'corriendo' : ''}`}>
                  <div className="pu-titulo">
                    <strong>{t.id} · {t.titulo}</strong>
                    <span className="pu-tiempo">⏱ {formatoTiempo(seg)}</span>
                  </div>
                  <p className="pu-consigna">“{t.consigna}”</p>
                  <p className="pu-exito">Éxito si: {t.exito}</p>
                  <div className="pu-botones">
                    {!corriendo && <button onClick={() => iniciar(t.id)}>{r.resultado ? '↺ Repetir' : '▶ Iniciar'}</button>}
                    {corriendo && (
                      <>
                        <button onClick={() => actualizar(t.id, { errores: r.errores + 1 })}>＋ Error ({r.errores})</button>
                        <button className="ok" onClick={() => terminar(t.id, 'exito')}>✓ Éxito</button>
                        <button className="ayuda" onClick={() => terminar(t.id, 'ayuda')}>🟡 Con ayuda</button>
                        <button className="mal" onClick={() => terminar(t.id, 'fracaso')}>✗ No completó</button>
                      </>
                    )}
                    {!corriendo && r.resultado && <span className="pu-resumen">{r.errores} error(es)</span>}
                  </div>
                  {r.resultado && (
                    <div className="pu-extra">
                      <label>Facilidad
                        <select value={r.facilidad ?? ''} onChange={(e) => actualizar(t.id, { facilidad: Number(e.target.value) || null })}>
                          <option value="">—</option>
                          {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
                        </select>
                      </label>
                      <input placeholder="Qué pasó / qué dijo" value={r.notas} onChange={(e) => actualizar(t.id, { notas: e.target.value })} />
                    </div>
                  )}
                </li>
              );
            })}
          </ol>

          <textarea placeholder="Observaciones generales (dudas, comentarios, dónde se trabó)" value={sesion.observaciones}
                    onChange={(e) => setSesion({ ...sesion, observaciones: e.target.value })} />
          <textarea placeholder="Mejoras propuestas (concretas: qué cambiar y por qué)" value={sesion.mejoras}
                    onChange={(e) => setSesion({ ...sesion, mejoras: e.target.value })} />

          <div className="pu-acciones">
            <button className="principal" onClick={descargar}>⬇ Descargar resultados (.md)</button>
            <button onClick={copiar}>{copiado ? '✓ Copiado' : '📋 Copiar'}</button>
            <button onClick={reiniciar}>🗑 Nuevo participante</button>
          </div>
        </div>
      )}
    </div>
  );
}
