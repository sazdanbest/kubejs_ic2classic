# IC2Classic × KubeJS — аддон (Project Guide)

> Этот файл — рабочая память проекта. Он написан, чтобы сессию можно было очистить и продолжить с нуля.

## Цель

Собрать **Forge-мод (аддон)**, который делает все рецептурные эндпоинты **IC2Classic 1.19.2** удобно доступными из **KubeJS**-скриптов — по образцу `kubejs-thermal` (референс-jar лежит в корне). Планируется **публичный релиз** (CurseForge/Modrinth). **JEI-поддержка обязательна.**

Две главные ценности аддона:
1. Скриптовый `add/remove/replace` для **рецептов машин** IC2 (мацератор, экстрактор, компрессор, электролизёр, каннер, recycler, sawmill, mass fab, fusion и т.д.) — KubeJS нативно их не видит.
2. **Счётные крафты верстака** (N предметов в одном слоте) + **фикс JEI** (кнопка «+» переносит по 1, число в слоте не показывается).

Механизмы/провода/новые блоки — вне первого релиза (это полноценная Java-разработка, не скриптинг).

---

## Текущий статус (обновлено 2026-07-04)

**Готово: Фазы 0–6 + Часть 1 Фазы 7 (локальный клиент, зелёная). Собран тест-jar. Дальше — Часть 2 Фазы 7 (реальный выделенный сервер), затем Фаза 7 D (релиз). Подробности и 4 находки/фикса — в журнале за 2026-07-04 ниже.** ⚠️ Правка к Фазе 4: `event.furnace/blastFurnace/smoker` УДАЛЕНЫ (это зеркала ванильных cooking-рецептов, не редактируются через machine-API; см. журнал).

Сделано в этой сессии:
- Поставлен **JDK 17** (Temurin, winget). Gradle обязан идти на 17 (см. ниже).
- Скелет Forge-мода написан вручную **прямо в этой папке** (`ic2classic_sazdanFix`): `build.gradle` (ForgeGradle 6.0), `settings.gradle`, `gradle.properties`, wrapper Gradle **8.1.1**, `gradlew(.bat)`.
- Идентичность: **modId `kubejs_ic2classic`**, пакет `com.sazdan.kubejsic2classic`. Классы `KubeJSIC2Classic` (@Mod) + `KubeJSIC2ClassicPlugin extends KubeJSPlugin` (пока smoke-test биндинг `IC2Recipes`; сигнатуры KubeJS `event.add(String,Object)`/`getType()` сверены `javap`).
- `mods.toml` (deps forge/minecraft/kubejs/ic2 mandatory, jei optional), `pack.mcmeta`, `kubejs.plugins.txt`.
- Реорг файлов: все jar-ы → `libs/`, распакованный IC2 → `reference/ic2-extracted/` (оба в `.gitignore`).
- **git** инициализирован (`main`), коммиты есть; `.gitattributes` (LF для `gradlew`).
- **Проверено на JDK 17:** `./gradlew tasks` и `./gradlew compileJava` → BUILD SUCCESSFUL (код компилируется против реального KubeJS API).

**Фаза 2 (зависимости) — ПОЛНОСТЬЮ СДЕЛАНА, runClient работает (2026-07-03):**
- Зависимости переведены на **Maven/flatDir-координаты** с рабочим `fg.deobf` (чинит dev-маппинги; `files(...)` был no-op):
  - Architectury `6.6.92` ← `maven.architectury.dev` (для 1.19.2 доступны 6.5.82/6.5.85/6.5.90/6.6.92).
  - KubeJS `1902.6.2-build.73` + Rhino `1902.2.3-build.284` ← `maven.saps.dev/releases` (`dev.latvian.mods`).
  - JEI `11.8.1.1034` ← `maven.blamejared.com` (`mezz.jei:jei-1.19.2-forge`).
  - **IC2** ← локальный `libs/` через **module-нотацию** `fg.deobf("ic2:IC2Classic:1.19.2-2.1.3.3")` (не `files()`!) → реально деобфусцируется, грузится в рантайме. `implementation` (нужен и в компайле, и в рантайме).
  - **CarbonConfig `1.19.2-2.0.0`** ← CurseMaven (`curse.maven:carbon-config-898104:7842759`) — **обязательная зависимость IC2** (без неё IC2 не грузится).
  - Версии — в `gradle.properties`. `ic2_version_range` исправлен на `[1.19.2-2.1.3.3,)` (реальная версия IC2 `1.19.2-2.1.3.3` не попадала в старый `[2.1,)`).
- **Фикс миксинов KubeJS в dev:** в run-конфигах заданы `mixin.env.remapRefMap=true` + `mixin.env.refMapRemappingFile=build/createSrgToMcp/output.srg` — иначе прод-refmap (SRG-имена типа `m_5705_`) не резолвится в `official`-маппинге dev, и KubeJS падает на APPLY.
- **Проверено на JDK 17:**
  - `./gradlew build` → BUILD SUCCESSFUL, jar в `build/libs/`.
  - **`./gradlew runClient` → ПОЛНАЯ ЗАГРУЗКА до главного меню.** IC2 + KubeJS + Rhino + Architectury + JEI + CarbonConfig + наш мод грузятся без крашей (лог: `OpenAL initialized`, `Sound engine started`, IC2 `Search Tree`, JEI-атлас). **KubeJS находит наш плагин: `[KubeJS] Found plugin source kubejs_ic2classic`** → это и acceptance Фазы 3.

⚠️ **`runServer` (dedicated) в dev НЕ поднимается** — и IC2, и KubeJS падают на CONSTRUCT именно на серверном dist: KubeJS тянет клиентский класс `BlockColor` (RuntimeDistCleaner: "invalid dist DEDICATED_SERVER"), IC2 — NPE в `IC2Config.save` (`getCurrentServer()==null`). Это **dev-userdev-специфика**, не наш баг (в проде обе моды на серверах работают). Вывод: **в dev тестируем через `runClient`**; тест выделенного сервера (Фаза 7) — только на реальном прод-наборе jar, не через `runServer`.

