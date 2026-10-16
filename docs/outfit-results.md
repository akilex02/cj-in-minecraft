# Cuerpo y ropa de CJ desde la partida (puntos 1 y 2)

Fecha: 2026-10-11.

## Hecho
- **Volcado desde el bridge:** `cj_outfit.json` en la carpeta de GTA, con las 10 claves de modelos y 18 de texturas de la ropa (`CPedClothesDesc`) y las estadísticas de músculo y gordura. Se escribe al cargar una partida y cuando algo cambia (mira como mucho una vez por segundo). Ruta de lectura: `CPed+0x480` (`m_pPlayerData`) → `+0x04` (`m_pPedClothesDesc`); músculo de `CStats::StatTypesFloat[23]`, gordura del propio descriptor. Cada puntero se comprueba antes de seguirlo.
- **Cuerpo mezclado:** el juego no tiene un CJ musculoso aparte, mezcla tres cuerpos (Ripped, Fat y Normal, en ese orden en los archivos) con porcentajes de las estadísticas (músculo / 1000; (gordura - 200) / 800; el resto normal). `convert_assets.py --outfit cj_outfit.json` (o `--muscle`, `--fat`) arma el cuerpo con esa mezcla. Sin esas opciones, el resultado es el cuerpo normal de siempre.
- **Reporte:** `python3 tools/outfit_report.py` traduce el volcado a nombres.

## Verificado con la partida real del usuario (cargada el 2026-10-11)
Músculo 1000 y gordura 0 (100 % Ripped). Ropa: sudadera (`hoodya`, textura `hoodyablack`), jeans (`denimsred`), zapatos (`shoe`, `shoedressblk`), lentes (`glasses01`, `glasses01dark`), gorra hacia atrás (`capback`, `capblkback`), barba (`beard`).

Descubierto: la clave de una textura es el hash del nombre del **archivo .txd** (por ejemplo `capblkback.txd` contiene la textura `capblk`), y la de un modelo el del nombre del .dff. El hash es el CRC-32 (reflejado, 0xEDB88320) del nombre en mayúsculas, sin la inversión final.

## Punto 3: la ropa de la partida (hecho)
`convert_assets.py --outfit cj_outfit.json` ahora **viste** a CJ como dice el volcado, además de darle su cuerpo:
- **Modelos:** cada clave de las 10 ranuras de modelo se traduce al `.dff` de `player.img` (ranura vacía: torso, head, hands, legs y feet por defecto, como hace GTA) y cada modelo mezcla sus tres cuerpos por los porcentajes. Partes nuevas: sudadera, zapatos, lentes y gorra en el caso de la partida del usuario.
- **Texturas:** la piel del torso es la mezcla de `torso_ripped`, `torso_fat` y `torso` (así el músculo también se ve en la piel). Encima se pegan las capas de ropa y tatuajes (ranuras 0 y 4 a 12, en ese orden) con la regla de GTA (`PlaceTextureOnTopOfTexture`: el pixel con alfa reemplaza al de abajo entero, sin mezclar). Cabeza, piernas, zapatos, lentes, gorra y demás usan la textura de su ranura; sin ella, la piel desnuda del juego (la cara de `player_face.txd`, `player_legs.txd`, `player_feet.txd`).
- Sin `--outfit` el modelo sigue siendo el de siempre (ropa fija); `--muscle` y `--fat` solos dan el cuerpo con la ropa fija.
- Los tests están en `tools/tests/test_dressing.py` (instalación sintética).

Limitaciones conocidas:
- Los lentes son translúcidos y el cliente dibuja con recorte (cutout): se ven oscuros y sólidos.
- Una clave que ningún archivo de `player.img` tiene da un error claro con el nombre de la ranura.
- Cambiar de ropa dentro de GTA no actualiza el modelo en vivo: hay que volver a correr el conversor con el `cj_outfit.json` nuevo y reiniciar Minecraft.

## Segunda pistola (punto 4)
Con la habilidad de la Colt 45 o la Micro SMG al máximo, GTA hace que CJ lleve **dos armas** (`bTwinPistol` en el `CWeaponInfo` del arma y la habilidad): cada ciclo de disparo lanza 2 rondas y la segunda arma se dibuja en la mano izquierda con el mismo modelo, girada 180° en X y desplazada (0.04, -0.05, 0) en ese marco.
- **Bridge:** `DualWielding` pregunta al propio juego (`CPed::GetWeaponSkill()` 0x5E6580 y `CWeaponInfo::GetWeaponInfo(arma, habilidad)` 0x743C60, bit 11 de las banderas, en el desplazamiento 0x18) y lo manda en las banderas del eco (`kEchoDualWield`, bit 0 de los dos últimos bytes). El registro del bridge anota una línea por arma y habilidad con las banderas leídas, para comprobar la disposición.
- **Cliente:** `WeaponAttach.leftHandFrame` (fórmula del juego, con tests) y `WeaponRig.drawLeft` dibujan el mismo modelo en el hueso 34; solo si el eco dice dos armas y el arma que GTA sostiene es la del ítem en mano.
- Como en GTA, con dos armas cada ciclo gasta 2 rondas (2 balas del inventario) y hace el doble de daño.
