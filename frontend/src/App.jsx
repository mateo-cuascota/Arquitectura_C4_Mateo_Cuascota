import { useEffect, useState } from 'react';
import {
  consultarPuntosLicencia, estadoAnt, estadoSri, obtenerVehiculo, verificarContribuyente,
} from './api.js';

const ORIGEN = {
  ANT: { label: 'En vivo desde la ANT', icon: '⚡', clase: 'ok' },
  CACHE: { label: 'Desde caché (consulta reciente)', icon: '🗄️', clase: 'info' },
  CACHE_RESPALDO: { label: 'Caché de respaldo · ANT no disponible', icon: '🛟', clase: 'warn' },
};

const PASOS = ['Usuario', 'Contribuyente', 'Licencia', 'Vehículo'];

function formatearFecha(valor) {
  if (!valor) return null;
  const m = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : valor;
}

function iniciales(nombre = '') {
  const p = nombre.trim().split(/\s+/);
  return ((p[2]?.[0] ?? p[0]?.[0] ?? '') + (p[0]?.[0] ?? '')).toUpperCase() || '?';
}

function Dato({ icon, label, value }) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <div className="dato">
      <span className="dato-icon">{icon}</span>
      <div>
        <span className="dato-label">{label}</span>
        <strong className="dato-valor">{String(value)}</strong>
      </div>
    </div>
  );
}

function Tarjeta({ paso, icon, titulo, subtitulo, children, acento }) {
  return (
    <section className={`card fade-up ${acento ?? ''}`}>
      <header className="card-head">
        <div className="card-icon">{icon}</div>
        <div>
          <span className="card-paso">Paso {paso}</span>
          <h2>{titulo}</h2>
          {subtitulo && <p>{subtitulo}</p>}
        </div>
      </header>
      {children}
    </section>
  );
}

function Stepper({ actual }) {
  return (
    <ol className="stepper">
      {PASOS.map((p, i) => (
        <li key={p} className={i < actual ? 'hecho' : i === actual ? 'activo' : ''}>
          <span className="bolita">{i < actual ? '✓' : i + 1}</span>
          <span className="paso-nombre">{p}</span>
        </li>
      ))}
    </ol>
  );
}

function Skeleton({ filas = 3 }) {
  return (
    <div className="skeleton">
      {Array.from({ length: filas }).map((_, i) => <div key={i} className="sk-linea" />)}
    </div>
  );
}

function GaugePuntos({ puntos }) {
  const max = 30;
  const valor = puntos ?? 0;
  const r = 70;
  const circ = 2 * Math.PI * r;
  const color = valor > 20 ? '#16a34a' : valor > 10 ? '#f59e0b' : '#dc2626';
  return (
    <div className="gauge">
      <svg viewBox="0 0 180 180" width="180" height="180">
        <circle cx="90" cy="90" r={r} className="gauge-fondo" />
        <circle cx="90" cy="90" r={r} className="gauge-valor" stroke={color}
                strokeDasharray={circ} strokeDashoffset={circ * (1 - valor / max)} />
      </svg>
      <div className="gauge-texto">
        <strong style={{ color }}>{puntos ?? '—'}</strong>
        <span>de {max} puntos</span>
      </div>
    </div>
  );
}

function Placa({ numero }) {
  return (
    <div className="placa">
      <span className="placa-banda">ECUADOR</span>
      <span className="placa-numero">{numero}</span>
    </div>
  );
}

const CIRCUITO = {
  CLOSED: { texto: 'cerrado', clase: 'ok' },
  HALF_OPEN: { texto: 'semiabierto', clase: 'warn' },
  OPEN: { texto: 'abierto', clase: 'mal' },
};

function Indicador({ estado, etiqueta, detalle }) {
  return (
    <span className={`indicador ind-${estado}`} title={detalle}>
      <i className="punto" />
      {etiqueta}
    </span>
  );
}

