package com.starrynights.app.model

import kotlinx.serialization.Serializable

/** Shared story vocabulary. These types deliberately describe a scene rather than prescribe real-world behaviour. */
@Serializable
enum class StoryMode { ONE_ON_ONE, CIRCLE }

@Serializable
enum class StartingRelationship(val label: String) {
    COMPLETE_STRANGERS("Complete Strangers"), FRIENDS_OF_FRIENDS("Friends of Friends"),
    CLOSE_FRIENDS("Close Friends"), ROOMMATES("Roommates"), OLD_FRIENDS("Old Friends"),
    EXES_REUNITED("Exes Reunited"), VACATION_GROUP("Vacation Group"), PARTY_GROUP("Party Group"),
    CUSTOM("Custom")
}

@Serializable
enum class InterestLevel { NOT_INTERESTED, CURIOUS, INTERESTED, HIGH_INTEREST }

@Serializable
enum class PrivatePace { SLOW, BALANCED, FAST }

@Serializable
enum class PrivateIntensity { GENTLE, PASSIONATE, INTENSE }

@Serializable
enum class PrivateInitiation { RARELY, SOMETIMES, OFTEN }

@Serializable
enum class PrivacyPreference { PRIVATE, FLEXIBLE, ADVENTUROUS }

@Serializable
data class SocialProfile(
    val communicationStyle: String = "Soft-spoken",
    val flirtingStyle: String = "Slow Burn",
    val affectionStyle: String = "Thoughtful",
    val attentionStyle: String = "Focused"
)

@Serializable
data class PrivateProfile(
    val pace: PrivatePace = PrivatePace.SLOW,
    val intensity: PrivateIntensity = PrivateIntensity.GENTLE,
    val style: Set<String> = setOf("Romantic"),
    val initiation: PrivateInitiation = PrivateInitiation.SOMETIMES,
    val privacy: PrivacyPreference = PrivacyPreference.PRIVATE
)

@Serializable
data class DesireProfile(
    val interests: Map<String, InterestLevel> = emptyMap()
) {
    fun level(category: String): InterestLevel = interests[category] ?: InterestLevel.CURIOUS
    /** An empty profile means preferences have not yet been established, not a refusal. */
    fun compatibleWith(other: DesireProfile): Boolean {
        if (interests.isEmpty() || other.interests.isEmpty()) return true
        return interests.keys
            .filter { level(it) != InterestLevel.NOT_INTERESTED }
            .any { other.level(it) != InterestLevel.NOT_INTERESTED }
    }
}

@Serializable
data class Circle(
    val id: String = "",
    val name: String,
    val characterIds: List<String>,
    val startingRelationship: StartingRelationship = StartingRelationship.PARTY_GROUP,
    val configuration: Map<String, String> = emptyMap()
)

@Serializable
data class ScenarioDefinition(
    val id: String,
    val name: String,
    val description: String,
    val locations: List<String>,
    val minimumParticipants: Int,
    val maximumParticipants: Int,
    val phases: List<String>,
    val actions: List<String>,
    val heatModifier: Int = 0,
    val roleplayCompatible: List<String> = emptyList()
)

@Serializable
data class RoleDefinition(
    val id: String,
    val name: String,
    val description: String,
    val moodTags: Set<String> = emptySet()
)

@Serializable
data class DirectorCard(
    val id: String,
    val name: String,
    val description: String,
    val effect: Map<String, String>,
    val requiredHeat: Int = 0
)

@Serializable
data class DirectorCardState(
    val cardId: String,
    val applied: Boolean = false,
    val available: Boolean = true,
    val appliedAtEvent: Int? = null
)

@Serializable
data class StoryProgression(
    val chapter: Int = 1,
    val phase: String = "arrival",
    val currentLocation: String = "",
    val mood: String = "curious",
    val sceneNumber: Int = 1,
    val completedBeats: Set<String> = emptySet(),
    val nextOptions: Set<String> = setOf("CONTINUE NIGHT", "CHANGE LOCATION", "RELATIONSHIP WEB")
)

@Serializable
data class PrivateMomentState(
    val focusedCharacterId: String? = null,
    val location: String? = null,
    val active: Boolean = false,
    val explicitlyOptedIn: Boolean = false
)

const val PLAYER_PARTICIPANT = "PLAYER"
fun relationshipKey(sourceId: String, targetId: String): String = "$sourceId->$targetId"

fun heatLabel(heat: Int): String = when (heat.coerceIn(0, 100)) {
    in 0..20 -> "SOCIAL"
    in 21..40 -> "FLIRTY"
    in 41..60 -> "CHEMISTRY"
    in 61..80 -> "INTENSE"
    else -> "AFTER DARK"
}

