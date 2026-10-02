package com.starrynights.app.engine

import com.starrynights.app.model.*
import kotlin.math.roundToInt
import kotlin.random.Random

data class BehaviourRead(
    val success: Boolean,
    val confidenceDelta: Int,
    val observation: String
)

/**
 * Fuses personality, social/private profiles, roles, the current relationship and the most recent action.
 * A role adds a lens to a character; it never replaces their underlying personality or boundaries.
 */
class CharacterBehaviourEngine(private val random: Random = Random.Default) {
    fun read(
        character: FictionalAdult,
        action: GameAction,
        timing: Float,
        relationship: RelationshipState,
        role: String? = null
    ): BehaviourRead {
        val traits = character.personality
        val social = character.resolvedSocialProfile()
        val privateProfile = character.resolvedPrivateProfile()
        var affinity = 0.0f
        fun trait(name: String) = (traits[name] ?: 0) / 5f
        affinity += when (action) {
            GameAction.OBSERVE, GameAction.LET_THEM_TALK -> trait("Mysterious") * .20f + trait("Observant") * .16f
            GameAction.TEASE -> trait("Teasing") * .25f + trait("Playful") * .18f + trait("Competitive") * .12f
            GameAction.FLIRT, GameAction.EYE_CONTACT, GameAction.SMILE -> trait("Flirty") * .18f + trait("Bold") * .14f
            GameAction.COMPLIMENT -> trait("Romantic") * .18f + trait("Emotionally Expressive") * .12f
            GameAction.MOVE_CLOSER, GameAction.APPROACH -> trait("Confident") * .12f - trait("Reserved") * .12f
            GameAction.STEP_BACK, GameAction.CHANGE_TOPIC -> trait("Private") * .18f + trait("Patient") * .16f + trait("Reserved") * .10f
            GameAction.CHANGE_LOCATION, GameAction.START_ACTIVITY -> trait("Adventurous") * .18f + trait("Curious") * .10f
            GameAction.SHIFT_ATTENTION -> trait("Independent") * .08f - trait("Jealous") * .16f
            GameAction.START_ROLEPLAY -> if (character.desireProfile.level("Roleplay") != InterestLevel.NOT_INTERESTED) .15f else -.12f
            GameAction.PRIVATE_MOMENT, GameAction.INVITE_CONTINUE -> when (privateProfile.initiation) {
                PrivateInitiation.OFTEN -> .16f
                PrivateInitiation.SOMETIMES -> .04f
                PrivateInitiation.RARELY -> -.10f
            }
            else -> 0f
        }
        affinity += when (social.flirtingStyle.lowercase()) {
            "playful" -> if (action in setOf(GameAction.TEASE, GameAction.REACT)) .12f else 0f
            "confident", "very forward" -> if (action in setOf(GameAction.APPROACH, GameAction.FLIRT)) .12f else 0f
            "slow burn", "rarely initiates" -> if (action in setOf(GameAction.OBSERVE, GameAction.COMPLIMENT, GameAction.STEP_BACK)) .12f else -.03f
            else -> 0f
        }
        if (role?.contains("Rival", true) == true && action == GameAction.TEASE) affinity += .12f
        if (role?.contains("Spy", true) == true && action == GameAction.OBSERVE) affinity += .12f
        if (role?.contains("Bodyguard", true) == true && action == GameAction.STEP_BACK) affinity += .08f
        val timingFit = if (!action.timing) .12f else (1f - kotlin.math.abs(timing - .70f)).coerceIn(-.30f, .30f)
        val relationshipFit = ((relationship.comfort + relationship.trust) / 200f) * .14f
        val guarded = trait("Shy") * .08f + trait("Emotionally Guarded") * .08f
        val score = .42f + affinity + timingFit + relationshipFit - guarded + random.nextFloat() * .08f
        val success = action in setOf(GameAction.OBSERVE, GameAction.STEP_BACK, GameAction.LET_THEM_TALK) || score >= .57f
        val cue = when {
            success && action.timing -> "The timing lands naturally."
            success -> "Their behaviour opens up a little."
            action == GameAction.STEP_BACK -> "The space you create is noticed."
            else -> "The room asks for a more careful read."
        }
        return BehaviourRead(success, if (success) 3 else -2, cue)
    }