**Фаза 4 (машинные рецепты) — ✅ ПОЛНОСТЬЮ СДЕЛАНА И ПРОВЕРЕНА В РАНТАЙМЕ (2026-07-03).** `IC2Events.recipes(event => …)` покрывает ВСЕ рецептурные реестры IC2 (10 машин + electrolyzer/scrapbox/fusion/uuMatter/canner/fluidFuels/rareEarth/potionBrewing + refining), плюс фундамент под флюиды. Ключевой механизм — **deferred-apply**: эвент записывает операции, применяются они из IC2 `registerListener` *после* датапак-загрузки IC2 (иначе KubeJS `onServerReload` идёт раньше и всё затирается). Рантайм: `registered IC2 reload listeners (10 machines + 8 special + refining)` + 19× `applied`, `/reload` без дублей. Детали и файлы — в разделе «Фаза 4» ниже.

**Фаза 5 (счётные крафты верстака) — ✅ СДЕЛАНА И ПРОВЕРЕНА В РАНТАЙМЕ (2026-07-03).** Добавлен `event.crafting` (внутри `IC2Events.recipes`, НЕ отдельный глобал — reload-safe через тот же deferred-apply): `shaped/shapeless` (count честно сохраняется через IC2 `IInput`), `smelting/stonecutter/smithing` (+ removes). Backend — `RecipeRegistry.CRAFTING` (`IAdvancedCraftingManager`, единичный). Рантайм: `crafting: applied +5/-0`, без parse-ошибок ванильного RecipeManager. Ключевой баг (IInput ok для shaped/shapeless, но cooking/single/smithing требуют ванильную форму `ItemStack/Tag/Ingredient`) найден и закрыт `IC2Conversions.toVanillaInput`. Детали — в разделе «Фаза 5».

**Фаза 6 (JEI count-крафты) — ✅ КОД ГОТОВ, ЧИСТО ГРУЗИТСЯ В РАНТАЙМЕ (2026-07-03).** Уточнение к плану: вместо отдельной JEI-категории (док. «Вариант А») выбран **строго лучший** идиоматичный путь — `ICraftingCategoryExtension` (крафты остаются в ванильной Crafting-категории, без дублей, с честным count из `IInput.getComponents()`). Перенос «+» чинится своим Forge `SimpleChannel`-пакетом (`FillCraftingGridPacket`): JEI-дефолт двигает ровно 1/слот (`RecipeTransferUtil` → `matchingStack.m_41774_(1)`), повторно использовать его count-aware нельзя; IC2-handler `IndustrialWorkbenchTransferHandler` — только для СВОих контейнеров/сети. Наш `IC2CraftingTransferHandler` регистрируется на `(CraftingMenu, minecraft:crafting)` и **wrap-and-delegate**: IC2-крафт (`RecipeIC2Base`) → наш пакет; всё остальное → дефолтный JEI-handler (`createUnregisteredRecipeTransferHandler`) — Risk #3 закрыт. Рантайм: наш плагин прошёл все фазы JEI (`Building recipe registry`, `Starting JEI took 1.708s`, `Registering recipes transfer handlers: kubejs_ic2classic:jei` — ПОСЛЕ `jei:minecraft`, т.е. перекрывает встроенный, без warning-конфликтов), сеть `network channel registered`, крафты применились. **Остался только интерактивный клик-тест** (число в слоте + «+» переносит N + не-IC2 крафты целы) — по природе визуальный. Детали — в разделе «Фаза 6».

Что дальше (не сделано):
- IDEA: импорт как Gradle-проект, Gradle JVM = 17, `genIntellijRuns`, плагин *Minecraft Development*.
- **Клик-тест Фазы 6** (ручной, в игре): открыть JEI на count-крафте `ic2:ingot_refined_iron` (P=4 advanced alloy) → проверить «×4» в слотах; поставить верстак 3×3, нажать кнопку переноса → должно класть 4/слот; ванильный крафт (факелы) переносится как обычно.
- **Фазы 4, 5 и 6 (код) — ✅ ЗАКРЫТЫ.** Следующий содержательный шаг — **Фаза 7** (тесты/регресс/релиз).

**Как собрать/запустить из консоли:**
```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot" ./gradlew build
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot" ./gradlew runClient   # dev-тест
```

---

## Окружение и версии

**Установлено:** JDK 25 + **JDK 17** (Temurin, `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`), IntelliJ IDEA 2026.1 (+ поставить плагин *Minecraft Development*), git, Node.
**ВАЖНО про запуск Gradle:** сам Gradle-демон должен стартовать на **JDK 17** (Forge 1.19.2 / ForgeGradle 6 не идут на JDK 25). В консоли — `JAVA_HOME=...jdk-17...` перед `./gradlew`; в IDEA — *Gradle JVM = 17*, Project SDK = 17.

**Стек зависимостей (jar-ы лежат в корне проекта):**
| Мод | Версия | modId |
|---|---|---|
| Forge | 1.19.2 (recommended `43.x`, min `[40.1.0,)`) | `forge` |
| IC2Classic | `1.19.2-2.1.3.3` | `ic2` |
| KubeJS | `1902.6.2-build.73` | `kubejs` |
| Rhino | `1902.2.3-build.284` | `rhino` |
| Architectury | `6.6.92` (maven.architectury.dev; для 1.19.2: 6.5.82..6.6.92) | `architectury` |
| JEI | `1.19.2-forge-11.8.1.1034` | `jei` |

> Начиная с Фазы 2, KubeJS/Rhino/Architectury/JEI тянутся из **Maven** (см. `build.gradle`/`gradle.properties`), а не из локальных jar. Локальным `compileOnly` остаётся только IC2. Реф-jar-ы в `libs/` больше не на build-класспасе (кроме IC2).

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
Мод kubejs_ic2classic (Forge, javafml, пакет com.sazdan.kubejsic2classic)
├── kubejs.plugins.txt  → KubeJSIC2ClassicPlugin
├── KubeJSIC2ClassicPlugin extends KubeJSPlugin
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
- [x] **JDK 17** (Temurin) поставлен через winget. В IDEA — доставить плагин *Minecraft Development* (руками).
- [x] Acceptance: `java -version` для JDK 17 ok.

