package com.starrynights.app.engine

import com.starrynights.app.model.*
import kotlin.math.roundToInt
import kotlin.random.Random

class IntimacyCompatibilityEngine {
    fun canOfferPrivateMoment(state: BlindDateState): Boolean {
        val boundary = state.character.boundaries["private_moment"] ?: Boundary.NEEDS_TRUST
        if (!state.character.isAdult || boundary == Boundary.DISABLED) return false
        val requiredTrust = if (boundary == Boundary.NEEDS_TRUST) 62 else 45
        val requiredHeat = if (boundary == Boundary.HIGH_CHEMISTRY_ONLY) 65 else 48
        val energyCompatible = state.character.resolvedPrivateProfile().style.isNotEmpty() &&
            state.character.desireProfile.compatibleWith(state.playerDesireProfile)
        return state.relationship.attraction >= 55 && state.relationship.comfort >= 55 &&
            state.relationship.trust >= requiredTrust && state.relationship.desire >= 35 && state.heat >= requiredHeat && energyCompatible
    }

    fun availableCards(state: BlindDateState): List<IntimacyCard> {
        val privateProfile = state.character.resolvedPrivateProfile()
        val energy = privateProfile.style.joinToString(" ").lowercase()
        val pace = privateProfile.pace.name.lowercase()
        return intimacyDeck.filter { card ->
            state.heat >= card.requiredHeat &&
                (card.compatibilityTags.any { it.lowercase() in energy || it.lowercase() in pace || state.character.desireProfile.level(it) >= InterestLevel.INTERESTED } || card.intensity <= 2)
        }
    }
}

class RuleBasedDialogueEngine(private val random: Random = Random.Default) {
    fun reaction(character: FictionalAdult, action: GameAction, success: Boolean): SceneLog {
        val traits = character.personality.keys
        val shy = "Shy" in traits || "Reserved" in traits
        val teasing = "Teasing" in traits || "Playful" in traits
        val bold = "Bold" in traits || "Confident" in traits
        val line = when {
            action == GameAction.OBSERVE -> "${character.name} notices that you are not trying to rush the moment."
            action == GameAction.STEP_BACK -> "${character.name} visibly relaxes when you give the moment more room."
            action == GameAction.END_DATE -> "${character.name} nods, grateful for the clarity. The night settles into a respectful ending."
            success && shy -> listOf("Their smile arrives a second late, but it is genuine.", "They look away, then meet your eyes again.").random(random)
            success && teasing -> listOf("\"Careful. That was almost charming,\" they say.", "They lift an eyebrow, inviting you to keep up.").random(random)
            success && bold -> listOf("\"I was hoping you would come over,\" they say.", "They lean in just enough to make their interest unmistakable.").random(random)
            success -> "The connection lands. ${character.name} gives the conversation more of their attention."
            action == GameAction.CHANGE_TOPIC -> "The new topic gives both of you a chance to reset the mood."
            else -> "${character.name} pauses, reading the intention behind your choice."
        }
        return SceneLog(speaker = character.name, text = line, cue = if (success) "😏" else "👀")
    }

    fun autonomous(character: FictionalAdult, relationship: RelationshipState): SceneLog {
        val line = when {
            relationship.jealousy > 30 -> "${character.name}'s attention shifts when someone else gets too close."
            "Observant" in character.personality -> "${character.name} catches a small detail in the room and smiles to themself."
            "Mysterious" in character.personality -> "${character.name} lets a thoughtful silence do some of the talking."
            "Playful" in character.personality -> "${character.name} turns a passing detail into an easy laugh."
            else -> "${character.name} takes in the atmosphere, deciding where the night might go next."
        }
        return SceneLog(speaker = "LIVING SCENE", text = line, cue = "✨", isSystem = true)
    }
}

