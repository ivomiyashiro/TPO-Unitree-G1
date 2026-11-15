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
| Datos | `data/` | `SimulationRobotRepository` (recorrido, gestos, voz, LED, batería, IMU y escenarios) y `RoomHistoryRepository` (escoltas y eventos en SQLite). |
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
- Diseño adaptable: tablet (navegación lateral, dos columnas) y teléfono (navegación inferior, una columna).

## ⚠️ Limitaciones conocidas

- **No hay conexión al emulador de la cátedra (G1 HMI Lab).** Falta la segunda implementación de `RobotRepository` (Retrofit + WebSocket); la selección de robot lo muestra como *no disponible*.
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
