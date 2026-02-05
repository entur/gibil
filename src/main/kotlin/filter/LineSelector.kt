package filter

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
        println(model)

        lineIds.forEach { lineId ->
            val entity = model.getEntity(lineId)
            if (entity != null) {
                println(">>> FOUND LINE: ${entity.id} <<<")  // DEBUG
                println(entity)
                addEntityAndRelated(entity, model, selectionMap, visited)
            } else {
                println(">>> LINE NOT FOUND: $lineId <<<")  // DEBUG
            }
        }

        // This ensures the content of journeys is kept.
        val childTypes = setOf(
            "TimetabledPassingTime",
            "PointOnRoute",
            "StopPointInJourneyPattern",
            "RoutePoint",
            "DestinationDisplay"
        )
        childTypes.forEach { type ->
            model.getEntitiesOfType(type).forEach { entity ->
                selectionMap.computeIfAbsent(entity.type) { mutableMapOf() }[entity.id] = entity
            }
        }

        // If we don't select these, the "Parent Rule" kills the ServiceFrame because
        val frameTypes = setOf(
            "CompositeFrame",
            "ServiceFrame",
            "TimetableFrame",
            "ResourceFrame",
            "GeneralFrame",
            "SiteFrame",
            "ServiceCalendarFrame"
        )
        println(">>> Auto-selecting Frames to preserve hierarchy <<<")
        frameTypes.forEach { type ->
            val frames = model.getEntitiesOfType(type)
            frames.forEach { entity ->
                selectionMap.computeIfAbsent(entity.type) { mutableMapOf() }[entity.id] = entity
            }
            if (frames.isNotEmpty()) println("   + Kept ${frames.size} $type")
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

/*fun main() {
    val lineIds = setOf("AVI:Line:SK_OSL-BGO", "AVI:Line:WF_BGO-EVE")

    println("Filtering for lines: $lineIds\n")

    val filterConfig = FilterConfig(
        entitySelectors = listOf(LineSelector(lineIds)),
        preserveComments = true,
        pruneReferences = true,
        referencesToExcludeFromPruning = setOf(
            "DayType",
            "DayTypeAssignment",
            "DayTypeRef",
            "TimetabledPassingTime",
            "passingTimes",
            "StopPointInJourneyPatternRef"
        ),
        unreferencedEntitiesToPrune = setOf(
            "DayTypeAssignment",
            "JourneyPattern", "Route", "PointOnRoute", "DestinationDisplay"
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
}*/