    fun autonomousBeat(
        actor: FictionalAdult,
        target: FictionalAdult?,
        relationship: RelationshipState,
        role: String? = null
    ): SceneLog {
        val social = actor.resolvedSocialProfile()
        val privateProfile = actor.resolvedPrivateProfile()
        val text = when {
            relationship.jealousy >= 45 -> "${actor.name} notices a shift in attention and grows quieter for a beat."
            relationship.competition >= 40 -> "${actor.name} turns a small disagreement into a playful challenge."
            role?.contains("Spy", true) == true -> "${actor.name} leaves a clue in the conversation, then watches who notices."
            role?.contains("Rival", true) == true -> "${actor.name} answers the room with a knowing, competitive smile."
            role?.contains("Host", true) == true -> "${actor.name} brings two conversations together without taking over either one."
            "Protective" in actor.personality && target != null -> "${actor.name} checks that ${target.name} is still comfortable with the room."
            social.attentionStyle.equals("Observant", true) || "Observant" in actor.personality -> "${actor.name} catches a detail that changes how they read the scene."
            privateProfile.privacy == PrivacyPreference.PRIVATE -> "${actor.name} scans the room for a quieter corner, without assuming anyone will follow."
            "Playful" in actor.personality -> "${actor.name} turns a passing detail into an easy laugh."
            else -> "${actor.name} takes in the atmosphere and chooses their own next move."
        }
        return SceneLog(speaker = actor.name, text = text, cue = "✨", isSystem = false)
    }
}

class EmotionEngine {
    private fun change(value: Int, delta: Int): Int = (value + delta).coerceIn(0, 100)

    fun apply(state: RelationshipState, action: GameAction, success: Boolean, isAttentionElsewhere: Boolean = false): RelationshipState {
        val q = if (success) 1 else -1
        return when (action) {
            GameAction.OBSERVE, GameAction.LET_THEM_TALK -> state.copy(curiosity = change(state.curiosity, 4), comfort = change(state.comfort, 2))
            GameAction.EYE_CONTACT, GameAction.SMILE -> state.copy(attraction = change(state.attraction, 5 * q), tension = change(state.tension, 3), confidence = change(state.confidence, 2 * q))
            GameAction.APPROACH, GameAction.MOVE_CLOSER -> state.copy(attraction = change(state.attraction, 4 * q), comfort = change(state.comfort, 4 * q), tension = change(state.tension, 4))
            GameAction.FLIRT, GameAction.TEASE, GameAction.REACT -> state.copy(attraction = change(state.attraction, 7 * q), desire = change(state.desire, 5 * q), tension = change(state.tension, 4 * q))
            GameAction.COMPLIMENT -> state.copy(trust = change(state.trust, 6 * q), attraction = change(state.attraction, 4 * q), comfort = change(state.comfort, 2 * q))
            GameAction.CHANGE_TOPIC, GameAction.CHANGE_MOOD, GameAction.CHANGE_MUSIC -> state.copy(comfort = change(state.comfort, 5), tension = change(state.tension, -4))
            GameAction.STEP_BACK -> state.copy(comfort = change(state.comfort, 7), trust = change(state.trust, 5), tension = change(state.tension, -6))
            GameAction.SHIFT_ATTENTION -> state.copy(jealousy = change(state.jealousy, if (isAttentionElsewhere) 8 else -3), confidence = change(state.confidence, 2))
            GameAction.FOCUS_CHARACTER, GameAction.JOIN -> state.copy(attachment = change(state.attachment, 3 * q), trust = change(state.trust, 3 * q), comfort = change(state.comfort, 2 * q))
            GameAction.START_ACTIVITY, GameAction.CHANGE_LOCATION -> state.copy(curiosity = change(state.curiosity, 4), comfort = change(state.comfort, 2 * q))
            GameAction.START_ROLEPLAY -> state.copy(curiosity = change(state.curiosity, 6 * q), tension = change(state.tension, 3 * q))
            GameAction.PRIVATE_MOMENT, GameAction.INVITE_CONTINUE -> state.copy(trust = change(state.trust, 4 * q), desire = change(state.desire, 4 * q))
            GameAction.DIRECTOR_CARD -> state.copy(curiosity = change(state.curiosity, 5), tension = change(state.tension, 3))
            GameAction.END_DATE, GameAction.CONTINUE_NIGHT, GameAction.NEXT_MORNING, GameAction.NEXT_DAY, GameAction.RETURN_TO_GROUP, GameAction.ORDER_DRINK -> state
        }
    }

