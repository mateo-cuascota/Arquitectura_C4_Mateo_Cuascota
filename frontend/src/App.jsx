import { useEffect, useRef, useState } from 'react';
import {
  configurarDemo, consultarPuntosLicencia, estadoAnt, estadoSri, obtenerVehiculo, verificarContribuyente,
} from './api.js';
import {
  BarraEstado, Confeti, Dato, FlujoCache, FondoAurora, GaugePuntos, PanelDemo, Placa, Skeleton, Stepper,
  Tarjeta, Toasts, formatearFecha, iniciales,
} from './componentes.jsx';

const ORIGEN = {
  ANT: { label: 'En vivo desde la ANT', icon: '⚡', clase: 'ok' },
  CACHE: { label: 'Desde la caché (consulta reciente)', icon: '🗄️', clase: 'info' },
  CACHE_RESPALDO: { label: 'Caché de respaldo · ANT no disponible', icon: '🛟', clase: 'warn' },
};

const TIPOS = {
  cedula: { largo: 10, patron: '\\d{10}', ejemplo: '1712345678', ayuda: '10 dígitos' },
  ruc: { largo: 13, patron: '\\d{10}001', ejemplo: '1712345678001', ayuda: '13 dígitos terminados en 001' },
};

export default function App() {
  const [email, setEmail] = useState('');
  const [tipo, setTipo] = useState('cedula');
  const [identificacion, setIdentificacion] = useState('');
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
  const [toasts, setToasts] = useState([]);
  const [confeti, setConfeti] = useState(0);
  const resultadosRef = useRef(null);

  const pasoActual = vehiculo ? 4 : persona ? (licencia || errorLicencia ? 3 : 2) : 0;

  /* ---------------------------------------------------------------- tema y notificaciones */

  useEffect(() => {
    document.documentElement.dataset.tema = tema;
    localStorage.setItem('tema', tema);
  }, [tema]);

  function avisar(tipoToast, icono, titulo, texto) {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, tipo: tipoToast, icono, titulo, texto }].slice(-4));
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500);
  }

  /* ---------------------------------------------------------------- estado del sistema */

  async function actualizarEstado() {
    const [sri, ant] = await Promise.allSettled([estadoSri(), estadoAnt()]);
    setEstado({
      sri: sri.status === 'fulfilled' ? sri.value : null,
      ant: ant.status === 'fulfilled' ? ant.value : null,
    });
  }

  useEffect(() => {
    actualizarEstado();
    const t = setInterval(actualizarEstado, 5000);
    return () => clearInterval(t);
  }, []);

  async function cambiarDemo(activo, antCaida) {
    try {
      await configurarDemo(activo, antCaida);
      await actualizarEstado();
      if (!activo) avisar('info', '🏛️', 'Modo demostración desactivado', 'Se vuelve a consultar el portal real de la ANT.');
      else if (antCaida) avisar('warn', '💥', 'ANT simulada caída', 'Responde 502. La consulta usará la caché de respaldo.');
      else avisar('ok', '🧪', 'Simulador de la ANT activo', 'Las respuestas se guardan en Redis con el prefijo demo:.');
    } catch (e) {
      avisar('error', '⛔', 'No se pudo cambiar el modo demo', e.message);
    }
  }

  /* ---------------------------------------------------------------- reintento automático */

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

  /* ---------------------------------------------------------------- consultas */

  async function consultarLicencia(cedula, placaVehiculo = '') {
    setErrorLicencia('');
    setCargandoLicencia(true);
    try {
      const r = await consultarPuntosLicencia(cedula, placaVehiculo);
      setLicencia(r);
      const o = ORIGEN[r.origen];
      avisar(r.origen === 'CACHE_RESPALDO' ? 'warn' : 'ok', o.icon, 'Puntos de licencia', o.label);
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
      const data = await verificarContribuyente(identificacion.trim(), email.trim());
      setPersona(data);
      if (data.esContribuyente) {
        setConfeti(Date.now());
        avisar('ok', '✅', 'Contribuyente verificado', `${data.contribuyente.razonSocial} · ${data.contribuyente.tipoContribuyente}`);
      } else {
        avisar('info', 'ℹ️', data.persona ? data.persona.nombreCompleto : 'Cédula válida sin RUC',
          'No es contribuyente del SRI; igual consultamos licencia y vehículo.');
      }
      setTimeout(() => resultadosRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150);
      // Con la cédula se consultan los puntos de la licencia en la ANT.
      consultarLicencia(data.cedula);
    } catch (err) {
      setError(err.message);
      avisar('error', '⛔', 'No se pudo verificar', err.message);
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
      const v = await obtenerVehiculo(placaNorm);
      setVehiculo(v);
      avisar('ok', '🚗', 'Vehículo encontrado', `${v.descripcionMarca ?? ''} ${v.descripcionModelo ?? ''}`.trim());
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
    setIdentificacion('');
    setPlaca('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function cambiarTipo(t) {
    setTipo(t);
    setIdentificacion((v) => (t === 'cedula' ? v.slice(0, 10) : v));
  }

  const c = persona?.contribuyente;
  const nombre = c?.razonSocial ?? persona?.persona?.nombreCompleto ?? null;
  const origen = licencia ? ORIGEN[licencia.origen] : null;
  const conf = TIPOS[tipo];

  /* ---------------------------------------------------------------- vista */

  return (
    <>
      <FondoAurora />
      <Confeti disparo={confeti} />
      <Toasts lista={toasts} cerrar={(id) => setToasts((t) => t.filter((x) => x.id !== id))} />
      <PanelDemo ant={estado.ant} cambiar={cambiarDemo} />

      <header className="hero">
        <div className="hero-bandera" />
        <div className="hero-contenido">
          <div className="hero-top">
            <span className="hero-chip"><i className="mini-bandera" /> SRI · ANT · Ecuador</span>
            <button className="tema" onClick={() => setTema(tema === 'oscuro' ? 'claro' : 'oscuro')}
                    title="Cambiar tema" aria-label="Cambiar tema">
              {tema === 'oscuro' ? '☀️ Claro' : '🌙 Oscuro'}
            </button>
          </div>

          <div className="hero-grid">
            <div>
              <h1>
                Tu <span className="texto-gradiente">RUC</span>, tu <span className="texto-gradiente">vehículo</span> y
                tu <span className="texto-gradiente">licencia</span> en un solo lugar
              </h1>
              <p className="hero-sub">
                Consulta en segundos el SRI y la ANT. Aunque la ANT se caiga, la caché en la nube responde por ti.
              </p>
              <Stepper actual={pasoActual} />
            </div>
            <div className="hero-arte" aria-hidden="true">
              <div className="orbita">
                <span className="flotante f1">🚗</span>
                <span className="flotante f2">🪪</span>
                <span className="flotante f3">🧾</span>
                <span className="flotante f4">🛡️</span>
                <div className="nucleo">⚡</div>
              </div>
            </div>
          </div>

          <BarraEstado sri={estado.sri} ant={estado.ant} />
        </div>
      </header>

      <main>
        <section className="card buscador entrar">
          <div className="card-brillo" />
          <div className="buscador-titulo">
            <div>
              <span className="card-paso">Paso 1</span>
              <h2>¿A quién consultamos?</h2>
            </div>
            <div className="segmentado" role="tablist">
              <button type="button" className={tipo === 'cedula' ? 'sel' : ''} onClick={() => cambiarTipo('cedula')}>🪪 Cédula</button>
              <button type="button" className={tipo === 'ruc' ? 'sel' : ''} onClick={() => cambiarTipo('ruc')}>🧾 RUC</button>
              <span className={`segmentado-fondo ${tipo}`} />
            </div>
          </div>

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
              <span>{tipo === 'cedula' ? 'Cédula de identidad' : 'RUC de persona natural'}</span>
              <div className="input-icono">
                <i>{tipo === 'cedula' ? '🪪' : '🧾'}</i>
                <input required pattern={conf.patron} maxLength={conf.largo} inputMode="numeric" value={identificacion}
                       onChange={(e) => setIdentificacion(e.target.value.replace(/\D/g, ''))}
                       placeholder={conf.ejemplo} title={conf.ayuda} />
                <span className={`barra-largo ${identificacion.length === conf.largo ? 'completa' : ''}`}
                      style={{ width: `${(identificacion.length / conf.largo) * 100}%` }} />
              </div>
              <small className="contador">{identificacion.length}/{conf.largo} · {conf.ayuda}</small>
            </label>
            <button className="btn btn-principal" disabled={cargando === 'persona'}>
              {cargando === 'persona' ? <><span className="spinner" /> Verificando…</> : <>Consultar <span className="flechita">→</span></>}
            </button>
          </form>
          {error && <div className="alerta error entrar">⛔ {error}</div>}
        </section>

        {cargando === 'persona' && <section className="card"><Skeleton filas={4} /></section>}

        <div ref={resultadosRef} />

        {persona && (
          <Tarjeta paso={2} icon="🧾" acento={persona.esContribuyente ? 'verde' : 'cian'}
                   titulo={persona.esContribuyente ? 'Contribuyente verificado' : 'Cédula verificada'}
                   subtitulo={persona.esContribuyente ? 'Información del catastro del SRI.' : persona.persona
                     ? 'Datos obtenidos por cédula del SRI; no registra RUC.' : 'La cédula es válida, pero el SRI no tiene registros de esta persona (ni RUC ni nombre).'}>
            <div className={`perfil ${persona.esContribuyente ? '' : 'sin-ruc'}`}>
              <div className="avatar">{nombre ? iniciales(nombre) : '🪪'}</div>
              <div className="perfil-info">
                <h3>{nombre ?? `Cédula ${persona.cedula}`}</h3>
                <div className="chips">
                  {persona.esContribuyente ? (
                    <>
                      <span className="chip chip-verde">✔ {c.tipoContribuyente}</span>
                      <span className={`chip ${c.estadoContribuyenteRuc === 'ACTIVO' ? 'chip-verde' : 'chip-rojo'}`}>● {c.estadoContribuyenteRuc}</span>
                      {c.regimen && <span className="chip chip-azul">{c.regimen}</span>}
                    </>
                  ) : (
                    <>
                      <span className="chip chip-azul">✔ Cédula válida</span>
                      {persona.persona && <span className="chip chip-verde">👤 Persona natural</span>}
                      <span className="chip chip-ambar">Sin RUC · no es contribuyente</span>
                    </>
                  )}
                </div>
              </div>
            </div>
            <div className="grid">
              {persona.ruc && <Dato icon="🆔" label="RUC" value={persona.ruc} />}
              <Dato icon="🪪" label="Cédula" value={persona.cedula} />
              {persona.persona && <Dato icon="👤" label="Nombre según cédula (SRI)" value={persona.persona.nombreCompleto} />}
              <Dato icon="✉️" label="Correo" value={persona.email} />
              {c && <Dato icon="💼" label="Actividad económica" value={c.actividadEconomicaPrincipal} />}
              {c && <Dato icon="📒" label="Obligado a llevar contabilidad" value={c.obligadoLlevarContabilidad} />}
              {c && <Dato icon="📅" label="Inicio de actividades" value={formatearFecha(c.informacionFechasContribuyente?.fechaInicioActividades)} />}
            </div>
          </Tarjeta>
        )}

        {persona && (
          <div className="dos-columnas">
            <Tarjeta paso={3} icon="🪪" titulo="Puntos de la licencia" acento="azul"
                     subtitulo="Consulta a la ANT protegida con caché en la nube."
                     extra={licencia?.simulado && <span className="sello-demo">🧪 Datos simulados</span>}>
              {cargandoLicencia && (
                <div className="cargando-ant"><span className="spinner oscuro" /> Consultando a la ANT…</div>
              )}

              {!cargandoLicencia && licencia && (
                <div className="licencia entrar">
                  <GaugePuntos puntos={licencia.licencia.puntosDisponibles} />
                  <div className="licencia-info">
                    <span className={`origen origen-${origen.clase}`}>{origen.icon} {origen.label}</span>
                    {licencia.aviso && <p className="aviso">{licencia.aviso}</p>}
                    <div className="mini-stats">
                      <div><strong>{licencia.licencia.totalCitaciones}</strong><span>citaciones</span></div>
                      <div><strong>{licencia.licencia.puntosDescontados ?? '—'}</strong><span>puntos perdidos</span></div>
                    </div>
                    <small className="obtenido">🕒 {new Date(licencia.obtenidoEn).toLocaleString()}</small>
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
                <div className="ant-caida entrar">
                  <div className="ant-caida-icono">📡</div>
                  <div>
                    <h3>La ANT no está disponible</h3>
                    <p>{errorLicencia}</p>
                    <p className="nota">
                      Cuando responda, la información se guardará en Redis y se mostrará desde allí aunque vuelva a fallar.
                      ¿Quieres verlo ya? Abre <b>🧪 Modo demostración</b>.
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

            <FlujoCache licencia={licencia} errorLicencia={errorLicencia} cargando={cargandoLicencia} ant={estado.ant} />
          </div>
        )}

        {persona && (
          <Tarjeta paso={4} icon="🚗" titulo="Matrícula de su vehículo" acento="naranja"
                   subtitulo="Ingresa la placa para obtener la información desde el SRI.">
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
                {cargando === 'vehiculo' ? <><span className="spinner" /> Consultando…</> : <>Consultar vehículo <span className="flechita">→</span></>}
              </button>
            </form>
            {errorVehiculo && <div className="alerta error entrar">⛔ {errorVehiculo}</div>}
            {cargando === 'vehiculo' && <Skeleton filas={3} />}
            {vehiculo && (
              <div className="vehiculo entrar">
                <div className="vehiculo-cabecera">
                  <Placa numero={vehiculo.numeroPlaca} />
                  <div>
                    <h3>{vehiculo.descripcionMarca} {vehiculo.descripcionModelo}</h3>
                    <div className="chips">
                      {vehiculo.anioAuto && <span className="chip chip-azul">📅 {vehiculo.anioAuto}</span>}
                      {vehiculo.ultimoAnioPagado && <span className="chip chip-verde">💳 Último pago {vehiculo.ultimoAnioPagado}</span>}
                      {vehiculo.descripcionPais && <span className="chip chip-violeta">🌎 {vehiculo.descripcionPais}</span>}
                    </div>
                  </div>
                </div>
                <div className="grid">
                  <Dato icon="🏭" label="Marca" value={vehiculo.descripcionMarca} />
                  <Dato icon="🚙" label="Modelo" value={vehiculo.descripcionModelo} />
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
        <div className="footer-tech">
          <span>⚛️ React</span><span>☕ Java 21 · Spring Boot</span><span>🗄️ Redis Cloud (Upstash)</span><span>🧩 C4 en IcePanel</span>
        </div>
        <div className="enlaces">
          <a href="https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota" target="_blank" rel="noreferrer">GitHub</a>
          <a href="https://app.icepanel.io/landscapes/ro5CkMchbD4bHcpi5HuM/versions/latest/diagrams/editor?diagram=5M5LMF8Q5X" target="_blank" rel="noreferrer">Modelo C4</a>
          <a href="http://localhost:8081/swagger-ui.html" target="_blank" rel="noreferrer">API SRI</a>
          <a href="http://localhost:8082/swagger-ui.html" target="_blank" rel="noreferrer">API ANT</a>
        </div>
      </footer>
    </>
  );
}
