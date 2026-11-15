# TPO: Interfaz HMI/HRI para Robot de Servicio Unitree G1 — "Vigía"

Trabajo Práctico Grupal para la materia **Desarrollo de Aplicaciones I** (UADE - Facultad de Ingeniería y Ciencias Exactas).

---

## 🤖 Qué hace la app

**Vigía** es la consola con la que el guardia del control de acceso de un centro logístico supervisa y opera un robot humanoide **Unitree G1**. El robot recibe al visitante en el hall, le da las normas de seguridad por voz y lo acompaña por el pasillo peatonal hasta la oficina de recepción, cruzando una calle interna de autoelevadores:

$$\text{BASE} \rightarrow \text{P1 (Hall)} \rightarrow \text{P2 (Pasillo)} \rightarrow \text{P3 (Cruce de autoelevadores)} \rightarrow \text{P4 (Oficina)}$$

El robot camina a $0{,}5\text{ m/s}$ y en P3 **espera hasta que el guardia confirma que el paso está libre**. El guardia no deja la garita: desde la tablet ve el recorrido, la telemetría y las alertas, y puede pausar, cancelar, mandar el robot a un punto seguro o detenerlo.

La app funciona **sin robot, servidor ni internet**: incluye un simulador propio del G1.

---

## 🚀 Cómo ejecutarla

1. Abrir la carpeta `VigiaApp` en **Android Studio** y sincronizar Gradle.
2. Ejecutar en un emulador o dispositivo Android 8.0+ (API 26). El diseño principal es para **tablet en horizontal** (por ejemplo, el perfil *Pixel Tablet*); en teléfono la app usa navegación inferior y una sola columna.

Por línea de comandos, desde `VigiaApp/`:

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # pruebas unitarias
```

### Recorrido de demo (unos 4 minutos)

1. **Selección de robot** → *Conectar* en G1 · GO-01.
2. **Panel** → marcar *El visitante tiene chaleco reflectivo* → *Iniciar escolta* → confirmar.
3. **Escolta**: el robot va a P1, saluda y da las 4 normas; camina hasta P3 y espera con LED ámbar → *Cruce libre · continuar*.
4. Llega a P4 → *Volver a la base* (o vuelve solo a los 45 s) → resumen de la escolta.
5. **Historial**: la escolta queda guardada con todos sus eventos, órdenes y alertas.

**Flujo con falla:** en **Control → Escenarios de prueba** tocar *Cortar conexión 25 s* y, en Escolta, *Pausar*. La orden queda *Pendiente*, a los 5 s pasa a *Sin respuesta*, los datos se ponen en gris con su antigüedad y a los 10 s aparece el aviso de desconexión. Al volver la conexión la app avisa que la pausa no se ejecutó y deja decidir si reenviarla.

Los otros escenarios (Tabla XVI del documento): batería en 19 %, visitante rezagado, inclinación de 20° y orden rechazada (código 3104).

### Con el emulador de la cátedra (G1 HMI Lab v0.7)

La app tiene **dos fuentes de datos** y se elige en la pantalla de selección de robot:

| Fuente | De dónde salen los datos | Cuándo usarla |
|---|---|---|
| **Simulador interno** (por defecto) | Todo lo simula la app: recorrido, batería, IMU, conexión, LED, voz y gestos. | Sin red, para la demo y las pruebas. |
| **Emulador de la cátedra** | Batería (BMS), inclinación (IMU), conexión, LED, volumen, voz y gestos vienen del G1 HMI Lab por REST + WebSocket. El recorrido (caminar, P1–P4, cruce) lo sigue simulando la app, porque el emulador no implementa locomoción. | Para probar los escenarios con la consola docente. |

1. Levantar el emulador en la computadora (Python 3.11+):
   ```bash
   cd sdk_emulator
   python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
   .venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1
   ```
2. En la app: **Fuente de datos → Emulador de la cátedra**. Desde el emulador de Android la dirección es `http://10.0.2.2:8000`; en una tablet real, la IP de la computadora en la red local.
3. Desde la consola docente (`http://localhost:8000/simulator`) bajar la batería, inclinar la IMU, desconectar el G1 o hacer fallar la próxima orden: la app lo refleja con las mismas alertas y estados. Los botones de **Control → Escenarios de prueba** llaman a esa misma consola.

El LED y los mensajes de voz que decide el recorrido se envían también al emulador, así su HMI y su registro de eventos muestran lo mismo que la app.

---

## 🏗️ Arquitectura

MVVM con capas de Clean Architecture. La UI no conoce de dónde vienen los datos: el simulador y un futuro cliente del emulador de la cátedra implementan la misma interfaz.

