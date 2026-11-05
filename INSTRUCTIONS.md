# kubejs_ic2classic — руководство пользователя

Аддон, который открывает **все рецептурные эндпоинты IC2Classic 1.19.2** для скриптов **KubeJS**,
плюс добавляет **счётные крафты верстака** (N предметов в одном слоте) с корректным отображением и
переносом в JEI.

- **Minecraft:** 1.19.2 (Forge 43.x)
- **modId:** `kubejs_ic2classic`
- **jar:** `kubejs_ic2classic-1.19.2-0.1.0.jar`

---

## 1. Установка

В папку `mods/` (и на клиент, и на сервер) положить **обязательные** зависимости — без любой из них
IC2 или аддон не стартуют:

| Мод | Версия |
|---|---|
| Forge | 1.19.2 (43.x) |
| IC2Classic | `1.19.2-2.1.3.3` |
| CarbonConfig | `1.19.2-2.0.0` (жёсткая зависимость IC2) |
| KubeJS | `1902.6.2` |
| Rhino | `1902.2.3` |
| Architectury | `6.6.92` |
| **kubejs_ic2classic** (этот мод) | `1.19.2-0.1.0` |
| JEI | `11.8.1.1034` — **только клиент**, серверу не нужен |

Скрипты кладутся в `kubejs/server_scripts/` — рецепты применяются на загрузке мира и на каждом
`/reload`.

---

## 2. Быстрый старт

```js
// kubejs/server_scripts/my_ic2.js
IC2Events.recipes(event => {
    // Мацерировать булыжник в гравий
    event.macerator.add('minecraft:gravel', 'minecraft:cobblestone')

    // Счётный крафт: каждый слот честно требует 2 продвинутых сплава -> 4 пластины
    event.crafting.shaped('kubejs:plate_grid', Item.of('ic2:plate_advanced_alloy', 4),
        ['AAA', 'AAA', 'AAA'],
        { A: Item.of('ic2:ingot_advanced_alloy', 2) })
})
```

**Всё авторится внутри `IC2Events.recipes(event => { … })`.** Отдельных глобалов нет — это сделано
намеренно, чтобы рецепты корректно переприменялись при `/reload` (см. §7).

---

## 3. Форматы входов и выходов

Аддон конвертирует JS-значения в нужные IC2-типы, **сохраняя количество**.

**Входы** (`event.<машина>.add(...)`, ключи крафтов):
| Запись | Значение |
|---|---|
| `'minecraft:diamond'` | 1 предмет |
| `Item.of('minecraft:diamond', 4)` | **4 предмета в одном слоте** (честный count — то, чего не умеет ванильный KubeJS) |
| `'#forge:ores/gold'` | тег |
| `'4x minecraft:redstone'` | 4 предмета (строковый префикс `Nx`) |

**Выходы** — `Item.of(id, n)` тоже сохраняет count; поддерживается NBT через `Item.of(...).withNBT(...)`.

**Жидкости** (refinery, fluid fuels): `'minecraft:lava'` = 1000 mB, либо `Fluid.of('minecraft:lava', 500)`.

**id рецепта** — необязательный **первый** аргумент. Если не задан — генерируется автоматически из
выхода/входа. Явный id нужен для адресного `remove(...)` и идемпотентности.
> ⚠️ **Namespace крафтов верстака** (`event.crafting.*`) обязан быть **загруженным modId** — `kubejs:`
> или id твоего мода. Чужой namespace (напр. выдуманный `ic2test:`) IC2 **молча выбрасывает** —
> рецепт не появится. Машинных рецептов это НЕ касается (они идут мимо ванильного RecipeManager).

---

## 4. Машинные рецепты

### 4.1. Обычные машины
`macerator`, `extractor`, `compressor`, `sawmill`, `recycler`, `massFabricator`, `mixingFurnace`.

Общие методы у каждой:
```js
event.macerator.add(output, input)                 // простой рецепт
event.macerator.add('my:id', output, input)        // с явным id
event.macerator.addXp(output, xp, input)           // + опыт
event.macerator.addChance(output, chance, input)   // шанс выхода (0.0..1.0)
event.macerator.addRange(output, min, max, input)  // случайное кол-во min..max
event.macerator.remove('ic2:macerator/iron_ore')   // по точному id
event.macerator.removeByOutput('minecraft:sand')   // по выходному предмету
event.macerator.removeByInput('minecraft:cobblestone') // по входу
```

Примеры:
```js
event.compressor.addChance('minecraft:flint', 0.25, 'minecraft:gravel')
event.sawmill.addRange('minecraft:stick', 2, 4, 'minecraft:oak_planks')
event.extractor.add('minecraft:redstone_block', Item.of('minecraft:redstone', 4)) // 4 в слоте
```

**Особенности:**
- `mixingFurnace` — **ровно 2 входа**: `event.mixingFurnace.add('ic2:dust_bronze', 'ic2:dust_copper', 'ic2:dust_tin')`. Один вход = невалидный рецепт (будет отклонён, см. §7).
- `recycler` — берётся **первый** подходящий рецепт; чтобы заменить дефолт IC2, сначала
  `removeByInput(...)`, потом `addChance(...)`.