val allTraitNames = listOf(
    "Shy", "Confident", "Mysterious", "Flirty", "Playful", "Romantic", "Reserved", "Teasing", "Bold",
    "Curious", "Adventurous", "Competitive", "Protective", "Independent", "Jealous", "Emotionally Expressive",
    "Emotionally Guarded", "Calm", "Unpredictable", "Intense", "Slow Burn", "Social", "Private", "Impulsive",
    "Patient", "Risk Taking", "Observant"
)

val desireCategories = listOf(
    "Romantic", "Slow Burn", "High Intensity", "Power Dynamic", "Control", "Submission", "Experimental",
    "Risk / Thrill", "Voyeuristic Fantasy", "Exhibition Fantasy", "Jealousy Fantasy", "Secret Attraction",
    "Multiple-Partner Curiosity", "Roleplay", "Unknown Stranger", "Rival Attraction", "Public/Private Personality Contrast"
)

val privateMomentLocations = listOf("Balcony", "Kitchen", "Garden", "Hallway", "Private Lounge", "Poolside", "Terrace")

val starterRoles = listOf(
    RoleDefinition("mysterious_stranger", "Mysterious Stranger", "Keeps details just out of reach.", setOf("mystery")),
    RoleDefinition("confident_stranger", "Confident Stranger", "Makes a clear first impression.", setOf("bold")),
    RoleDefinition("celebrity", "Celebrity", "Draws attention without owning the room.", setOf("spotlight")),
    RoleDefinition("vip", "VIP", "Moves through the room with poise.", setOf("status")),
    RoleDefinition("bodyguard", "Bodyguard", "Notices every change in the room.", setOf("protective")),
    RoleDefinition("rival", "Rival", "Turns friction into a lively challenge.", setOf("competition")),
    RoleDefinition("royal", "Royal", "Adds a theatrical sense of occasion.", setOf("elegant")),
    RoleDefinition("spy", "Spy", "Performs confidence while holding a secret.", setOf("mystery")),
    RoleDefinition("double_agent", "Double Agent", "Changes loyalties as the story develops.", setOf("unpredictable")),
    RoleDefinition("detective", "Detective", "Follows social clues.", setOf("observant")),
    RoleDefinition("suspect", "Suspect", "Has a story to protect.", setOf("guarded")),
    RoleDefinition("vampire", "Vampire", "A gothic theatrical role, never a literal claim.", setOf("intense")),
    RoleDefinition("hunter", "Hunter", "Brings focused resolve to a fictional role.", setOf("focused")),
    RoleDefinition("host", "Host", "Shapes the night without controlling people.", setOf("social")),
    RoleDefinition("vacation_stranger", "Vacation Stranger", "Makes a fleeting night feel significant.", setOf("adventurous")),
    RoleDefinition("old_flame", "Old Flame", "Carries a shared history into the scene.", setOf("history")),
    RoleDefinition("secret_crush", "Secret Crush", "Hides feeling in plain sight.", setOf("secret"))
)

val starterDirectorCards = listOf(
    DirectorCard("lights_out", "LIGHTS OUT", "The room dims; everyone chooses their own response.", mapOf("mood" to "mysterious")),
    DirectorCard("secret_message", "SECRET MESSAGE", "A message introduces uncertainty, not a command.", mapOf("event" to "message")),
    DirectorCard("change_location", "CHANGE LOCATION", "The group receives a chance to move.", mapOf("event" to "location_offer")),
    DirectorCard("unexpected_guest", "UNEXPECTED GUEST", "A new presence changes the social rhythm.", mapOf("event" to "arrival")),
    DirectorCard("two_alone", "TWO CHARACTERS ALONE", "Two people get a moment away from the group.", mapOf("event" to "private_chat")),
    DirectorCard("jealousy", "MOMENT OF JEALOUSY", "A glance changes how someone reads attention.", mapOf("emotion" to "jealousy")),
    DirectorCard("role_reversal", "ROLE REVERSAL", "The social script turns on its head.", mapOf("event" to "role_shift")),
    DirectorCard("takes_initiative", "CHARACTER TAKES INITIATIVE", "A character may choose to make a move.", mapOf("event" to "initiative")),
    DirectorCard("hidden_secret", "HIDDEN SECRET", "A clue reveals something small but meaningful.", mapOf("event" to "discovery")),
    DirectorCard("private_invitation", "PRIVATE INVITATION", "An optional invitation appears only when trust allows.", mapOf("event" to "private_offer"), 48)
)

