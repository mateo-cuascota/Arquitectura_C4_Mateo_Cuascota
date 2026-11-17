import { useEffect, useRef, useState } from 'react';

/* ------------------------------------------------------------------ utilidades */

export function formatearFecha(valor) {
  if (!valor) return null;
  const m = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : valor;
}

export function iniciales(nombre = '') {
  const p = nombre.trim().split(/\s+/).filter(Boolean);
  if (p.length === 0) return '?';
  return ((p[2]?.[0] ?? p[0][0]) + p[0][0]).toUpperCase();
}

/** Número que sube animado desde 0 hasta el valor. */
export function Contador({ valor, duracion = 900 }) {
  const [actual, setActual] = useState(0);
  useEffect(() => {
    if (valor === null || valor === undefined) return undefined;
    let raf;
    const inicio = performance.now();
    const paso = (t) => {
      const p = Math.min(1, (t - inicio) / duracion);
      setActual(Math.round(valor * (1 - Math.pow(1 - p, 3))));
      if (p < 1) raf = requestAnimationFrame(paso);
    };
    raf = requestAnimationFrame(paso);
    return () => cancelAnimationFrame(raf);
  }, [valor, duracion]);
  return <>{valor === null || valor === undefined ? '—' : actual}</>;
}

/* ------------------------------------------------------------------ fondo y efectos */

export function FondoAurora() {
  return (
    <div className="aurora" aria-hidden="true">
      <span className="blob b1" />
      <span className="blob b2" />
      <span className="blob b3" />
      <span className="blob b4" />
      <div className="rejilla" />
    </div>
  );
}

export function Confeti({ disparo }) {
  const [piezas, setPiezas] = useState([]);
  useEffect(() => {
    if (!disparo) return undefined;
    const colores = ['#fcd116', '#0033a0', '#ce1126', '#22c55e', '#a855f7', '#06b6d4'];
    setPiezas(Array.from({ length: 70 }, (_, i) => ({
      id: `${disparo}-${i}`,
      x: Math.random() * 100,
      color: colores[i % colores.length],
      retraso: Math.random() * 0.3,
      giro: Math.random() * 720 - 360,
      ancho: 6 + Math.random() * 6,
    })));
    const t = setTimeout(() => setPiezas([]), 2600);
    return () => clearTimeout(t);
  }, [disparo]);
  return (
    <div className="confeti" aria-hidden="true">
      {piezas.map((p) => (
        <i key={p.id} style={{
          left: `${p.x}%`, background: p.color, width: p.ancho, height: p.ancho * 0.45,
          animationDelay: `${p.retraso}s`, '--giro': `${p.giro}deg`,
        }} />
      ))}
    </div>
  );
}