- **Замена рецепта** = `removeByInput`/`removeByOutput`/`remove` + `add` в том же эвенте.

### 4.2. Электропечь / доменная печь / коптильня IC2 — НЕТ прямого API
Это **read-only зеркала** ванильных cooking-рецептов. `event.furnace/blastFurnace/smoker`
намеренно отсутствуют. Чтобы добавить рецепт, который появится в этих машинах, используй
`event.crafting.smelting(out, in, 'furnace' | 'blast_furnace' | 'smoker')` (§6) — IC2 подхватит его
из ванильного RecipeManager.

---

## 5. Специальные реестры

```js
// ЭЛЕКТРОЛИЗЁР: (input, output, energyEU) — charge / discharge / dual
event.electrolyzer.addCharge('minecraft:apple', 'minecraft:golden_apple', 12000)
event.electrolyzer.addDischarge('minecraft:golden_apple', 'minecraft:apple', 6000)
event.electrolyzer.addDual('minecraft:magma_cream', 'minecraft:blaze_powder', 8000)

// SCRAP BOX: (item, weight) — ВЕС в пуле выпадения, НЕ вероятность. Мелкий вес ≈ никогда.
event.scrapbox.addDrop('minecraft:diamond', 1000)
event.scrapbox.removeDrop('minecraft:diamond')

// FUSION: (item, fuelValueEU, productionRate, consumed?)
event.fusion.addFuel('minecraft:blaze_rod', 40000, 1.0, true)
event.fusion.removeFuel('minecraft:blaze_rod')

// UU-MATTER: (item, uuNeeded)
event.uuMatter.addEntry('minecraft:diamond', 166000)
event.uuMatter.removeByOutput('minecraft:diamond')

// CANNER
event.canner.addFuel('minecraft:coal', 8)                              // топливная ценность
event.canner.addFuelMultiplier('minecraft:lava_bucket', 2.0)          // множитель
event.canner.addFillable('ic2:cell_empty', 'minecraft:water_bucket', 'ic2:cell_water') // наполнение
event.canner.removeFuel(...) / removeFillable(...)

// FLUID FUELS: (fluid, ticksPerBucket, euPerTick) — топливо жидкостного генератора
event.fluidFuels.addFuel('minecraft:lava', 1000, 4)
event.fluidFuels.removeFuel('minecraft:lava')

// REFINERY: fluent-билдер (2 жидкости + опц. катализатор -> жидкость и/или предмет)
event.refining.add('kubejs:water_to_lava')
    .firstFluid('minecraft:water', 1000)
    .secondFluid('minecraft:water', 1000)   // опционально
    .catalyst('minecraft:redstone')         // опционально
    .fluidOutput('minecraft:lava', 100)
    .itemOutput('minecraft:blaze_powder')
    .build()

// RARE EARTH (центрифуга): пул выходов + взвешенные входы
event.rareEarth.addOutput('minecraft:emerald')
event.rareEarth.addInput(1.0, 'minecraft:prismarine_shard')  // (вес, ...предметы)
event.rareEarth.removeOutput(...) / removeInput(...)

// POTION BREWING (варка зелий IC2): (ingredient, effect[, secondEffect])
event.potionBrewing.addBrew('minecraft:golden_carrot', 'minecraft:night_vision')
event.potionBrewing.addBrew('minecraft:pufferfish', 'minecraft:water_breathing', 'minecraft:poison')
event.potionBrewing.removeBrew(...)
```

---

## 6. Замена предмета/жидкости везде + правка диапазона

```js
// substitute(from, to) — проходит по ВСЕМ уже зарегистрированным рецептам (включая дефолтные
// рецепты самого IC2) в: macerator, extractor, compressor, sawmill, massFabricator, mixingFurnace,
// recycler, refining и fluidFuels — и там, где встречается "from" (как предмет ИЛИ как жидкость),
// меняет его на "to". Всё остальное на рецепте (прочие входы, тип выхода fixed/chance/range, сам
// диапазон, XP, NBT) остаётся без изменений.
event.substitute('ic2:bio_fuel', 'industrialforegoing:biofuel')
```

**Не покрывается** `substitute()` (у каждого своя форма рецепта — правь через методы §5):
electrolyzer, scrapbox, fusion, uuMatter, canner, rareEarth, potionBrewing, crafting.
Катализаторы **по тегу** (`#forge:ores/iron` и т.п.) никогда не переписываются — замена одного
предмета не должна тайком сужать список того, что тег принимает.

Правка случайного разброса выхода **существующего** рецепта — без пересоздания рецепта:
```js
event.macerator.setRange('ic2:macerator/some_recipe', 2, 5)
event.refining.setItemRange('ic2:iron_to_raw_advanced', 4, 12)
event.refining.setFluidRange('kubejs:refining/my_recipe', 200, 800)
```
Готовый пример под замену топлива — `examples/server_scripts/fuel_substitute.js`.

> ⚠️ Эта фича (v0.1.0+) прошла компиляцию и адверсариал-ревью, но ещё не тестировалась кликом в
> живой игре — перед продакшеном стоит проверить в `runClient`/на тест-мире.

