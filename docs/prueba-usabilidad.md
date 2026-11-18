# Prueba de usabilidad

> **Importante:** los resultados de este documento deben salir de una sesión real con una persona.
> La web incluye un registrador (**📋 Prueba de usabilidad**, abajo a la derecha) que mide tiempos, cuenta errores
> y exporta la tabla de resultados en Markdown para pegarla en la sección *Resultados*.

## 1. Objetivo
Comprobar si una persona sin conocimiento del sistema puede verificar su información en el SRI, conocer los puntos de su licencia y los datos de su vehículo, y si entiende cuándo la información viene de la caché porque la ANT está caída.

## 2. Participante
- Al menos **1 persona** (idealmente 3 a 5) que **no** haya participado en el desarrollo.
- Perfil sugerido: adulto con cédula ecuatoriana; si tiene vehículo, mejor.
- Se registra con un alias (P1, P2…), no con su nombre.
- Se le pide permiso para tomar notas y medir tiempos. Usa **su propia cédula y placa**, o los datos del facilitador.

## 3. Entorno
- Computador con el sistema corriendo (`./iniciar.sh`) y la web en http://localhost:5173.
- Navegador a pantalla completa. El facilitador abre el panel **📋 Prueba de usabilidad**.
- Antes de T4 el facilitador explica que existe el botón **🧪 Modo demostración**.

## 4. Guion del facilitador
1. "Vamos a probar la página, no a ti. No hay respuestas incorrectas."
2. "Piensa en voz alta: dime qué buscas y qué esperas que pase."
3. Lee la consigna de cada tarea **tal como está escrita**, pulsa **Iniciar** y no ayudes salvo que la persona se trabe más de 2 minutos (en ese caso marca **Con ayuda**).
4. Cada vez que la persona haga clic en algo equivocado, escriba un dato con formato incorrecto o vuelva atrás, pulsa **＋ Error**.
5. Al terminar cada tarea pregunta: "Del 1 al 5, ¿qué tan fácil fue?" y anota la respuesta y lo que dijo.

## 5. Tareas y criterios de éxito

| Tarea | Consigna (leer en voz alta) | Éxito si… | Tiempo objetivo |
|---|---|---|---|
| T1 | "Ingresa tu correo y tu cédula y averigua si eres contribuyente del SRI." | Ve la tarjeta *Contribuyente verificado* o *Cédula verificada* con su nombre | ≤ 1:00 |
| T2 | "¿Cuántos puntos tiene tu licencia y de dónde salió esa información?" | Dice los puntos (o que la ANT no está disponible) y el origen del dato | ≤ 0:45 |
| T3 | "Consulta tu vehículo con la placa y di de qué año es." | Ingresa la placa y lee el año correcto | ≤ 0:45 |
| T4 | "Con el modo demostración, simula que la ANT se cae y vuelve a consultar. ¿El dato es reciente?" | Activa la caída, vuelve a consultar y reconoce el aviso *Este dato no es reciente* | ≤ 1:30 |
| T5 | "Guarda un reporte en PDF de tu consulta." | Abre la impresión desde *Descargar reporte (PDF)* | ≤ 0:30 |

## 6. Métricas
- **Éxito:** ✅ sin ayuda · 🟡 con ayuda · ❌ no completó.
- **Tiempo** por tarea (m:ss), medido por el registrador.
- **Errores** por tarea (clics equivocados, datos mal ingresados, retrocesos).
- **Facilidad percibida** de 1 (muy difícil) a 5 (muy fácil).
- Comentarios textuales de la persona.

## 7. Resultados
*(Pegar aquí el Markdown exportado desde el panel 📋 por cada participante.)*

## 8. Análisis y mejoras propuestas
Para cada problema observado: **qué pasó → por qué → qué cambiar → prioridad**.

| # | Problema observado | Evidencia (tarea, tiempo, errores, comentario) | Mejora concreta | Prioridad |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

Preguntas que ayudan a encontrar mejoras:
- ¿Entendió la diferencia entre *Cédula* y *RUC* en el selector?
- ¿Notó la casilla de consentimiento antes de pulsar *Consultar*?
- ¿Entendió qué significa "Desde la caché" o "Caché de respaldo"?
- ¿Encontró la placa sin desplazarse mucho?
- ¿El aviso de "ANT no disponible" le pareció un error del sistema o lo entendió?
