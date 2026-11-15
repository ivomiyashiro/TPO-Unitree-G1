# Evidencias de los cinco escenarios

Capturas tomadas en un emulador **Pixel Tablet (Android 15, 2560 × 1600)** con el **simulador interno**, en una
sola escolta (E-0001) del 15/11/2026. Los escenarios 2 a 5 se provocaron desde **Control → Escenarios de prueba**.

## Escenario 1 · Misión normal: inicio, progreso y finalización

| | |
|---|---|
| ![](01-seleccion-robot.png) Selección del robot: solo se conecta a uno en línea. | ![](02-confirmar-inicio.png) Inicio con chaleco verificado y confirmación. |
| ![](03-escolta-p1-normas.png) En P1: saludo y normas de seguridad (LED azul). | ![](04-espera-cruce-p3.png) En P3 espera hasta que el guardia confirma el cruce (LED ámbar). |
| ![](05-escolta-en-curso.png) Camino a P4; la última orden se muestra con su estado. | ![](06-llegada-p4.png) Llegada a P4: volver a la base o dar la mano. |
| ![](07-escolta-completada.png) Escolta completada: duración, recorrido, alertas, órdenes y eventos. | ![](08-historial.png) Historial guardado en Room. |
| ![](09-telemetria.png) Telemetría completa y variables del servicio. | ![](18-control.png) Control: cada orden con su condición de habilitación. |

## Escenario 2 · Batería baja: advertencia y acción disponible

| | |
|---|---|
| ![](15-bateria-baja-alerta.png) Alerta de batería baja con la acción **Ir a punto seguro**. | ![](16-bateria-baja-panel.png) El panel destaca la batería y la alerta. |

## Escenario 3 · Desconexión: datos antiguos, órdenes sin confirmar y recuperación

| | |
|---|---|
| ![](20-desconexion-orden-pendiente.png) Se corta la conexión y el guardia envía *Pausar*: queda **Pendiente** y los datos pasan a **antiguos**. | ![](21-desconexion-sin-respuesta.png) A los 5 s la orden queda **Sin respuesta**; no se reenvía sola. |
| ![](22-desconexion-sin-conexion.png) Pasados 10 s: aviso de desconexión, datos en gris con su antigüedad y órdenes deshabilitadas. | ![](23-conexion-recuperada.png) Al volver la conexión, la app avisa que *Pausar* no se ejecutó y que el robot siguió hasta P4. |

## Escenario 4 · Incidente: falla que exige una decisión del operador

| | |
|---|---|
| ![](13-incidente-inclinacion.png) Inclinación anormal de 20°: alerta crítica con tres decisiones (punto seguro, reanudar con el área despejada o cancelar). | ![](14-incidente-robot-detenido.png) El robot se detuvo solo (LED rojo); la escolta queda detenida hasta que el guardia decide. |

## Escenario 5 · Propio: visitante rezagado

| | |
|---|---|
| ![](10-visitante-rezagado-escolta.png) El visitante quedó a más de 3 m: el robot se detiene y le pide por voz que se acerque. | ![](11-visitante-rezagado-alerta.png) El guardia decide: seguir esperando, reanudar o cancelar. |
| ![](12-reanudar-rechazado.png) *Reanudar* se **rechaza** (código 3201) mientras el visitante no está junto al robot. | ![](17-alertas-reconocidas.png) Alertas reconocidas con hora y operador. |

Los mismos escenarios 2, 3, 4 y la orden rechazada se probaron también con el **emulador de la cátedra
(G1 HMI Lab v0.7)**, provocándolos desde su consola docente.