### Фаза 1 — Dev-workspace (Forge MDK 1.19.2) — СКЕЛЕТ СОБРАН В ЭТОЙ ЖЕ ПАПКЕ
Решение: проект живёт прямо в `ic2classic_sazdanFix` (совпадает с конвеншеном аддонов; jar-ы/CLAUDE.md/память уже тут), а не в отдельной папке.
- [x] Скелет по образцу Forge 1.19.2 MDK, но написан вручную: `build.gradle` (**ForgeGradle 6.0**, Gradle wrapper **8.1.1**), `settings.gradle`, `gradle.properties`, `gradlew`.
- [x] `build.gradle`: mappings **official** (parchment опционально позже, требует Librarian-плагин), Java toolchain 17, runs client/server/data, `processResources` с подстановкой в mods.toml.
- [x] Реорг: реф-jar-ы → `libs/` (в .gitignore), распакованный IC2 → `reference/ic2-extracted/` (в .gitignore, там же `ic2/api/**` для `javap`).
- [x] git инициализирован (`main`), первый коммит сделан. Идентичность: sazdanbest.
- [ ] IDEA: импортировать как Gradle-проект, Gradle JVM = 17, `genIntellijRuns`.
- [ ] Acceptance: `./gradlew build` (нужен Architectury в classpath для рантайма) + `runClient`. **Блокер: Architectury jar ещё не добавлен** (см. libs/README.md).

### Фаза 2 — Зависимости — ✅ СДЕЛАНА ПОЛНОСТЬЮ (2026-07-03)
- [x] IC2: `implementation fg.deobf("ic2:IC2Classic:1.19.2-2.1.3.3")` через flatDir module-нотацию (деобфусцируется, грузится в рантайме).
- [x] KubeJS/Rhino/Architectury/JEI: на **Maven** с рабочим `fg.deobf`. Architectury `6.6.92`. Версии в `gradle.properties`.
- [x] **CarbonConfig `1.19.2-2.0.0`** (обязат. зависимость IC2) через CurseMaven. `ic2_version_range` исправлен.
- [x] Фикс миксинов KubeJS в dev: `mixin.env.remapRefMap` + `refMapRemappingFile` в run-конфигах.
- [x] Классподход вместо `run/mods/`: моды подключены на класспас через `fg.deobf` (стандарт, лучше чем прод-jar в run/mods). Папка `run/mods/` не нужна.
- [x] Acceptance (компиляция): `./gradlew build` → BUILD SUCCESSFUL; jar в `build/libs/`.
- [x] **Acceptance (рантайм): `./gradlew runClient` — полная загрузка до меню с IC2+KubeJS+Rhino+Architectury+JEI+CarbonConfig, без крашей; наш плагин найден KubeJS-ом.**
- [~] `runServer` (dedicated) в dev не поднимается — dev-специфика dist (IC2 NPE + KubeJS client-класс), не наш баг. Тест сервера отложен на Фазу 7 (реальный прод-набор).

### Фаза 3 — Каркас плагина
- [x] `META-INF/mods.toml`: `modId=kubejs_ic2classic`, зависимости `forge`+`minecraft`+`kubejs`+`ic2`(mandatory), `jei`(optional), `ordering=AFTER`. **Сделано.**
- [x] `kubejs.plugins.txt` → FQN `com.sazdan.kubejsic2classic.KubeJSIC2ClassicPlugin`. **Сделано.**
- [x] `KubeJSIC2ClassicPlugin extends KubeJSPlugin` + `registerBindings(BindingsEvent)` → `IC2Recipes` (пока smoke-test строка). Сигнатуры сверены `javap`: `event.add(String,Object)`, `event.getType()`. **Сделано.**
- [x] Acceptance (частично): `runClient` поднимается, KubeJS находит плагин (`Found plugin source kubejs_ic2classic`), CONSTRUCT без ошибок. Осталось проверить `console.log(IC2Recipes)` из скрипта в игре.

### Фаза 4 — Машинные рецепты (ядро) — ✅ ЯДРО СДЕЛАНО И ПРОВЕРЕНО В РАНТАЙМЕ (2026-07-03)
- [x] TypeWrappers: JS → `IInput` с учётом count (`IC2Conversions.toInput`: `ItemInput`/`ItemStackInput`/`ItemTagInput`, префикс `"Nx "`, `#tag`). Зарегистрирован только `IInput` (⚠️ **НЕ** `ResourceLocation` — KubeJS уже его регистрирует, повторная регистрация кидает `Wrapper already exists` и валит CONSTRUCT). Выходы: `SimpleRecipeOutput`/`ChanceRecipeOutput`/`RangeRecipeOutput`/XP через `ItemWithNBTGenerator` (NBT+count).
- [x] `EventGroup "IC2Events"` (auto-биндится как глобал: `BuiltinKubeJSPlugin.registerBindings` итерирует `EventGroup.getGroups()`) + server-эвент `recipes` → `IC2RecipesEventJS`. Аксессоры `event.<machine>` для plain-списков: macerator, extractor, compressor, sawmill, massFabricator, mixingFurnace (+ recycler). ⚠️ **furnace/blastFurnace/smoker УДАЛЕНЫ** (2026-07-04): это `CookingMachineRecipeList` — зеркала ванильных cooking-рецептов, `reload()` игнорирует листенеры; авторить через `event.crafting.smelting(out,in,'furnace'|'blast_furnace'|'smoker')`. Методы: `add / addXp / addChance / addRange / remove(id) / removeByOutput / removeByInput`. Опц. первый арг — явный id (эвристика `looksLikeId`).
- [x] **Спец-обёртки (2026-07-03, проверено рантаймом):** `recycler` (`IRecyclerRecipeList extends IMachineRecipeList` → добавлен прямо в `Machines`, получает add/chance/range/remove даром); `electrolyzer` (addCharge/addDischarge/addDual(input,output,energy)+remove), `scrapbox` (addDrop(item,chance)/removeDrop), `fusion` (addFuel(item,value,rate,consumed)/removeFuel), `uuMatter` (addEntry(item,uu)/removeByOutput), `canner` (addFuel/addFuelMultiplier/removeFuel/addFillable/removeFillable). Все `extends IListenableRegistry` → тот же deferred-apply. Общая база `RecordingWrapper<X>`, обобщённый `IC2RecipeApplier.registerBoth(...)`.
- [x] **Флюиды + refining + fluidFuels (2026-07-03, проверено рантаймом):** `IC2Conversions.toFluidStack/toFluid/toFluidInput` (JS→`net.minecraftforge.fluids.FluidStack` через `FluidStackJS.of`, count в mB, default 1000). `fluidFuels` (публичный `RecipeRegistry.FLUID_FUELS`): addFuel(fluid,ticksPerBucket,euPerTick)/removeFuel. **`refining`** — единственный реестр без поля в публичном `RecipeRegistry` (API-интерфейс `IRefiningRecipeList` есть, но забыт); достаётся через **internal `ic2.core.IC2.RECIPES.get(sim).refining`** (guarded try/catch → при смене версий IC2 деградирует с warn). Fluent-билдер: `event.refining.add(id?).firstFluid/secondFluid/catalyst/fluidOutput/itemOutput.build()`; выход `SimpleFluidOutput(itemGens, fluidStacks)`, пустой катализатор `EmptyInput.INSTANCE`. Лог: `registered IC2 reload listeners (10 machines + 6 special + refining)`, все 17 `applied`.
- [x] **`rareEarth` + `potionBrewing` (2026-07-03, проверено рантаймом):** публичные (`RecipeRegistry.RARE_EARTH`/`POTION_BREWING`). `rareEarth`: addOutput(item)/addInput(weight,...items)/remove/removeInput/removeOutput. `potionBrewing`: addBrew(item,effect[,second])/removeBrew — эффекты через `IC2Conversions.toMobEffect` (id→`ForgeRegistries.MOB_EFFECTS`). Лог: `10 machines + 8 special + refining`, 19× `applied`. **Все рецептурные реестры IC2 теперь покрыты.**
- [x] **Reload-safe (ключевой нюанс):** `onServerReload` в KubeJS выполняется **РАНЬШЕ**, чем IC2 перестраивает рецепты из датапака (в логе: KubeJS-скрипты → потом `IC2Classic: Loading Recipes`), и IC2 очищает список при загрузке → прямое применение затирается. Решение: эвент **записывает** операции (`MachineRecipeWrapper` копит `Consumer<IMachineRecipeList>`), а применяются они из **IC2 `registerListener`** (`IC2RecipeApplier`), который срабатывает *после* наполнения списка. Листенеры вешаются один раз на обе стороны `SidedObject.get(true/false)` (дедуп по identity). Идемпотентность: каждый add делает `removeRecipe(id)` перед `addRecipe`.
- [x] **Acceptance (рантайм, runClient + загрузка мира):** `macerator: applied +2/-1; list now has 60 recipes` (база IC2 на месте, наши сосуществуют), `removeByOutput` работает; **`/reload` не плодит дубли (60→60)**, `Reloaded with no KubeJS errors!`. Срабатывает и на загрузке мира, и на `/reload`.

