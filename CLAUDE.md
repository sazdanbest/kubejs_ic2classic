# IC2Classic × KubeJS — аддон (Project Guide)

> Этот файл — рабочая память проекта. Он написан, чтобы сессию можно было очистить и продолжить с нуля.

## Цель

Собрать **Forge-мод (аддон)**, который делает все рецептурные эндпоинты **IC2Classic 1.19.2** удобно доступными из **KubeJS**-скриптов — по образцу `kubejs-thermal` (референс-jar лежит в корне). Планируется **публичный релиз** (CurseForge/Modrinth). **JEI-поддержка обязательна.**

Две главные ценности аддона:
1. Скриптовый `add/remove/replace` для **рецептов машин** IC2 (мацератор, экстрактор, компрессор, электролизёр, каннер, recycler, sawmill, mass fab, fusion и т.д.) — KubeJS нативно их не видит.
2. **Счётные крафты верстака** (N предметов в одном слоте) + **фикс JEI** (кнопка «+» переносит по 1, число в слоте не показывается).

Механизмы/провода/новые блоки — вне первого релиза (это полноценная Java-разработка, не скриптинг).

---

## Окружение и версии

**Установлено:** JDK 25 (Temurin), IntelliJ IDEA 2026.1 (+ поставить плагин *Minecraft Development*), git, Node.
**Доставить обязательно:** **JDK 17** (Temurin) — Forge 1.19.2 не работает на JDK 25; Project SDK / Gradle toolchain = 17.

**Стек зависимостей (jar-ы лежат в корне проекта):**
| Мод | Версия | modId |
|---|---|---|
| Forge | 1.19.2 (recommended `43.x`, min `[40.1.0,)`) | `forge` |
| IC2Classic | `1.19.2-2.1.3.3` | `ic2` |
| KubeJS | `1902.6.2-build.73` | `kubejs` |
| Rhino | `1902.2.3-build.284` | `rhino` |
| Architectury | **≥ 6.5.82 — jar ещё НЕ добавлен в корень** | `architectury` |
| JEI | `1.19.2-forge-11.8.1.1034` | `jei` |

Исходников IC2 нет — только jar + распакованные `.class` в `./ic2/`. Для компиляции нужен только пакет **`ic2.api.*`** (читается через `javap`); в рантайме — реальный jar.

---

## Как устроены рецепты IC2 (reverse-engineered, проверено javap)

### Машинные рецепты
- **НЕ в ванильном `RecipeManager`.** Хранятся в `ic2.core.platform.recipes.misc.GlobalRecipes`, доступны через публичный API `ic2.api.recipes.RecipeRegistry` — статические `SidedObject<IMachineRecipeList>`: `MACERATOR, EXTRACTOR, COMPRESSOR, RECYCLER, SAWMILL, CANNER, ELECTROLYZER, MASS_FABRICATOR, FUSION_REACTOR, FURNACE, BLAST_FURNACE, SMOKER, MIXING_FURNACE, SCRAP_BOX, UU_SHAPES, RARE_EARTH, POTION_BREWING, FLUID_FUELS`.
- `IMachineRecipeList`: `addRecipe / addSimpleRecipe / addChanceRecipe / addRangeRecipe / addXPRecipe / removeRecipe(ResourceLocation) / getAllRecipes / getAllEntries`.
- Спец-интерфейсы: `IElectrolyzerRecipeList`, `IFusionRecipeList`, `ICannerRecipeRegistry`, `IRecyclerRecipeList`, `IRefiningRecipeList`, `IScrapBoxRegistry`, `IUUMatterRegistry` — им нужны отдельные обёртки.
- **Reload-hook:** `IListenableRegistry.registerListener(Consumer<T>)` есть на каждом списке — переприменять скрипты при reload.
- **Датапак-путь (без аддона):** `data/<ns>/ic2_recipes/<машина>/<имя>.json` (читает `IC2RecipeLoader extends SimpleJsonResourceReloadListener`, папка `ic2_recipes`). Заморочно, без JS-логики — поэтому нужен аддон.
- **JEI бесплатно:** `ic2.jeiplugin.core.JEIPlugin` строит категории из тех же `GlobalRecipes.macerator.getAllEntries()` и т.д. → любой рецепт, добавленный через API, **сам появляется в JEI**.

### Крафты верстака — ДВА слоя (ключевой нюанс)
Рецепт: `RecipeIC2Base implements CraftingRecipe`, сериализатор `ic2:crafting_ic2_automated`, тип `minecraft:crafting`.
- **Реальный слой (IC2):** `IInput` несёт **количество** — `ItemInput.size`, `getInputSize()`, `getComponents()` → `ItemStack(item, N)`. Матчинг (`matches`) и сборка используют его.
- **Ванильный слой (lossy):** `ShapedIC2Recipe.m_7527_()` (`getIngredients`) заполняется `IInput.asIngredient()`, а у ванильного `Ingredient` **count нет** → N схлопывается в 1.

