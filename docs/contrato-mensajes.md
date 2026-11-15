# Contrato de mensajes propuesto

> **Contrato propuesto por el grupo entre la app y el robot. No es el protocolo oficial de Unitree.**
> Los nombres de los campos de batería, IMU y LED siguen los del emulador de la cátedra (G1 HMI Lab v0.7),
> para que la implementación que se conecta a él solo tenga que traducir el formato.

En la app, estos mensajes son los modelos del dominio (`domain/model`). `SimulationRobotRepository` los produce y
los consume; `EmulatorRobotRepository` traduce desde y hacia el formato del emulador (ver la tabla al final).

## 1. Telemetría (robot → app, una muestra por segundo)

```json
{
  "type": "telemetry",
  "robotId": "GO-01",
  "timestamp": "2026-11-15T12:57:10Z",
  "valid": true,
  "link": { "status": "ONLINE", "latencyMs": 45 },
  "battery": { "soc": 64, "soh": 98, "minutesLeft": 76 },
  "robotState": "ON_MISSION",
  "busyAction": null,
  "mission": {
    "id": "E-0001",
    "state": "RUNNING",
    "checkpoint": "P2",
    "progressPct": 40,
    "distanceM": 18.5,
    "startedAt": "2026-11-15T12:55:16Z"
  },
  "pose": { "checkpoint": "P2", "x": 27.0, "y": 21.3 },
  "motion": { "mode": "WALKING", "speedMps": 0.5, "rollDeg": 1.2, "pitchDeg": -0.8 },
  "alerts": { "activeCount": 0 },
  "service": {
    "visitor": { "state": "WITH_ROBOT", "distanceM": 1.4 },
    "safetyMessages": { "given": 4, "planned": 4 },
    "led": { "r": 0, "g": 0, "b": 255, "meaning": "ESCORTING" },
    "lastTts": "Cruzamos. Camine detrás de mí."
  }
}
```

| Campo | Valores | Modelo en la app |
|---|---|---|
| `link.status` | `ONLINE`, `DEGRADED`, `OFFLINE` | `LinkStatus` |
| `robotState` | `IDLE`, `ON_MISSION`, `WAITING`, `PAUSED`, `STOPPED`, `AT_DESTINATION`, `RETURNING`, `GOING_SAFE`, `AT_SAFE` | `RobotState.code` |
| `mission.state` | `RUNNING`, `PAUSED`, `COMPLETED`, `CANCELLED`, `ABORTED` | `MissionStatus` |
| `mission.checkpoint` / `pose.checkpoint` | `BASE`, `P1`, `P2`, `P3`, `P4`, `SAFE` | `Checkpoint` |
| `busyAction` | `null`, `GREETING`, `HANDSHAKE` | `BusyAction` |
| `service.visitor.state` | `NONE`, `WITH_ROBOT`, `LAGGING` (> 3 m), `LOST` (> 8 m), `DELIVERED` | `VisitorState` |
| `service.led.meaning` | `AVAILABLE` (verde), `ESCORTING` (azul), `WAITING` (ámbar), `STOPPED` (rojo) | `LedColor` |

**Antigüedad del dato.** La app no confía en que el robot avise que se cortó: mide cuánto pasó desde el último
`timestamp`. Más de 3 s → *datos antiguos* (en gris). Más de 10 s, o `valid: false` → *sin conexión* y órdenes
deshabilitadas.

## 2. Orden (app → robot)

```json
{
  "type": "command",
  "commandId": "c-7f3a",
  "robotId": "GO-01",
  "command": "PAUSE_ESCORT",
  "params": {},
  "sentAt": "2026-11-15T12:58:21Z",
  "operator": "guardia-turno-1"
}
```

Orden de inicio, con parámetros:

```json
{
  "type": "command",
  "commandId": "c-1b20",
  "robotId": "GO-01",
  "command": "START_ESCORT",
  "params": { "missionId": "E-0001", "visitor": "Proveedor · Transporte Sur", "destination": "P4" },
  "sentAt": "2026-11-15T12:55:16Z",
  "operator": "guardia-turno-1"
}
```

| `command` | Orden en la app | Habilitada cuando |
|---|---|---|
| `START_ESCORT` | Iniciar escolta | Robot en BASE, en línea, batería ≥ 30 % y sin alertas críticas |
| `PAUSE_ESCORT` | Pausar | Escolta en curso |
| `RESUME_ESCORT` | Reanudar | En pausa o detenido, batería > 10 % |
| `CANCEL_ESCORT` | Cancelar escolta (pide confirmación) | Escolta en curso, en pausa o detenida |
| `GO_SAFE` | Ir a punto seguro | Escolta en curso, en pausa o detenida |
| `STOP_MOVE` | Detener (mantener 2 s) | Robot caminando, esperando en el cruce o con un gesto |
| `CONFIRM_CROSSING` | Cruce libre · continuar | Robot esperando en P3 |
| `RETURN_TO_BASE` | Volver a la base | Robot quieto en P4 o en el punto seguro |
| `ACK_ALERT` (`params.alertId`) | Reconocer alerta | Hay una alerta sin reconocer |
| `GREETING` | Saludar | Robot quieto y libre |
| `HANDSHAKE` | Dar la mano (pide confirmación) | Robot quieto en P4 y libre |
| `RELEASE_ARMS` | Liberar brazos | Hay un gesto en curso |
| `TTS` (`params.text`) | Hablar | En línea |
| `SET_LED` (`params.led`) | Cambiar LED | En línea |
| `SET_VOLUME` (`params.volume`) | Cambiar volumen | En línea |

