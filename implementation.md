# Factcalc Implementation Plan

## 1. Overview & Technical Stack

**Factcalc (Factory Calculator)** is a desktop Java + Swing application for designing, calculating, and persisting factory production lines (e.g., Factorio, Satisfactory).

### Core Technology Choices
- **Language & Build:** Java 22 (`pom.xml`), Maven
- **UI Framework:** Pure Java Swing (standard Swing Look & Feel, e.g., Nimbus/System L&F — no third-party UI libraries)
- **UI Language:** **Hungarian** (all labels, menus, buttons, dialogs, validation/error messages); **English** for source code, identifiers, and documentation
- **Dependencies (Minimal):**
  - `com.google.code.gson:gson` — JSON serialization/deserialization for catalogs and saved production plans
  - `org.junit.jupiter:junit-jupiter` (test scope) — Unit and integration tests
- **Solver Engine:** Custom pure-Java solver (no external math/LP libraries), using active-set Gaussian elimination for exact flow solving (including cyclic/recursive byproduct graphs) and pruned DFS backtracking for discrete `auto` recipe optimization
- **Production Line Visualization:** Structured Swing `JTree` + `JTable` views (dependency tree, machine/recipe breakdown table with inline controls, explicit graph-edge connections table, and summary view)

---

## 2. Package & Project Structure

```text
src/
├── main/
│   ├── java/hu/bme/prog3/
│   │   ├── Main.java                              # Application entry point (launches Swing EDT)
│   │   └── factcalc/
│   │       ├── model/                             # Domain entities & DTOs
│   │       │   ├── Item.java
│   │       │   ├── Machine.java                   # extends Item
│   │       │   ├── ItemAmount.java                # (itemId, piece)
│   │       │   ├── RecipeMachineSpec.java         # (id?, type?, multiplier)
│   │       │   ├── Recipe.java
│   │       │   ├── Catalog.java                   # In-memory items, machines, recipes
│   │       │   ├── TimeUnit.java                  # PER_SECOND, PER_MINUTE, PER_HOUR
│   │       │   ├── CalculationMode.java           # TARGET_OUTPUT, MAX_OUTPUT
│   │       │   ├── RateEntry.java                 # (itemId, rate, unit)
│   │       │   ├── ProductionPlanConfig.java      # User inputs, targets, overrides, flags
│   │       │   ├── ProductionNode.java            # Solved recipe node + machine + rates
│   │       │   ├── FlowEdge.java                  # Directed edge in the production graph
│   │       │   ├── ProductionPlanResult.java      # Full solved graph, summary, notices
│   │       │   ├── SolverNotice.java              # INFO / WARNING / ERROR messages
│   │       │   └── SavedPlan.java                 # Persistable plan (config + result snapshot)
│   │       ├── persistence/                       # JSON storage
│   │       │   ├── GsonFactory.java               # Custom adapters (UUID, Instant, etc.)
│   │       │   ├── CatalogRepository.java         # Load/save single JSON catalog presets
│   │       │   └── PlanRepository.java            # Load/save production line plans
│   │       ├── service/                           # Application services
│   │       │   ├── CatalogService.java            # CRUD, validation, referential integrity
│   │       │   └── IconCacheService.java          # Async image loader (URL/file) + placeholder
│   │       ├── solver/                            # Calculation & optimization engine
│   │       │   ├── ProductionSolver.java          # Facade orchestrating validation & solving
│   │       │   ├── RecipeResolver.java            # Dependency resolution, conflicts, alternatives
│   │       │   ├── RecipeResolutionCallback.java  # Interactive callback for UI prompts
│   │       │   ├── LinearSystemSolver.java        # Gaussian elimination with partial pivoting
│   │       │   ├── ByproductAnalyzer.java         # Recycling, self-sustaining & feedback checks
│   │       │   ├── AutoOptimizer.java             # Discrete DFS search for max-output `auto`
│   │       │   └── SolverException.java           # Domain-specific solver errors (Hungarian msg)
│   │       └── ui/                                # Swing GUI (Hungarian labels)
│   │           ├── MainFrame.java                 # Menu bar, JTabbedPane, status bar
│   │           ├── catalog/
│   │           │   ├── ItemManagerPanel.java      # Item CRUD table + toolbar
│   │           │   ├── MachineManagerPanel.java   # Machine CRUD table + toolbar
│   │           │   ├── RecipeManagerPanel.java    # Recipe CRUD table + toolbar
│   │           │   ├── ItemEditDialog.java
│   │           │   ├── MachineEditDialog.java
│   │           │   └── RecipeEditDialog.java
│   │           ├── planner/
│   │           │   ├── PlannerPanel.java          # Split pane: config (left) + results (right)
│   │           │   ├── PlanConfigPanel.java       # Mode, inputs, outputs, unit, auto checkbox
│   │           │   ├── PlanResultPanel.java       # Tabbed JTree / JTable / Edges / Summary
│   │           │   ├── DependencyTreeModel.java   # Cycle-safe tree representation of graph
│   │           │   ├── StepsTableModel.java       # Editable machine & recycle columns
│   │           │   ├── EdgesTableModel.java       # Graph connections (From -> Item -> To)
│   │           │   ├── RecipeConflictDialog.java  # Choose among multiple original recipes
│   │           │   ├── AlternativeOnlyDialog.java # Continue with alternative or abort
│   │           │   └── RecipeOverrideDialog.java  # Visual alternative recipe selector
│   │           └── components/
│   │               ├── ItemComboBoxRenderer.java  # Icon + name rendering
│   │               ├── MachineComboBoxEditor.java # Inline JTable machine selector
│   │               └── ErrorDialogHelper.java     # Consistent Hungarian alert dialogs
│   └── resources/
│       └── presets/
│           └── sample_catalog.json                # Built-in starter catalog preset
└── test/
    └── java/hu/bme/prog3/factcalc/
        ├── model/MultiplierCalculationTest.java
        ├── service/CatalogServiceTest.java
        ├── persistence/PersistenceRoundTripTest.java
        └── solver/
            ├── TargetOutputSolverTest.java
            ├── MaxOutputSolverTest.java
            ├── AlternativeRecipeAndAutoTest.java
            └── RecursiveByproductTest.java
```