**Последствия:**
- KubeJS видит рецепт и может его **удалить** (`event.remove({mod:'ic2'})`), но **не может добавить счётный крафт** нативным `event.shaped` (ванильный `Ingredient` без count).
- **Авторинг счётных крафтов** — только через `RecipeRegistry.CRAFTING` = `IAdvancedCraftingManager`: `addShapedRecipe / addShapelessRecipe / removeCraftingRecipe`, а также `addSmeltingRecipe / addStoneCutterRecipe / addSmithingRecipe / addRepairRecipe`. Его `Object...` идёт через `IInput` (count сохраняется).
- **Баг JEI:** кнопка «+» в ванильном верстаке переносит по 1 (дефолт JEI читает lossy-слой). IC2 уже сделал count-aware `IRecipeTransferHandler` **только для своих контейнеров** (`ic2.jeiplugin.core.transferhandler.IndustrialWorkbenchTransferHandler` / `CraftingUpgradeTransferHandler`), но не для ванильного `CraftingMenu`. → это образец для нашего фикса.

### Прочее
- Официальный addon-API: `ic2.api.addons.IC2Plugin` / `IModule`.
- Синхронизация рецептов клиент↔сервер: `RecipeUpdatePacket`.
- Энергосеть/провода (на будущее): `ic2.api.energy.*` — `IEnergySink/Source/Conductor`.

---

## Архитектура аддона

```
Мод kubejs_ic2 (Forge, javafml)
├── kubejs.plugins.txt  → KubeJSIC2Plugin
├── KubeJSIC2Plugin extends KubeJSPlugin
│    ├── registerBindings()      → глобалы IC2Recipes, IC2Crafting
│    ├── registerEvents()        → EventGroup "IC2Events" (recipes handler)
│    ├── registerTypeWrappers()  → JS → IInput / IRecipeOutput / ItemStack(count)
│    └── onServerReload()        → переприменение + хук IC2 registerListener
├── обёртки над RecipeRegistry.* (машины) → addRecipe/removeRecipe
├── обёртка над IAdvancedCraftingManager (счётные крафты)
└── JEI-модуль (client): своя категория для IC2-крафтов (Вариант А) + transfer handler
```

**Подтверждённые хуки `KubeJSPlugin` (в 1902.6.2):** `registerBindings(BindingsEvent)`, `registerEvents()`, `registerTypeWrappers(ScriptType, TypeWrappers)`, `onServerReload()`, `registerClasses(...)`. Кастомные события — через `dev.latvian.mods.kubejs.event.EventGroup` + `EventHandler` (классы присутствуют в jar).

**Целевой синтаксис скриптов:**
```js
// server_scripts
IC2Events.recipes(event => {
  event.macerator.remove('ic2:macerator/iron_ore')
  event.macerator.add('kjs:diamond_dust', Item.of('minecraft:diamond', 2), '#forge:gems/diamond')
  event.electrolyzer.add(...)
})

IC2Crafting.shaped('ic2:advanced_machine', ['PPP','PMP','PPP'], {
  P: Item.of('ic2:advanced_alloy', 4),  // 4 в одном слоте — честно, через IInput
  M: 'ic2:machine'
})
```

**JEI — Вариант А (выбран):** IC2-крафты выводить в **отдельную JEI-категорию** нашего аддона; слоты наполнять из `ShapedIC2Recipe.inputs[i].getComponents()` (там есть count) → корректное число + собственный count-aware transfer handler под полным контролем. Общая ванильная «Crafting» остаётся дефолтной.

---

## План разработки

### Фаза 0 — Тулчейн
- [ ] Поставить **JDK 17** (Temurin). В IDEA — плагин *Minecraft Development*.
- [ ] Acceptance: `java -version` есть; IDEA видит JDK 17.