Las reglas están en `domain/usecase/CommandRules.kt` y tienen pruebas en `CommandRulesTest`.

## 3. Respuesta a la orden (robot → app)

Confirmada (el robot **aceptó** la orden; que la cumpla se ve después en la telemetría):

```json
{
  "type": "command_response",
  "commandId": "c-7f3a",
  "status": "CONFIRMED",
  "reason": null,
  "rpcCode": 0,
  "respondedAt": "2026-11-15T12:58:21Z"
}
```

Rechazada, con motivo y código:

```json
{
  "type": "command_response",
  "commandId": "c-9d41",
  "status": "REJECTED",
  "reason": "El visitante no está junto al robot",
  "rpcCode": 3201,
  "respondedAt": "2026-11-15T12:56:08Z"
}
```

| `rpcCode` | Motivo (simulador) |
|---|---|
| `3001` | La orden no corresponde al estado actual (por ejemplo, pausar sin escolta) |
| `3002` | Batería baja o crítica |
| `3104` | Rechazo forzado por el escenario de prueba |
| `3201` | El visitante no está junto al robot (no se puede reanudar) |
| `3203` | Robot ocupado con otra acción o gesto |

**Sin respuesta** no viaja por la red: la app lo asigna si pasan 5 s sin respuesta (`SendCommandUseCase`) y no
reenvía la orden sola. **Pendiente** es el estado mientras se espera la respuesta; el botón queda bloqueado.

## 4. Eventos (robot → app)

Se guardan en el historial (Room) con la escolta a la que pertenecen.

```json
{ "type": "event", "event": "checkpoint_reached", "missionId": "E-0001", "checkpoint": "P3",
  "text": "P3 · espera antes del cruce", "at": "2026-11-15T12:57:34Z" }
```

```json
{ "type": "event", "event": "alert_raised", "missionId": "E-0001",
  "alert": { "id": "a-51c2", "type": "TILT", "severity": "CRITICAL", "title": "Inclinación anormal · 20°",
             "checkpoint": "P2" },
  "at": "2026-11-15T12:56:56Z" }
```

```json
{ "type": "event", "event": "mission_finished", "missionId": "E-0001", "state": "COMPLETED",
  "distanceM": 92.0, "at": "2026-11-15T13:00:12Z" }
```

| `alert.type` | Severidad | Cuándo |
|---|---|---|
| `CROSSING_WAIT` | `INFO` | El robot llegó a P3 y espera la confirmación del guardia |
| `BATTERY_LOW` | `WARNING` | Batería < 25 % |
| `BATTERY_CRITICAL` | `CRITICAL` | Batería < 10 %: aborta la escolta y vuelve a la base |
| `VISITOR_LAGGING` | `WARNING` | Visitante a más de 3 m: el robot se detiene |
| `TILT` | `CRITICAL` | Roll o pitch > 15°: el robot se detiene |
| `COMMAND_REJECTED` | `WARNING` | El robot rechazó una orden |

## 5. Traducción al emulador de la cátedra (G1 HMI Lab v0.7)

| Contrato propuesto | Emulador | SDK2 que reproduce el emulador |
|---|---|---|
| `battery.soc`, `battery.soh` | `sensors.bms_state.soc`, `.soh` | `BmsState_` |
| `motion.rollDeg`, `pitchDeg` | `sensors.low_state.imu_state.rpy` (radianes → grados) | `IMUState_` |
| `valid`, `link.status` | `connected` y `sensors.valid` | — (propio del emulador) |
| `busyAction` | `mode: "busy"` + `action` | — |
| `GREETING` | `POST /api/robots/g1/commands/greeting` | `LocoClient.WaveHand` |
| `HANDSHAKE` | `POST /api/robots/g1/commands/handshake` | `LocoClient.ShakeHand` |
| `RELEASE_ARMS` | `POST /api/robots/g1/commands/release-arms` | `G1ArmActionClient.ExecuteAction(99)` |
| `TTS` | `POST /api/robots/g1/audio/tts` | `AudioClient.TtsMaker` |
| `SET_LED` | `PUT /api/robots/g1/led` | `AudioClient.LedControl` |
| `SET_VOLUME` | `PUT /api/robots/g1/audio/volume` | `AudioClient.SetVolume` |
| `CONFIRMED` | HTTP 202 con `accepted: true` y `rpc_code: 0` | — |
| `REJECTED` | `accepted: false` con `rpc_code ≠ 0`, HTTP 409 (ocupado) o HTTP 503 (sin conexión) | — |

`START_ESCORT`, `PAUSE_ESCORT`, `RESUME_ESCORT`, `CANCEL_ESCORT`, `GO_SAFE`, `STOP_MOVE`, `CONFIRM_CROSSING` y
`RETURN_TO_BASE` no existen en el emulador (no implementa locomoción ni misiones): las resuelve el simulador de la app.