function BarraEstado({ sri, ant }) {
  const antOk = ant && ant.ultimaRespuestaAnt && (!ant.ultimoFalloAnt || ant.ultimaRespuestaAnt > ant.ultimoFalloAnt);
  const antEstado = !ant ? 'off' : antOk ? 'ok' : ant.fallosAnt > 0 ? 'mal' : 'warn';
  const circuito = ant ? CIRCUITO[ant.circuitoAnt] ?? CIRCUITO.CLOSED : null;
  return (
    <div className="barra-estado">
      <Indicador estado={!sri ? 'off' : sri.sriDisponible ? 'ok' : 'mal'}
                 etiqueta={!sri ? 'SRI · sin conexión' : sri.sriDisponible ? `SRI en línea · ${sri.latenciaMs} ms` : 'SRI no responde'} />
      <Indicador estado={antEstado}
                 etiqueta={!ant ? 'ANT · servicio apagado' : antOk ? 'ANT en línea' : ant.fallosAnt > 0 ? 'ANT caída' : 'ANT sin consultar'}
                 detalle={ant?.ultimoError ?? ''} />
      {circuito && <Indicador estado={circuito.clase} etiqueta={`Circuit breaker ${circuito.texto}`} />}
      <Indicador estado={!ant ? 'off' : ant.redisConectado ? 'ok' : 'mal'}
                 etiqueta={!ant ? 'Caché · sin datos' : ant.redisConectado
                   ? `Caché Redis · ${ant.consultasEnCache} guardada(s) · ${ant.consultasPendientes} en cola`
                   : 'Caché Redis desconectada'} />
    </div>
  );
}

function escenarioCache(licencia, errorLicencia, cargando) {
  if (cargando) return { activo: 'cargando', pasos: [1] };
  if (licencia?.origen === 'CACHE') return { activo: 'cache', pasos: [1] };
  if (licencia?.origen === 'ANT') return { activo: 'ant', pasos: [1, 2, 3] };
  if (licencia?.origen === 'CACHE_RESPALDO') return { activo: 'respaldo', pasos: [1, 2, 4] };
  if (errorLicencia) return { activo: 'cola', pasos: [1, 2, 5] };
  return { activo: 'inactivo', pasos: [] };
}

const PASOS_CACHE = [
  [1, '🔎', 'Buscar en Redis', 'Si hay una copia de menos de 30 min se responde desde la caché.'],
  [2, '📡', 'Consultar la ANT', 'Con Retry (2 intentos) y Circuit Breaker para no saturar el portal.'],
  [3, '💾', 'Guardar y leer de la caché', 'Cada respuesta adecuada se guarda (7 días) y se devuelve desde Redis.'],
  [4, '🛟', 'Respaldo', 'Si la ANT falla se devuelve la última copia válida guardada.'],
  [5, '⏳', 'Cola y reintento', 'Sin copia: la consulta queda en cola y se reintenta cada 2 min.'],
];