```text
UI (Compose) ──► VigiaViewModel ──► Casos de uso ──► Repositorios (interfaces de dominio)
                     ▲  StateFlow<VigiaUiState>            │
                     │                                     ├─► SimulationRobotRepository (simulador del G1)
                     └──────── telemetría 1/s ◄────────────┤
                                                           └─► RoomHistoryRepository (Room, offline)
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Presentación | `ui/` | Pantallas Compose, `AppShell` (navegación lateral/inferior, barra de estado, avisos globales), `VigiaViewModel` con un único `StateFlow`. |
| Dominio | `domain/` | Modelos (telemetría, órdenes, alertas, misión), interfaces `RobotRepository` / `HistoryRepository`, reglas de habilitación de órdenes (`CommandRules`), antigüedad del dato (`DataFreshness`) y casos de uso (`SendCommandUseCase`, `RecordRobotEventsUseCase`). |
| Datos | `data/` | `SimulationRobotRepository` (recorrido, gestos, voz, LED, batería, IMU y escenarios), `EmulatorRobotRepository` (Retrofit + WebSocket contra el G1 HMI Lab), `RobotGateway` (elige la fuente) y `RoomHistoryRepository` (escoltas y eventos en SQLite). |
| DI | `di/AppContainer` | Inyección manual. Vive en `VigiaApplication`, así el simulador y el historial siguen funcionando si la Activity se recrea. |

### Decisiones relevantes

- **Estado de la orden separado del estado del robot.** Cada orden pasa por *Pendiente → Confirmada / Rechazada / Sin respuesta*. *Confirmada* significa aceptada, no cumplida: lo que pasa realmente se ve en el recorrido y la telemetría.
- **Tiempo de espera de 5 s sin reenvío automático** (`SendCommandUseCase`). Mientras una orden está pendiente su botón queda bloqueado.
- **Antigüedad del dato calculada en la app**: más de 3 s sin muestras → *datos antiguos* (en gris); más de 10 s → *sin conexión*, órdenes deshabilitadas.
- **Prevención de órdenes accidentales**: *Cancelar* y *Dar la mano* piden confirmación; *Detener* se mantiene presionado 2 s.
- **Privacidad**: el historial no guarda datos personales del visitante, solo una etiqueta (empresa o motivo).
- **Offline first**: el historial se lee siempre de Room, con o sin conexión.

---

## 🧰 Tecnologías

| Tecnología | Para qué |
|---|---|
| Kotlin + Jetpack Compose (Material 3) | UI declarativa. El sistema de diseño (colores, chips, botones de orden, tarjetas, íconos) replica el prototipo de Figma. |
| ViewModel + StateFlow + Coroutines | Estado único de pantalla; telemetría cada 1 s y antigüedad del dato. |
| Navigation Compose | Panel, Telemetría, Escolta, Alertas, Historial y Control. |
| Room | Historial local de escoltas, órdenes y alertas. |
| Retrofit + OkHttp (WebSocket) | Comunicación con el emulador de la cátedra: órdenes por REST y telemetría por WebSocket (1 muestra/s). |
| JUnit + kotlinx-coroutines-test | Reglas de órdenes, tiempo de espera y simulador con tiempo virtual. |

---

## ✅ Funcionalidades implementadas

- Selección de robot (solo se conecta a un robot en línea).
- Panel: plano del recorrido con robot, visitante y LED; telemetría resumida; alertas; alta de escolta con verificación de chaleco.
- Escolta: avance, normas entregadas, seguimiento del visitante, confirmación del cruce, llegada a P4, resumen final.
- Barra de órdenes: Pausar / Reanudar, Ir a punto seguro, Cancelar (con confirmación), Detener (mantener 2 s), última orden con su estado.
- Telemetría completa (Tabla IX) con la antigüedad de la última muestra.
- Alertas activas con acción sugerida y alertas reconocidas (hora y operador).
- Historial con filtros (Todo / Escoltas / Órdenes / Alertas) y detalle de cada escolta.
- Control: órdenes de misión con su condición de habilitación, gestos, mensajes de voz, volumen, LED y escenarios de prueba.
- Desconexión: aviso sobre cualquier pantalla, datos en gris, órdenes deshabilitadas y aviso al reconectar si una orden quedó sin respuesta.
- Conexión opcional al emulador de la cátedra (segunda fuente de datos), con reconexión automática del WebSocket.
- Diseño adaptable: tablet (navegación lateral, dos columnas) y teléfono (navegación inferior, una columna).

## ⚠️ Limitaciones conocidas

- Con el emulador de la cátedra, el recorrido sigue siendo simulado por la app: el G1 HMI Lab no implementa locomoción, misiones ni posición.
- Si la consola docente usa `SIMULATOR_TOKEN`, los botones de escenarios de la app no lo envían: en ese caso usar la consola docente directamente.
- La versión mobile usa los mismos componentes en una columna, pero no replica pantalla por pantalla el diseño mobile de Figma.
- *Detener* es un comando de software simulado: **no reemplaza una parada de emergencia física**.
- El operador es fijo (`guardia-turno-1`); no hay inicio de sesión.

---

## 📂 Estructura

```text
VigiaApp/app/src/
├── main/java/com/uade/vigia/
│   ├── domain/        # model/, repository/, usecase/
│   ├── data/          # simulation/ (simulador del G1), local/ (Room)
│   ├── di/            # AppContainer
│   ├── ui/            # AppShell, VigiaViewModel, Format, components/, screens/, theme/
│   ├── VigiaApplication.kt
│   └── MainActivity.kt
└── test/java/com/uade/vigia/   # CommandRulesTest, SendCommandUseCaseTest, SimulationRobotRepositoryTest
```