---

## 7. Счётные крафты верстака ← ключевая фича

Идут через IC2 `IInput`, поэтому count в **одном слоте** честный: `Item.of(id, 4)` реально требует 4
предмета (ванильный `event.shaped` так не умеет — там count схлопывается в 1).

```js
// SHAPED: паттерн + ключи. Каждый слот 'A' требует 2 продвинутых сплава -> 4 пластины.
event.crafting.shaped('kubejs:plate_grid', Item.of('ic2:plate_advanced_alloy', 4),
    ['AAA', 'AAA', 'AAA'],
    { A: Item.of('ic2:ingot_advanced_alloy', 2) })

// SHAPELESS: входы массивом или varargs. Count честен и на входе, и на выходе.
event.crafting.shapeless(Item.of('minecraft:diamond', 2), ['minecraft:diamond_block'])
event.crafting.shapeless('minecraft:blaze_rod', Item.of('minecraft:stick', 4))  // 4 палки в слоте

// SMELTING: тип furnace(деф.) | blast_furnace | smoker | campfire; опц. xp, время.
event.crafting.smelting('kubejs:smelt_blast', 'ic2:ingot_refined_iron', 'minecraft:iron_ingot', 'blast_furnace')
event.crafting.smelting('kubejs:smelt_cf', 'minecraft:cooked_cod', 'minecraft:cod', 'campfire', 0.5, 200)

// STONECUTTER
event.crafting.stonecutter('kubejs:cut', Item.of('minecraft:stone_bricks', 4), 'minecraft:stone')

// SMITHING: (id?, output, base, addition)
event.crafting.smithing('kubejs:smith_hoe', 'minecraft:netherite_hoe', 'minecraft:diamond_hoe', 'minecraft:netherite_ingot')

// Удаление
event.crafting.remove('ic2:some_id')
event.crafting.removeSmelting('id', 'furnace')  // + removeStonecutter, removeSmithing
```

**JEI:** count-крафты остаются в **ванильной** категории Crafting, но показывают правильное **×N** в
слотах, а кнопка переноса **«+»** заполняет сетку по **N на слот** (а не по 1). Не-IC2 ванильные
крафты не затронуты — переносятся как обычно.

> `smelting/stonecutter/smithing` — одиночный вход, count там нерелевантен; их вход должен быть
> ванильной формы (предмет / `#tag`), не count-стек.

---

## 8. Как это работает под капотом (важно для отладки)

- **Deferred-apply.** Эвент только **записывает** операции; применяются они из IC2-листенера
  **после** того, как IC2 нагрузит свои рецепты из датапака. Иначе KubeJS-reload шёл бы раньше и всё
  затирал. Поэтому не пытайся вызывать API вне `IC2Events.recipes(...)`.
- **Идемпотентность.** Каждый `add` делает `remove(id)` перед добавлением → `/reload` не плодит
  дубли. Размер списков стабилен между reload.
- **Изоляция ошибок.** Один невалидный рецепт **не роняет** весь reload. В логе будет пара:
  ```
  [ERROR] mixingFurnace: a recipe op was rejected by IC2 and skipped — Recipe Is Invalid: <id>
  [INFO]  mixingFurnace: applied +N/-M op(s), 1 rejected by IC2
  ```
  Это **штатное** поведение — остальные рецепты применяются, сервер стоит.

**Здоровый лог при старте:**
```
registered IC2 reload listeners (7 machines + 8 special + crafting + refining)
<машина>: applied +N/-M op(s)      ← по строке на каждый использованный реестр
network channel registered          ← канал переноса сетки крафта
```

---

## 9. Частые проблемы

| Симптом | Причина / решение |
|---|---|
| Крафт верстака не появился в JEI/игре | Namespace id не равен загруженному modId. Используй `kubejs:` или свой мод (§3). |
| `Recipe Is Invalid` в логе | Невалидный рецепт (напр. mixingFurnace с 1 входом). Штатно пропускается (§8). |
| `Input has to be either a [Stack/…]` | В `smelting/stonecutter/smithing` передан count-стек. Дай ванильный вход (предмет/тег). |
| `looksLikeId` спутал вход с id | Первый строковый аргумент выглядит как `ns:path` незарегистрированного предмета — аддон принял его за recipe-id. Используй реальные id (IC2: префиксы `ingot_`/`plate_`/`dust_`…) или задавай id явно. |
| addChance даёт 0% | Порядок аргументов: `addChance(output, chance, input)`. |
| Рецепт не редактируется у электропечи/домны/коптильни IC2 | Это зеркала ванили — авторь через `event.crafting.smelting(...)` (§4.2). |
| `substitute()` не тронул рецепт | Реестр не входит в список поддерживаемых (§6), либо предмет стоит за тегом (`#forge:...`) — теги никогда не переписываются. |

---

## 10. Полный пример

См. `examples/server_scripts/ic2_recipes_example.js` (обзор всех API) и `examples/tests/*.js`
(тест-наборы по каждой машине/реестру/крафту с тегами `T-xxx`).