    fun reciprocal(state: RelationshipState, success: Boolean): RelationshipState = state.copy(
        attraction = change(state.attraction, if (success) 3 else -1),
        trust = change(state.trust, if (success) 2 else 0),
        confidence = change(state.confidence, if (success) 2 else -1)
    )
}

/** Applies player actions, then lets the remaining fictional adults keep acting. */
class StorySimulationEngine(
    private val behaviour: CharacterBehaviourEngine = CharacterBehaviourEngine(),
    private val emotions: EmotionEngine = EmotionEngine(),
    private val random: Random = Random.Default
) {
    fun start(
        characters: List<FictionalAdult>,
        scenario: ScenarioDefinition,
        location: DateLocation,
        mode: StoryMode,
        relationshipStart: StartingRelationship,
        circle: Circle? = null,
        roles: Map<String, String> = emptyMap()
    ): BlindDateState {
        require(characters.isNotEmpty())
        require(characters.all(FictionalAdult::isAdult)) { "All story participants must be fictional adults." }
        val primary = characters.first()
        val directional = buildDirectionalRelationships(characters, relationshipStart)
        val discoveries = characters.associate { adult -> adult.id to adult.personality.keys.associateWith { TraitKnowledge.UNKNOWN } }
        val locationName = if (location.label in scenario.locations) location.label else scenario.locations.firstOrNull() ?: location.label
        return BlindDateState(
            character = primary,
            location = location,
            mode = mode,
            scenario = scenario,
            participants = characters,
            circle = circle,
            relationshipStart = relationshipStart,
            relationship = directional[relationshipKey(PLAYER_PARTICIPANT, primary.id)] ?: RelationshipState(),
            directionalRelationships = directional,
            discoveries = discoveries[primary.id].orEmpty(),
            discoveredTraitsByCharacter = discoveries,
            assignedRoles = roles,
            story = StoryProgression(phase = scenario.phases.firstOrNull() ?: "arrival", currentLocation = locationName),
            focusCharacterId = primary.id,
            log = listOf(
                SceneLog(speaker = "SYSTEM", text = "${scenario.name.uppercase()} — ${locationName.uppercase()}", cue = "✦", isSystem = true),
                SceneLog(speaker = "SYSTEM", text = if (mode == StoryMode.CIRCLE) "The group has its own history and will keep reacting when you do not direct it." else "Their deeper traits remain UNKNOWN until behaviour reveals them.", cue = "☾", isSystem = true),
                SceneLog(speaker = primary.name, text = "The night begins with room for either of you to set the pace.", cue = "👀")
            )
        )
    }

    fun perform(state: BlindDateState, action: GameAction, timing: Float, targetId: String? = state.focusCharacterId): BlindDateState {
        if (state.ended || state.inPrivateMoment) return state
        val target = state.participants.firstOrNull { it.id == targetId } ?: state.character
        val playerKey = relationshipKey(PLAYER_PARTICIPANT, target.id)
        val targetKey = relationshipKey(target.id, PLAYER_PARTICIPANT)
        val current = state.directionalRelationships[playerKey] ?: state.relationship
        val read = behaviour.read(target, action, timing, current, state.assignedRoles[target.id])
        val targetEmotion = emotions.apply(current, action, read.success)
        val reverseEmotion = emotions.reciprocal(state.directionalRelationships[targetKey] ?: RelationshipState(), read.success)
        var directional = state.directionalRelationships + (playerKey to targetEmotion) + (targetKey to reverseEmotion)
        val updatedDiscoveries = reveal(state.discoveredTraitsByCharacter, target, action, read.success)
        val discovery = updatedDiscoveries[target.id].orEmpty().entries.firstOrNull { (trait, knowledge) ->
            knowledge == TraitKnowledge.DISCOVERED && state.discoveredTraitsByCharacter[target.id]?.get(trait) == TraitKnowledge.UNKNOWN
        }?.key
        val heat = updateHeat(state.heat, action, read.success, targetEmotion, state.scenario.heatModifier)
        val actionLog = SceneLog(speaker = "YOU", text = action.label, cue = if (read.success) "✦" else "…")
        val reaction = SceneLog(speaker = target.name, text = reactionLine(target, action, read.success, state.assignedRoles[target.id]), cue = if (read.success) "😏" else "👀")
        val discoveryLog = discovery?.let {
            SceneLog(speaker = "DISCOVERED", text = "${target.name}: ${it.uppercase()} — their behaviour makes more sense now.", cue = "◇", isSystem = true)
        }
        val group = groupBeats(state.copy(directionalRelationships = directional), action, target)
        directional = group.relationships
        val privateAllowed = canOffer(state.copy(directionalRelationships = directional, relationship = targetEmotion, heat = heat), target)
        val story = advanceProgress(state.story, action, state.scenario, state.inPrivateMoment)
        val base = state.copy(
            character = target,
            heat = heat,
            relationship = targetEmotion,
            directionalRelationships = directional,
            discoveries = updatedDiscoveries[target.id].orEmpty(),
            discoveredTraitsByCharacter = updatedDiscoveries,
            story = story,
            focusCharacterId = target.id,
            privateOfferVisible = state.privateOfferVisible || (action in setOf(GameAction.INVITE_CONTINUE, GameAction.PRIVATE_MOMENT) && privateAllowed),
            ended = action == GameAction.END_DATE,
            log = state.log + listOfNotNull(actionLog, reaction, discoveryLog) + group.logs
        )
        return if (base.privateOfferVisible && !state.privateOfferVisible) {
            base.copy(log = base.log + SceneLog(speaker = "SYSTEM", text = "CONTINUE PRIVATE MOMENT is available. It is optional; anyone can slow down, change direction, or stop.", cue = "☾", isSystem = true))
        } else base
    }

    fun applyDirectorCard(state: BlindDateState, card: DirectorCard): BlindDateState {
        val cardState = state.directorCards[card.id] ?: return state
        if (!cardState.available || cardState.applied || state.heat < card.requiredHeat) return state
        val selected = state.participants.randomOrNull(random) ?: return state
        var directional = state.directionalRelationships
        if (card.effect["emotion"] == "jealousy") {
            val key = relationshipKey(selected.id, PLAYER_PARTICIPANT)
            directional += key to (directional[key] ?: RelationshipState()).copy(jealousy = ((directional[key]?.jealousy ?: 0) + 10).coerceAtMost(100))
        }
        val newStory = state.story.copy(mood = card.effect["mood"] ?: state.story.mood, completedBeats = state.story.completedBeats + card.id)
        return state.copy(
            directionalRelationships = directional,
            directorCards = state.directorCards + (card.id to cardState.copy(applied = true, available = false, appliedAtEvent = state.log.size)),
            selectedDirectorCardId = card.id,
            story = newStory,
            log = state.log + SceneLog(speaker = "DIRECTOR", text = "${card.name}: ${card.description}", cue = "◇", isSystem = true) + groupBeats(state, GameAction.DIRECTOR_CARD, selected).logs
        )
    }

    fun focus(state: BlindDateState, characterId: String): BlindDateState {
        val focused = state.participants.firstOrNull { it.id == characterId } ?: return state
        return state.copy(character = focused, focusCharacterId = focused.id, relationship = state.directionalRelationships[relationshipKey(PLAYER_PARTICIPANT, focused.id)] ?: state.relationship,
            discoveries = state.discoveredTraitsByCharacter[focused.id].orEmpty(),
            log = state.log + SceneLog(speaker = "SYSTEM", text = "Your attention settles on ${focused.name}. The rest of the scene keeps moving.", cue = "👀", isSystem = true)
        )
    }

    fun enterPrivate(state: BlindDateState, location: String): BlindDateState {
        val focused = state.participants.firstOrNull { it.id == state.focusCharacterId } ?: state.character
        if (!canOffer(state, focused)) return state
        val leftBehind = groupBeats(state, GameAction.LET_THEM_TALK, focused)
        return state.copy(
            inPrivateMoment = true,
            privateState = PrivateMomentState(focusedCharacterId = focused.id, location = location, active = true, explicitlyOptedIn = true),
            directionalRelationships = leftBehind.relationships,
            log = state.log + leftBehind.logs + SceneLog(speaker = "SYSTEM", text = "PRIVATE MOMENT — ${location.uppercase()}. This is a mutual, optional story beat.", cue = "☾", isSystem = true)
        )
    }

    fun returnToGroup(state: BlindDateState): BlindDateState = state.copy(
        inPrivateMoment = false,
        privateState = state.privateState.copy(active = false),
        story = advanceProgress(state.story, GameAction.RETURN_TO_GROUP, state.scenario, false),
        log = state.log + SceneLog(speaker = "SYSTEM", text = "RETURN TO GROUP — the relationships changed while the room kept living.", cue = "✨", isSystem = true)
    )

    fun advanceStory(state: BlindDateState, action: GameAction): BlindDateState = state.copy(
        story = advanceProgress(state.story, action, state.scenario, state.inPrivateMoment),
        log = state.log + SceneLog(
            speaker = "SYSTEM",
            text = when (action) {
                GameAction.NEXT_MORNING -> "NEXT MORNING — yesterday's choices still shape the room."
                GameAction.NEXT_DAY -> "NEXT DAY — attachments, distance, and discoveries carry forward."
                GameAction.CHANGE_LOCATION -> "CHANGE LOCATION — the scene has a new atmosphere, not a reset."
                else -> "CONTINUE NIGHT — the story keeps its consequences."
            },
            cue = "☾",
            isSystem = true
        )
    )

    fun canOffer(state: BlindDateState, target: FictionalAdult): Boolean {
        val relationship = state.directionalRelationships[relationshipKey(PLAYER_PARTICIPANT, target.id)] ?: state.relationship
        val boundary = target.boundaries["private_moment"] ?: Boundary.NEEDS_TRUST
        if (!target.isAdult || boundary == Boundary.DISABLED) return false
        val trustNeeded = if (boundary == Boundary.NEEDS_TRUST) 62 else 45
        val heatNeeded = if (boundary == Boundary.HIGH_CHEMISTRY_ONLY) 65 else 48
        val desireCompatible = target.desireProfile.compatibleWith(state.playerDesireProfile)
        return relationship.attraction >= 55 && relationship.comfort >= 55 && relationship.trust >= trustNeeded &&
            relationship.desire >= 35 && state.heat >= heatNeeded && desireCompatible
    }

    private fun buildDirectionalRelationships(characters: List<FictionalAdult>, start: StartingRelationship): Map<String, RelationshipState> {
        val seed = when (start) {
            StartingRelationship.COMPLETE_STRANGERS -> RelationshipState()
            StartingRelationship.FRIENDS_OF_FRIENDS -> RelationshipState(trust = 38, comfort = 38, curiosity = 48)
            StartingRelationship.CLOSE_FRIENDS, StartingRelationship.OLD_FRIENDS -> RelationshipState(attraction = 38, trust = 65, comfort = 64, curiosity = 42, attachment = 35)
            StartingRelationship.ROOMMATES -> RelationshipState(trust = 54, comfort = 60, curiosity = 36, attachment = 28)
            StartingRelationship.EXES_REUNITED -> RelationshipState(attraction = 48, trust = 38, comfort = 42, tension = 42, attachment = 48)
            StartingRelationship.VACATION_GROUP, StartingRelationship.PARTY_GROUP -> RelationshipState(attraction = 34, trust = 34, comfort = 42, curiosity = 55)
            StartingRelationship.CUSTOM -> RelationshipState()
        }
        val ids = listOf(PLAYER_PARTICIPANT) + characters.map(FictionalAdult::id)
        return buildMap {
            ids.forEach { source -> ids.filterNot { it == source }.forEach { target -> put(relationshipKey(source, target), seed) } }
        }
    }

    private data class GroupResult(val relationships: Map<String, RelationshipState>, val logs: List<SceneLog>)

    private fun groupBeats(state: BlindDateState, action: GameAction, focused: FictionalAdult): GroupResult {
        if (state.participants.size < 2) return GroupResult(state.directionalRelationships, emptyList())
        var relationships = state.directionalRelationships
        val logs = mutableListOf<SceneLog>()
        state.participants.filterNot { it.id == focused.id }.forEach { actor ->
            val toFocusedKey = relationshipKey(actor.id, focused.id)
            val toPlayerKey = relationshipKey(actor.id, PLAYER_PARTICIPANT)
            val toFocused = relationships[toFocusedKey] ?: RelationshipState()
            val toPlayer = relationships[toPlayerKey] ?: RelationshipState()
            val shifted = action in setOf(GameAction.FOCUS_CHARACTER, GameAction.FLIRT, GameAction.MOVE_CLOSER, GameAction.PRIVATE_MOMENT)
            val nextFocused = if (shifted) toFocused.copy(jealousy = (toFocused.jealousy + if (actor.personality.containsKey("Jealous")) 7 else 2).coerceAtMost(100)) else toFocused
            val nextPlayer = if (action == GameAction.JOIN) toPlayer.copy(comfort = (toPlayer.comfort + 2).coerceAtMost(100)) else toPlayer
            relationships = relationships + (toFocusedKey to nextFocused) + (toPlayerKey to nextPlayer)
            if (random.nextFloat() > .30f) logs += behaviour.autonomousBeat(actor, focused, nextFocused, state.assignedRoles[actor.id])
        }
        return GroupResult(relationships, logs)
    }

    private fun reveal(
        discoveries: Map<String, Map<String, TraitKnowledge>>,
        target: FictionalAdult,
        action: GameAction,
        success: Boolean
    ): Map<String, Map<String, TraitKnowledge>> {
        if (!success) return discoveries
        val own = discoveries[target.id] ?: target.personality.keys.associateWith { TraitKnowledge.UNKNOWN }
        val candidates = own.filterValues { it == TraitKnowledge.UNKNOWN }.keys
        val preferred = when (action) {
            GameAction.OBSERVE -> candidates.filter { it in setOf("Mysterious", "Observant", "Reserved", "Private") }
            GameAction.TEASE -> candidates.filter { it in setOf("Teasing", "Playful", "Competitive") }
            GameAction.COMPLIMENT -> candidates.filter { it in setOf("Romantic", "Shy", "Intense", "Emotionally Expressive") }
            GameAction.START_ACTIVITY, GameAction.CHANGE_LOCATION -> candidates.filter { it in setOf("Adventurous", "Curious", "Risk Taking") }
            else -> candidates.toList()
        }
        val reveal = (preferred.ifEmpty { candidates.toList() }).randomOrNull(random) ?: return discoveries
        return discoveries + (target.id to (own + (reveal to TraitKnowledge.DISCOVERED)))
    }

    private fun updateHeat(heat: Int, action: GameAction, success: Boolean, relationship: RelationshipState, scenarioModifier: Int): Int {
        val delta = when (action) {
            GameAction.FLIRT, GameAction.TEASE -> if (success) 6 else -4
            GameAction.EYE_CONTACT, GameAction.SMILE, GameAction.COMPLIMENT -> if (success) 4 else -2
            GameAction.MOVE_CLOSER, GameAction.APPROACH -> if (success) 3 else -5
            GameAction.INVITE_CONTINUE, GameAction.PRIVATE_MOMENT -> if (success) 5 else -3
            GameAction.STEP_BACK, GameAction.CHANGE_TOPIC, GameAction.CHANGE_MOOD -> -2
            GameAction.DIRECTOR_CARD, GameAction.START_ROLEPLAY -> if (success) 2 else 0
            else -> 0
        }
        val chemistry = if (relationship.attraction > 55 && relationship.comfort > 50 && action !in setOf(GameAction.OBSERVE, GameAction.LET_THEM_TALK)) 1 else 0
        return (heat + delta + chemistry + if (action == GameAction.START_ACTIVITY) scenarioModifier / 4 else 0).coerceIn(0, 100)
    }

    private fun reactionLine(character: FictionalAdult, action: GameAction, success: Boolean, role: String?): String {
        val traits = character.personality.keys
        val roleLead = role?.let { "$it aside, " }.orEmpty()
        return when {
            action == GameAction.STEP_BACK -> "${roleLead}${character.name} visibly relaxes when you give the moment more room."
            action == GameAction.END_DATE -> "${roleLead}${character.name} nods, grateful for the clarity."
            success && "Shy" in traits -> "${roleLead}their smile arrives a second late, but it is genuine."
            success && ("Teasing" in traits || "Playful" in traits) -> "${roleLead}\"Careful. That was almost charming,\" they say."
            success && ("Bold" in traits || "Confident" in traits) -> "${roleLead}\"I was hoping you would come over,\" they say."
            success -> "${roleLead}the connection lands, and they give the scene more of their attention."
            else -> "${roleLead}${character.name} pauses, reading the intention behind your choice."
        }
    }

    private fun advanceProgress(current: StoryProgression, action: GameAction, scenario: ScenarioDefinition, privateActive: Boolean): StoryProgression {
        val phaseIndex = scenario.phases.indexOf(current.phase).coerceAtLeast(0)
        val shouldAdvance = action in setOf(GameAction.CONTINUE_NIGHT, GameAction.NEXT_MORNING, GameAction.NEXT_DAY, GameAction.CHANGE_LOCATION, GameAction.RETURN_TO_GROUP)
        val nextPhase = if (shouldAdvance) scenario.phases.getOrElse(phaseIndex + 1) { current.phase } else current.phase
        val options = buildSet {
            add("CONTINUE NIGHT"); add("CHANGE LOCATION"); add("NEW SCENARIO"); add("RELATIONSHIP WEB")
            if (current.sceneNumber > 1) add("NEXT MORNING")
            if (privateActive) add("RETURN TO GROUP")
            if (!privateActive) add("PRIVATE MOMENT")
            if (scenario.roleplayCompatible.isNotEmpty()) add("START ROLEPLAY")
        }
        return current.copy(
            chapter = current.chapter + if (action == GameAction.NEXT_DAY) 1 else 0,
            phase = nextPhase,
            sceneNumber = current.sceneNumber + if (shouldAdvance) 1 else 0,
            completedBeats = current.completedBeats + action.name,
            nextOptions = options
        )
    }
}