class RelationshipEngine {
    fun apply(state: RelationshipState, action: GameAction, success: Boolean): RelationshipState {
        val quality = if (success) 1 else -1
        fun add(value: Int, delta: Int) = (value + delta).coerceIn(0, 100)
        return when (action) {
            GameAction.OBSERVE -> state.copy(curiosity = add(state.curiosity, 3), comfort = add(state.comfort, 1))
            GameAction.EYE_CONTACT, GameAction.SMILE -> state.copy(attraction = add(state.attraction, 5 * quality), tension = add(state.tension, 3), confidence = add(state.confidence, 2 * quality))
            GameAction.APPROACH, GameAction.MOVE_CLOSER -> state.copy(comfort = add(state.comfort, 4 * quality), attraction = add(state.attraction, 4 * quality), tension = add(state.tension, 4))
            GameAction.FLIRT, GameAction.TEASE -> state.copy(attraction = add(state.attraction, 7 * quality), tension = add(state.tension, 5 * quality), desire = add(state.desire, 5 * quality))
            GameAction.COMPLIMENT -> state.copy(trust = add(state.trust, 6 * quality), attraction = add(state.attraction, 4 * quality), comfort = add(state.comfort, 2 * quality))
            GameAction.CHANGE_TOPIC -> state.copy(comfort = add(state.comfort, 5), tension = add(state.tension, -3))
            GameAction.ORDER_DRINK, GameAction.CHANGE_LOCATION -> state.copy(comfort = add(state.comfort, 3 * quality), curiosity = add(state.curiosity, 3))
            GameAction.INVITE_CONTINUE -> state.copy(trust = add(state.trust, 4 * quality), confidence = add(state.confidence, 3 * quality))
            GameAction.STEP_BACK -> state.copy(comfort = add(state.comfort, 6), trust = add(state.trust, 4), tension = add(state.tension, -5))
            GameAction.LET_THEM_TALK -> state.copy(curiosity = add(state.curiosity, 4), trust = add(state.trust, 2))
            GameAction.DIRECTOR_CARD -> state.copy(curiosity = add(state.curiosity, 5), tension = add(state.tension, 3))
            GameAction.JOIN, GameAction.FOCUS_CHARACTER -> state.copy(trust = add(state.trust, 3 * quality), attachment = add(state.attachment, 3 * quality))
            GameAction.REACT -> state.copy(attraction = add(state.attraction, 4 * quality), tension = add(state.tension, 3 * quality))
            GameAction.SHIFT_ATTENTION -> state.copy(jealousy = add(state.jealousy, if (success) -2 else 5), confidence = add(state.confidence, 2))
            GameAction.CHANGE_MOOD, GameAction.CHANGE_MUSIC -> state.copy(comfort = add(state.comfort, 4), tension = add(state.tension, -3))
            GameAction.START_ACTIVITY -> state.copy(curiosity = add(state.curiosity, 5), comfort = add(state.comfort, 2 * quality))
            GameAction.PRIVATE_MOMENT, GameAction.START_ROLEPLAY -> state.copy(desire = add(state.desire, 4 * quality), curiosity = add(state.curiosity, 4 * quality))
            GameAction.CONTINUE_NIGHT, GameAction.NEXT_MORNING, GameAction.NEXT_DAY, GameAction.RETURN_TO_GROUP, GameAction.END_DATE -> state
        }
    }
}

class HeatEngine {
    fun update(heat: Int, action: GameAction, success: Boolean, relationship: RelationshipState): Int {
        val actionDelta = when (action) {
            GameAction.FLIRT, GameAction.TEASE -> if (success) 6 else -4
            GameAction.EYE_CONTACT, GameAction.SMILE, GameAction.COMPLIMENT -> if (success) 4 else -2
            GameAction.MOVE_CLOSER, GameAction.APPROACH -> if (success) 3 else -5
            GameAction.INVITE_CONTINUE -> if (success) 5 else -3
            GameAction.DIRECTOR_CARD, GameAction.START_ROLEPLAY -> 3
            GameAction.PRIVATE_MOMENT -> if (success) 4 else -3
            GameAction.STEP_BACK, GameAction.CHANGE_TOPIC, GameAction.CHANGE_MOOD -> -2
            else -> 0
        }
        val chemistryBonus = if (relationship.attraction > 55 && relationship.comfort > 50) 1 else 0
        return (heat + actionDelta + chemistryBonus).coerceIn(0, 100)
    }
}

