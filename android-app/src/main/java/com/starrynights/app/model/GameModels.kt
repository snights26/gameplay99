package com.starrynights.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FictionalAdult(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val age: Int,
    val gender: String = "Unspecified",
    val pronouns: String = "",
    val avatarReference: String = "",
    val description: String = "",
    val personality: Map<String, Int>,
    val communication: String,
    val flirtingStyle: String,
    val intimacyEnergy: String,
    val pace: String,
    val socialProfile: SocialProfile = SocialProfile(),
    val privateProfile: PrivateProfile = PrivateProfile(),
    val desireProfile: DesireProfile = DesireProfile(),
    val boundaries: Map<String, Boundary> = mapOf("private_moment" to Boundary.NEEDS_TRUST),
    val hiddenTraits: Set<String> = emptySet()
) {
    val isAdult: Boolean get() = age >= 18
    fun resolvedSocialProfile(): SocialProfile = socialProfile.copy(
        communicationStyle = if (socialProfile.communicationStyle == "Soft-spoken" && communication != "Soft-spoken") communication else socialProfile.communicationStyle,
        flirtingStyle = if (socialProfile.flirtingStyle == "Slow Burn" && flirtingStyle != "Slow Burn") flirtingStyle else socialProfile.flirtingStyle
    )
    fun resolvedPrivateProfile(): PrivateProfile = privateProfile.copy(
        pace = runCatching { PrivatePace.valueOf(pace.uppercase().replace(' ', '_')) }.getOrDefault(privateProfile.pace),
        style = if (privateProfile.style == setOf("Romantic") && intimacyEnergy.isNotBlank()) setOf(intimacyEnergy) else privateProfile.style
    )
}

@Serializable enum class Boundary { COMFORTABLE, NEEDS_TRUST, HIGH_CHEMISTRY_ONLY, DISABLED }
@Serializable enum class TraitKnowledge { UNKNOWN, DISCOVERED }
@Serializable enum class DateLocation(val label: String, val ambience: String) {
    CAFE("Cafe", "Soft light, a window table, and a little room to breathe."),
    ROOFTOP_RESTAURANT("Rooftop Restaurant", "City lights gather beneath the terrace."),
    COCKTAIL_LOUNGE("Cocktail Lounge", "Low music and a bar that makes conversation feel cinematic."),
    BEACH_BAR("Beach Bar", "Salt air and lanterns moving in the night breeze."),
    HOTEL_LOUNGE("Hotel Lounge", "A polished, unhurried room just after dark."),
    NIGHT_CLUB("Night Club", "A bright rhythm with pockets of quieter space."),
    PRIVATE_DINNER("Private Dinner", "A candlelit table with no need to rush."),
    RESORT("Resort", "Warm air, distant water, and a night that can wander."),
    PENTHOUSE_BAR("Penthouse Bar", "A skyline, a reserved booth, and a sense of possibility.")
}

enum class GameAction(val label: String, val timing: Boolean = false) {
    OBSERVE("OBSERVE"), EYE_CONTACT("MAKE EYE CONTACT", true), SMILE("SMILE", true), APPROACH("APPROACH"),
    TEASE("TEASE", true), FLIRT("FLIRT", true), COMPLIMENT("COMPLIMENT", true), CHANGE_TOPIC("CHANGE TOPIC"),
    MOVE_CLOSER("MOVE CLOSER"), ORDER_DRINK("ORDER ANOTHER DRINK"), CHANGE_LOCATION("CHANGE LOCATION"),
    INVITE_CONTINUE("INVITE TO CONTINUE DATE"), STEP_BACK("STEP BACK"), END_DATE("END DATE"),
    LET_THEM_TALK("LET THEM TALK"), DIRECTOR_CARD("DIRECTOR CARD"), JOIN("JOIN"), REACT("REACT"),
    SHIFT_ATTENTION("SHIFT ATTENTION"), FOCUS_CHARACTER("FOCUS CHARACTER"), CHANGE_MOOD("CHANGE MOOD"),
    CHANGE_MUSIC("CHANGE MUSIC"), START_ACTIVITY("START ACTIVITY"), PRIVATE_MOMENT("PRIVATE MOMENT"),
    START_ROLEPLAY("START ROLEPLAY"), CONTINUE_NIGHT("CONTINUE NIGHT"), NEXT_MORNING("NEXT MORNING"),
    NEXT_DAY("NEXT DAY"), RETURN_TO_GROUP("RETURN TO GROUP")
}

