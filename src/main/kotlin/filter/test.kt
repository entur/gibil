package org.gibil.filter  // Your package

import org.entur.netex.tools.cli.app.FilterNetexApp
import org.entur.netex.tools.lib.config.CliConfig
import org.entur.netex.tools.lib.config.FilterConfig
import org.entur.netex.tools.lib.selectors.entities.EntitySelector
import org.entur.netex.tools.lib.selectors.entities.EntitySelectorContext
import org.entur.netex.tools.lib.selections.EntitySelection
import org.entur.netex.tools.lib.model.Entity
import java.io.File

// Make sure this class is in the SAME FILE as main() or properly imported
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
        return EntitySelection(selectionMap, model)
    }

    private fun addEntityAndRelated(
        entity: Entity,
        model: org.entur.netex.tools.lib.model.EntityModel,
        selectionMap: MutableMap<String, MutableMap<String, Entity>>,
        visited: MutableSet<String>
    ) {
        if (entity.id in visited) return
        visited.add(entity.id)

        selectionMap.computeIfAbsent(entity.type) { mutableMapOf() }[entity.id] = entity

        val referringEntities = model.getEntitiesReferringTo(entity)
        referringEntities.forEach { refEntity ->
            addEntityAndRelated(refEntity, model, selectionMap, visited)
        }

        val allRefs = model.listAllRefs()
        allRefs.filter { it.source.id == entity.id }.forEach { ref ->
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
        entitySelectors = listOf(LineSelector(lineIds)),  // Make sure this is here!
        pruneReferences = true
    )

    println(">>> FilterConfig has ${filterConfig.entitySelectors.size} selectors <<<")  // DEBUG

    val filterReport = FilterNetexApp(
        cliConfig = CliConfig(),
        filterConfig = filterConfig,
        input = File("src/main/kotlin/filter/exampleFiles"),
        target = File("src/main/kotlin/filter/output")
    ).run()

    println("\n=== COMPLETE ===")
}