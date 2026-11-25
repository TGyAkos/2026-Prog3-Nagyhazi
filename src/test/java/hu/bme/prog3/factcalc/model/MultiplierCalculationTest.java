package hu.bme.prog3.factcalc.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplierCalculationTest {

    @Test
    void specificMachineIdOverridesGenericTypeMultiplier() {
        UUID woodId = UUID.randomUUID();
        UUID planksId = UUID.randomUUID();
        UUID assemblerMk1Id = UUID.randomUUID();
        UUID assemblerMk2Id = UUID.randomUUID();

        Machine mk1 = new Machine(assemblerMk1Id, "Assembler Mk1", "assembler", "", 1.5);
        Machine mk2 = new Machine(assemblerMk2Id, "Assembler Mk2", "assembler", "", 2.0);
        Machine smelter = new Machine(UUID.randomUUID(), "Smelter", "smelter", "", 1.0);

        Recipe recipe = new Recipe(
                UUID.randomUUID(),
                "Wood Planks",
                20.0,
                "",
                true,
                List.of(
                        RecipeMachineSpec.byId(assemblerMk1Id, 2.0),
                        RecipeMachineSpec.byType("assembler", 1.0)
                ),
                List.of(new ItemAmount(woodId, 1.0)),
                List.of(new ItemAmount(planksId, 5.0)),
                List.of()
        );

        assertTrue(recipe.isCompatibleWith(mk1));
        assertTrue(recipe.isCompatibleWith(mk2));
        assertFalse(recipe.isCompatibleWith(smelter));

        // mk1: 20.0 / (2.0 * 1.5) = 6.6666...
        assertEquals(20.0 / 3.0, recipe.getEffectiveTime(mk1), 1e-9);
        // mk2: 20.0 / (1.0 * 2.0) = 10.0
        assertEquals(10.0, recipe.getEffectiveTime(mk2), 1e-9);

        assertThrows(IllegalArgumentException.class, () -> recipe.getEffectiveTime(smelter));
    }

    @Test
    void timeUnitAndCeilMachineCountHandleEpsilonCleanly() {
        assertEquals(1.0, TimeUnit.PER_MINUTE.toPerSecond(60.0), 1e-9);
        assertEquals(3600.0, TimeUnit.PER_HOUR.fromPerSecond(1.0), 1e-9);

        assertEquals(0, ProductionNode.computeCeilMachines(0.0));
        assertEquals(2, ProductionNode.computeCeilMachines(2.00000000001));
        assertEquals(3, ProductionNode.computeCeilMachines(2.01));
    }
}
