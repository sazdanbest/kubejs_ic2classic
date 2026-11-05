// Замена топлива ic2:bio_fuel -> industrialforegoing:biofuel везде, где IC2 его производит/расходует:
// генератор (fluidFuels), Refinery (bio_gas_fuel, bio_fuel), лестница обогащения руды (6 *_to_raw_advanced,
// включая случайный разброс выхода). Тег-катализаторы (#forge:ores/iron и т.п.) не трогаются.
//
// ⚠️ Перед деплоем сверь id жидкости IF в игре (JEI/F3+H на ведре биотоплива) — если отличается
// от 'industrialforegoing:biofuel', поправь строку ниже.

IC2Events.recipes(event => {
    event.substitute('ic2:bio_fuel', 'industrialforegoing:biofuel')
})
