# Primera persona con los brazos de CJ — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En primera persona, con un arma de fuego, el bate o la mano vacía, se ven los brazos de CJ (pose de GTA) y el arma 3D desde los ojos de CJ, y la jugabilidad es la misma que en tercera persona.

**Architecture:** Tres piezas puras y probadas: `FirstPersonArms` (qué triángulos de la malla son de los brazos), `FirstPersonPlacement` (de espacio de modelo a espacio de cámara con la cabeza de CJ como origen) y `FirstPersonHolds` (tipo por arma, ajuste sin apuntar, su mezcla y la calibración). `CjRenderer.renderFirstPerson` las usa con el `Skinner` y el `WeaponRig` existentes; un mixin en `HeldItemRenderer.renderFirstPersonItem` lo llama en lugar del dibujo vanilla. `CjEffectsClient.cjInThirdPerson()` pasa a `cjActive()` sin mirar la perspectiva.

**Tech Stack:** Java 21, Fabric 1.21.1 (yarn), Mixin, JUnit 5. Construcción: `cd cj-fabric && ./gradlew build --offline --no-daemon` (el demonio de Gradle se cae en esta máquina; con `--no-daemon` va).

**Spec:** `docs/superpowers/specs/2026-10-12-first-person-cj-design.md`

## Global Constraints

- Solo armas de fuego (colt45, desert_eagle, micro_uzi, chromegun, ak47, m4, minigun, rocket_launcher), el bate (baseball_bat) y la mano vacía; todo lo demás, vanilla.
- Solo con pose fresca de GTA; sin pose, vanilla. Nunca en el modo skate ni conduciendo.
- Triángulo de brazo: sus tres vértices con al menos 0.5 de peso en los nodos 22–26 o 32–36.
- El ajuste sin apuntar se desvanece en 0.15 s al apuntar o disparar.
- Tipos: pistola (colt45, desert_eagle, micro_uzi), rifle (chromegun, ak47, m4), pesada (minigun, rocket_launcher), bate, puños.
- Comando de calibración bajo el `/cj` existente: `/cj fp <tipo> x y z pitch yaw roll` y `/cj fp <tipo>` (el spec lo llama `/cjfp`; se sigue el patrón de `/cj hold`).
- Comentarios y nombres en el estilo del código vecino (frases en inglés que dicen el porqué).

## Review Focus

- Mano secundaria con un ítem mientras se ven los brazos de CJ: no se dibuja y no debe quedar un brazo de Steve flotando (Tarea 5: el mixin cancela también la mano secundaria).
- GTA cerrado a mitad de partida (pose vieja): debe volver el dibujo vanilla en el siguiente fotograma, sin errores (Tarea 5: `renderFirstPerson` devuelve false sin pose).
- Un modelo de CJ sin huesos de brazo (un cj.glb raro): no hay brazos y queda vanilla (Tarea 1: selección vacía; Tarea 5: sin triángulos → false).
- Cambiar de arma mientras se apunta: el ajuste sigue mezclándose sin saltos (Tarea 3: la mezcla avanza por tiempo, no por tipo).
- Mirar muy arriba o muy abajo: la colocación debe seguir el pitch de la cámara (Tarea 2: test con pitch).

---

### Task 1: Triángulos de los brazos

**Files:**
- Create: `cj-fabric/src/main/java/dev/cjmc/FirstPersonArms.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/FirstPersonArmsTest.java`

**Interfaces:**
- Consumes: `GlbModel.Part(String name, float[] positions, float[] normals, float[] uvs, int[] indices, int[] joints, float[] weights, int image)`, `GlbModel.jointNodeIds`.
- Produces: `static final int[] ARM_NODES`; `static boolean[] armJoints(int[] jointNodeIds)`; `static int[] armTriangles(GlbModel.Part part, boolean[] armJoint)`; `static List<GlbModel.Part> armParts(GlbModel model)` (las partes con solo sus triángulos de brazo, mismas posiciones; vacía si no hay ninguno).

- [ ] **Step 1: Write the failing test**

