# Specifikáció - Factcalc

> Tamáskovits Gyula Ákos - Q4JZL0   
> 2026-10-23

## Factcalc

Az alkalmazás a következő fogalmakat használja:
- Item: egy definiált tárgy, dolog, megadott paraméterekkel
- Recept: az item-ek egy részhalmazát bemenetként, az item-ek egy részhalmazát kimenetként, valamint melléktermékként az item-ek egy részhalmazát tartalmazza, további paraméterekkel együtt bemenet, kimenet, melléktermék darabszáma, szorzó, elkészítési idő, felhasználható gépek részhalmaza 
*Megjegyzés: egy recept akkor lesz alternatív, ha a felhasználó annak titulálja, különben eredeti*
- Gép: egy olyan speciális item, amely egy adott gyártósoron belül egy lépésben pontosan egy receptet tud alkalmazni, több paraméterrel is rendelkezik, típus, szorzó
- Gyártósor: gépek listája, ami tetszőleges számú item bemenetként való felhasználásával, tetszőleges számú itemet képes létrehozni kimenetként

A Factcalc, avagy Factory Calculator egy GUI-val rendelkező applikáció, amely segítségével gyárépítő játékok, például Factorio, Satisfactory, gyártósorait lehet tervezni, modellezni.

A felhasználónak grafikusan tudnia kell hozzáadnia, szerkeszteni és törölni recepteket, gépeket, item-eket, amelyben egy item-hez, recepthez, géphez tartozó információkat tud rögzíteni.

Az alkalmazásnak ki kell tudnia számolni egy kiválasztott item adott mennyiségű (item/mp, item/perc vagy item/óra) legyártásához szükséges gyártósor részleteit (gépek gráfja és recepteken keresztüli összeköttetése), valamint egy adott mennyiségű item(ek) (item/mp, item/perc vagy item/óra) bemenetből, mekkora a felhasználó által kiválasztott item, maximum legyártható (item/mp, item/perc vagy item/óra) kimenete, amennyiben van olyan item amit a felhasználó nem adott meg bemenetként, de kell a kimenet legyártásához, akkor az alkalmazásnak ezeket is ki kell tudnia számolni. A felhasználó semmilyen esetben nem választhat célként olyan item-et, amelyet csak bemenet nélküli recept állít elő, vagy amelyet egyetlen recept sem állít elő. Ha egy item-hez több eredetinek titulált recept is tartozik, akkor a felhasználónak kell eldönteni melyiket fogja használni.

Az alkalmazásnak támogatnia kell az alternatív recepteket, vizuálisan a felhasználónak ki kell tudnia választani, ha akarja, hogy melyik alternatív recept(et/eket) használja a gyártósor. Valamint, ha a felhasználó ki szeretné számoltatni pontosan egy általa kiválasztott item, maximum legyártható (item/mp, item/perc vagy item/óra) kimenetét, csak akkor kell lennie egy `auto` beállításnak, melyben a legtöbb kimenetet gyártó gyártósor tervét adja ki a felhasználónak, akár használva az alkalmazásban szereplő alternatív recepteket is, alapértelmezetten a gyártósor ne használjon alternatív recepteket. Alapértelmezett módban ha egy item csak alternatív recepteken keresztül gyártható le, akkor ezt jelezni kell a felhasználónak, aki eldöntheti, hogy az alternatív recepttel folytatja, vagy abbahagyja a gyártósor tervezését.

Az alkalmazásnak támogatnia kell azt, hogy egy receptet egy adott típusú gép tudjon legyártani, amely mentén a specifikus gépeket a felhasználó tudja kiválasztani grafikus módon, a gyártósorban szereplő recepteknél.

Az alkalmazásnak szintén támogatnia kell a rekurzív recepteket egy gyártósoron belül a mellékterméket generáló recepteknél, ahol a keletkező melléktermék szerepel a gyártósor bemenetében. A felhasználónak tudnia kell választani, hogy azt rekurzívan felhasználja-e a gyártósor, vagy melléktermék kimenetként jelenjen meg. Ha ez egy önfenntartó folyamat, akkor erről tájékoztatni kell a felhasználót, ha viszont ez a gyártósorban pozitív visszacsatolást eredményez (például ha egy recept csak egy fajta mellékterméket használ fel bemenetként, majd abból többet termel, mint amennyit felhasznál), akkor hibát kell jeleznie az alkalmazásnak, viszont ha más külső bemenet is van, akkor a többlet mellékterméket kimenetként kell azt jeleznie az alkalmazásnak.

A felhasználónak tudni kell a gyártósornak több bemenetet/kimenetet beállítania, valamint egy legenerált gyártósor tervét elmentenie, betöltenie.  