class KotlinGameEngine(
    private val compatibility: IntimacyCompatibilityEngine = IntimacyCompatibilityEngine(),
    private val dialogue: RuleBasedDialogueEngine = RuleBasedDialogueEngine(),
    private val relationships: RelationshipEngine = RelationshipEngine(),
    private val heatEngine: HeatEngine = HeatEngine(),
    private val random: Random = Random.Default,
    private val storySimulation: StorySimulationEngine = StorySimulationEngine()
) {
    fun start(character: FictionalAdult, location: DateLocation): BlindDateState = storySimulation.start(
        characters = listOf(character), scenario = starterScenarios.first(), location = location,
        mode = StoryMode.ONE_ON_ONE, relationshipStart = StartingRelationship.COMPLETE_STRANGERS
    )

    fun startStory(
        characters: List<FictionalAdult>, scenario: ScenarioDefinition, location: DateLocation,
        mode: StoryMode, relationshipStart: StartingRelationship, circle: Circle? = null, roles: Map<String, String> = emptyMap()
    ): BlindDateState = storySimulation.start(characters, scenario, location, mode, relationshipStart, circle, roles)

    fun perform(state: BlindDateState, action: GameAction, timingScore: Float = 0.7f, targetId: String? = state.focusCharacterId): BlindDateState =
        storySimulation.perform(state, action, timingScore, targetId)

    fun focusCharacter(state: BlindDateState, characterId: String): BlindDateState = storySimulation.focus(state, characterId)
    fun applyDirectorCard(state: BlindDateState, card: DirectorCard): BlindDateState = storySimulation.applyDirectorCard(state, card)
    fun continueStory(state: BlindDateState, action: GameAction): BlindDateState = storySimulation.advanceStory(state, action)
    fun returnToGroup(state: BlindDateState): BlindDateState = storySimulation.returnToGroup(state)

    fun enterPrivateMoment(state: BlindDateState, location: String = "Private Lounge"): BlindDateState = storySimulation.enterPrivate(state, location)

    fun selectCard(state: BlindDateState, card: IntimacyCard): BlindDateState = state.copy(
        selectedCard = card,
        log = state.log + SceneLog(speaker = "SYSTEM", text = "${card.name.uppercase()} selected. ${card.description}", cue = card.icon, isSystem = true)
    )

    fun privateChoice(state: BlindDateState, choice: PrivateChoice): BlindDateState {
        val (heatChange, text) = when (choice) {
            PrivateChoice.CONTINUE -> 3 to "The scene develops through mutual reactions rather than a fixed animation."
            PrivateChoice.SLOW_DOWN -> -4 to "The pace softens. Comfort and trust take priority."
            PrivateChoice.CHANGE_DIRECTION -> 0 to "You both reset the mood and choose a different emotional direction."
            PrivateChoice.STOP_SCENE -> -8 to "The private scene stops immediately. The story continues with a clear boundary respected."
        }
        val after = state.relationship.copy(
            trust = (state.relationship.trust + if (choice == PrivateChoice.STOP_SCENE) 4 else 2).coerceAtMost(100),
            attachment = (state.relationship.attachment + if (choice == PrivateChoice.CONTINUE) 4 else 1).coerceAtMost(100),
            comfort = (state.relationship.comfort + if (choice == PrivateChoice.SLOW_DOWN || choice == PrivateChoice.STOP_SCENE) 3 else 1).coerceAtMost(100)
        )
        val focusedCharacterId = state.focusCharacterId ?: state.character.id
        val directional = state.directionalRelationships + (relationshipKey(PLAYER_PARTICIPANT, focusedCharacterId) to after)
        val updated = state.copy(
            heat = (state.heat + heatChange).coerceIn(0, 100), relationship = after, directionalRelationships = directional,
            inPrivateMoment = choice != PrivateChoice.STOP_SCENE,
            privateState = state.privateState.copy(active = choice != PrivateChoice.STOP_SCENE),
            log = state.log + SceneLog(speaker = "SYSTEM", text = text, cue = "♡", isSystem = true)
        )
        return if (choice == PrivateChoice.STOP_SCENE) storySimulation.returnToGroup(updated) else updated
    }

    fun availableIntimacyCards(state: BlindDateState): List<IntimacyCard> = compatibility.availableCards(state)

}