function FlujoCache({ licencia, errorLicencia, cargando, ant }) {
  const { activo, pasos } = escenarioCache(licencia, errorLicencia, cargando);
  const antMal = activo === 'respaldo' || activo === 'cola';
  const redisLeido = ['cache', 'respaldo', 'ant'].includes(activo);
  return (
    <section className="card fade-up acento-violeta">
      <header className="card-head">
        <div className="card-icon">🧠</div>
        <div>
          <span className="card-paso">Arquitectura en vivo</span>
          <h2>¿De dónde salió la información de la licencia?</h2>
          <p>Patrón Cache-Aside con respaldo + Circuit Breaker sobre Redis en la nube.</p>
        </div>
      </header>

      <div className="flujo">
        <div className="nodo activo"><span>🖥️</span>Web App<small>React</small></div>
        <div className="flecha activo">→</div>
        <div className={`nodo ${activo !== 'inactivo' ? 'activo' : ''}`}><span>☕</span>ANT Service<small>Spring Boot</small></div>
        <div className="ramas">
          <div className="rama">
            <div className={`flecha ${redisLeido ? 'activo' : activo === 'cola' ? 'falla' : ''}`}>↔</div>
            <div className={`nodo ${redisLeido ? 'activo verde' : activo === 'cola' ? 'falla' : ''}`}>
              <span>🗄️</span>Redis Cloud<small>{activo === 'cola' ? 'sin copia aún' : 'Upstash'}</small>
            </div>
          </div>
          <div className="rama">
            <div className={`flecha ${activo === 'ant' ? 'activo' : antMal ? 'falla' : ''}`}>{antMal ? '✕' : '→'}</div>
            <div className={`nodo ${activo === 'ant' ? 'activo verde' : antMal ? 'falla' : ''}`}>
              <span>🏛️</span>Portal ANT<small>{antMal ? 'no responde' : 'consultaweb'}</small>
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
          <div><strong>{ant.respuestasAnt}</strong><span>respuestas de la ANT</span></div>
          <div><strong>{ant.respuestasCache}</strong><span>desde caché</span></div>
          <div><strong>{ant.respuestasRespaldo}</strong><span>desde respaldo</span></div>
          <div><strong>{ant.fallosAnt}</strong><span>fallos de la ANT</span></div>
          <div><strong>{ant.consultasEnCache}</strong><span>consultas guardadas</span></div>
          <div><strong>{ant.consultasPendientes}</strong><span>en cola</span></div>
        </div>
      )}
    </section>
  );
}