**Файлы Фазы 4:** `recipe/IC2Conversions.java` (JS→IInput/генераторы/RL/числа/auto-id), `recipe/RecordingWrapper.java` (общая база: запись `Consumer<X>` + счётчики + applyTo), `recipe/MachineRecipeWrapper.java` (машины, auto-id), `recipe/Machines.java` (реестр name→SidedObject, 10 машин), `recipe/IC2RecipeApplier.java` (collect + обобщённый `registerBoth` листенер на 10+5), `recipe/special/{Electrolyzer,Scrapbox,Fusion,UuMatter,Canner,FluidFuels,Refining,RareEarth,PotionBrew}Wrapper.java` (Refining с fluent-`Builder`), `event/IC2Events.java` (EventGroup), `event/IC2RecipesEventJS.java` (аксессоры). Пример: `examples/server_scripts/ic2_recipes_example.js`. **⚠️ IC2RecipeApplier импортирует `ic2.core.IC2`/`ic2.core...GlobalRecipes` (internal) ради refining — единственная зависимость от не-API IC2, обёрнута в try/catch.**

### Фаза 5 — Счётные крафты верстака — ✅ СДЕЛАНО И ПРОВЕРЕНО В РАНТАЙМЕ (2026-07-03)
**Ключевое решение — не отдельный глобал `IC2Crafting`, а `event.crafting` внутри `IC2Events.recipes`.** Причина: та же проблема порядка, что и в Фазе 4 — глобал выполнялся бы при eval скрипта (до перестройки рецептов IC2) и копил бы stale-операции между `/reload`. `event.crafting` получает свежую обёртку каждый reload и применяется через deferred-apply → переиспользует всю инфраструктуру Фазы 4.
- [x] Обёртка `CraftingWrapper extends RecordingWrapper<IAdvancedCraftingManager>` (`recipe/CraftingWrapper.java`). Методы: `shaped(id?, output, pattern[], keys{})`, `shapeless(id?, output, [inputs]|...inputs)`, `smelting(id?, output, input, [type...], [xp], [time])` (типы `furnace/blast_furnace/smoker/campfire`), `stonecutter(id?, output, input)`, `smithing(id?, output, base, addition)`; removes: `remove(id)`, `removeSmelting(id,...types)`, `removeStonecutter(id)`, `removeSmithing(id)`. Auto-id `kubejs_ic2classic:crafting/<kind>/<out>`, идемпотентность через remove-перед-add.
- [x] **Механизм авторинга сверен `javap`:** `RecipeRegistry.CRAFTING` = `AdvRecipeRegistry.INSTANCE` (`IAdvancedCraftingManager extends IListenableRegistry`, единичный, НЕ sided). `addShapedRecipe(rl, ItemStack, Object...)` → `ShapedIC2Recipe.create` парсит ванильный формат `"row","row","row", 'A', input, 'B', input`; каждое значение проходит через `RecipeRegistry.INGREDIENTS.createInputFrom(Object)`, который **уже-готовый `IInput` пропускает насквозь** → count сохраняется. Значения ключей строим через `IC2Conversions.toInput`.
- [x] **Порядок листенеров подтверждён `javap` (ключевой нюанс):** `GlobalRecipes.reloadRecipes()` вызывает `AdvRecipeRegistry.INSTANCE.reload()` в том же цикле, что и машины. `BaseRegistry.reload()` = `reloadInternals()` (очистка) → листенеры **по порядку регистрации** → `onAfterReload()` (инжект в игру). IC2 в `init()` вешает свой листенер `IC2.loadRecipes` (→ `IC2Recipes.addRecipes` — наполняет реестр ВСЕМИ дефолтными крафтами IC2) РАНЬШЕ нашего. Значит наш листенер идёт **последним** — дефолты IC2 уже на месте → и `add`, и `remove('ic2:...')` работают, инжект в игру идёт после нас в `onAfterReload`. Это идеальный порядок.
- [x] JS-типы: pattern-массив → `ListJS.of` (Rhino `NativeArray implements List`), keys-объект → `MapJS.of` (Rhino `NativeObject implements Map`).
- [x] **⚠️ Рантайм-баг найден и исправлен (2026-07-03): shaped/shapeless vs cooking/single/smithing используют РАЗНЫЕ пути входов.** shaped/shapeless → `ShapedIC2Recipe.create` → `createInputFrom` (принимает `IInput`, count сохраняется). А smelting/stonecutter/smithing → приватный `AdvRecipeRegistry.createFrom(Object)`, который принимает **только** `ItemStack/ItemLike/TagKey/Ingredient` (ванильные формы) и при инжекте в ванильный RecipeManager сериализуется как обычный `Ingredient`. Передача `IInput` туда → `CookingSerializer/SingleSerializer/SmithingSerializer.fromJson` кидает `"Input has to be either a [Stack/IItemProvider/ItemTag/Ingredient]"`. Фикс: новый `IC2Conversions.toVanillaInput(Object)` (ItemStack/`#tag`→TagKey/passthrough), им кормятся именно эти 3 типа. shaped/shapeless остаются на `toInput` (IInput, count важен). count для cooking/single/smithing нерелевантен (одиночный вход).
- [x] `SmeltingType.convert(rl)` переписывает id на `namespace:path_<suffix>` (furnace/blast/…). Значит реальный id smelting-рецепта = `<наш rl>_furnace` и т.п.; `removeSmeltingRecipe(rl, types)` делает ту же конверсию → идемпотентность сохраняется.
- [x] Acceptance (сборка): `./gradlew compileJava` + `./gradlew build` → BUILD SUCCESSFUL.
- [x] **Acceptance (рантайм, runClient + автозагрузка мира `--quickPlaySingleplayer`): `crafting: applied +5/-0 op(s)` рядом со всеми машинами Фазы 4; после фикса — `NO_PARSE_ERRORS` (ванильный RecipeManager принимает наши cooking/single/smithing).** ⚠️ Item-id в примерах должны быть реальными (`ic2:ingot_refined_iron`, `ic2:ingot_advanced_alloy`, НЕ `ic2:refined_iron`/`ic2:advanced_alloy`) — иначе эвристика `looksLikeId` примет незарегистрированный id за явный recipe-id и распарсит вызов неверно.