### Фаза 1 — Dev-workspace (Forge MDK 1.19.2)
- [ ] Forge 1.19.2 MDK (`43.x`) в новую папку (например `D:\Unspace\Projects\kubejs-ic2\`).
- [ ] `build.gradle`: mappings (parchment/official), Java toolchain 17.
- [ ] `./gradlew genIntellijRuns`, импорт в IDEA, Project SDK = 17.
- [ ] Acceptance: `./gradlew build` проходит на пустом MDK, `runClient` стартует.

### Фаза 2 — Зависимости
- [ ] IC2: `compileOnly files('libs/IC2Classic-1.19.2-2.1.3.3.jar')` (нужен только `ic2.api.*`).
- [ ] KubeJS/Rhino/Architectury/JEI: через CurseMaven **или** локальные jar из корня. Версии — см. таблицу. **Добавить Architectury ≥6.5.82** (его jar ещё нет).
- [ ] В `run/mods/`: реальные jar (IC2 + KubeJS + Rhino + Architectury + JEI) для запуска с настоящим IC2.
- [ ] Acceptance: проект компилируется против `ic2.api.*` и KubeJS API; `runClient` поднимается с IC2+KubeJS+JEI.

### Фаза 3 — Каркас плагина
- [ ] `META-INF/mods.toml`: `modId=kubejs_ic2`, зависимости `kubejs`(mandatory), `ic2`(mandatory), `jei`(optional), `ordering=AFTER`.
- [ ] `kubejs.plugins.txt` → FQN `KubeJSIC2Plugin`.
- [ ] `KubeJSIC2Plugin extends KubeJSPlugin` с пустыми хуками + `registerBindings` → `IC2Recipes`.
- [ ] Acceptance: в игре `console.log(IC2Recipes)` не падает.

### Фаза 4 — Машинные рецепты (ядро)
- [ ] TypeWrappers: JS → `IInput` (`ItemInput`/`ItemTagInput`/`ItemStackInput`/`FluidInput`) с учётом count; JS → `IRecipeOutput` (`SimpleRecipeOutput`/`ChanceRecipeOutput`/`RangeRecipeOutput`, XP, NBT).
- [ ] `EventGroup "IC2Events"` + `event.<machine>.add/remove` для всех `RecipeRegistry.*` списков.
- [ ] Спец-обёртки: electrolyzer, fusion, canner, recycler, scrapbox, uu, refining.
- [ ] Reload-safe: подписка на IC2 `registerListener` + `onServerReload` → переприменение.
- [ ] Acceptance: add/remove мацератора виден в игре и в JEI; `/reload` не плодит дубли.

### Фаза 5 — Счётные крафты верстака
- [ ] Обёртка `IC2Crafting` над `IAdvancedCraftingManager`: `shaped/shapeless/remove` + smelting/stonecutter/smithing/repair, с сохранением count из `Item.of(id, N)`.
- [ ] Acceptance: крафт с `Item.of('...',4)` реально требует 4 в слоте в верстаке.

### Фаза 6 — JEI (Вариант А)
- [ ] Клиентский `IModPlugin`: своя `IRecipeCategory` для IC2-крафтов; слоты из `getComponents()` (с count).
- [ ] `IRecipeTransferHandler` для `CraftingMenu` + тип `minecraft:crafting`: если `recipe instanceof RecipeIC2Base` → переносить `getInputSize()` предметов в слот; иначе — обычное поведение. Образец: `IndustrialWorkbenchTransferHandler`.
- [ ] Регистрация рецептов в свою категорию (источник — `RecipeRegistry.CRAFTING`/скан по сериализатору `ic2:crafting_ic2_automated`).
- [ ] Acceptance: число в слоте корректное; «+» переносит N; не-IC2 крафты не сломаны.

### Фаза 7 — Тесты и релиз
- [ ] Функц.: add/remove/replace, chance/range/XP/NBT-выходы, счётные крафты, `/reload`.
- [ ] Регресс: нетронутые IC2-рецепты целы; клиент↔сервер (`RecipeUpdatePacket`); сейв/лоад.
- [ ] Совместимость: «чистый» набор и «толстый» модпак.
- [ ] Документация (README + примеры скриптов), лицензия, `mods.toml` метаданные, публикация.

---

## Риски / открытые вопросы
1. **Стыковка версий KubeJS-стека** — проверяется быстрым `runClient` (jar-ы уже есть, кроме Architectury).
2. **JEI-категория vs общая Crafting** — выбран Вариант А (отдельная категория): корректно, но IC2-крафты не в общей вкладке.
3. **Transfer handler для CraftingMenu** — наш handler заменяет дефолт для типа crafting; для не-IC2 рецептов обязательно сохранить обычное поведение.
4. **Синхронизация reload** — заложена (IC2 listener + `RecipeUpdatePacket`), но требует явного теста на выделенном сервере.

## Файлы в корне (справка)
- `IC2Classic-1.19.2-2.1.3.3.jar` + распакованные `./ic2/**.class` (только байткод).
- `kubejs-forge-1902.6.2-build.73.jar`, `rhino-forge-1902.2.3-build.284.jar`, `jei-1.19.2-forge-11.8.1.1034.jar`.
- `kubejs-thermal-1902.1.9-build.16.jar` — референс (структура плагина, `kubejs.plugins.txt`, RecipeSchema — но для IC2 схема НЕ применима, т.к. рецепты не в RecipeManager).
