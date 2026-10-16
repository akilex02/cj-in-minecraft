# Primera persona con los brazos de CJ — diseño

**Fecha:** 2026-10-12

## Objetivo

Jugar en primera persona (como Minecraft) con las armas del mod sin tener que pasar a F5: ver el arma 3D de GTA en las manos de CJ (su piel y su ropa), y
caminar, apuntar, disparar, recargar, golpear con el bate y dar puñetazos igual que en tercera persona. Los ítems de Minecraft se siguen viendo como en vanilla.

## Decisiones del usuario

- Vista: **arma 3D + brazos de CJ** (no solo el arma, no la cámara dentro del CJ entero).
- Alcance: **armas de fuego** (colt45, desert_eagle, micro_uzi, chromegun, ak47, m4, minigun, rocket_launcher), **bate** y **mano vacía (puños)**.
  La munición, la patineta, el jetpack y los ítems de Minecraft se quedan en vanilla.
- Enfoque **A**: los brazos y el arma con la pose real de GTA, vistos desde la cabeza de CJ.
- La jugabilidad en primera persona es la misma que en tercera (efectos de velocidad y salto retrasado de CJ incluidos); la mira vanilla queda en el centro.

## Diseño

### 1. Cuándo se usa

Se dibujan los brazos de CJ cuando se cumple todo:

- la cámara está en primera persona;
- hay una pose fresca de GTA (`CjRenderer.hasFreshPose()`);
- la mano principal tiene un arma de fuego o el bate del mod, o está vacía;
- no está en el modo skate ni conduciendo.

En cualquier otro caso, Minecraft dibuja su mano y su ítem como siempre.

### 2. Dónde se engancha

Un mixin en `HeldItemRenderer.renderFirstPersonItem(AbstractClientPlayerEntity, float, float, Hand, float, ItemStack, float, MatrixStack,
VertexConsumerProvider, int)`, que Minecraft llama una vez por mano con la pila ya en el espacio de la cámara.

- Mano principal: se cancela el dibujo vanilla y se dibujan los brazos de CJ y el arma.
- Mano secundaria: también se cancela, para que no flote el brazo de Steve.
  Mientras se ven los brazos de CJ, un ítem en la mano secundaria no se ve en primera persona (limitación aceptada).

### 3. Qué se dibuja: `FirstPersonArms`

- **Selección (al cargar el modelo):** para cada parte de la malla de CJ, los triángulos cuyos tres vértices tienen la mayor parte de su peso
  (al menos 0.5) en huesos del brazo: brazo, antebrazo, mano y dedos de cada lado (nodos de GTA 22–26 y 32–36). Así entran las mangas y la piel de los
  brazos; el torso, la cabeza y las clavículas quedan fuera, porque taparían la vista. Pura y probada.
- **Cada fotograma:** la malla se piela con la pose de GTA (el mismo `Skinner` de la tercera persona); solo se emiten los triángulos elegidos.
- **Colocación:** se usa la misma orientación del cuerpo que en tercera persona (el yaw de `BodyFacing` o, al apuntar, el del ped de GTA) y la posición de
  la cabeza de CJ (nodo 5, sus ojos) como origen: `cámara ← giro de la mirada⁻¹ · giro del cuerpo · (vértice − cabeza)`.
  Así los brazos se ven desde los ojos de CJ y, al apuntar, el arma de GTA ya apunta al centro de la pantalla. La matemática es una clase pura y probada.
- **Armas:** el `WeaponRig` existente, en la mano derecha (nodo 24) y, con la doble pistola, también en la izquierda (nodo 34).

### 4. Cuando no apuntas

GTA deja los brazos colgando y el arma puede quedar fuera de la vista. Por eso hay un ajuste en el espacio de la cámara (desplazamiento x, y, z y giro
pitch, yaw, roll) por tipo: **pistola** (colt45, desert_eagle, micro_uzi), **rifle** (chromegun, ak47, m4), **pesada** (minigun, rocket_launcher),
**bate** y **puños**.

- Al apuntar o disparar, el ajuste se desvanece en unos 0.15 s y queda la pose exacta de GTA; al soltar, vuelve en el mismo tiempo.
- Los valores por defecto los pone el mod. El comando `/cjfp <tipo> x y z pitch yaw roll` los cambia en vivo hasta cerrar el juego, para calibrar a ojo
  (como `/cjhold`), y `/cjfp <tipo>` muestra los actuales.

### 5. Jugabilidad

- `CjEffectsClient.cjInThirdPerson()` pasa a ser "CJ activo" (puente vivo y pose fresca), sin mirar la perspectiva. Los efectos de velocidad, el salto
  retrasado y el `JumpIntent` aplican también en primera persona.
- La mira vanilla queda en el centro en primera persona (ya es así); el disparo ya usa el rayo desde la cámara (`AimRay`), que en primera persona sale de los ojos.

### 6. Errores

- Sin pose fresca, sin modelo o con un fallo al dibujar: se vuelve al dibujo vanilla, y el fallo queda en el log una sola vez.
- La selección de triángulos se hace una vez por modelo; si un modelo no tiene huesos de brazo, no hay brazos y se usa vanilla.

### 7. Pruebas

- Unitarias: la selección de los triángulos de los brazos; la colocación (de modelo a cámara con la cabeza como origen); la mezcla del ajuste al apuntar;
  la elección del tipo según el arma; el análisis del comando `/cjfp`.
- En el juego (el usuario): caminar con cada tipo, apuntar, disparar, recargar, doble pistola, bate, puños; que los ítems de Minecraft, la munición, la
  patineta y el jetpack sigan en vanilla; que en tercera persona nada cambie.

## Fuera de alcance

- Primera persona en el modo skate y en el coche (siguen como están).
- Mostrar el ítem de la mano secundaria junto a los brazos de CJ.