export default function App() {
  const [email, setEmail] = useState('');
  const [ruc, setRuc] = useState('');
  const [placa, setPlaca] = useState('');

  const [persona, setPersona] = useState(null);
  const [vehiculo, setVehiculo] = useState(null);
  const [licencia, setLicencia] = useState(null);

  const [cargando, setCargando] = useState('');
  const [cargandoLicencia, setCargandoLicencia] = useState(false);
  const [error, setError] = useState('');
  const [errorVehiculo, setErrorVehiculo] = useState('');
  const [errorLicencia, setErrorLicencia] = useState('');

  const [segundos, setSegundos] = useState(0);
  const [estado, setEstado] = useState({ sri: null, ant: null });
  const [tema, setTema] = useState(() => localStorage.getItem('tema') ?? 'claro');

  useEffect(() => {
    document.documentElement.dataset.tema = tema;
    localStorage.setItem('tema', tema);
  }, [tema]);

  async function actualizarEstado() {
    const [sri, ant] = await Promise.allSettled([estadoSri(), estadoAnt()]);
    setEstado({
      sri: sri.status === 'fulfilled' ? sri.value : null,
      ant: ant.status === 'fulfilled' ? ant.value : null,
    });
  }

  // Panel de estado: se actualiza cada 15 s y después de cada consulta de licencia.
  useEffect(() => {
    actualizarEstado();
    const t = setInterval(actualizarEstado, 15000);
    return () => clearInterval(t);
  }, []);

  const pasoActual = vehiculo ? 4 : persona ? (licencia || errorLicencia ? 3 : 2) : 0;

  // Si la ANT está caída, se reintenta automáticamente cada 30 s mientras la página esté abierta.
  useEffect(() => {
    if (!errorLicencia || !persona) return undefined;
    setSegundos(30);
    const t = setInterval(() => setSegundos((s) => s - 1), 1000);
    return () => clearInterval(t);
  }, [errorLicencia, persona]);

  useEffect(() => {
    if (errorLicencia && persona && segundos === 0 && !cargandoLicencia) {
      consultarLicencia(persona.cedula, vehiculo?.numeroPlaca ?? '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [segundos]);

  async function consultarLicencia(cedula, placaVehiculo = '') {
    setErrorLicencia('');
    setCargandoLicencia(true);
    try {
      setLicencia(await consultarPuntosLicencia(cedula, placaVehiculo));
    } catch (e) {
      setLicencia(null);
      setErrorLicencia(e.message);
    } finally {
      setCargandoLicencia(false);
      actualizarEstado();
    }
  }

  async function onVerificar(e) {
    e.preventDefault();
    setError('');
    setPersona(null);
    setVehiculo(null);
    setLicencia(null);
    setErrorLicencia('');
    setErrorVehiculo('');
    setCargando('persona');
    try {
      const data = await verificarContribuyente(ruc.trim(), email.trim());
      setPersona(data);
      // Con la cédula (10 primeros dígitos del RUC) se consultan los puntos de la licencia en la ANT.
      consultarLicencia(data.cedula);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando('');
    }
  }

  async function onVehiculo(e) {
    e.preventDefault();
    setErrorVehiculo('');
    setVehiculo(null);
    setCargando('vehiculo');
    try {
      const placaNorm = placa.trim().toUpperCase().replace('-', '');
      setVehiculo(await obtenerVehiculo(placaNorm));
      consultarLicencia(persona.cedula, placaNorm);
    } catch (err) {
      setErrorVehiculo(err.message);
    } finally {
      setCargando('');
    }
  }

  function nuevaConsulta() {
    setPersona(null);
    setVehiculo(null);
    setLicencia(null);
    setError('');
    setErrorLicencia('');
    setErrorVehiculo('');
    setRuc('');
    setPlaca('');
  }

  const c = persona?.contribuyente;
  const origen = licencia ? ORIGEN[licencia.origen] : null;

  return (
    <>
      <div className="hero">
        <div className="hero-bandera" />
        <div className="hero-contenido">
          <div className="hero-top">
            <span className="hero-chip"><i className="mini-bandera" /> SRI · ANT · Ecuador</span>
            <button className="tema" onClick={() => setTema(tema === 'oscuro' ? 'claro' : 'oscuro')}
                    title="Cambiar tema" aria-label="Cambiar tema">
              {tema === 'oscuro' ? '☀️ Claro' : '🌙 Oscuro'}
            </button>
          </div>
          <h1>Consulta de contribuyente, vehículo y licencia</h1>
          <p>Verifica tu RUC en el SRI, conoce los datos de tu vehículo y los puntos de tu licencia en un solo lugar.</p>
          <Stepper actual={pasoActual} />
          <BarraEstado sri={estado.sri} ant={estado.ant} />
        </div>
      </div>

      <main>
        <Tarjeta paso={1} icon="👤" titulo="Datos del usuario"
                 subtitulo="Ingresa tu correo y el RUC de persona natural (13 dígitos terminados en 001).">
          <form onSubmit={onVerificar} className="form">
            <label className="campo">
              <span>Correo electrónico</span>
              <div className="input-icono">
                <i>✉️</i>
                <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                       placeholder="usuario@correo.com" />
              </div>
            </label>
            <label className="campo">
              <span>RUC (persona natural)</span>
              <div className="input-icono">
                <i>🧾</i>
                <input required pattern="\d{10}001" maxLength={13} inputMode="numeric" value={ruc}
                       onChange={(e) => setRuc(e.target.value.replace(/\D/g, ''))}
                       placeholder="1712345678001" title="13 dígitos terminados en 001" />
              </div>
              <small className="contador">{ruc.length}/13</small>
            </label>
            <button className="btn" disabled={cargando === 'persona'}>
              {cargando === 'persona' ? <><span className="spinner" /> Verificando…</> : 'Verificar en el SRI →'}
            </button>
          </form>
          {error && <div className="alerta error fade-up">⛔ {error}</div>}
        </Tarjeta>

        {cargando === 'persona' && <section className="card"><Skeleton filas={4} /></section>}

        {persona && (
          <Tarjeta paso={2} icon="🧾" titulo="Contribuyente verificado"
                   subtitulo="Información obtenida del catastro del SRI." acento="acento-verde">
            <div className="perfil">
              <div className="avatar">{iniciales(c.razonSocial)}</div>
              <div className="perfil-info">
                <h3>{c.razonSocial}</h3>
                <div className="chips">
                  <span className="chip chip-verde">✔ {c.tipoContribuyente}</span>
                  <span className={`chip ${c.estadoContribuyenteRuc === 'ACTIVO' ? 'chip-verde' : 'chip-rojo'}`}>
                    ● {c.estadoContribuyenteRuc}
                  </span>
                  {c.regimen && <span className="chip chip-azul">{c.regimen}</span>}
                </div>
              </div>
            </div>
            <div className="grid">
              <Dato icon="🆔" label="RUC" value={c.numeroRuc} />
              <Dato icon="🪪" label="Cédula" value={persona.cedula} />
              <Dato icon="✉️" label="Correo" value={persona.email} />
              <Dato icon="💼" label="Actividad económica" value={c.actividadEconomicaPrincipal} />
              <Dato icon="📒" label="Obligado a llevar contabilidad" value={c.obligadoLlevarContabilidad} />
              <Dato icon="📅" label="Inicio de actividades"
                    value={formatearFecha(c.informacionFechasContribuyente?.fechaInicioActividades)} />
            </div>
          </Tarjeta>
        )}

        {persona && (
          <Tarjeta paso={3} icon="🪪" titulo="Puntos de la licencia"
                   subtitulo="Consulta a la ANT protegida con caché en la nube (Redis)." acento="acento-azul">
            {cargandoLicencia && (
              <div className="cargando-ant"><span className="spinner oscuro" /> Consultando a la ANT…</div>
            )}

            {!cargandoLicencia && licencia && (
              <div className="licencia fade-up">
                <GaugePuntos puntos={licencia.licencia.puntosDisponibles} />
                <div className="licencia-info">
                  <span className={`origen origen-${origen.clase}`}>{origen.icon} {origen.label}</span>
                  {licencia.aviso && <p className="aviso">{licencia.aviso}</p>}
                  <div className="grid compacto">
                    <Dato icon="📋" label="Citaciones" value={licencia.licencia.totalCitaciones} />
                    <Dato icon="➖" label="Puntos descontados" value={licencia.licencia.puntosDescontados} />
                    <Dato icon="🕒" label="Información obtenida" value={new Date(licencia.obtenidoEn).toLocaleString()} />
                  </div>
                </div>
              </div>
            )}

            {!cargandoLicencia && licencia?.licencia.citaciones.length > 0 && (
              <div className="tabla">
                <table>
                  <thead>
                    <tr>{Object.keys(licencia.licencia.citaciones[0]).map((k) => <th key={k}>{k}</th>)}</tr>
                  </thead>
                  <tbody>
                    {licencia.licencia.citaciones.map((fila, i) => (
                      <tr key={i}>{Object.values(fila).map((v, j) => <td key={j}>{v}</td>)}</tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {!cargandoLicencia && errorLicencia && (
              <div className="ant-caida fade-up">
                <div className="ant-caida-icono">📡</div>
                <div>
                  <h3>La ANT no está disponible en este momento</h3>
                  <p>{errorLicencia}</p>
                  <p className="nota">
                    Cuando la ANT responda correctamente, la información se guardará en la caché en la nube (Redis)
                    y desde ese momento se mostrará desde allí aunque el portal vuelva a fallar.
                    El servidor también reintenta en segundo plano.
                  </p>
                  <div className="reintento">
                    <button className="btn secundario" onClick={() => consultarLicencia(persona.cedula, vehiculo?.numeroPlaca ?? '')}>
                      ↻ Reintentar ahora
                    </button>
                    {segundos > 0 && <span className="cuenta">Reintento automático en {segundos} s</span>}
                  </div>
                </div>
              </div>
            )}
          </Tarjeta>
        )}

        {persona && (
          <FlujoCache licencia={licencia} errorLicencia={errorLicencia} cargando={cargandoLicencia} ant={estado.ant} />
        )}

        {persona && (
          <Tarjeta paso={4} icon="🚗" titulo="Matrícula de su vehículo"
                   subtitulo="Ingresa la placa para obtener la información desde el SRI." acento="acento-naranja">
            <form onSubmit={onVehiculo} className="form inline">
              <label className="campo">
                <span>Placa</span>
                <div className="input-icono">
                  <i>🔎</i>
                  <input required value={placa} maxLength={10} onChange={(e) => setPlaca(e.target.value.toUpperCase())}
                         placeholder="PBA1234" className="mayus" />
                </div>
              </label>
              <button className="btn naranja" disabled={cargando === 'vehiculo'}>
                {cargando === 'vehiculo' ? <><span className="spinner" /> Consultando…</> : 'Consultar vehículo →'}
              </button>
            </form>
            {errorVehiculo && <div className="alerta error fade-up">⛔ {errorVehiculo}</div>}
            {cargando === 'vehiculo' && <Skeleton filas={3} />}
            {vehiculo && (
              <div className="vehiculo fade-up">
                <div className="vehiculo-cabecera">
                  <Placa numero={vehiculo.numeroPlaca} />
                  <div>
                    <h3>{vehiculo.descripcionMarca} {vehiculo.descripcionModelo}</h3>
                    <div className="chips">
                      {vehiculo.anioAuto && <span className="chip chip-azul">📅 {vehiculo.anioAuto}</span>}
                      {vehiculo.ultimoAnioPagado && (
                        <span className="chip chip-verde">💳 Último pago {vehiculo.ultimoAnioPagado}</span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="grid">
                  <Dato icon="🏭" label="Marca" value={vehiculo.descripcionMarca} />
                  <Dato icon="🚙" label="Modelo" value={vehiculo.descripcionModelo} />
                  <Dato icon="🌎" label="País" value={vehiculo.descripcionPais} />
                  <Dato icon="🏷️" label="CAMV / CPN" value={vehiculo.numeroCamvCpn} />
                  <Dato icon="🚦" label="Clase" value={vehiculo.nombreClase} />
                  <Dato icon="🛠️" label="Servicio" value={vehiculo.descripcionServicio} />
                  <Dato icon="⚙️" label="Cilindraje" value={vehiculo.cilindraje} />
                  <Dato icon="🎨" label="Color" value={vehiculo.colorVehiculo1} />
                  <Dato icon="📍" label="Cantón" value={vehiculo.descripcionCanton} />
                  <Dato icon="⏳" label="Caducidad matrícula" value={formatearFecha(vehiculo.fechaCaducidadMatricula)} />
                </div>
              </div>
            )}
          </Tarjeta>
        )}

        {persona && (
          <div className="acciones">
            <button className="btn fantasma" onClick={() => window.print()}>🖨️ Descargar reporte (PDF)</button>
            <button className="btn fantasma" onClick={nuevaConsulta}>↺ Nueva consulta</button>
          </div>
        )}
      </main>

      <footer>
        <div className="impreso">Reporte generado el {new Date().toLocaleString()}</div>
        React · Java 21 / Spring Boot (microservicios SRI y ANT) · Redis Cloud (Upstash) · Modelo C4 en IcePanel
        <div className="enlaces">
          <a href="https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota" target="_blank" rel="noreferrer">GitHub</a>
          <a href="https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=5M5LMF8Q5X" target="_blank" rel="noreferrer">Modelo C4 (IcePanel)</a>
          <a href="http://localhost:8081/swagger-ui.html" target="_blank" rel="noreferrer">API SRI (Swagger)</a>
          <a href="http://localhost:8082/swagger-ui.html" target="_blank" rel="noreferrer">API ANT (Swagger)</a>
        </div>
      </footer>
    </>
  );
}
