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
 *
 * STRATEGY:
 * 1. Start with specified Line IDs
 * 2. Recursively traverse ALL relationships (both directions)
 * 3. Force-add TimetabledPassingTime (actual schedule data)
 * 4. Force-add all Frames (preserve XML structure)
 *
 * RESULT: Complete, valid NeTEx file containing only the selected lines
 *         and all their related data (routes, journeys, stops, times, etc.)
 *
 * @param lineIds Line IDs to filter (e.g., "AVI:Line:SK_OSL-BGO")
 */
class LineSelector(private val lineIds: Set<String>) : EntitySelector {
    /**
     * overrides the selectEntities interface method to select entities based on the provided line IDs, while ensuring that all related entities are also included in the selection to preserve the integrity of the data.
     * @param context The EntitySelectorContext providing access to the entity model and other relevant information for selection.
     * @return An EntitySelection containing all entities related to the specified line IDs, including parent entities and entities that refer to the selected lines, as well as ensuring that the content of journeys is kept and that frames are preserved to maintain the hierarchy of the data.
     */
    override fun selectEntities(context: EntitySelectorContext): EntitySelection {
        val model = context.entityModel

        // This map will hold the selected entities, organized by their type and ID.
        val selectionMap = mutableMapOf<String, MutableMap<String, Entity>>()

        //tracks already visited entities to avoid infinite loops when traversing relationships
        val visited = mutableSetOf<String>()

        if (debugPrinting) {
            println(">>> LineSelector.selectEntities() CALLED <<<")
            println(">>> Looking for: $lineIds <<<")
            println(model)
        }

        //1. Look it up in the model
        //2. If found, call addEntityAndRelated() to add it AND everything related to it
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

        /**
         * Force-add critical leaf entities that contain actual timetable data.
         * These are nested within ServiceJourneys and might not be reached by recursive traversal
         * because they're XML child elements rather than referenced entities.
         *
         * Without this, you'd have ServiceJourneys but no actual departure/arrival times!
         */
        val childTypes = setOf(
            "TimetabledPassingTime" // Contains DepartureTime and ArrivalTime for each stop
        )
        childTypes.forEach { type ->
            model.getEntitiesOfType(type).forEach { entity ->
                selectionMap.computeIfAbsent(entity.type) { mutableMapOf() }[entity.id] = entity
            }
        }

        // If we don't select these, the "Parent Rule" kills the ServiceFrame
        /* structure preservation, keeps the top parts of the hierarchy, so we don't end up with a flat structure with no context. This is important for understanding the relationships between entities and for ensuring that we have all the necessary information to work with the selected lines.
            PublicationDelivery
              └─ CompositeFrame
                  ├─ ServiceFrame
                  │   ├─ Lines
                  │   └─ Routes
                  └─ TimetableFrame
                      └─ ServiceJourneys
                            └─ (the information we need)
         */
        val frameTypes = setOf(
            "CompositeFrame",
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

    /**
     * Recursively adds an entity and all related entities to the selection.
     *
     * Traverses:
     * - Parent chain (ensures frame structure is preserved)
     * - Entities referring to this one (e.g., ServiceJourneys referring to a Line)
     * - Entities this one refers to (e.g., Line referring to Operator)
     *
     * @param entity Entity to add
     * @param model Entity model containing all relationships
     * @param selectionMap Accumulates selected entities by type and ID
     * @param visited Tracks visited entities to prevent infinite loops
     */
    private fun addEntityAndRelated(
        entity: Entity,
        model: EntityModel,
        selectionMap: MutableMap<String, MutableMap<String, Entity>>,
        visited: MutableSet<String>
    ) {
        //prevent infinite loops
        if (entity.id in visited) return
        visited.add(entity.id)

        //add this entity to our selection map under its type
        selectionMap.getOrPut(entity.type) { mutableMapOf<String, Entity>() }[entity.id] = entity

        //parenting correction, follow the parenting chain, ensure structure
        entity.parent?.let { parentEntity ->
            if (parentEntity.type != "ServiceFrame") {
                addEntityAndRelated(parentEntity, model, selectionMap, visited)
            }
        }

        //follow up the hierarchy: get entities that refer to this entity
        model.getEntitiesReferringTo(entity).forEach { referringEntity ->
            addEntityAndRelated(referringEntity, model, selectionMap, visited)
        }

        //follow down the hierarchy: get entities that this entity refers to
        model.listAllRefs().filter { it.source.id == entity.id }.forEach { ref ->
            val targetEntity = model.getEntity(ref.ref)
            if (targetEntity != null) {
                addEntityAndRelated(targetEntity, model, selectionMap, visited)
            }
        }
    }
}