enum class PrivateChoice(val label: String) {
    CONTINUE("CONTINUE"), SLOW_DOWN("SLOW DOWN"), CHANGE_DIRECTION("CHANGE DIRECTION"), STOP_SCENE("STOP SCENE")
}

@Serializable
data class RelationshipState(
    val attraction: Int = 32,
    val trust: Int = 28,
    val comfort: Int = 30,
    val curiosity: Int = 45,
    val jealousy: Int = 0,
    val tension: Int = 18,
    val attachment: Int = 12,
    val competition: Int = 0,
    val desire: Int = 15,
    val confidence: Int = 50
)

@Serializable data class SceneLog(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String,
    val text: String,
    val cue: String = "✨",
    val isSystem: Boolean = false
)

@Serializable data class IntimacyCard(
    val id: String,
    val name: String,
    val category: String,
    val intensity: Int,
    val closeness: Int,
    val controlBalance: Int,
    val difficulty: Int,
    val experimentalScore: Int,
    val requiredHeat: Int,
    val compatibilityTags: Set<String>,
    val icon: String,
    val description: String,
    val energy: String = "ROMANTIC",
    val unlockRules: Set<String> = emptySet()
)

@Serializable data class BlindDateState(
    val character: FictionalAdult,
    val location: DateLocation,
    val mode: StoryMode = StoryMode.ONE_ON_ONE,
    val scenario: ScenarioDefinition = starterScenarios.first(),
    val participants: List<FictionalAdult> = listOf(character),
    val circle: Circle? = null,
    val relationshipStart: StartingRelationship = StartingRelationship.COMPLETE_STRANGERS,
    val playerDesireProfile: DesireProfile = DesireProfile(),
    val heat: Int = 18,
    val relationship: RelationshipState = RelationshipState(),
    val directionalRelationships: Map<String, RelationshipState> = mapOf(
        relationshipKey(PLAYER_PARTICIPANT, character.id) to RelationshipState(),
        relationshipKey(character.id, PLAYER_PARTICIPANT) to RelationshipState()
    ),
    val discoveries: Map<String, TraitKnowledge> = character.personality.keys.associateWith { TraitKnowledge.UNKNOWN },
    val discoveredTraitsByCharacter: Map<String, Map<String, TraitKnowledge>> = mapOf(character.id to character.personality.keys.associateWith { TraitKnowledge.UNKNOWN }),
    val assignedRoles: Map<String, String> = emptyMap(),
    val directorCards: Map<String, DirectorCardState> = starterDirectorCards.associate { it.id to DirectorCardState(it.id) },
    val story: StoryProgression = StoryProgression(currentLocation = location.label),
    val privateState: PrivateMomentState = PrivateMomentState(),
    val log: List<SceneLog> = emptyList(),
    val privateOfferVisible: Boolean = false,
    val inPrivateMoment: Boolean = false,
    val ended: Boolean = false,
    val selectedCard: IntimacyCard? = null,
    val focusCharacterId: String? = null,
    val selectedDirectorCardId: String? = null,
    val unlockedRoutes: Set<String> = emptySet()
)