export function Toasts({ lista, cerrar }) {
  return (
    <div className="toasts" role="status">
      {lista.map((t) => (
        <div key={t.id} className={`toast toast-${t.tipo}`} onClick={() => cerrar(t.id)}>
          <span className="toast-icono">{t.icono}</span>
          <div>
            <strong>{t.titulo}</strong>
            {t.texto && <p>{t.texto}</p>}
          </div>
        </div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ piezas de UI */

export function Dato({ icon, label, value, destacado }) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <div className={`dato ${destacado ? 'destacado' : ''}`}>
      <span className="dato-icon">{icon}</span>
      <div>
        <span className="dato-label">{label}</span>
        <strong className="dato-valor">{String(value)}</strong>
      </div>
    </div>
  );
}

export function Tarjeta({ paso, icon, titulo, subtitulo, children, acento = 'indigo', extra, className = '' }) {
  return (
    <section className={`card entrar acento-${acento} ${className}`}>
      <div className="card-brillo" />
      <header className="card-head">
        <div className="card-icon">{icon}</div>
        <div className="card-titulos">
          {paso && <span className="card-paso">Paso {paso}</span>}
          <h2>{titulo}</h2>
          {subtitulo && <p>{subtitulo}</p>}
        </div>
        {extra && <div className="card-extra">{extra}</div>}
      </header>
      {children}
    </section>
  );
}

const PASOS = [
  ['👤', 'Usuario'],
  ['🧾', 'SRI'],
  ['🪪', 'Licencia'],
  ['🚗', 'Vehículo'],
];

export function Stepper({ actual }) {
  return (
    <ol className="stepper">
      {PASOS.map(([icono, nombre], i) => (
        <li key={nombre} className={i < actual ? 'hecho' : i === actual ? 'activo' : ''}>
          <span className="bolita">{i < actual ? '✓' : icono}</span>
          <span className="paso-nombre">{nombre}</span>
          {i < PASOS.length - 1 && <span className="conector" />}
        </li>
      ))}
    </ol>
  );
}

export function Skeleton({ filas = 3 }) {
  return (
    <div className="skeleton">
      {Array.from({ length: filas }).map((_, i) => <div key={i} className="sk-linea" />)}
    </div>
  );
}

export function GaugePuntos({ puntos }) {
  const max = 30;
  const valor = puntos ?? 0;
  const r = 78;
  const circ = 2 * Math.PI * r;
  const [offset, setOffset] = useState(circ);
  useEffect(() => {
    const t = setTimeout(() => setOffset(circ * (1 - valor / max)), 60);
    return () => clearTimeout(t);
  }, [valor, circ]);
  const nivel = valor > 20 ? 'alto' : valor > 10 ? 'medio' : 'bajo';
  const texto = { alto: 'Excelente', medio: 'Cuidado', bajo: 'En riesgo' }[nivel];
  return (
    <div className={`gauge gauge-${nivel}`}>
      <svg viewBox="0 0 200 200" width="200" height="200">
        <defs>
          <linearGradient id="grad-alto" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#22c55e" /><stop offset="100%" stopColor="#06b6d4" />
          </linearGradient>
          <linearGradient id="grad-medio" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#f59e0b" /><stop offset="100%" stopColor="#f97316" />
          </linearGradient>
          <linearGradient id="grad-bajo" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ef4444" /><stop offset="100%" stopColor="#db2777" />
          </linearGradient>
        </defs>
        <circle cx="100" cy="100" r={r} className="gauge-fondo" />
        <circle cx="100" cy="100" r={r} className="gauge-valor" stroke={`url(#grad-${nivel})`}
                strokeDasharray={circ} strokeDashoffset={offset} />
      </svg>
      <div className="gauge-texto">
        <strong><Contador valor={puntos} /></strong>
        <span>de {max} puntos</span>
        <em>{texto}</em>
      </div>
    </div>
  );
}

export function Placa({ numero }) {
  return (
    <div className="placa">
      <span className="placa-banda">ECUADOR</span>
      <span className="placa-numero">{numero}</span>
    </div>
  );
}

export function Interruptor({ activo, onChange, etiqueta, descripcion, peligro }) {
  return (
    <label className={`interruptor ${activo ? 'on' : ''} ${peligro ? 'peligro' : ''}`}>
      <input type="checkbox" checked={activo} onChange={(e) => onChange(e.target.checked)} />
      <span className="riel"><span className="perilla" /></span>
      <span className="int-texto">
        <strong>{etiqueta}</strong>
        {descripcion && <small>{descripcion}</small>}
      </span>
    </label>
  );
}

/* ------------------------------------------------------------------ estado del sistema */

function Indicador({ estado, icono, etiqueta, detalle }) {
  return (
    <span className={`indicador ind-${estado}`} title={detalle}>
      <i className="punto" />
      {icono && <span className="ind-icono">{icono}</span>}
      {etiqueta}
    </span>
  );
}

export function BarraEstado({ sri, ant }) {
  const antOk = ant && ant.ultimaRespuestaAnt && (!ant.ultimoFalloAnt || ant.ultimaRespuestaAnt > ant.ultimoFalloAnt);
  const nombreAnt = ant?.modoDemo ? 'ANT simulada' : 'ANT';
  let antEstado = 'off';
  let antTexto = 'ANT · servicio apagado';
  if (ant) {
    if (antOk) { antEstado = 'ok'; antTexto = `${nombreAnt} en línea`; }
    else if (ant.fallosAnt > 0) { antEstado = 'mal'; antTexto = ant.consultasEnCache > 0 ? `${nombreAnt} caída · respaldo desde caché` : `${nombreAnt} caída · consultas en cola`; }
    else { antEstado = 'warn'; antTexto = `${nombreAnt} · esperando consulta`; }
  }

  let cb = null;
  if (ant) {
    if (ant.circuitoAnt === 'OPEN') {
      cb = { estado: 'escudo', texto: `Protección activa · reintenta en ${ant.segundosParaReintentarAnt} s`,
        detalle: 'Circuit breaker ABIERTO: tras varios fallos deja de llamar a la ANT por 60 s y responde desde la caché. Es el comportamiento esperado.' };
    } else if (ant.circuitoAnt === 'HALF_OPEN') {
      cb = { estado: 'warn', texto: 'Circuit breaker probando la ANT', detalle: 'Semiabierto: deja pasar 2 llamadas de prueba.' };
    } else {
      cb = { estado: 'ok', texto: 'Circuit breaker cerrado', detalle: 'Las consultas pasan normalmente a la ANT.' };
    }
  }

  return (
    <div className="barra-estado">
      <Indicador estado={!sri ? 'off' : sri.sriDisponible ? 'ok' : 'mal'} icono="🧾"
                 etiqueta={!sri ? 'SRI · sin conexión' : sri.sriDisponible ? `SRI en línea · ${sri.latenciaMs} ms` : 'SRI no responde'} />
      <Indicador estado={antEstado} icono="🏛️" etiqueta={antTexto} detalle={ant?.ultimoError ?? ''} />
      {cb && <Indicador estado={cb.estado} icono="🛡️" etiqueta={cb.texto} detalle={cb.detalle} />}
      <Indicador estado={!ant ? 'off' : ant.redisConectado ? 'ok' : 'mal'} icono="🗄️"
                 etiqueta={!ant ? 'Caché · sin datos' : ant.redisConectado
                   ? `Redis · ${ant.consultasEnCache} en caché · ${ant.consultasPendientes} en cola`
                   : 'Redis desconectado'} />
    </div>
  );
}

export function PanelDemo({ ant, cambiar }) {
  const [abierto, setAbierto] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    if (!abierto) return undefined;
    const fuera = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false); };
    document.addEventListener('mousedown', fuera);
    return () => document.removeEventListener('mousedown', fuera);
  }, [abierto]);
  const activo = !!ant?.modoDemo;
  const caida = !!ant?.antCaidaSimulada;
  return (
    <div className={`panel-demo ${abierto ? 'abierto' : ''} ${activo ? 'activo' : ''}`} ref={ref}>
      <button className="panel-demo-boton" onClick={() => setAbierto(!abierto)}>
        🧪 {activo ? (caida ? 'Demo · ANT caída' : 'Demo · ANT simulada') : 'Modo demostración'}
      </button>
      {abierto && (
        <div className="panel-demo-cuerpo entrar">
          <h3>Modo demostración</h3>
          <p>
            El portal real de la ANT responde <b>502</b>. Activa el simulador para ver el patrón de caché
            completo; los datos simulados se marcan como tales y se guardan aparte en Redis.
          </p>
          <Interruptor activo={activo} onChange={(v) => cambiar(v, false)}
                       etiqueta="Usar simulador de la ANT" descripcion="Responde citaciones de ejemplo" />
          <Interruptor activo={caida} peligro onChange={(v) => cambiar(true, v)}
                       etiqueta="Simular caída de la ANT" descripcion="Responde 502 para probar el respaldo" />
          <ol className="guia-demo">
            <li>Activa el simulador y consulta una cédula → <b>En vivo</b> (se guarda en Redis).</li>
            <li>Vuelve a consultar antes de 10 s → <b>Desde caché</b>.</li>
            <li>Activa la caída, espera 10 s y consulta → <b>Caché de respaldo</b>.</li>
          </ol>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ flujo de la caché */

function escenarioCache(licencia, errorLicencia, cargando) {
  if (cargando) return { activo: 'cargando', pasos: [1] };
  if (licencia?.origen === 'CACHE') return { activo: 'cache', pasos: [1] };
  if (licencia?.origen === 'ANT') return { activo: 'ant', pasos: [1, 2, 3] };
  if (licencia?.origen === 'CACHE_RESPALDO') return { activo: 'respaldo', pasos: [1, 2, 4] };
  if (errorLicencia) return { activo: 'cola', pasos: [1, 2, 5] };
  return { activo: 'inactivo', pasos: [] };
}

const PASOS_CACHE = [
  [1, '🔎', 'Buscar en Redis', 'Si hay una copia reciente se responde desde la caché.'],
  [2, '📡', 'Consultar la ANT', 'Con Retry (2 intentos) y Circuit Breaker.'],
  [3, '💾', 'Guardar y leer de la caché', 'Cada respuesta adecuada se guarda 7 días y se devuelve desde Redis.'],
  [4, '🛟', 'Respaldo', 'Si la ANT falla se devuelve la última copia válida.'],
  [5, '⏳', 'Cola y reintento', 'Sin copia: queda en cola y se reintenta cada 2 min.'],
];

export function FlujoCache({ licencia, errorLicencia, cargando, ant }) {
  const { activo, pasos } = escenarioCache(licencia, errorLicencia, cargando);
  const antMal = activo === 'respaldo' || activo === 'cola';
  const redisLeido = ['cache', 'respaldo', 'ant'].includes(activo);
  return (
    <Tarjeta icon="🧠" titulo="Arquitectura en vivo" acento="violeta"
             subtitulo="¿De dónde salió la información de la licencia?">
      <div className="flujo">
        <div className="nodo activo"><span>🖥️</span>Web App<small>React</small></div>
        <div className="flecha activo"><i /></div>
        <div className={`nodo ${activo !== 'inactivo' ? 'activo' : ''}`}><span>☕</span>ANT Service<small>Spring Boot</small></div>
        <div className="ramas">
          <div className="rama">
            <div className={`flecha ${redisLeido ? 'activo' : activo === 'cola' ? 'falla' : ''}`}><i /></div>
            <div className={`nodo ${redisLeido ? 'activo verde' : activo === 'cola' ? 'falla' : ''}`}>
              <span>🗄️</span>Redis Cloud<small>{activo === 'cola' ? 'sin copia aún' : 'Upstash'}</small>
            </div>
          </div>
          <div className="rama">
            <div className={`flecha ${activo === 'ant' ? 'activo' : antMal ? 'falla' : ''}`}><i /></div>
            <div className={`nodo ${activo === 'ant' ? 'activo verde' : antMal ? 'falla' : ''}`}>
              <span>🏛️</span>{ant?.modoDemo ? 'ANT simulada' : 'Portal ANT'}<small>{antMal ? 'no responde' : 'citaciones'}</small>
            </div>
          </div>
        </div>
      </div>

      <ol className="pasos-cache">
        {PASOS_CACHE.map(([n, icono, titulo, texto]) => (
          <li key={n} className={pasos.includes(n) ? (n === pasos[pasos.length - 1] ? 'actual' : 'hecho') : ''}>
            <span className="pc-icono">{icono}</span>
            <div><strong>{titulo}</strong><p>{texto}</p></div>
          </li>
        ))}
      </ol>

      {ant && (
        <div className="metricas">
          <div><strong>{ant.respuestasAnt}</strong><span>en vivo</span></div>
          <div><strong>{ant.respuestasCache}</strong><span>desde caché</span></div>
          <div><strong>{ant.respuestasRespaldo}</strong><span>respaldo</span></div>
          <div><strong>{ant.fallosAnt}</strong><span>fallos ANT</span></div>
          <div><strong>{ant.consultasEnCache}</strong><span>guardadas</span></div>
          <div><strong>{ant.consultasPendientes}</strong><span>en cola</span></div>
        </div>
      )}
    </Tarjeta>
  );
}