**Файлы Фазы 5:** `recipe/CraftingWrapper.java` (новый); правки в `event/IC2RecipesEventJS.java` (`public final CraftingWrapper crafting`) и `recipe/IC2RecipeApplier.java` (`registerListenerDedup(RecipeRegistry.CRAFTING, …)` — единичный, лог `… + crafting + refining`). Примеры — в `examples/server_scripts/ic2_recipes_example.js` (блок «Count-aware workbench crafts»).

### Фаза 6 — JEI (count-крафты) — ✅ КОД ГОТОВ, ЧИСТО ГРУЗИТСЯ В РАНТАЙМЕ (2026-07-03)
**Уточнение к плану (Вариант А → A′):** вместо отдельной `IRecipeCategory` — `ICraftingCategoryExtension` (JEI 11.8): IC2-крафты остаются в **ванильной** Crafting-категории, но мы сами раскладываем сетку с сохранением count. Строго лучше отдельной категории: нет дублей (иначе крафт был бы виден и в ванильной Crafting с count=1, и в нашей), не нужно ничего прятать, канонично.
- [x] Клиентский `@JeiPlugin` `IC2JeiPlugin implements IModPlugin`: `registerVanillaCategoryExtensions` → `addCategoryExtension(ShapedIC2Recipe.class, IC2ShapedCraftingExtension::new)` + shapeless. Слоты из `IInput.getComponents()` (count цел) через `ICraftingGridHelper.createAndSetInputs/createAndSetOutputs`.
- [x] `IC2CraftingTransferHandler implements IRecipeTransferHandler<CraftingMenu, CraftingRecipe>`, зарегистрирован на `(CraftingMenu, RecipeTypes.CRAFTING)`. **Wrap-and-delegate:** `recipe instanceof RecipeIC2Base` → шлём `FillCraftingGridPacket`; иначе → дефолтный JEI-handler (`transferHelper.createUnregisteredRecipeTransferHandler(createBasicRecipeTransferInfo(CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1,9,10,36))`). Не-IC2 крафты не тронуты (Risk #3 закрыт). Наш плагин регистрируется ПОСЛЕ `jei:minecraft` → перекрывает встроенный crafting-transfer, без warning-конфликтов.
- [x] **Перенос count через свой `SimpleChannel`** (`net/IC2Net.java` + `net/FillCraftingGridPacket.java`, C2S). Причина: JEI-дефолт двигает ровно 1/слот (`mezz.jei.common.transfer.RecipeTransferUtil#getRecipeTransferOperations` → `matchingStack.m_41774_(1)`), повторно использовать нельзя; IC2 `IndustrialWorkbenchTransferHandler` шлёт данные в СВОЙ BlockEntity/сеть — для ванильного `CraftingMenu` его нет. Серверная раскладка: рецепт по id из `RecipeManager`, per-slot `IInput` (shaped: `inputs[r*width+c]`→grid `r*3+c`; shapeless: по порядку), очистка сетки (возврат в инвентарь), затем `pullMatching` — изъять `getInputSize()` штук по `IInput.matches` из `Inventory.items`, положить в `menu.getSlot(1+g).container`, `slotsChanged`+`broadcastChanges`. maxTransfer — TODO (пока 1×, чинит базовый баг «1 вместо N»).
- [x] Источник рецептов: НЕ регистрируем сами — JEI уже держит `ShapedIC2Recipe`/`ShapelessIC2Recipe` (тип `minecraft:crafting`) в ванильной Crafting-категории; extension лишь переопределяет раскладку по классу рецепта.
- [x] **Acceptance (сборка+рантайм-загрузка):** `./gradlew build` OK; `runClient` → JEI прошёл все фазы для `kubejs_ic2classic:jei` (Registration/Recipes/Transfer/Runtime), `Building recipe registry` + `Starting JEI took 1.708s`, `network channel registered`, крафты применились — **без единого исключения**. Значит все JEI-API вызовы корректны.
- [ ] **Acceptance (ручной клик-тест, в игре):** число «×4» в слотах count-крафта; кнопка переноса кладёт 4/слот и рецепт матчится; ванильный крафт переносится как обычно. ⚠️ Визуальный — требует ручной проверки.

**Файлы Фазы 6:** `jei/IC2JeiPlugin.java`, `jei/IC2ShapedCraftingExtension.java`, `jei/IC2ShapelessCraftingExtension.java`, `jei/IC2CraftingTransferHandler.java`, `net/IC2Net.java`, `net/FillCraftingGridPacket.java`; правка `KubeJSIC2Classic.java` (+`IC2Net.init()` в конструкторе). **⚠️ Компиляция против official-маппинга: методы рецепта — `getResultItem()`/`getId()`, НЕ SRG `m_8043_`/`m_6423_` (javap по IC2-jar показывает SRG, т.к. jar обфусцирован; deobf-classpath — official).**

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

## Раскладка файлов (после реорга)
- `libs/` (в .gitignore) — все jar-ы: `IC2Classic-1.19.2-2.1.3.3.jar`, `kubejs-forge-...jar`, `rhino-forge-...jar`, `jei-...jar`, `kubejs-thermal-...jar` (референс). См. `libs/README.md`.
- `reference/ic2-extracted/` (в .gitignore) — распакованный IC2-jar: `ic2/api/**` для `javap` (напр. `javap -cp libs/IC2Classic-1.19.2-2.1.3.3.jar ic2.api.recipes.RecipeRegistry`), плюс `data/`, `assets/`, оригинальные `mods.toml`/mixins IC2.
- `src/main/java/com/sazdan/kubejsic2classic/` — `KubeJSIC2Classic` (@Mod) + `KubeJSIC2ClassicPlugin`.
- `src/main/resources/` — `META-INF/mods.toml`, `pack.mcmeta`, `kubejs.plugins.txt`.
- `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew(.bat)`, `gradle/wrapper/`.
- **kubejs-thermal** остаётся референсом (структура плагина, `kubejs.plugins.txt`), но RecipeSchema для IC2 НЕ применима — рецепты не в RecipeManager.

---

## Журнал сессий

### 2026-07-03 — Фаза 2 (зависимости) доведена до рабочего `runClient`
Итог: **проект собирается и запускается в dev** со всем стеком. Детали — в разделе «Текущий статус» выше. Кратко, что сделано за сессию:
- Зависимости переведены с локальных `files()` на **Maven/flatDir + рабочий `fg.deobf`** (это же починило dev-маппинги, ранее `files()` давал no-op).
- Добавлен **Architectury `6.6.92`** (`maven.architectury.dev`) — снят главный блокер рантайма.
- Найдены и закрыты 3 скрытых блокера запуска: (1) **CarbonConfig** — обязательная зависимость IC2 (CurseMaven); (2) `ic2_version_range` не матчил реальную версию IC2 `1.19.2-2.1.3.3`; (3) **миксины KubeJS** падали в official-dev — вылечено `mixin.env.remapRefMap` + `refMapRemappingFile`.
- **`./gradlew build` и `./gradlew runClient` → успех**, клиент грузится до меню, KubeJS видит наш плагин.
- Установлено: **`runServer` в dev не поднимается** (dist-специфика IC2/KubeJS) — dev-тест только через `runClient`.
- Правки: `build.gradle`, `gradle.properties`, `libs/README.md`, `CLAUDE.md`. Память проекта обновлена.

**Операционные памятки (важно для новой сессии):**
- Сборка/запуск ТОЛЬКО с `JAVA_HOME=...jdk-17...` (Gradle не идёт на JDK 25).
- Первый `runClient` долгий: ForgeGradle качает assets/natives + деобфусцирует IC2/CarbonConfig (кэш в `~/.gradle/caches/forge_gradle/deobf_dependencies/`).
- `runClient` открывает GL-окно — работает на реальном рабочем столе (не headless). Логи пишем в `run/*.log` (gitignored).

### 2026-07-03 — Фаза 4 (ядро машинных рецептов) доведена до рабочего рантайма
Итог: **`IC2Events.recipes(event => …)` работает в игре.** Реализованы 9 plain-машин с `add/addXp/addChance/addRange/remove/removeByOutput/removeByInput`; JS→`IInput`/выходы с сохранением count; auto-id по выходу+входу. За сессию:
- API сверен `javap` (RecipeRegistry, IMachineRecipeList, IInput-реализации, генераторы/выходы, KubeJS EventGroup/EventHandler/EventJS, `ItemStackJS.of`).
- **2 рантайм-бага найдены и закрыты через runClient + загрузку мира:**
  1. Повторная регистрация TypeWrapper `ResourceLocation` (KubeJS уже его регистрирует) → `Wrapper already exists` валил CONSTRUCT. Убрано (регистрируем только `IInput`).
  2. **Порядок применения:** `onServerReload` идёт раньше `IC2Classic: Loading Recipes`, IC2 чистит список → прямые add затирались (в логе было `macerator has 2 recipes` = только наши). Переделано на **deferred-apply**: эвент пишет операции, применение — из IC2 `registerListener` после наполнения. Итог: `macerator: applied +2/-1; list now has 60`.
- Acceptance подтверждён логами: база IC2 на месте, наши рецепты сосуществуют, `/reload` не плодит дубли (60→60), `Reloaded with no KubeJS errors!`.
- Файлы: `recipe/{IC2Conversions,MachineRecipeWrapper,Machines,IC2RecipeApplier}.java`, `event/{IC2Events,IC2RecipesEventJS}.java`, плагин, `examples/server_scripts/ic2_recipes_example.js`.

### 2026-07-03 (продолжение) — Спец-обёртки Фазы 4
Добавлены обёртки для нестандартных реестров, проверено рантаймом (загрузка мира):
- **recycler** — оказался `IRecyclerRecipeList extends IMachineRecipeList`, поэтому просто добавлен в `Machines.ALL` (получил add/chance/range/remove даром). `Machines.Def` → `SidedObject<? extends IMachineRecipeList>`.
- **electrolyzer / scrapbox / fusion / uuMatter / canner** — свои API, все `extends IListenableRegistry` → тот же deferred-apply. Выделена общая база `RecordingWrapper<X>` (запись `Consumer<X>`), `MachineRecipeWrapper` перенесён на неё. `IC2RecipeApplier` обобщён: `registerBoth(SidedObject<? extends X>, …)` вешает листенер на обе стороны для 10 машин + 5 спец-реестров.
- Лог: `registered IC2 reload listeners (10 machines + 5 special)` + 15 строк `applied +N/-M`, без ошибок.
- **refining отложен** (флюидный builder `IRefiningRecipeList.Builder`, FluidStack in/out) — вместе с фундаментом под флюиды.

### 2026-07-03 (продолжение) — Refining + флюиды: Фаза 4 полностью закрыта
- **Флюиды:** `IC2Conversions.toFluidStack/toFluid/toFluidInput` через `FluidStackJS.of` (JS→Forge `FluidStack`, mB, default 1000), `fluidId`.
- **fluidFuels** — чистый публичный (`RecipeRegistry.FLUID_FUELS`): addFuel/removeFuel.
- **refining** — обнаружено: `IRefiningRecipeList` есть в API, но **не выведен в `RecipeRegistry`** (единственный такой). Доступ — через internal `ic2.core.IC2.RECIPES` (`SidedGateway<GlobalRecipes>`) → `global.refining` (`RefiningRecipeList implements IRefiningRecipeList`). Обёрнуто в try/catch (деградация при смене IC2). Fluent-билдер (`firstFluid/secondFluid/catalyst/fluidOutput/itemOutput/build`), выход `SimpleFluidOutput`.
- Рантайм: `registered IC2 reload listeners (10 machines + 6 special + refining)` + 17× `applied`, без ошибок. **Фаза 4 закрыта полностью.**

### 2026-07-03 (продолжение) — Фаза 5: счётные крафты верстака (проверено рантаймом)
Итог: **`event.crafting` работает в игре.** `shaped/shapeless/smelting/stonecutter/smithing` + removes через `RecipeRegistry.CRAFTING` (`IAdvancedCraftingManager`, единичный, не sided). За сессию:
- **Решение по API:** не отдельный глобал `IC2Crafting` (как в исходном плане), а `event.crafting` внутри `IC2Events.recipes` — та же проблема порядка reload, что в Фазе 4; глобал копил бы stale-операции между `/reload`. Переиспользована вся инфраструктура deferred-apply.
- **Механизм сверен `javap`:** shaped-формат — ванильный `"row","row","row",'A',in,...`, значения через `RecipeRegistry.INGREDIENTS.createInputFrom` (уже-`IInput` пропускает насквозь → count цел). `GlobalRecipes.reloadRecipes()` дёргает `AdvRecipeRegistry.INSTANCE.reload()` в общем цикле; `BaseRegistry.reload()` = clear → листенеры по порядку → onAfterReload(инжект). IC2 в `init()` вешает свой листенер (`IC2.loadRecipes` → наполняет реестр дефолтными крафтами) РАНЬШЕ нашего → мы идём последними (дефолты уже на месте: add и remove работают).
- **2 рантайм-бага найдены и закрыты через runClient + автозагрузку мира (`--quickPlaySingleplayer "New World"`):**
  1. **IInput vs ванильный вход:** shaped/shapeless принимают `IInput` (count цел), но cooking/single/smithing идут через приватный `createFrom(Object)` → только `ItemStack/ItemLike/TagKey/Ingredient`, сериализуются в ванильный `Ingredient`. `IInput` туда → `CookingSerializer/SingleSerializer/SmithingSerializer.fromJson` кидает "Input has to be either a [Stack/IItemProvider/ItemTag/Ingredient]". Фикс: `IC2Conversions.toVanillaInput` для этих 3 типов.
  2. **Неверные item-id в примере** (`ic2:refined_iron`/`ic2:advanced_alloy` — незарегистрированные) → эвристика `looksLikeId` принимала их за явный recipe-id и распарсивала вызов неверно. Реальные id: `ic2:ingot_refined_iron`, `ic2:ingot_advanced_alloy` (из `assets/ic2/lang/items_en_us.json`).
- Acceptance подтверждён логами: `registered IC2 reload listeners (10 machines + 8 special + crafting + refining)`, `crafting: applied +5/-0`, без parse-ошибок ванильного RecipeManager.
- Файлы: `recipe/CraftingWrapper.java` (новый), правки в `recipe/IC2Conversions.java` (+`toVanillaInput`), `event/IC2RecipesEventJS.java` (+`crafting`), `recipe/IC2RecipeApplier.java` (+листенер CRAFTING), `examples/server_scripts/ic2_recipes_example.js`.

**Операционные памятки по dev-тесту крафтов (для новой сессии):**
- Тест — `runClient` c `--args='--quickPlaySingleplayer "New World"'` (автозагрузка мира триггерит `onServerReload` → `crafting: applied`). Мир `New World` уже есть в `run/saves/`.
- После теста убивать форкнутый JVM клиента (иначе лок `session.lock` мешает след. запуску): PowerShell по `CommandLine -match 'net.minecraft.client.main.Main'`.
- Item-id в примерах — только реальные (IC2 использует префиксы `ingot_`/`plate_`/… — сверять по `assets/ic2/lang/items_en_us.json`).

### 2026-07-03 (продолжение) — Фаза 6: JEI count-крафты (код готов, чисто грузится)
Итог: **JEI-интеграция count-крафтов написана и чисто грузится в рантайме.** За сессию:
- **Исследование `javap`+CFR-декомпиляция:** `IndustrialWorkbenchTransferHandler` (шлёт содержимое слотов в СВОЙ IC2 BlockEntity/сеть — для ванильного `CraftingMenu` не годится), JEI `RecipeTransferUtil` (дефолт двигает ровно 1/слот → `m_41774_(1)`, count-aware переиспользовать нельзя), `ShapedIC2Recipe`/`ShapelessIC2Recipe` (публичные `inputs` с count через `IInput.getComponents()/getInputSize()`), JEI-API (`ICraftingCategoryExtension`, `IVanillaCategoryExtensionRegistration`, `IRecipeTransferHandlerHelper`).
- **Решение по архитектуре (уточнение плана):** `ICraftingCategoryExtension` вместо отдельной категории (док. «Вариант А») — крафты в ванильной Crafting-категории с честным count, без дублей. Перенос — свой `SimpleChannel`-пакет (wrap-and-delegate handler: IC2 → наш пакет, остальное → дефолтный JEI-handler через `createUnregisteredRecipeTransferHandler`; Risk #3 закрыт).
- **Рантайм-подтверждение (runClient + загрузка мира):** плагин `kubejs_ic2classic:jei` прошёл все фазы JEI (Registration/Recipes/`Registering recipes transfer handlers` — ПОСЛЕ `jei:minecraft`, перекрывает встроенный без warning-конфликтов/Runtime), `Building recipe registry` + `Starting JEI took 1.708s`, `network channel registered`, крафты применились — **0 исключений** → все JEI-API вызовы корректны.
- **Баг компиляции найден и закрыт:** сперва использовал SRG-имена рецепта (`m_8043_`/`m_6423_`, как в `javap` по обфусцированному IC2-jar) → компиляция против official-classpath не видит их. Правка на `getResultItem()`/`getId()`.
- Файлы: `jei/{IC2JeiPlugin,IC2ShapedCraftingExtension,IC2ShapelessCraftingExtension,IC2CraftingTransferHandler}.java`, `net/{IC2Net,FillCraftingGridPacket}.java`, правка `KubeJSIC2Classic.java`.
- **Не закрыто:** ручной клик-тест в игре (число в слоте + «+» переносит N + не-IC2 целы) — визуальный; maxTransfer пока 1×.

**Следующий шаг: клик-тест Фазы 6, затем Фаза 7** (функц.-тесты, регресс, клиент↔сервер `RecipeUpdatePacket`, совместимость, README/примеры, публикация CurseForge/Modrinth).

### 2026-07-04 — Фаза 7: локальное тестирование (пройдено), 4 находки/фикса
Итог: **Часть 1 чек-листа (локальный клиент) полностью зелёная, включая headline-фичу — счётные крафты в JEI показывают ×N и кнопка «+» переносит N/слот.** Собран тест-jar `build/libs/kubejs_ic2classic-1.19.2-0.1.0.jar`. Написаны тестовый набор (`run/kubejs/server_scripts/ic2_test_{1_machines,2_special,3_crafting,4_resilience}.js`) и чек-лист `TESTING.md` (Часть 1 клиент + Часть 2 сервер). За сессию найдено и закрыто:
- **Устойчивость (код).** Один невалидный рецепт из скрипта бросал исключение внутри IC2 `addRecipe`, оно пробрасывалось через `GlobalRecipes.reloadRecipes` и **роняло весь reload IC2** (молча терялись остальные рецепты аддона). Фикс: `RecordingWrapper.applyTo` изолирует каждую операцию (try/catch, лог `… rejected by IC2`), `IC2RecipeApplier` пишет `applied +N/-M, K rejected`.
- **furnace/blast/smoker — УБРАНЫ (важная правка к Фазе 4!).** Раньше числились рабочими «plain-машинами» — **это неверно**. javap: они конструируются как `CookingMachineRecipeList`, чей `reload()` перестраивает список из ванильного `RecipeManager` (SMELTING/BLASTING/SMOKING) и **игнорирует листенеры** → `event.furnace/blastFurnace/smoker` молча ничего не делали. Это read-only зеркала ванильных cooking-рецептов. Аксессоры удалены из `Machines.ALL` и `IC2RecipesEventJS` (лог теперь `7 machines`, не 10). Корректный путь — `event.crafting.smelting(out, in, 'furnace'|'blast_furnace'|'smoker')` (инжектит ванильный cooking-рецепт, зеркала его подхватывают).
- **addChance давал 0% (код).** javap: у `ChanceRecipeOutput(List, float, float)` 2-й float пишется в поле `xp`, 3-й — в `chance` (имена параметров врут). Было `(gens, chance, 0f)` → шанс уходил в xp. Фикс: `(gens, 0f, chance)`. `MachineRecipeWrapper.addChance`.
- **Счётные крафты не появлялись в верстаке/JEI — namespace крафта (важный нюанс, не баг ядра).** IC2 отдаёт крафты (`AdvRecipeRegistry`) в ванильный `RecipeManager` через **виртуальный ресурс-пак `ic2.core.platform.recipes.PackHack$IC2RecipeHacker`**, чей `m_5698_` (getNamespaces) отдаёт только namespace = **id загруженного мода** (+`ic2`/`minecraft`). Крафт с id в «чужом» namespace (в тесте был `ic2test:`) **молча выкидывается**. Машинные рецепты идут мимо `RecipeManager` (через `GlobalRecipes`+JEI-плагин IC2) — их namespace не важен. Фикс теста: id крафтов на `kubejs:`. Фикс аддона (footgun-guard): `CraftingWrapper.served(rl)` — warning, если namespace не загруженный мод. **Подтверждено вживую: 4 Advanced Alloy Plate из 18 Mixed Metal Ingot (×2/слот), «+» переносит по 2; ваниль по 1 (регресс цел); recycler-replace через `removeByInput` перед add.**
- **Прочее (методика/тест-данные, не дефекты):** scrapbox `addDrop(item, вес)` — 2-й арг это ВЕС в пуле, не вероятность (0.001 ≈ 0%); mixing furnace — `MultiMachineRecipeList`, нужно ровно 2 входа (`maxInputs=2, exact=true`); fusion/uu/rare-earth — не про верстак, простой постановкой блока не проверяются; rare-earth в тесте задан неверно (`No Output found … ignoring`) — уточнить API. quick-play автозагрузка мира иногда «отваливается» на титуле → удалить `run/saves/New World/session.lock` и перезапустить.
- Файлы кода: `recipe/{RecordingWrapper,IC2RecipeApplier,Machines,MachineRecipeWrapper,CraftingWrapper}.java`, `event/IC2RecipesEventJS.java`.

**Следующий шаг: Часть 2 `TESTING.md` — тест на реальном выделенном сервере с собранным jar** (клиент↔сервер синк, `FillCraftingGridPacket` по сети, `/reload`, персистентность, совместимость). Затем Фаза 7 D (README/лицензия/logo/версия 1.0.0/публикация).
