package org.gibil.filter

import org.entur.netex.tools.cli.app.FilterNetexApp
import org.entur.netex.tools.lib.config.CliConfig
import org.entur.netex.tools.lib.config.FilterConfig
import org.entur.netex.tools.lib.selectors.entities.EntitySelector
import org.entur.netex.tools.lib.selectors.entities.EntitySelectorContext
import org.entur.netex.tools.lib.selections.EntitySelection
import org.entur.netex.tools.lib.model.Entity
import org.entur.netex.tools.lib.model.EntityModel
import java.io.File

class LineSelector(private val lineIds: Set<String>) : EntitySelector {
    override fun selectEntities(context: EntitySelectorContext): EntitySelection {
        val model = context.entityModel
        val selectionMap = mutableMapOf<String, MutableMap<String, Entity>>()
        val visited = mutableSetOf<String>()

        println(">>> LineSelector.selectEntities() CALLED <<<")  // DEBUG
        println(">>> Looking for: $lineIds <<<")  // DEBUG

        lineIds.forEach { lineId ->
            val entity = model.getEntity(lineId)
            if (entity != null) {
                println(">>> FOUND LINE: ${entity.id} <<<")  // DEBUG
                addEntityAndRelated(entity, model, selectionMap, visited)
            } else {
                println(">>> LINE NOT FOUND: $lineId <<<")  // DEBUG
            }
        }

        println(">>> Total selected: ${selectionMap.values.sumOf { it.size }} <<<")  // DEBUG
        println("All available IDs: " + model.listAllEntities().take(10).map { it.id })
        return EntitySelection(selectionMap, model)
    }

    private fun addEntityAndRelated(
        entity: Entity,
        model: EntityModel,
        selectionMap: MutableMap<String, MutableMap<String, Entity>>,
        visited: MutableSet<String>
    ) {
        if (entity.id in visited) return
        visited.add(entity.id)

        selectionMap.getOrPut(entity.type) { mutableMapOf<String, Entity>() }[entity.id] = entity

        entity.parent?.let { parentEntity ->
            addEntityAndRelated(parentEntity, model, selectionMap, visited)
        }

        model.getEntitiesReferringTo(entity).forEach { referringEntity ->
            addEntityAndRelated(referringEntity, model, selectionMap, visited)
        }

        model.listAllRefs().filter { it.source.id == entity.id }.forEach { ref ->
            val targetEntity = model.getEntity(ref.ref)
            if (targetEntity != null) {
                addEntityAndRelated(targetEntity, model, selectionMap, visited)
            }
        }
    }
}

fun main() {
    val lineIds = setOf("AVI:Line:SK_OSL-BGO")

    println("Filtering for lines: $lineIds\n")

    val filterConfig = FilterConfig(
        entitySelectors = listOf(LineSelector(lineIds)),
        preserveComments = false,
        pruneReferences = true,
        unreferencedEntitiesToPrune = setOf(
            "DayType", "DayTypeAssignment", "TimetabledPassingTime",
            "JourneyPattern", "Route", "PointOnRoute",
            "StopPointInJourneyPattern", "DestinationDisplay"
        )
    )

    //println(">>> FilterConfig has ${filterConfig.entitySelectors.size} selectors <<<")  // DEBUG

    FilterNetexApp(
        cliConfig = CliConfig(alias = mapOf()),
        filterConfig = filterConfig,
        input = File("src/main/kotlin/filter/exampleFiles"),
        target = File("src/main/kotlin/filter/output")
    ).run()

    println("\n=== FILTERING FERDIG ===")
}