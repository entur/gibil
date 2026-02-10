package filter

import org.entur.netex.tools.lib.selectors.entities.EntitySelector
import org.entur.netex.tools.lib.selectors.entities.EntitySelectorContext
import org.entur.netex.tools.lib.selections.EntitySelection
import org.entur.netex.tools.lib.model.Entity
import org.entur.netex.tools.lib.model.EntityModel
import org.gibil.LineSelector

val debugPrinting = LineSelector.DEBUG_PRINTING_LINESELECTOR

/**
 * An EntitySelector that selects entities based on a set of line IDs.
 * Made custom to ensure all relevant child data inside servicejourney is kept, as the "Parent Rule" would otherwise prune away important information.
 * This selector is designed to be used in the context of filtering NeTEx data for specific lines, ensuring that all relevant information is retained.
 * @param lineIds A set of strings representing the line IDs to filter for (e.g., "AVI:Line:SK_OSL-BGO", "AVI:Line:WF_BGO-EVE").
 * @return An EntitySelection containing all entities related to the specified line IDs
 */
class LineSelector(private val lineIds: Set<String>) : EntitySelector {
    override fun selectEntities(context: EntitySelectorContext): EntitySelection {
        val model = context.entityModel
        val selectionMap = mutableMapOf<String, MutableMap<String, Entity>>()
        val visited = mutableSetOf<String>()

        if (debugPrinting) {
            println(">>> LineSelector.selectEntities() CALLED <<<")
            println(">>> Looking for: $lineIds <<<")
            println(model)
        }
        lineIds.forEach { lineId ->
            val entity = model.getEntity(lineId)
            if (entity != null) {
                if (debugPrinting) {
                    println(">>> FOUND LINE: ${entity.id} <<<")
                    println(entity)
                }
                addEntityAndRelated(entity, model, selectionMap, visited)
            } else {
                if (debugPrinting) {
                    println(">>> LINE NOT FOUND: $lineId <<<")
                }
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
        if (debugPrinting) {
            println(">>> Auto-selecting Frames to preserve hierarchy <<<")
        }
        frameTypes.forEach { type ->
            val frames = model.getEntitiesOfType(type)
            frames.forEach { entity ->
                selectionMap.computeIfAbsent(entity.type) { mutableMapOf() }[entity.id] = entity
            }
            if (frames.isNotEmpty()) println("   + Kept ${frames.size} $type")
        }

        if(debugPrinting) {
            println(">>> Total selected: ${selectionMap.values.sumOf { it.size }} <<<")
            println("All available IDs: " + model.listAllEntities().map { it.id })
        }
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