fun demoAdults(): List<FictionalAdult> = listOf(
    FictionalAdult(
        name = "Maya", age = 26, gender = "Woman", pronouns = "she/her",
        description = "A graphic designer who watches a room before choosing a moment.",
        personality = mapOf("Mysterious" to 4, "Shy" to 4, "Curious" to 3, "Intense" to 4),
        communication = "Soft-spoken", flirtingStyle = "Slow Burn", intimacyEnergy = "Romantic", pace = "Slow",
        socialProfile = SocialProfile("Soft-spoken", "Slow Burn", "Thoughtful", "Observant"),
        privateProfile = PrivateProfile(PrivatePace.SLOW, PrivateIntensity.INTENSE, setOf("Romantic", "Responsive"), PrivateInitiation.RARELY, PrivacyPreference.PRIVATE),
        desireProfile = DesireProfile(mapOf("Romantic" to InterestLevel.HIGH_INTEREST, "Slow Burn" to InterestLevel.HIGH_INTEREST, "Secret Attraction" to InterestLevel.INTERESTED)),
        hiddenTraits = setOf("Intense")
    ),
    FictionalAdult(
        name = "Riya", age = 29, gender = "Woman", pronouns = "she/her",
        description = "A travel photographer with a quick laugh and a sharper observation.",
        personality = mapOf("Playful" to 5, "Observant" to 4, "Bold" to 3, "Romantic" to 3),
        communication = "Witty", flirtingStyle = "Playful", intimacyEnergy = "Playful", pace = "Balanced",
        socialProfile = SocialProfile("Witty", "Playful", "Expressive", "Inclusive"),
        privateProfile = PrivateProfile(PrivatePace.BALANCED, PrivateIntensity.PASSIONATE, setOf("Playful", "Romantic", "Experimental"), PrivateInitiation.OFTEN, PrivacyPreference.FLEXIBLE),
        desireProfile = DesireProfile(mapOf("Romantic" to InterestLevel.INTERESTED, "Roleplay" to InterestLevel.INTERESTED, "Experimental" to InterestLevel.CURIOUS)),
        boundaries = mapOf("private_moment" to Boundary.COMFORTABLE)
    ),
    FictionalAdult(
        name = "Arjun", age = 31, gender = "Man", pronouns = "he/him",
        description = "A musician who is calm until a challenge makes him light up.",
        personality = mapOf("Calm" to 4, "Teasing" to 4, "Competitive" to 3, "Protective" to 3),
        communication = "Direct", flirtingStyle = "Confident", intimacyEnergy = "Responsive", pace = "Balanced",
        socialProfile = SocialProfile("Direct", "Confident", "Steady", "Focused"),
        privateProfile = PrivateProfile(PrivatePace.BALANCED, PrivateIntensity.PASSIONATE, setOf("Responsive", "Assertive"), PrivateInitiation.SOMETIMES, PrivacyPreference.PRIVATE),
        desireProfile = DesireProfile(mapOf("Romantic" to InterestLevel.INTERESTED, "Rival Attraction" to InterestLevel.CURIOUS, "Control" to InterestLevel.CURIOUS)),
        boundaries = mapOf("private_moment" to Boundary.NEEDS_TRUST)
    )
)

val intimacyDeck = listOf(
    IntimacyCard("slow_dance", "Slow Dance", "ROMANTIC", 1, 5, 0, 1, 1, 45, setOf("Romantic", "Slow"), "◐", "A quiet, close cinematic beat built around trust."),
    IntimacyCard("quiet_confession", "Quiet Confession", "ROMANTIC", 1, 5, 0, 1, 1, 48, setOf("Romantic", "Trust"), "♡", "A moment for words, eye contact, and emotional clarity."),
    IntimacyCard("playful_challenge", "Playful Challenge", "PLAYFUL", 2, 3, 0, 2, 2, 55, setOf("Playful", "Confident"), "✦", "A responsive game of charm where either person can redirect."),
    IntimacyCard("shared_rhythm", "Shared Rhythm", "PLAYFUL", 2, 4, 0, 2, 2, 58, setOf("Playful", "Responsive"), "≈", "A warm, abstract shared rhythm rather than a fixed script."),
    IntimacyCard("close_embrace", "Close Embrace", "PASSIONATE", 3, 5, 0, 2, 1, 62, setOf("Passionate", "Romantic"), "☾", "A cinematic connection that remains guided by each reaction."),
    IntimacyCard("lead_follow", "Lead & Follow", "DYNAMIC", 3, 3, 1, 3, 3, 70, setOf("Assertive", "Responsive"), "↔", "A consensual exchange of social initiative and response."),
    IntimacyCard("roleplay_reveal", "Roleplay Reveal", "EXPERIMENTAL", 3, 3, 0, 4, 4, 75, setOf("Experimental", "Roleplay"), "◇", "A fictional-role beat that reveals a more private side."),
    IntimacyCard("new_discovery", "New Discovery", "EXPERIMENTAL", 4, 3, 0, 4, 5, 82, setOf("Experimental", "Adventurous"), "✧", "A high-trust narrative discovery with no prescribed physical detail.")
)

