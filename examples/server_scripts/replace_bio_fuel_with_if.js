// "Мягкая" замена ic2:bio_fuel -> industrialforegoing:biofuel — теперь ОДНОЙ строкой.
//
// event.substitute(from, to) проходит по всем уже зарегистрированным рецептам (включая
// дефолтные рецепты самого IC2) в: macerator, extractor, compressor, sawmill, massFabricator,
// mixingFurnace, recycler, refining и fluidFuels — и там, где встречается "from" (как предмет
// ИЛИ как жидкость), меняет его на "to", не трогая ничего больше: остальные входы, тип выхода
// (fixed/chance/range), диапазон случайного разброса, XP, NBT — всё остаётся ровно как было.
// Катализаторы по тегу (#forge:ores/iron и т.п.) НИКОГДА не переписываются — замена одного
// предмета не должна тайком сужать список того, что тег принимает.
//
// Для случая bio_fuel это покрывает всё, что реально можно перенаправить скриптом:
//  - топливо генератора (fluidFuels)
//  - производство на Refinery (bio_gas_fuel, bio_fuel) — оба рецепта переопределяются на месте
//  - средний ярус лестницы обогащения руды (6 рецептов *_to_raw_advanced) — включая их честный
//    случайный разброс выхода [min,max], который раньше пришлось бы аппроксимировать вручную
//
// ⚠️ Не покрывается (см. описание substitute() в IC2RecipesEventJS.java) — не имеет отношения
// к bio_fuel, но важно для общего понимания границ: electrolyzer/scrapbox/fusion/uuMatter/
// canner/rareEarth/potionBrewing/crafting. Сама жидкость ic2:bio_fuel и её ячейки в IC2 остаются
// зарегистрированы (это Java-уровень IC2, аддон это не трогает) — просто с нуля рецептов их
// никто больше не производит и не расходует.
//
// ⚠️ Проверить перед деплоем: точный fluid id Industrial Foregoing (обычно
// 'industrialforegoing:biofuel', но в этом проекте jar-а IF под рукой не было для сверки —
// проверить в игре через JEI/F3+H на ведре биотоплива IF).

IC2Events.recipes(event => {
    event.substitute('ic2:bio_fuel', 'industrialforegoing:biofuel')
})

// Примеры сопутствующих новых операций (не обязательны для замены bio_fuel, для справки):
//
// Прямая правка случайного разброса выхода существующего рецепта, без пересоздания рецепта:
//   event.macerator.setRange('ic2:macerator/some_recipe', 2, 5)
//   event.refining.setItemRange('ic2:iron_to_raw_advanced', 4, 12)
//   event.refining.setFluidRange('kjs:refining/my_recipe', 200, 800)
//
// Авторинг НОВОГО refining-рецепта со случайным разбросом (раньше было невозможно):
//   event.refining.add('kjs:refining/scrap_from_slag')
//       .firstFluid('ic2:slag', 1000)
//       .itemOutputRange('minecraft:iron_nugget', 1, 4)
//       .build()