---

## 3. Data Model & JSON Formats

### 3.1 Domain Entities

1. **`Item`**
   - `UUID id`
   - `String name` (non-blank)
   - `String img` (optional URL or local file path)

2. **`Machine extends Item`**
   - Inherits `id`, `name`, `img` (so a machine can also be crafted as an item in recipes!)
   - `String type` (non-blank category, e.g., `"assembler"`, `"smelter"`)
   - `double multiplier` (`> 0.0`, general crafting speed multiplier)

3. **`Recipe`**
   - `UUID id`
   - `String name` (non-blank)
   - `double time` (`> 0.0`, base crafting time in seconds)
   - `String img` (optional; defaults to primary output's `img` if left empty in UI)
   - `boolean isOriginal` (JSON key: `"is_original"`)
   - `List<RecipeMachineSpec> machines` (non-empty list; each entry has `UUID id` XOR `String type`, plus `double multiplier > 0.0`)
   - `List<ItemAmount> inputs` (may be empty for raw resource / extraction recipes)
   - `List<ItemAmount> outputs` (non-empty; each `piece > 0.0`)
   - `List<ItemAmount> byproducts` (optional, may be empty; each `piece > 0.0`)

### 3.2 Machine Compatibility & Effective Craft Time Formula

Given a `Recipe r` and a `Machine m`:
1. Search `r.machines` for an entry with `entry.id != null && entry.id.equals(m.id)`.
   - If found, `recipeMultiplier = entry.multiplier` (**specific `id` always overrides `type`**).
2. Otherwise, search `r.machines` for an entry with `entry.type != null && entry.type.equals(m.type)`.
   - If found, `recipeMultiplier = entry.multiplier`.
3. Otherwise, `m` cannot execute `r`.

Effective craft time (in seconds):
$$t_{\text{eff}}(r, m) = \frac{r.\text{time}}{\text{recipeMultiplier} \times m.\text{multiplier}}$$

Required machine count for recipe execution rate $x_r$ (cycles/sec):
$$N_r = x_r \cdot t_{\text{eff}}(r, m)$$
Displayed in the UI as both exact fractional and rounded-up integer: e.g., `2.50 (3 db)` (with epsilon tolerance `1e-9` so `2.0000000001` rounds cleanly to `2 db`).

### 3.3 Catalog JSON Format (Single File Preset)

Preserves the exact entity schemas from the specification:

```json
{
  "name": "Sample Factory Preset",
  "items": [
    {
      "id": "9bfda7f7-77bc-492c-bb03-34490709c432",
      "name": "wood",
      "img": "link_to_img"
    }
  ],
  "machines": [
    {
      "id": "460a54c7-03c3-477d-b02c-f8efb86e9793",
      "name": "assembler mk1",
      "type": "assembler",
      "img": "link_to_img",
      "multiplier": 1.0
    }
  ],
  "recipes": [
    {
      "id": "b66b93a7-e293-46f1-a5f8-00681cc9a5a0",
      "name": "wood planks",
      "time": 20.0,
      "img": "link_to_img",
      "is_original": true,
      "machines": [
        { "id": "460a54c7-03c3-477d-b02c-f8efb86e9793", "multiplier": 2.0 },
        { "type": "assembler", "multiplier": 1.0 }
      ],
      "inputs": [
        { "id": "9bfda7f7-77bc-492c-bb03-34490709c432", "piece": 1.0 }
      ],
      "outputs": [
        { "id": "72af2e15-1568-4d65-a2e4-fc2c414aac5d", "piece": 5.0 }
      ],
      "byproducts": [
        { "id": "2ad16410-a608-4ae6-9e27-1c250cf8a127", "piece": 2.0 }
      ]
    }
  ]
}
```

In memory, `Catalog` indexes all plain `Item`s and `Machine`s in a unified `Map<UUID, Item>` (supporting `Machine extends Item`), plus typed views `getMachines()`, `getPlainItems()`, and `getRecipes()`.

### 3.4 Saved Production Plan JSON Format

Saved as a standalone JSON file (e.g., `*.factplan.json`) containing:
- `planName` (string), `catalogName` (string), `savedAt` (ISO-8601 timestamp)
- `config`: `mode` (`TARGET_OUTPUT` | `MAX_OUTPUT`), `displayUnit`, `autoMode` (boolean), `inputs` (list of `{itemId, rate, unit}`), `targets` (list of `{itemId, rate, unit}`), `recipeOverrides` (`itemId -> recipeId`), `machineSelections` (`recipeId -> machineId`), `recycledByproducts` (`itemId -> boolean`)
- `result`: serialized `ProductionPlanResult` (nodes, flow edges, external inputs, outputs, byproducts, machine totals, notices)

When loading a plan:
1. Validate referenced item/machine/recipe IDs against the currently loaded catalog.
2. If all references exist, load the configuration and result (allowing immediate inspection and live edits/recalculation).
3. If the catalog differs (missing IDs), display a clear Hungarian warning and offer read-only inspection of the saved snapshot or abort.

---

## 4. CRUD & Referential Integrity Rules (`CatalogService`)

1. **Create / Update Validations:**
   - `Item`: non-blank name, unique UUID.
   - `Machine`: non-blank name, non-blank type, `multiplier > 0`.
   - `Recipe`:
     - Non-blank name, `time > 0`.
     - At least 1 output (`piece > 0`); all input/output/byproduct item IDs must exist in catalog.
     - No duplicate item IDs within the same list (`inputs`, `outputs`, or `byproducts`).
     - At least 1 valid machine spec (`id` XOR `type`, `multiplier > 0`) that resolves to at least one existing machine (or warn if a `type` currently has no concrete machines).
2. **Deletion Referential Integrity (Block & Inform):**
   - **Deleting an `Item`:** Scan all recipes (`inputs`, `outputs`, `byproducts`). If referenced by any recipe, **block deletion** and show a dialog listing the names of the referencing recipes.
   - **Deleting a `Machine`:**
     - Check all recipes for item references (`inputs`, `outputs`, `byproducts`, since `Machine extends Item`).
     - Check all recipes for explicit machine `id` references in `recipe.machines`.
     - Also check if deleting this machine leaves any `type`-based recipe with zero compatible machines in the catalog.
     - If any references exist, **block deletion** and list the affected recipes.
   - **Deleting a `Recipe`:** Allowed immediately (refresh planner view if the deleted recipe was active in the current plan).

---

## 5. Production Line Calculation Engine (`solver` package)

All internal calculations normalize rates to **`items / second` (`double`)**:
- `PER_SECOND`: `rate`
- `PER_MINUTE`: `rate / 60.0`
- `PER_HOUR`: `rate / 3600.0`

### 5.1 Pre-Calculation Validation
1. **Target Eligibility Check:**
   - Every target item $t$ must have at least one recipe $r$ where $t \in r.\text{outputs}$ **and** `!r.inputs.isEmpty()`.
   - Items with no producing recipe or only inputless producing recipes are **filtered out** of the target picker in the UI **and** rejected by the solver with a clear Hungarian error.
2. **Mode-Specific Constraints:**
   - `TARGET_OUTPUT`: Requires $\ge 1$ target output with `rate > 0`. Supports multiple target outputs and optional multiple user-provided external inputs.
   - `MAX_OUTPUT`: Requires **exactly 1** target output and $\ge 1$ user-provided input with `rate > 0`. The `auto` toggle is only available in this mode.

### 5.2 Recipe Resolution (`RecipeResolver`) — Default & Manual Mode
Starting from the target item(s), traverse dependencies via BFS/DFS:
1. If item $i$ is explicitly supplied by the user as an external input: treat $i$ as a terminal source (do not expand upstream recipes).
2. If the user has an explicit override in `config.recipeOverrides`: use that recipe.
3. Otherwise, find all recipes with $i \in r.\text{outputs}$:
   - Filter to `isOriginal == true`:
     - **Count == 1:** Select automatically.
     - **Count > 1:** Invoke `RecipeResolutionCallback.chooseOriginalRecipe(item, candidates)` -> opens `RecipeConflictDialog` so the user decides.
     - **Count == 0:** Check `isOriginal == false` (alternative recipes):
       - **Count >= 1:** Invoke `RecipeResolutionCallback.confirmAlternativeRecipe(item, alternatives)` -> opens `AlternativeOnlyDialog` warning that the item is only craftable via alternative recipes; user chooses an alternative recipe or aborts planning.
       - **Count == 0:** Throw `SolverException` ("A választott kimenet nem gyártható le: a(z) '<item>' nem állítható elő és nem szerepel a bemenetek között.").
4. **Traceability Check:** Every leaf item in the resolved recipe graph must be either a user-provided input or produced by an inputless recipe (`inputs.isEmpty()`). Otherwise fail with `SolverException`.

### 5.3 Exact Flow Solving & Recursive Byproducts (`LinearSystemSolver` + `ByproductAnalyzer`)

Given a resolved set of active recipes $R = \{r_1, \dots, r_k\}$ (at most one producing recipe per non-terminal item) and the user's recycling toggles (`recycledByproducts`):

1. **Eligible Recursive Byproducts:**
   - A byproduct $b$ produced by some $r \in R$ is **recyclable** if $b$ also appears in the `inputs` of at least one recipe in $R$ (including $r$ itself).
   - If the user has not yet decided for a recyclable byproduct $b$, default to not recycling (`false`) or prompt/highlight in the UI with an immediate toggle in the production steps table and summary panel so the user can switch between *"Rekurzív felhasználás"* and *"Melléktermék kimenet"* at any time.

2. **Linear Balance System (Active-Set Gaussian Elimination):**
   - Unknowns: recipe execution rates $x_1, \dots, x_k \ge 0$ (cycles/sec), where $r_j$ is the chosen producer for item $p_j$.
   - For each active recipe $r_j \in R$, construct the material balance equation for its primary item $p_j$:
     $$\text{out}(r_j, p_j) x_j + \sum_{r_m \in R,\, m \neq j} \text{out}(r_m, p_j) x_m + \sum_{\substack{r_m \in R \\ \text{recycled}(p_j)}} \text{byp}(r_m, p_j) x_m - \sum_{r_m \in R} \text{in}(r_m, p_j) x_m = d_{p_j}$$
     where $d_{p_j} \ge 0$ is the external target demand for item $p_j$.
   - Solve $A \mathbf{x} = \mathbf{d}$ using **Gaussian elimination with partial pivoting** (`LinearSystemSolver`).
   - **Handling Surplus / Negative $x_j$ (Active-Set Iteration):**
     - If a non-target recipe $r_j$ solves to $x_j < -\epsilon$ (meaning recycled byproducts or multi-output co-products already supply more of $p_j$ than needed), deactivate $r_j$ ($x_j = 0$) and re-solve the reduced system.
     - If $A$ is singular (e.g., an isolated cycle with exact loop gain 1.0) or yields an inconsistent/unbounded system: analyze the strongly connected component (SCC) of the cycle using `ByproductAnalyzer`.

3. **Recursive Byproduct Classification (`ByproductAnalyzer`):**
   For every item $b$ with `recycle = true`:
   - Compute total consumption $C_b = \sum_{r \in R} \text{in}(r, b) x_r$ and total recycled byproduct production $P_b = \sum_{r \in R} \text{byp}(r, b) x_r$.
   - Compute net external requirement $\Delta_b = C_b - P_b$:
     - **$\Delta_b > \epsilon$ (Partial Offset):** Recycled flow $= P_b$; remaining external input needed $= \Delta_b$.
     - **$|\Delta_b| \le \epsilon$ (Self-Sustaining):** External steady-state input $= 0$; add an `INFO` `SolverNotice`: *"Önfenntartó melléktermék-ciklus: `<item>` (indítás után nem igényel külső bemenetet)."*
     - **$\Delta_b < -\epsilon$ (Net Surplus / Feedback Check):**
       - Inspect the recipe(s) in the cycle generating the surplus of $b$:
         - **No other external input exists** in the cycle (e.g., a recipe only takes byproduct $b$ and produces more $b$ than it consumes) $\implies$ throw `SolverException` (**Positive Feedback Error**: *"Pozitív visszacsatolás észlelve `<item>` esetén külső bemenet nélkül!"*).
         - **Other external input(s) exist** $\implies$ Valid! Recycled flow $= C_b$, external net input of $b = 0$ (add self-sustaining info), and emit surplus $-\Delta_b > 0$ as a **byproduct output** of the production line.

4. **Graph Construction (`ProductionPlanResult`):**
   - For each active recipe $r$ with $x_r > \epsilon$:
     - Resolve selected `Machine` (from `config.machineSelections`, defaulting to the first compatible machine in catalog).
     - Calculate `effectiveTime`, `exactMachineCount`, `ceilMachineCount`.
   - Build directed `FlowEdge` list connecting:
     - `EXTERNAL_INPUT` $\to$ Recipe node
     - Recipe node $\to$ Recipe node (intermediate transfers & recycled byproduct feedback edges flagged `isRecursive = true`)
     - Recipe node $\to$ `FINAL_OUTPUT`
     - Recipe node $\to$ `BYPRODUCT_OUTPUT` (unrecycled byproducts & surplus recycled byproducts)
   - Verify user-provided inputs in `TARGET_OUTPUT` mode: if a user entered an optional rate cap on an input and required consumption exceeds it, attach a `WARNING` notice showing the deficit.

### 5.4 Max Output Mode (`CalculationMode.MAX_OUTPUT`)

Given 1 target item $T$ and user inputs $U = \{(u_1, C_1), \dots, (u_m, C_m)\}$:
1. Solve the `TARGET_OUTPUT` linear system for **unit target demand** ($d_T = 1.0$ item/sec).
2. Extract unit external input consumptions $\hat{c}_i$ for all external inputs $i$.
3. Check if at least one user-provided input $u_j \in U$ has $\hat{c}_{u_j} > \epsilon$:
   - If **none** are used $\implies$ throw `SolverException` (*"A megadott bemenetek egyike sincs felhasználva a gyártósorban!"*).
4. Compute maximum achievable output rate:
   $$S_{\max} = \min_{\substack{u_j \in U \\ \hat{c}_{u_j} > \epsilon}} \frac{C_j}{\hat{c}_{u_j}}$$
5. Scale all unit execution rates $\hat{x}_r$, flows, machine counts, byproducts, and **missing/unspecified required raw inputs** ($i \notin U$, required rate $= S_{\max} \cdot \hat{c}_i$) by $S_{\max}$.
6. Mark which user input(s) are the **bottleneck** ($\hat{c}_{u_j} \cdot S_{\max} \approx C_j$) and clearly separate user-supplied inputs from auto-calculated additional raw inputs in the summary.

### 5.5 `auto` Mode Optimization (`AutoOptimizer`)

Enabled **only** in `MAX_OUTPUT` mode (single target item $T$). Performs a discrete search picking **at most one recipe per item** across both original and alternative recipes:

1. **Reachability Pre-Pruning:**
   - Run a bottom-up fixed-point pass starting from `userInputs ∪ {outputs of inputless recipes}` to compute the set of all craftable items. Discard any recipe whose inputs are not all craftable.
2. **Lazy Backtracking DFS:**
   - Start with `needed = {T}`, `chosen = {}`.
   - Pick the first unresolved item $i \in \text{needed}$ that is not in `userInputs`.
   - Iterate over all craftable recipes $r$ producing $i$ (both original and alternative):
     - Skip if adding $r$ creates an unresolvable recipe-definition cycle without a base input.
     - Recurse with `chosen[i] = r` and `r.inputs` added to `needed`.
   - At each complete leaf assignment:
     - Determine recyclable byproducts (test enabling recycling when beneficial and valid; reject positive-feedback configurations).
     - Run the unit-demand solver and compute $S_{\max}$.
     - Update `bestResult` if $S_{\max} > \text{best} + \epsilon$, breaking ties by: (1) fewer alternative recipes used, (2) fewer total recipes/machines.
3. **Responsiveness & Safety:**
   - Execute `AutoOptimizer` inside a `SwingWorker` so the Swing EDT stays responsive, with a progress indicator and a configurable search-node budget (e.g., 50,000 states) to guard against pathological cyclic catalogs.

---

## 6. Swing UI Design (Hungarian Localization)

### 6.1 Main Window (`MainFrame`)
- **Title:** `Factcalc — Gyártósor Tervező`
- **Menu Bar (`JMenuBar`):**
  - `Fájl`:
    - `Új katalógus`
    - `Katalógus megnyitása...` (load preset JSON)
    - `Katalógus mentése` / `Katalógus mentése másként...`
    - Separator
    - `Gyártósor terv betöltése...`
    - `Gyártósor terv mentése...`
    - Separator
    - `Kilépés`
  - `Nézet`:
    - `Alapértelmezett példa katalógus betöltése`
- **Main Tabs (`JTabbedPane`):**
  1. `Gyártósor tervező` (`PlannerPanel`)
  2. `Itemek` (`ItemManagerPanel`)
  3. `Gépek` (`MachineManagerPanel`)
  4. `Receptek` (`RecipeManagerPanel`)
- **Bottom Status Bar:** Active catalog name, counts (`Itemek: X | Gépek: Y | Receptek: Z`), unsaved changes indicator.

### 6.2 Catalog Management Tabs (CRUD)
Each tab has a filter/search bar, a sortable `JTable` with 24x24 icons (loaded asynchronously via `IconCacheService`), and action buttons (`Hozzáadás`, `Szerkesztés`, `Törlés`):
- **`ItemManagerPanel` & `ItemEditDialog`:**
  - Fields: `Név`, `Kép URL / útvonal` (with `Tallózás...` file chooser + live preview).
- **`MachineManagerPanel` & `MachineEditDialog`:**
  - Fields: `Név`, `Típus` (editable `JComboBox` pre-populated with existing types), `Szorzó` (`JSpinner`, `> 0`), `Kép URL / útvonal`.
- **`RecipeManagerPanel` & `RecipeEditDialog`:**
  - Fields: `Név`, `Elkészítési idő (mp)`, `Eredeti recept` (`JCheckBox`), `Kép URL / útvonal`.
  - Dynamic sub-tables with Add/Remove rows for:
    - `Felhasználható gépek / géptípusok` (Mode selector: `Specifikus gép` vs `Géptípus`, target selector, `Recept szorzó`)
    - `Bemenetek` (`Item`, `Darabszám`)
    - `Kimenetek` (`Item`, `Darabszám`)
    - `Melléktermékek` (`Item`, `Darabszám`)

### 6.3 Production Line Planner (`PlannerPanel`)

Organized as a `JSplitPane`:

#### Left Panel — Configuration (`PlanConfigPanel`)
- **Számítási mód:** Radio buttons:
  - `Cél mennyiségből gyártósor` (`TARGET_OUTPUT`)
  - `Bemenetből maximum kimenet` (`MAX_OUTPUT`)
- **Megjelenítési mértékegység:** `JComboBox` (`item/mp`, `item/perc`, `item/óra`) — immediately converts displayed numbers without losing precision.
- **Cél kimenetek:**
  - In `TARGET_OUTPUT` mode: editable table of `(Item, Mennyiség, Egység)` with `+` / `-` buttons; dropdown **excludes** non-craftable and inputless-only items.
  - In `MAX_OUTPUT` mode: single target item selector (`Cél item`) + `Auto (alternatív receptek optimalizálása)` `JCheckBox` (hidden/disabled in `TARGET_OUTPUT` mode).
- **Megadott bemenetek:**
  - Editable table of `(Item, Mennyiség, Egység)` with `+` / `-` buttons (in `TARGET_OUTPUT` mode, quantity can be optional/unlimited or capped; in `MAX_OUTPUT` mode, positive quantity is required).
- **Recept beállítások:**
  - Button: `Alternatív receptek kiválasztása...` -> opens `RecipeOverrideDialog` listing items with multiple recipes so the user can visually pick original or alternative recipes before or after solving.
  - Button: `Választások visszaállítása` (resets overrides to defaults).
- **Primary Action Buttons:**
  - `Számítás` (runs solver)
  - `Terv mentése...` / `Terv betöltése...`

#### Right Panel — Results (`PlanResultPanel`)
Contains a **Notice Banner** at the top (displays `INFO` such as self-sustaining loops, `WARNING`s, or `ERROR`s) and 4 sub-tabs:
1. **`Függőségi fa` (`JTree`):**
   - Hierarchical tree rooted at each target output, expanding recipes -> inputs -> upstream recipes down to external/raw inputs.
   - Each node displays icon, recipe/item name, flow rate (`item/mp|perc|óra`), selected machine + count (`2.50 (3 db)`), and tags like `[Alternatív]`, `[Külső bemenet]`, `[Számított bemenet]`, or `[Rekurzív visszacsatolás]` (stops infinite expansion cleanly).
2. **`Gyártási lépések` (`JTable`):**
   - Columns: `Recept`, `Típus (Eredeti/Alt)`, `Gép` (**interactive `JComboBox` cell editor** listing only compatible machines for that recipe — changing it immediately recalculates machine counts!), `Effektív idő (mp)`, `Gépek száma` (`2.50 (3 db)`), `Bemenetek`, `Kimenetek`, `Melléktermékek`, `Rekurzív felhasználás` (**interactive `JCheckBox`** enabled only when a recipe has a recyclable byproduct — toggling immediately recalculates!).
   - Context action / button: `Recept cseréje...` to switch the selected step's item to an alternative recipe.
3. **`Összeköttetések` (`JTable`):**
   - Explicit graph edge list showing how machines/recipes connect through items:
     - Columns: `Forrás`, `Item`, `Mennyiség`, `Cél`, `Kapcsolat típusa` (`Bemenet`, `Köztes`, `Végső kimenet`, `Melléktermék kimenet`, `Rekurzív visszacsatolás`).
4. **`Összegzés` (`JPanel` with summary tables):**
   - **Bemenetek:** separates `Felhasználó által megadott bemenetek` (marking bottlenecks in `MAX_OUTPUT` mode) and `Automatikusan számított hiányzó bemenetek`.
   - **Kimenetek & Melléktermékek:** Final target outputs + unrecycled/surplus byproduct outputs.
   - **Összesített gépszükséglet:** Aggregated count per concrete `Machine` across all steps (exact + integer sum).

---

## 7. Implementation Roadmap (Phased)

### Phase 1: Project Setup & Domain Model
- Update [pom.xml](file:///G:/bme/prog3/pom.xml) to add `gson` and `junit-jupiter` dependencies.
- Implement `hu.bme.prog3.factcalc.model.*` classes (`Item`, `Machine`, `Recipe`, `ItemAmount`, `RecipeMachineSpec`, `Catalog`, `TimeUnit`, plan/result DTOs).
- Implement effective craft time calculation and machine compatibility resolution with unit tests.

### Phase 2: Persistence & Catalog Service
- Implement `GsonFactory`, `CatalogRepository`, and `PlanRepository`.
- Create `src/main/resources/presets/sample_catalog.json` containing examples of basic chains, multi-machine types + specific overrides, alternative recipes, inputless extraction recipes, multi-output recipes, and recursive byproduct loops.
- Implement `CatalogService` with full validation and referential-integrity deletion blocking.
- Write unit tests for persistence round-trips and CRUD/integrity rules.

### Phase 3: Calculation & Optimization Engine (`solver`)
- Implement `LinearSystemSolver` (Gaussian elimination with partial pivoting + tolerance handling).
- Implement `RecipeResolver` (target eligibility, original conflict callback, alternative-only confirmation callback, traceability to user/inputless inputs).
- Implement `ByproductAnalyzer` (recyclable detection, partial offset, self-sustaining detection, positive feedback error vs external-input surplus byproduct).
- Implement `ProductionSolver` for both `TARGET_OUTPUT` and `MAX_OUTPUT` modes (including unused user inputs check and unspecified required input calculation).
- Implement `AutoOptimizer` (reachability pruning + lazy DFS backtracking over discrete recipe assignments).
- Write thorough JUnit 5 test suite covering every requirement and edge case from the specification.

### Phase 4: Swing Frontend — Core Shell & Catalog CRUD
- Implement `IconCacheService` (async local file / HTTP URL loader with caching and fallback placeholder).
- Implement `MainFrame` with Hungarian menus, file dialogs, and tabbed layout.
- Implement `ItemManagerPanel`, `MachineManagerPanel`, `RecipeManagerPanel`, and their modal edit dialogs with live validation and deletion-block messaging.

### Phase 5: Swing Frontend — Production Line Planner
- Implement `PlanConfigPanel` (mode switching, dynamic input/output tables, target eligibility filtering, unit selector, `auto` checkbox, alternative recipe override dialog).
- Implement interactive solver callbacks (`RecipeConflictDialog`, `AlternativeOnlyDialog`) and `SwingWorker` execution for `auto` mode.
- Implement `PlanResultPanel`:
  - `DependencyTreeModel` (`JTree`)
  - `StepsTableModel` (`JTable` with inline machine dropdown and recursive byproduct toggle)
  - `EdgesTableModel` (`JTable` of graph connections)
  - Summary tables and status/notice banner
- Wire plan save/load (`PlanRepository`) into `MainFrame` and `PlannerPanel`.

### Phase 6: Verification & Polish
- Run full `mvn test` suite and verify all edge cases manually through the Swing GUI.
- Verify Hungarian UI copy consistency, error messages, window resizing, keyboard shortcuts, and unsaved-changes prompts.

---

## 8. Requirement-to-Component Traceability

| Specification Requirement | Implementing Components |
| --- | --- |
| Item, Machine (`extends Item`), Recipe with spec JSON schema | `model.Item`, `model.Machine`, `model.Recipe`, `persistence.CatalogRepository` |
| Graphical CRUD for items, machines, recipes | `ui.catalog.*`, `service.CatalogService` |
| Target output calculation (`item/mp`, `item/perc`, `item/óra`) | `solver.ProductionSolver`, `model.TimeUnit`, `ui.planner.*` |
| Max output from inputs + calculating unspecified required inputs | `solver.ProductionSolver` (`MAX_OUTPUT`) |
| Reject targets with no recipe or only inputless recipes | `solver.ProductionSolver`, `ui.planner.PlanConfigPanel` |
| Prompt user when multiple original recipes exist | `solver.RecipeResolver`, `ui.planner.RecipeConflictDialog` |
| Alternative recipes visual selection + default-mode warning when only alternative exists | `ui.planner.RecipeOverrideDialog`, `ui.planner.AlternativeOnlyDialog` |
| `auto` mode only for 1-item max output, optimizing across alternatives | `solver.AutoOptimizer`, `ui.planner.PlanConfigPanel` |
| Machine type & specific machine override (`id` overrides `type`), multiplier formula | `model.Recipe`, `ui.planner.MachineComboBoxEditor` |
| Recursive byproducts: user choice, self-sustaining notice, positive feedback error vs surplus output | `solver.ByproductAnalyzer`, `solver.LinearSystemSolver`, `ui.planner.StepsTableModel` |
| Multiple inputs/outputs & saving/loading generated plans | `model.SavedPlan`, `persistence.PlanRepository`, `ui.planner.PlannerPanel` |
| Traceability to user/inputless inputs & unused inputs error in max output mode | `solver.RecipeResolver`, `solver.ProductionSolver` |