```java
package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FirstPersonArmsTest {
    // joints of a model: 0 = pelvis (node 1), 1 = right upper arm (node 22), 2 = right hand (node 24)
    private static final int[] NODES = {1, 22, 24};

    private static GlbModel.Part part(int[] joints, float[] weights, int[] indices) {
        int vertices = joints.length / 4;
        return new GlbModel.Part("p", new float[3 * vertices], new float[3 * vertices], new float[2 * vertices], indices, joints, weights, 0);
    }

    @Test
    void theArmJointsAreTheArmsHandsAndFingersOfBothSides() {
        boolean[] arm = FirstPersonArms.armJoints(new int[] {1, 3, 21, 22, 23, 24, 25, 26, 31, 32, 33, 34, 35, 36, 41});
        assertArrayEquals(new boolean[] {false, false, false, true, true, true, true, true, false, true, true, true, true, true, false}, arm);
    }

    @Test
    void aTriangleAllOnTheArmIsKeptOneOnTheBodyIsNot() {
        // vertices 0, 1, 2 on the arm (joint 1 or 2); 3, 4, 5 on the pelvis (joint 0)
        int[] joints = {1, 0, 0, 0, 2, 0, 0, 0, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 0.5f, 0.5f, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        int[] kept = FirstPersonArms.armTriangles(part(joints, weights, new int[] {0, 1, 2, 3, 4, 5}), FirstPersonArms.armJoints(NODES));
        assertArrayEquals(new int[] {0, 1, 2}, kept);
    }

    @Test
    void aTriangleWithOneVertexMostlyOnTheBodyIsLeftOut() {
        // the shoulder: vertex 2 has only 0.4 of its weight on the arm
        int[] joints = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 0.4f, 0.6f, 0, 0};
        assertEquals(0, FirstPersonArms.armTriangles(part(joints, weights, new int[] {0, 1, 2}), FirstPersonArms.armJoints(NODES)).length);
    }

    @Test
    void aModelWithoutArmBonesHasNoArmParts() {
        int[] joints = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        GlbModel.Part body = part(joints, weights, new int[] {0, 1, 2});
        assertTrue(FirstPersonArms.armTriangles(body, FirstPersonArms.armJoints(new int[] {1})).length == 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*FirstPersonArmsTest*'`
