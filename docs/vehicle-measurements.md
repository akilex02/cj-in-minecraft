# Coche: medidas (Tarea 1)

Fecha: 2026-10-09. Greenwood (`greenwoo`, modelo 492). Medido fuera de GTA (`gta3.img`) y en GTA real con el experimento temporal del puente (no está en el repositorio).

## Ejes
Marco del coche de GTA: x derecha, y delante, z arriba. Rumbo de GTA antihorario desde el norte (delante = (−sen H, cos H)).

## Constantes
| Dato | Valor | Cómo se midió |
|---|---|---|
| Radio de la rueda | **0,35 m** (malla `wheel`: 0,39 de ancho × 0,70 de alto) | extensión de la geometría |
| Ruedas (marcos `wheel_*_dummy`, x, y, z) | lf (−0,925, 1,71, −0,35), rf (0,925, 1,71, −0,35), lb (−0,925, −1,70, −0,35), rb (0,925, −1,70, −0,35) | marcos del `.dff` |
| Distancia entre ejes / ancho de vía | 3,41 / 1,85 | de lo anterior |
| CJ sentado, en ejes del coche | **derecha −0,463, delante 0,140, arriba −0,095** | en vivo (el marco `ped_frontseat` dice +0,463: GTA lo refleja para el conductor, que va a la izquierda) |
| Rumbo de CJ respecto al coche | **0** (mira igual que el coche) | en vivo |
| Volante | `CVehicle::m_fSteerAngle` en **+0x494** (float, radianes); **negativo = a la derecha** (mando X = +100 → −0,32 y el coche giró a la derecha). Acelerador +0x49C, freno +0x4A0 | en vivo; el valor cambia con el mando |
| Marcha atrás | `ButtonSquare` con el coche parado: acelerador −1 | en vivo |
| Colores por defecto | pares de `carcols.dat` `greenwoo`: (30, 26), (77, 26)… primer par: 30 = (66, 31, 33) granate, 26 = (165, 169, 167) plata | `carcols.dat` |
| Colores de pintura en el modelo | primario (60, 255, 0), secundario (255, 0, 175); luces: faros (185, 255, 0), intermitentes (255, 175, 0), freno (255, 60, 0), marcha atrás (0, 255, 200) | materiales de `chassis` |
| Piezas | `chassis` (1886 vértices, 2,14 × 5,46 × 1,44, marco en (0, 1,71, −0,35)), `chassis_vlo` (baja resolución, se descarta) | `.dff` |

## Pose de CJ sentado
Con el puente sin soltar a CJ en vehículo (parche temporal: sin la liberación por vehículo ni la deriva, y sin devolver la posición del escenario), el lector de esqueleto actual funciona sin compensación: en Minecraft (F5) CJ aparece sentado, mirando al frente, a la altura del CJ de pie. Hay que dibujarlo en el sitio de GTA respecto al coche.

## Seguridad
Subir, conducir (acelerar, girar, frenar, marcha atrás), bajar con `FlushImmediately(true)` + `Teleport` y borrar con `CWorld::Remove` + destructor: GTA vivo más de 12 s, CJ normal a pie, Steve vuelve a ser CJ. Tras bajar, `ped+0x58C` (`m_pVehicle`) sigue apuntando al coche (el campo `m_pDriver` del coche sí se borra): el puente no debe fiarse de ese puntero tras bajar y borrar; usar la bandera `InVehicle` del ped.