val starterScenarios = listOf(
    ScenarioDefinition("0d0bda7e-0001-4db0-8c00-000000000001", "Blind Date", "Discover a fictional adult through timing, observation, and actions.", DateLocation.entries.map { it.label }, 1, 1, listOf("arrival", "first_meeting", "chemistry", "decision"), listOf("observe", "flirt", "tease", "invite_continue"), 4, listOf("Mysterious Stranger", "Confident Stranger", "Secret Crush")),
    ScenarioDefinition("0d0bda7e-0002-4db0-8c00-000000000002", "House Party", "A living group scene where attention and alliances shift.", listOf("Living Room", "Kitchen", "Garden", "Balcony"), 2, 5, listOf("arrival", "group_spark", "friction", "after_hours"), listOf("join", "focus_character", "shift_attention", "director_card"), 2, listOf("Host", "Rival", "VIP")),
    ScenarioDefinition("0d0bda7e-0003-4db0-8c00-000000000003", "Masquerade", "Masks and roles add mystery without replacing base personality.", listOf("Grand Hall", "Terrace", "Moonlit Bar"), 1, 5, listOf("masks_on", "first_clue", "reveal", "choice"), listOf("observe", "flirt", "start_roleplay", "director_card"), 3, listOf("Spy", "Detective", "Royal")),
    ScenarioDefinition("0d0bda7e-0004-4db0-8c00-000000000004", "Late Night Lounge", "A consent-led lounge scene with room for emotional clarity.", listOf("Cocktail Lounge", "Terrace", "Private Lounge"), 1, 3, listOf("warmup", "chemistry", "private_offer", "aftermath"), listOf("observe", "flirt", "move_closer", "invite_continue"), 8, listOf("VIP", "Mysterious Stranger", "Old Flame")),
    ScenarioDefinition("e1d10000-0005-4db0-8c00-000000000005", "Luxury Villa", "A polished group night with private spaces and shifting alliances.", listOf("Poolside", "Terrace", "Private Lounge"), 2, 5, listOf("arrival", "dinner", "late_hours"), listOf("join", "change_music", "private_moment"), 4, listOf("VIP", "Bodyguard", "Royal")),
    ScenarioDefinition("e1d10000-0006-4db0-8c00-000000000006", "Vacation Resort", "A warm, temporary world where first impressions matter.", listOf("Resort", "Beach Bar", "Poolside"), 1, 5, listOf("check_in", "sunset", "night"), listOf("observe", "start_activity", "change_location"), 3, listOf("Vacation Stranger", "Secret Crush")),
    ScenarioDefinition("e1d10000-0007-4db0-8c00-000000000007", "Beach House", "A relaxed social scene that can become emotionally complicated.", listOf("Deck", "Kitchen", "Beach", "Garden"), 2, 5, listOf("arrival", "shared_activity", "night"), listOf("join", "tease", "let_them_talk"), 2, listOf("Rival", "Old Flame")),
    ScenarioDefinition("e1d10000-0008-4db0-8c00-000000000008", "Road Trip", "Close quarters make attention and pacing meaningful.", listOf("Roadside Stop", "Hotel Lounge", "Lookout"), 1, 5, listOf("drive", "stop", "night"), listOf("change_music", "change_mood", "observe"), 1, listOf("Rival", "Bodyguard")),
    ScenarioDefinition("e1d10000-0009-4db0-8c00-000000000009", "Club Afterparty", "High energy with clear boundaries and multiple social currents.", listOf("Dance Floor", "Lounge", "Terrace"), 2, 5, listOf("arrival", "energy", "wind_down"), listOf("flirt", "shift_attention", "step_back"), 6, listOf("Celebrity", "VIP", "Spy")),
    ScenarioDefinition("e1d10000-0010-4db0-8c00-000000000010", "Weekend Getaway", "A longer story with changing locations and consequences.", listOf("Cabin", "Garden", "Private Dinner", "Terrace"), 1, 5, listOf("arrival", "night", "next_morning", "next_day"), listOf("continue_night", "next_morning", "next_day"), 2, listOf("Old Flame", "Secret Crush", "Vacation Stranger")),
    ScenarioDefinition("e1d10000-0011-4db0-8c00-000000000011", "Mysterious Strangers", "A roleplay-focused night of observation and carefully earned reveals.", listOf("Lounge", "Balcony", "Private Bar"), 1, 5, listOf("cover", "clue", "reveal"), listOf("observe", "start_roleplay", "director_card"), 3, listOf("Mysterious Stranger", "Spy", "Double Agent")),
    ScenarioDefinition("e1d10000-0012-4db0-8c00-000000000012", "Rivals at Midnight", "Competition, attraction, and consent-led choices share the room.", listOf("Rooftop", "Games Room", "Terrace"), 1, 5, listOf("challenge", "shift", "choice"), listOf("tease", "start_activity", "step_back"), 3, listOf("Rival", "Detective", "Royal"))
)