Expected: FAIL (compilation: `FirstPersonArms` does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
package dev.cjmc;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of CJ's mesh that is drawn in first person: the triangles of his arms (sleeves and skin of the upper arms, forearms, hands and fingers of both sides).
 * The torso, the head and the clavicles would cover the view from his eyes, so they are left out. Pure.
 */
public final class FirstPersonArms {
    /** The nodes of GTA's skeleton that make the arms: upper arm, forearm, hand and two finger bones, right (22-26) and left (32-36). */
    public static final int[] ARM_NODES = {22, 23, 24, 25, 26, 32, 33, 34, 35, 36};
    /** A vertex belongs to the arms when at least this much of its weight is on arm joints. */
    static final float MIN_ARM_WEIGHT = 0.5f;

    private FirstPersonArms() {}

    /** Which joints of a model (by their node ids) are arm joints. */
    public static boolean[] armJoints(int[] jointNodeIds) {
        boolean[] arm = new boolean[jointNodeIds.length];
        for (int j = 0; j < jointNodeIds.length; j++) {
            for (int node : ARM_NODES) {
                arm[j] |= jointNodeIds[j] == node;
            }
        }
        return arm;
    }

    /** The indices of the part's triangles whose three vertices are on the arms. */
    public static int[] armTriangles(GlbModel.Part part, boolean[] armJoint) {
        int[] idx = part.indices();
        int[] kept = new int[idx.length];
        int n = 0;
        for (int t = 0; t + 2 < idx.length; t += 3) {
            if (onArm(part, idx[t], armJoint) && onArm(part, idx[t + 1], armJoint) && onArm(part, idx[t + 2], armJoint)) {
                kept[n++] = idx[t];
                kept[n++] = idx[t + 1];
                kept[n++] = idx[t + 2];
            }
        }
        return java.util.Arrays.copyOf(kept, n);
    }

    private static boolean onArm(GlbModel.Part part, int v, boolean[] armJoint) {
        float onArm = 0;
        for (int k = 0; k < 4; k++) {
            int j = part.joints()[4 * v + k];
            if (j >= 0 && j < armJoint.length && armJoint[j]) {
                onArm += part.weights()[4 * v + k];
            }
        }
        return onArm >= MIN_ARM_WEIGHT;
    }

    /** The model's parts with only their arm triangles (the same vertices, so the skinned positions of the whole part serve), in the model's order; empty parts too. */
    public static List<GlbModel.Part> armParts(GlbModel model) {
        boolean[] arm = armJoints(model.jointNodeIds);
        List<GlbModel.Part> out = new ArrayList<>();
        for (GlbModel.Part p : model.parts) {
            out.add(new GlbModel.Part(p.name(), p.positions(), p.normals(), p.uvs(), armTriangles(p, arm), p.joints(), p.weights(), p.image()));
        }
        return out;
    }

    /** Whether any part has an arm triangle. */
    public static boolean any(List<GlbModel.Part> parts) {
        for (GlbModel.Part p : parts) {
            if (p.indices().length > 0) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*FirstPersonArmsTest*'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/FirstPersonArms.java cj-fabric/src/test/java/dev/cjmc/FirstPersonArmsTest.java
git commit -m "First person: the triangles of CJ's arms"
```

---

### Task 2: De modelo a cámara desde la cabeza de CJ

**Files:**
- Create: `cj-fabric/src/main/java/dev/cjmc/FirstPersonPlacement.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/FirstPersonPlacementTest.java`

**Interfaces:**
- Consumes: `Mat4.mul(float[] a, int ao, float[] b, int bo, float[] out, int oo)`, `Mat4.identity`.
- Produces: `static float[] modelToCamera(float cameraYaw, float cameraPitch, float bodyYaw, float[] headModel)` → 16 floats column-major; `static float[] headInModel(JointFrame frame, int headJoint, float groundOffset)` → la cabeza en el espacio de modelo de Minecraft (x izquierda, y arriba, z adelante), como la deja el `Skinner`; `static float[] apply(float[] m, float[] p)` (para tests y depuración).

- [ ] **Step 1: Write the failing test**

```java
package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class FirstPersonPlacementTest {
    private static final float[] HEAD = {0f, 1.6f, 0f};

    @Test
    void theHeadIsTheEye() {
        float[] m = FirstPersonPlacement.modelToCamera(30f, 10f, 30f, HEAD);
        assertArrayEquals(new float[] {0, 0, 0}, FirstPersonPlacement.apply(m, HEAD), 1e-5f);
    }

    @Test
    void lookingTheWayTheBodyFacesWhatIsAheadOfHimIsAheadOfTheCamera() {
        // the model faces +z; the camera looks down -z with +x to its right (his left is the camera's left: -x)
        float[] m = FirstPersonPlacement.modelToCamera(0f, 0f, 0f, HEAD);
        assertArrayEquals(new float[] {0, 0, -1}, FirstPersonPlacement.apply(m, new float[] {0, 1.6f, 1}), 1e-5f);
        assertArrayEquals(new float[] {-1, 0, 0}, FirstPersonPlacement.apply(m, new float[] {1, 1.6f, 0}), 1e-5f);
        assertArrayEquals(new float[] {0, 1, 0}, FirstPersonPlacement.apply(m, new float[] {0, 2.6f, 0}), 1e-5f);
    }

    @Test
    void aBodyTurnedAwayFromTheLookShowsHisFrontToTheSide() {
        // body facing yaw 90 (toward -x in the world), camera facing yaw 0 (+z, so +x is at its left and -x at its right): his front is at the camera's right
        float[] m = FirstPersonPlacement.modelToCamera(0f, 0f, 90f, HEAD);
        assertArrayEquals(new float[] {1, 0, 0}, FirstPersonPlacement.apply(m, new float[] {0, 1.6f, 1}), 1e-5f);
    }

    @Test
    void lookingDownTheThingsAheadRiseInTheView() {
        // pitch 90: looking straight down; what is ahead of him (level) is now above the middle of the screen
        float[] m = FirstPersonPlacement.modelToCamera(0f, 90f, 0f, HEAD);
        assertArrayEquals(new float[] {0, 1, 0}, FirstPersonPlacement.apply(m, new float[] {0, 1.6f, 1}), 1e-5f);
    }

    @Test
    void theHeadComesFromTheJointInMinecraftsAxes() {
        // GTA's model space (x forward, y left, z up) -> Minecraft's (x left, y up, z forward), lifted by the ground offset
        JointFrame f = new JointFrame(2);
        f.pos[3] = 0.1f;   // forward
        f.pos[4] = 0.2f;   // left
        f.pos[5] = 0.6f;   // up
        assertArrayEquals(new float[] {0.2f, 1.6f, 0.1f}, FirstPersonPlacement.headInModel(f, 1, 1.0f), 1e-6f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*FirstPersonPlacementTest*'`
Expected: FAIL (compilation: `FirstPersonPlacement` does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
package dev.cjmc;

/**
 * Where CJ's arms are seen from in first person: his model space (Minecraft's axes, feet at the origin, facing +z, as the Skinner leaves it) turned the way his
 * body faces in the world, moved so his head is at the eye, and turned to the camera (which looks down -z, +x to its right, +y up). Pure.
 */
public final class FirstPersonPlacement {
    private FirstPersonPlacement() {}

    /** The 4x4 (column-major) that takes a point of the model to the camera: RotX(pitch) * RotY(cameraYaw + 180) * RotY(-bodyYaw) * Translate(-head). */
    public static float[] modelToCamera(float cameraYaw, float cameraPitch, float bodyYaw, float[] headModel) {
        float[] translate = new float[16];
        Mat4.identity(translate, 0);
        translate[12] = -headModel[0];
        translate[13] = -headModel[1];
        translate[14] = -headModel[2];
        float[] body = rotY(-bodyYaw);
        float[] view = mul(rotX(cameraPitch), rotY(cameraYaw + 180f));
        return mul(view, mul(body, translate));
    }

    /** The head joint of a pose in Minecraft's model axes, as the Skinner places the mesh (GTA's z up, lifted by the ground offset). */
    public static float[] headInModel(JointFrame frame, int headJoint, float groundOffset) {
        float x = frame.pos[3 * headJoint], y = frame.pos[3 * headJoint + 1], z = frame.pos[3 * headJoint + 2] + groundOffset;
        float[] v = {x, y, z};
        Axes.toMinecraft(v, 0);
        return v;
    }

    public static float[] apply(float[] m, float[] p) {
        return new float[] {m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12], m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13],
                m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]};
    }

    // Minecraft's RotationAxis.POSITIVE_X / POSITIVE_Y by degrees
    static float[] rotX(float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1};
    }

    static float[] rotY(float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        Mat4.mul(a, 0, b, 0, out, 0);
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*FirstPersonPlacementTest*'`
Expected: PASS. If a sign is off (the yaw tests), fix the formula, not the tests: the tests state how Minecraft's camera sees the world (yaw 0 faces +z, yaw 90 faces -x, pitch 90 looks down).

- [ ] **Step 5: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/FirstPersonPlacement.java cj-fabric/src/test/java/dev/cjmc/FirstPersonPlacementTest.java
git commit -m "First person: from CJ's model to the camera, his head at the eye"
```

---

### Task 3: Tipos, ajuste sin apuntar, mezcla y calibración

**Files:**
- Create: `cj-fabric/src/main/java/dev/cjmc/FirstPersonHolds.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/FirstPersonHoldsTest.java`

**Interfaces:**
- Consumes: `FirstPersonPlacement.rotX`, `rotY`, `mul` (package-private, same package).
- Produces: `enum Kind { PISTOL, RIFLE, HEAVY, BAT, FISTS }`; `static Kind kindOf(String weaponId)` (null id → FISTS; unknown → null); `static Kind parseKind(String)` (null if unknown); instance `float[] hold(Kind)` (x, y, z, pitch, yaw, roll); `void setHold(Kind, float[] six)`; `float blend(boolean aiming, double seconds)` (1 = full hold, 0 = GTA's pose); `static float[] holdMatrix(float[] six, float amount)` (4x4, column-major).

- [ ] **Step 1: Write the failing test**

```java
package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FirstPersonHoldsTest {
    @Test
    void eachWeaponHasItsKind() {
        assertEquals(FirstPersonHolds.Kind.PISTOL, FirstPersonHolds.kindOf("colt45"));
        assertEquals(FirstPersonHolds.Kind.PISTOL, FirstPersonHolds.kindOf("desert_eagle"));
        assertEquals(FirstPersonHolds.Kind.PISTOL, FirstPersonHolds.kindOf("micro_uzi"));
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.kindOf("chromegun"));
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.kindOf("ak47"));
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.kindOf("m4"));
        assertEquals(FirstPersonHolds.Kind.HEAVY, FirstPersonHolds.kindOf("minigun"));
        assertEquals(FirstPersonHolds.Kind.HEAVY, FirstPersonHolds.kindOf("rocket_launcher"));
        assertEquals(FirstPersonHolds.Kind.BAT, FirstPersonHolds.kindOf("baseball_bat"));
        assertEquals(FirstPersonHolds.Kind.FISTS, FirstPersonHolds.kindOf(null));
        assertNull(FirstPersonHolds.kindOf("jetpack"));
    }

    @Test
    void theKindOfTheCommandIsReadByName() {
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.parseKind("rifle"));
        assertEquals(FirstPersonHolds.Kind.FISTS, FirstPersonHolds.parseKind("FISTS"));
        assertNull(FirstPersonHolds.parseKind("sword"));
    }

    @Test
    void aimingTakesTheHoldAwayIn015SecondsAndLettingGoBringsItBack() {
        FirstPersonHolds h = new FirstPersonHolds();
        assertEquals(1f, h.blend(false, 0.05), 1e-6f);
        assertEquals(1f - 0.075f / 0.15f, h.blend(true, 0.075), 1e-5f);
        assertEquals(0f, h.blend(true, 0.2), 1e-6f);
        assertEquals(0f, h.blend(true, 1.0), 1e-6f);
        assertEquals(0.5f, h.blend(false, 0.075), 1e-5f);
    }

    @Test
    void aCalibratedHoldReplacesTheDefault() {
        FirstPersonHolds h = new FirstPersonHolds();
        float[] six = {0.1f, -0.2f, 0.3f, 10f, 20f, 30f};
        h.setHold(FirstPersonHolds.Kind.BAT, six);
        assertArrayEquals(six, h.hold(FirstPersonHolds.Kind.BAT), 0f);
    }

    @Test
    void noHoldIsNoChangeAndAFullOneMovesByItsOffset() {
        float[] six = {0.1f, 0.2f, -0.3f, 0f, 0f, 0f};
        assertArrayEquals(new float[] {0, 0, 0}, FirstPersonPlacement.apply(FirstPersonHolds.holdMatrix(six, 0f), new float[] {0, 0, 0}), 1e-6f);
        assertArrayEquals(new float[] {0.1f, 0.2f, -0.3f}, FirstPersonPlacement.apply(FirstPersonHolds.holdMatrix(six, 1f), new float[] {0, 0, 0}), 1e-6f);
        assertArrayEquals(new float[] {0.05f, 0.1f, -0.15f}, FirstPersonPlacement.apply(FirstPersonHolds.holdMatrix(six, 0.5f), new float[] {0, 0, 0}), 1e-6f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*FirstPersonHoldsTest*'`
Expected: FAIL (compilation: `FirstPersonHolds` does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
package dev.cjmc;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * When CJ does not aim, GTA lets his arms hang and the weapon can fall out of the view from his eyes: each kind of weapon then gets a shift and a turn in the
 * camera's space (x right, y up, z toward the viewer) that brings it in. Aiming or firing takes it away in BLEND_SECONDS, leaving GTA's own pose, which points at
 * the middle of the screen. The defaults are a start; `/cj fp` calibrates them by eye until the game closes.
 */
public final class FirstPersonHolds {
    public enum Kind { PISTOL, RIFLE, HEAVY, BAT, FISTS }

    static final double BLEND_SECONDS = 0.15;

    private static final Map<Kind, float[]> DEFAULTS = new EnumMap<>(Kind.class);
    static {
        DEFAULTS.put(Kind.PISTOL, new float[] {0.0f, 0.35f, -0.25f, 25f, 0f, 0f});
        DEFAULTS.put(Kind.RIFLE, new float[] {0.0f, 0.25f, -0.15f, 15f, 0f, 0f});
        DEFAULTS.put(Kind.HEAVY, new float[] {0.0f, 0.20f, -0.10f, 10f, 0f, 0f});
        DEFAULTS.put(Kind.BAT, new float[] {0.0f, 0.35f, -0.20f, 25f, 0f, 0f});
        DEFAULTS.put(Kind.FISTS, new float[] {0.0f, 0.40f, -0.30f, 30f, 0f, 0f});
    }

    private final Map<Kind, float[]> holds = new EnumMap<>(DEFAULTS);
    private float amount = 1f;

    /** The kind of a weapon of weapons.json by its id; the empty hand (null) is the fists; null for anything else. */
    public static Kind kindOf(String weaponId) {
        if (weaponId == null) {
            return Kind.FISTS;
        }
        return switch (weaponId) {
            case "colt45", "desert_eagle", "micro_uzi" -> Kind.PISTOL;
            case "chromegun", "ak47", "m4" -> Kind.RIFLE;
            case "minigun", "rocket_launcher" -> Kind.HEAVY;
            case "baseball_bat" -> Kind.BAT;
            default -> null;
        };
    }

    public static Kind parseKind(String name) {
        for (Kind k : Kind.values()) {
            if (k.name().equals(name.toUpperCase(Locale.ROOT))) {
                return k;
            }
        }
        return null;
    }

    public float[] hold(Kind kind) {
        return holds.get(kind).clone();
    }

    public void setHold(Kind kind, float[] six) {
        holds.put(kind, six.clone());
    }

    /** How much of the hold shows after `seconds` more: toward 0 while aiming, toward 1 when not, at 1 / BLEND_SECONDS per second. */
    public float blend(boolean aiming, double seconds) {
        float step = (float) (Math.max(0, seconds) / BLEND_SECONDS);
        amount = aiming ? Math.max(0f, amount - step) : Math.min(1f, amount + step);
        return amount;
    }

    /** The hold `six` (x, y, z, pitch, yaw, roll in degrees) taken `amount` of the way, as a 4x4 (column-major) applied in the camera's space. */
    public static float[] holdMatrix(float[] six, float amount) {
        float[] m = new float[16];
        Mat4.identity(m, 0);
        m[12] = six[0] * amount;
        m[13] = six[1] * amount;
        m[14] = six[2] * amount;
        float[] turn = FirstPersonPlacement.mul(FirstPersonPlacement.rotY(six[4] * amount),
                FirstPersonPlacement.mul(FirstPersonPlacement.rotX(six[3] * amount), rotZ(six[5] * amount)));
        return FirstPersonPlacement.mul(m, turn);
    }

    private static float[] rotZ(float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*FirstPersonHoldsTest*'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/FirstPersonHolds.java cj-fabric/src/test/java/dev/cjmc/FirstPersonHoldsTest.java
git commit -m "First person: the hold of each kind of weapon while not aiming, its blend and its calibration"
```

---

### Task 4: La jugabilidad de CJ también en primera persona

**Files:**
- Modify: `cj-fabric/src/main/java/dev/cjmc/CjEffectsClient.java` (rename `cjInThirdPerson` → `cjActive`, drop the perspective check)
- Modify: `cj-fabric/src/main/java/dev/cjmc/CjClient.java:268`, `cj-fabric/src/main/java/dev/cjmc/mixin/KeyboardInputMixin.java:23`, `cj-fabric/src/main/java/dev/cjmc/JumpIntent.java:11-13` (parameter name `cjActive`)
- Test: `cj-fabric/src/test/java/dev/cjmc/JumpIntentTest.java` (comment and test name follow the new name; behaviour unchanged)

**Interfaces:**
- Produces: `public static boolean CjEffectsClient.cjActive()` — the bridge is live and CJ has a fresh pose, whatever the camera.

- [ ] **Step 1: Change the method**

```java
    /** CJ is played: the bridge is live and he has a fresh pose, in first person as in third (what the effects and the delayed jump are for). */
    public static boolean cjActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.world != null && CjClient.bridgeLive() && CjRenderer.INSTANCE.hasFreshPose();
    }
```

Replace every call of `cjInThirdPerson()` with `cjActive()` (`CjEffectsClient.tick`, `CjClient.jumpIntent`, `KeyboardInputMixin`), rename the `JumpIntent.wanted` parameter and the class comment of `CjEffectsClient` ("wanted while the player is played as CJ"), and in `JumpIntentTest` rename the comment `cjInThirdPerson` → `cjActive` and the test `aPressedKeyOnTheGroundAsCjInThirdPersonAnnouncesTheJump` → `aPressedKeyOnTheGroundAsCjAnnouncesTheJump`.

- [ ] **Step 2: Run the whole suite**

Run: `cd cj-fabric && ./gradlew test --offline --no-daemon`
Expected: PASS (no behaviour test depends on the perspective)

- [ ] **Step 3: Commit**

```bash
git add -A cj-fabric/src
git commit -m "CJ's speed, effects and delayed jump in first person too"
```

---

### Task 5: Dibujo en primera persona, mixin y comando

**Files:**
- Modify: `cj-fabric/src/main/java/dev/cjmc/CjRenderer.java` (fields `armParts`, `headJoint`, `holds`, `lastFirstPersonNs`; `init` computes `armParts`/`headJoint`; new `renderFirstPerson`, `firstPersonWanted`, `setFirstPersonHold`, `firstPersonHold`)
- Create: `cj-fabric/src/main/java/dev/cjmc/mixin/HeldItemRendererMixin.java`
- Modify: `cj-fabric/src/main/resources/cj_fabric.mixins.json` (add `HeldItemRendererMixin` to `client`)
- Modify: `cj-fabric/src/main/java/dev/cjmc/CjClient.java` (the `/cj fp` command next to `/cj hold`)

**Interfaces:**
- Consumes: `FirstPersonArms.armParts(GlbModel)`, `FirstPersonArms.any(List)`, `FirstPersonPlacement.modelToCamera(...)`, `FirstPersonPlacement.headInModel(...)`, `FirstPersonHolds.kindOf/parseKind/hold/setHold/blend/holdMatrix`, `CjEffectsClient.cjActive()`, `MeshEmitter.emit(MatrixStack.Entry, VertexConsumerProvider, GlbModel.Part, float[], float[], int, Identifier)`, `WeaponRig.draw(WeaponDef, JointFrame, int, float, MatrixStack.Entry, VertexConsumerProvider, int)` and `drawLeft(...)`, `SkateRide.active()`, `VehicleClient.driving()`.
- Produces: `public boolean CjRenderer.firstPersonWanted(AbstractClientPlayerEntity player)`; `public boolean CjRenderer.renderFirstPerson(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light)`.

- [ ] **Step 1: CjRenderer — fields and init**

In `CjRenderer`, next to the other `volatile` fields:

```java
    private volatile List<GlbModel.Part> armParts = List.of(); // CJ's arms only, for first person
    private volatile int headJoint = -1;
    private static final int HEAD_NODE = 5; // nodeID of CJ's head: his eyes in first person
    private final FirstPersonHolds holds = new FirstPersonHolds();
    private long lastFirstPersonNs;
```

In `init`, after `torsoJoint = ...`:

```java
            headJoint = jointOfNode.getOrDefault(HEAD_NODE, -1);
            armParts = FirstPersonArms.armParts(m);
            if (!FirstPersonArms.any(armParts)) {
                LOG.warn("cj.glb has no arm triangles: first person keeps Minecraft's hand");
            }
```

- [ ] **Step 2: CjRenderer — when and how**

Add to `CjRenderer`:

```java
    /** First person shows CJ's arms: a fresh pose of GTA, his arms in the model, not skating nor driving, and a weapon of the mod (gun or bat) or an empty main hand. */
    public boolean firstPersonWanted(AbstractClientPlayerEntity player) {
        if (model == null || headJoint < 0 || !FirstPersonArms.any(armParts) || !CjEffectsClient.cjActive()
                || dev.cjmc.skate.SkateRide.active() || VehicleClient.driving()) {
            return false;
        }
        WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
        return held != null ? FirstPersonHolds.kindOf(held.id()) != null : player.getMainHandStack().isEmpty();
    }

    /**
     * CJ's arms and weapon seen from his eyes, in the camera's space the hand renderer gives: GTA's pose, the body turned as in third person, the head at the eye,
     * and, while he does not aim, the hold of the kind of weapon. False (Minecraft draws its hand) when there is nothing to draw.
     */
    public boolean renderFirstPerson(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        GlbModel m = model;
        PoseBuffer buffer = poses;
        if (m == null || buffer == null) {
            return false;
        }
        long now = System.nanoTime();
        JointFrame frame = buffer.sample(now);
        if (frame == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean pushed = false;
        try {
            if (!texturesReady) {
                uploadTextures(client, m);
            }
            if (skinner == null) {
                skinner = new Skinner(m);
            }
            skinner.skin(frame);
            // the body turns as in third person (the player model is not drawn in first person, so its facing is updated here)
            Vec3d v = player.getVelocity();
            boolean aimingNow = aiming;
            float facingYaw = aimingNow ? Headings.mcYawFromGta(frame.heading) : player.getYaw(tickDelta);
            float bodyYaw = facing.update((float) (v.x * 20.0), (float) (v.z * 20.0), facingYaw, lastFrameNs == 0 ? 0 : now - lastFrameNs, aimingNow);
            lastFrameNs = now;
            WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
            FirstPersonHolds.Kind kind = FirstPersonHolds.kindOf(held == null ? null : held.id());
            double seconds = lastFirstPersonNs == 0 ? 0 : Math.min(0.1, (now - lastFirstPersonNs) / 1e9);
            lastFirstPersonNs = now;
            float amount = holds.blend(aimingNow, seconds);
            float[] head = FirstPersonPlacement.headInModel(frame, headJoint, m.groundOffset);
            float[] place = FirstPersonPlacement.modelToCamera(player.getYaw(tickDelta), player.getPitch(tickDelta), bodyYaw, head);
            float[] hold = FirstPersonHolds.holdMatrix(holds.hold(kind), amount);

            matrices.push();
            pushed = true;
            matrices.multiplyPositionMatrix(new org.joml.Matrix4f().set(hold));
            matrices.multiplyPositionMatrix(new org.joml.Matrix4f().set(place));
            MatrixStack.Entry entry = matrices.peek();
            List<GlbModel.Part> arms = armParts;
            for (int p = 0; p < arms.size(); p++) {
                if (arms.get(p).indices().length > 0) {
                    MeshEmitter.emit(entry, vcp, arms.get(p), skinner.positions[p], skinner.normals[p], light, textures.get(arms.get(p).image()));
                }
            }
            WeaponRig rig = weapons;
            if (held != null && rig != null && handJoint >= 0) {
                rig.draw(held, frame, handJoint, m.groundOffset, entry, vcp, light);
                if (leftHandJoint >= 0 && CjClient.dualWielding(held.gta())) {
                    rig.drawLeft(held, frame, leftHandJoint, m.groundOffset, entry, vcp, light);
                }
            }
            matrices.pop();
            pushed = false;
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                matrices.pop();
            }
            problem = "CJ renderer disabled: " + e;
            model = null;
            LOG.error("drawing CJ's arms in first person failed; going back to Minecraft's hand", e);
            return false;
        }
    }

    /** `/cj fp`: the hold of a kind (x, y, z, pitch, yaw, roll) until the game closes. */
    public void setFirstPersonHold(FirstPersonHolds.Kind kind, float[] six) {
        holds.setHold(kind, six);
    }

    public float[] firstPersonHold(FirstPersonHolds.Kind kind) {
        return holds.hold(kind);
    }
```

`org.joml.Matrix4f.set(float[])` reads column-major, as `Mat4` writes.

- [ ] **Step 3: The mixin**

Create `cj-fabric/src/main/java/dev/cjmc/mixin/HeldItemRendererMixin.java`:

```java
package dev.cjmc.mixin;

import dev.cjmc.CjRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person with a weapon of the mod or an empty hand: CJ's arms and weapon instead of Minecraft's hand. The main hand draws them; the other hand is not drawn
 * then, or Steve's arm would float beside CJ's.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void cj$firstPersonArms(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
                                    float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!CjRenderer.INSTANCE.firstPersonWanted(player)) {
            return;
        }
        if (hand == Hand.OFF_HAND || CjRenderer.INSTANCE.renderFirstPerson(player, tickDelta, matrices, vertexConsumers, light)) {
            ci.cancel();
        }
    }
}
```

Add `"HeldItemRendererMixin"` to the `client` list of `cj_fabric.mixins.json` (after `"ClientWorldMixin"`).

- [ ] **Step 4: The `/cj fp` command**

In `CjClient`, add a branch to the `/cj` command, after `.then(ClientCommandManager.literal("hold") ...)`:

```java
                        .then(ClientCommandManager.literal("fp")
                                .then(ClientCommandManager.argument("kind", StringArgumentType.word())
                                        .executes(context -> {
                                            FirstPersonHolds.Kind kind = FirstPersonHolds.parseKind(StringArgumentType.getString(context, "kind"));
                                            if (kind == null) {
                                                context.getSource().sendFeedback(Text.literal("Kinds: pistol, rifle, heavy, bat, fists"));
                                                return 0;
                                            }
                                            float[] h = CjRenderer.INSTANCE.firstPersonHold(kind);
                                            context.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT, "%s first-person hold: %s %s %s %s %s %s",
                                                    kind.name().toLowerCase(Locale.ROOT), h[0], h[1], h[2], h[3], h[4], h[5])));
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("x", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("y", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("z", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("pitch", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("yaw", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("roll", FloatArgumentType.floatArg())
                                                .executes(context -> {
                                                    FirstPersonHolds.Kind kind = FirstPersonHolds.parseKind(StringArgumentType.getString(context, "kind"));
                                                    if (kind == null) {
                                                        context.getSource().sendFeedback(Text.literal("Kinds: pistol, rifle, heavy, bat, fists"));
                                                        return 0;
                                                    }
                                                    float[] six = {FloatArgumentType.getFloat(context, "x"), FloatArgumentType.getFloat(context, "y"),
                                                            FloatArgumentType.getFloat(context, "z"), FloatArgumentType.getFloat(context, "pitch"),
                                                            FloatArgumentType.getFloat(context, "yaw"), FloatArgumentType.getFloat(context, "roll")};
                                                    CjRenderer.INSTANCE.setFirstPersonHold(kind, six);
                                                    context.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT,
                                                            "%s first-person hold set (in FirstPersonHolds.DEFAULTS: {%sf, %sf, %sf, %sf, %sf, %sf})",
                                                            kind.name().toLowerCase(Locale.ROOT), six[0], six[1], six[2], six[3], six[4], six[5])));
                                                    return 1;
                                                }))))))))))