A gyártósornak a felhasználó által megadott bemenetekre és/vagy az adatbázisban szereplő bemenet nélküli receptekre kell visszavezethetőnek lennie. Ha ez nem teljesül, vagyis a választott kimenet(ek) nem gyártható(k) le, az alkalmazásnak hibát kell jeleznie. Ha maximum kimenet számításnál felhasználó által megadott bemenetek egyike sincs felhasználva a gyártósorban, az alkalmazásnak hibát kell jeleznie.

A szorzókat a következőképpen használja fel az alkalmazás: recept ideje / (a lehetséges gépek közül kiválasztott gép recept specifikus szorzója * a lehetséges gépek közül kiválasztott gép általános szorzója)

## Feladatspecifikáció és Adatstruktúrák

---

### Item

Példa reprezentáció:

```json
{
  "id": "9bfda7f7-77bc-492c-bb03-34490709c432",
  "name": "wood",
  "img": "link_to_img"
}

```

Mezők leírása:

- `id` *(string, UUID)*: Az item egyedi azonosítója.
- `name` *(string)*: Az item megjelenített neve.
- `img` *(string, URL)*: Az itemhez tartozó ikon vagy kép elérési útvonala/linkje.

---

### Gép

Egy speciális item, a példában az öröklődés nincs külön feltüntetve:

```json
{
  "id": "460a54c7-03c3-477d-b02c-f8efb86e9793",
  "name": "assembler mk1",
  "type": "assembler",
  "img": "link_to_img",
  "multiplier": 1.0
}

```

Mezők leírása:

- `id` *(string, UUID)*: A gép egyedi azonosítója.
- `name` *(string)*: A gép megnevezése (pl. "assembler mk1").
- `type` *(string)*: A gép kategóriája vagy típusa (pl. "assembler", "smelter", "constructor").
- `img` *(string, URL)*: A géphez tartozó kép elérési útvonala/linkje.
- `multiplier` *(float)*: A gép alapvető gyártási sebességének szorzója.

---

### Recept

A recept definiálja, hogy milyen alapanyagokból, mennyi idő alatt, milyen gépekkel, milyen vég- és melléktermékek jönnek létre.

Példa reprezentáció:

```json
{
  "id": "b66b93a7-e293-46f1-a5f8-00681cc9a5a0",
  "name": "wood planks",
  "time": 20.0,
  "img": "link_to_img",
  "is_original": true,
  "machines": [
    {
      "id": "460a54c7-03c3-477d-b02c-f8efb86e9793",
      "multiplier": 2.0
    },
    {
      "type": "assembler",
      "multiplier": 1.0
    }
  ],
  "inputs": [
    {
      "id": "9bfda7f7-77bc-492c-bb03-34490709c432",
      "piece": 1.0
    }
  ],
  "outputs": [
    {
      "id": "72af2e15-1568-4d65-a2e4-fc2c414aac5d",
      "piece": 5.0
    }
  ],
  "byproducts": [
    {
      "id": "2ad16410-a608-4ae6-9e27-1c250cf8a127",
      "piece": 2.0
    }
  ]
}

```

Mezők leírása:

- `id` *(string, UUID)*: A recept egyedi azonosítója.
- `name` *(string)*: A recept megnevezése.
- `time` *(float)*: A recept alapértelmezett végrehajtási ideje (másodpercben).
- `img` *(string, URL)*: A recepthez tartozó kép/ikon elérési útvonala (általában megegyezik az elsődleges output képével).
- `is_original` *(boolean)*: A receptről megmondja, hogy az eredeti-e, a felhasználó szerint, ezt csak ő választhatja ki
- `machines` *(array of objects)*: Azoknak a gépeknek vagy géptípusoknak a listája, amelyek képesek a recept legyártására.  
**Gépek specifikálási szabálya:** Egy listaelem vagy egy specifikus gépet jelöl az `id` mezővel, vagy egy generikus géptípust a `type` mezővel. **A specifikus gép beállítása (ha van `id`) minden esetben felülírja a típushoz megadott recept-specifikus szorzót.**
  - `id` *(string, UUID, opcionális)*: Egy specifikus gép egyedi azonosítója.
  - `type` *(string, opcionális)*: Generikus géptípus hivatkozása.
  - `multiplier` *(float)*: A recept specifikus gyártási sebesség-szorzója az adott gépen vagy géptípuson.

- `inputs` *(array of objects)*: A recepthez szükséges bemeneti elemek (alapanyagok) listája.
  - `id` *(string, UUID)*: A bemeneti item azonosítója.
  - `piece` *(float)*: A szükséges mennyiség egy gyártási ciklushoz.

- `outputs` *(array of objects)*: A recept által generált fő kimeneti elemek listája.
  - `id` *(string, UUID)*: A kimeneti item azonosítója.
  - `piece` *(float)*: A legyártott mennyiség egy ciklus alatt.

- `byproducts` *(array of objects, opcionális)*: A gyártás során keletkező melléktermékek listája.
  - `id` *(string, UUID)*: A melléktermék (item) azonosítója.
  - `piece` *(float)*: A keletkező melléktermék mennyisége egy ciklus alatt.