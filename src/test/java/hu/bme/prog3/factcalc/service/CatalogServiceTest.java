package hu.bme.prog3.factcalc.service;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.RecipeMachineSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogServiceTest {

    @Test
    void blocksItemDeletionWhenReferencedByRecipe() {
        Catalog catalog = new Catalog("Test");
        CatalogService service = new CatalogService(catalog);

        Item ore = service.createItem("Iron Ore", "");
        Item plate = service.createItem("Iron Plate", "");
        service.createMachine("Smelter", "smelter", "", 1.0);
        Recipe smelt = service.createRecipe(
                "Smelt Plate", 2.0, "", true,
                List.of(RecipeMachineSpec.byType("smelter", 1.0)),
                List.of(new ItemAmount(ore.getId(), 1.0)),
                List.of(new ItemAmount(plate.getId(), 1.0)),
                List.of()
        );

        CatalogService.ValidationException ex = assertThrows(
                CatalogService.ValidationException.class,
                () -> service.deleteItem(ore.getId())
        );
        assertTrue(ex.getMessage().contains("Smelt Plate"));

        service.deleteRecipe(smelt.getId());
        service.deleteItem(ore.getId());
        assertEquals(1, catalog.getPlainItems().size());
    }

    @Test
    void blocksMachineDeletionWhenExplicitlyReferencedOrLastForType() {
        Catalog catalog = new Catalog("Test");
        CatalogService service = new CatalogService(catalog);

        Item ore = service.createItem("Ore", "");
        Item plate = service.createItem("Plate", "");
        Machine smelter = service.createMachine("Smelter Mk1", "smelter", "", 1.0);

        service.createRecipe(
                "Smelt", 2.0, "", true,
                List.of(RecipeMachineSpec.byType("smelter", 1.0)),
                List.of(new ItemAmount(ore.getId(), 1.0)),
                List.of(new ItemAmount(plate.getId(), 1.0)),
                List.of()
        );

        // Deleting sole smelter leaves 'Smelt' with 0 compatible machines -> blocked
        assertThrows(CatalogService.ValidationException.class, () -> service.deleteMachine(smelter.getId()));

        // Adding a second smelter allows deleting smelter1 if no explicit id reference exists
        Machine smelter2 = service.createMachine("Smelter Mk2", "smelter", "", 2.0);
        service.deleteMachine(smelter.getId());
        assertEquals(1, catalog.getMachines().size());
        assertEquals(smelter2.getId(), catalog.getMachines().get(0).getId());
    }

    @Test
    void machineCanBeCraftedAsRecipeOutputAndCannotBeDeletedWhileReferenced() {
        Catalog catalog = new Catalog("Test");
        CatalogService service = new CatalogService(catalog);

        Item plate = service.createItem("Plate", "");
        Machine assembler = service.createMachine("Assembler Mk1", "assembler", "", 1.0);
        Machine assembler2 = service.createMachine("Assembler Mk2", "assembler", "", 2.0);

        service.createRecipe(
                "Craft Assembler", 5.0, "", true,
                List.of(RecipeMachineSpec.byId(assembler2.getId(), 1.0)),
                List.of(new ItemAmount(plate.getId(), 10.0)),
                List.of(new ItemAmount(assembler.getId(), 1.0)),
                List.of()
        );

        assertThrows(CatalogService.ValidationException.class, () -> service.deleteMachine(assembler.getId()));
    }
}
