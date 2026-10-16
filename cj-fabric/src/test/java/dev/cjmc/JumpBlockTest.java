package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JumpBlockTest {
    @Test
    void aWeaponThatCannotJumpBlocksTheJumpOnSolidGround() {
        assertTrue(JumpBlock.applies(false, true, false, false, false, false));
    }

    @Test
    void aWeaponThatCanJumpNeverBlocksIt() {
        assertFalse(JumpBlock.applies(true, true, false, false, false, false));
    }

    @Test
    void theJumpKeyStaysFreeWhereItDoesSomethingElse() {
        // in the air (elytra opens with it, creative flight starts with a double tap), flying, on a mount, swimming or climbing
        assertFalse(JumpBlock.applies(false, false, false, false, false, false), "in the air");
        assertFalse(JumpBlock.applies(false, true, true, false, false, false), "flying");
        assertFalse(JumpBlock.applies(false, true, false, true, false, false), "riding");
        assertFalse(JumpBlock.applies(false, true, false, false, true, false), "in a fluid");
        assertFalse(JumpBlock.applies(false, true, false, false, false, true), "climbing");
    }
}