```

Import `com.mojang.brigadier.arguments.StringArgumentType` in `CjClient`.

- [ ] **Step 5: Build and launch to check the mixin target**

Run: `cd cj-fabric && ./gradlew build --offline --no-daemon -q` → no output.
Launch Minecraft (`tools/start_games.sh`) and check `cj-fabric/run/logs/latest.log`: no `Mixin ... failed` / `InvalidInjectionException` for `HeldItemRendererMixin` (a mixin target is only proved by a launch, as with `ClientWorldMixin`).

- [ ] **Step 6: Commit**

```bash
git add -A cj-fabric/src
git commit -m "First person: CJ's arms and weapon from his eyes instead of Minecraft's hand, and /cj fp to calibrate the holds"
```

---

### Task 6: Prueba y calibración en el juego (con el usuario)

- [ ] **Step 1:** El usuario prueba en primera persona: caminar con cada tipo (pistola, rifle, pesada, bate, puños), apuntar (clic derecho), disparar, recargar, doble pistola, golpe del bate, puñetazo; que los ítems de Minecraft, la munición, la patineta y el jetpack sigan en vanilla; que la tercera persona no cambie.
- [ ] **Step 2:** Calibrar con `/cj fp <tipo> x y z pitch yaw roll` los ajustes de cada tipo que se vean mal sin apuntar; copiar los valores elegidos a `FirstPersonHolds.DEFAULTS`, correr `./gradlew test --offline --no-daemon` y commitear.
