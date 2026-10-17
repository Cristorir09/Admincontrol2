# Admin Control (Fabric 1.21.1)

ESTO ES CÓDIGO FUENTE: hay que COMPILARLO para obtener el .jar (el .jar correcto pesa ~100 KB, no 1 KB).

## Opción A: sin instalar nada (GitHub Actions)
1. Crea cuenta en github.com y un repositorio nuevo (privado está bien).
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta .github).
3. Pestaña "Actions" -> espera el check verde (2-4 min) -> abre la ejecución -> abajo "Artifacts" -> descarga admincontrol-jar.
4. Descomprime el zip: dentro está admincontrol-1.0.0.jar. Ponlo en la carpeta mods.
Si sale rojo, abre el paso "gradle build" y copia el error.

## Opción B: en tu PC
1. Instala JDK 21 (adoptium.net) y Gradle 8.10 (gradle.org/install).
2. En esta carpeta: gradle build
3. El jar queda en build/libs/admincontrol-1.0.0.jar (NO el -sources).

## Usar
Escribe /ac en el juego. Config en config/admincontrol.json

## Novedades
- /ac > Items y bloqueos: catálogo por categorías (como el panel del video), buscador, detalle del ítem
  (bloquear ítem / receta / proteger bloque / permitir a jugador / obtener).
- /ac > Evento: reparte jugadores al azar y de forma equilibrada entre los anfitriones (ej. 75 jugadores / 5 admins = 15 c/u),
  los teletransporta dispersos alrededor de cada anfitrión, con cuenta atrás, vista previa, "barajar de nuevo" y "devolver a todos".
  Comandos: /ac event | run | shuffle | return | cancel

## Panel gráfico (cliente)
Con el mod instalado en tu cliente, `/ac` (o la tecla K) abre un panel propio con barra lateral:
Items (categorías, buscador, detalle), Spawn, Daño, Reglas, Jugadores, Evento y Vanish/Admin.
Si un jugador NO tiene el mod, /ac le abre el menú de cofre (/ac chest lo fuerza).
Los admins pueden usar /vanish directamente.

## Reglas extremas: por qué "no pasa nada"
Se necesitan las dos cosas: interruptor general ENCENDIDO y no estar en creativo (o activar "Afecta a creativo").
Al añadir una regla desde el panel el interruptor general se enciende solo, y si una regla se ignora el juego te avisa en la barra de acción.
