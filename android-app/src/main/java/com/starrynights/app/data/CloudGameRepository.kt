package com.starrynights.app.data

import android.content.Context
import android.provider.Settings
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.starrynights.app.model.BlindDateState
import com.starrynights.app.model.Boundary
import com.starrynights.app.model.Circle
import com.starrynights.app.model.DesireProfile
import com.starrynights.app.model.FictionalAdult
import com.starrynights.app.model.InterestLevel
import com.starrynights.app.model.PrivateInitiation
import com.starrynights.app.model.PrivateIntensity
import com.starrynights.app.model.PrivatePace
import com.starrynights.app.model.PrivateProfile
import com.starrynights.app.model.PrivacyPreference
import com.starrynights.app.model.SocialProfile
import com.starrynights.app.model.StartingRelationship
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import java.util.UUID

@Serializable data class BootstrapRequest(val installId: String)
@Serializable data class BootstrapResponse(val userId: String, val hasConsent: Boolean = false)
@Serializable data class ConsentRequest(val ageConfirmed: Boolean, val dataStorageConsent: Boolean, val consentVersion: String)
@Serializable data class CloudCharacter(
    val id: String? = null, val name: String, val age: Int, val gender: String, val pronouns: String? = null,
    val avatarKey: String? = null, val description: String? = null,
    val personality: JsonObject, val communicationProfile: JsonObject, val flirtProfile: JsonObject,
    val intimacyProfile: JsonObject, val fantasyProfile: JsonObject, val boundaries: JsonObject,
    val hiddenTraits: JsonObject = buildJsonObject {}
)
@Serializable data class CloudCircleMember(val id: String, val name: String = "", val displayOrder: Int)
@Serializable data class CloudCircle(
    val id: String? = null,
    val name: String,
    val startingRelationship: String = StartingRelationship.PARTY_GROUP.name,
    val configuration: JsonObject = buildJsonObject {},
    /** Request field expected by the API. Responses additionally include rich member records below. */
    val characterIds: List<String> = emptyList(),
    val members: List<CloudCircleMember> = emptyList()
)
@Serializable data class CloudSession(
    val id: String,
    val status: String = "ACTIVE",
    @SerialName("current_state") val currentState: JsonObject? = null
)
@Serializable data class GameEventPayload(
    val clientEventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val payload: JsonObject,
    val actorParticipantId: String? = null,
    val targetParticipantId: String? = null
)
@Serializable data class SyncRequest(val state: JsonObject, val events: List<GameEventPayload> = emptyList())
@Serializable data class SyncResponse(val sessionId: String, val syncedAt: String, val eventsPersisted: Int)
@Serializable data class SessionRequest(
    val scenarioId: String, val mode: String, val participantCharacterIds: List<String>, val relationshipStart: String,
    val circleId: String? = null, val state: JsonObject
)

interface StarryNightsApi {
    @POST("api/v1/trial/bootstrap") suspend fun bootstrap(@Body request: BootstrapRequest): BootstrapResponse
    @POST("api/v1/consent") suspend fun consent(@Header("X-Trial-User-Id") userId: String, @Body request: ConsentRequest)
    @GET("api/v1/characters") suspend fun characters(@Header("X-Trial-User-Id") userId: String): List<CloudCharacter>
    @POST("api/v1/characters") suspend fun createCharacter(@Header("X-Trial-User-Id") userId: String, @Body character: CloudCharacter): CloudCharacter
    @PUT("api/v1/characters/{id}") suspend fun updateCharacter(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String, @Body character: CloudCharacter): CloudCharacter
    @DELETE("api/v1/characters/{id}") suspend fun deleteCharacter(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String)
    @GET("api/v1/circles") suspend fun circles(@Header("X-Trial-User-Id") userId: String): List<CloudCircle>
    @POST("api/v1/circles") suspend fun createCircle(@Header("X-Trial-User-Id") userId: String, @Body circle: CloudCircle): CloudCircle
    @PUT("api/v1/circles/{id}") suspend fun updateCircle(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String, @Body circle: CloudCircle)
    @GET("api/v1/sessions/active") suspend fun activeSession(@Header("X-Trial-User-Id") userId: String): CloudSession?
    @POST("api/v1/sessions") suspend fun createSession(@Header("X-Trial-User-Id") userId: String, @Body request: SessionRequest): CloudSession
    @POST("api/v1/sessions/{id}/sync") suspend fun sync(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String, @Body snapshot: SyncRequest): SyncResponse
}

/** Important state goes through the API. No database credentials or save files live in the APK. */
interface GameRepository {
    suspend fun bootstrap(context: Context): BootstrapResponse
    suspend fun recordConsent(userId: String)
    suspend fun loadCharacters(userId: String): List<FictionalAdult>
    suspend fun saveCharacter(userId: String, character: FictionalAdult): CloudCharacter
    suspend fun loadCircles(userId: String): List<Circle>
    suspend fun saveCircle(userId: String, circle: Circle): Circle
    suspend fun resumeActiveSession(userId: String): CloudSession?
    suspend fun createStorySession(userId: String, characterIds: List<String>, state: BlindDateState): CloudSession
    suspend fun syncSession(userId: String, sessionId: String, state: BlindDateState, eventType: String): SyncResponse
}

class CloudGameRepository(private val api: StarryNightsApi) : GameRepository {
    override suspend fun bootstrap(context: Context): BootstrapResponse = api.bootstrap(BootstrapRequest(installId(context)))
    override suspend fun recordConsent(userId: String) = api.consent(userId, ConsentRequest(true, true, "trial-1"))
    override suspend fun loadCharacters(userId: String): List<FictionalAdult> = api.characters(userId).mapNotNull(CloudCharacter::toFictionalAdult)
    override suspend fun saveCharacter(userId: String, character: FictionalAdult): CloudCharacter = api.createCharacter(userId, character.toCloudCharacter())
    override suspend fun loadCircles(userId: String): List<Circle> = api.circles(userId).map { cloud ->
        Circle(
            id = cloud.id.orEmpty(), name = cloud.name, characterIds = cloud.members.sortedBy(CloudCircleMember::displayOrder).map(CloudCircleMember::id),
            startingRelationship = runCatching { StartingRelationship.valueOf(cloud.startingRelationship) }.getOrDefault(StartingRelationship.PARTY_GROUP),
            configuration = cloud.configuration.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull.orEmpty() }
        )
    }
    override suspend fun saveCircle(userId: String, circle: Circle): Circle {
        val cloud = circle.toCloudCircle()
        val result = if (circle.id.isBlank()) api.createCircle(userId, cloud) else {
            api.updateCircle(userId, circle.id, cloud)
            cloud.copy(id = circle.id)
        }
        return circle.copy(id = requireNotNull(result.id) { "Cloud circle save did not return an id." })
    }
    override suspend fun resumeActiveSession(userId: String): CloudSession? = api.activeSession(userId)
    override suspend fun createStorySession(userId: String, characterIds: List<String>, state: BlindDateState): CloudSession = api.createSession(
        userId, SessionRequest(state.scenario.id, state.mode.name, characterIds, state.relationshipStart.name, state.circle?.id?.ifBlank { null }, state.toSnapshot())
    )
    override suspend fun syncSession(userId: String, sessionId: String, state: BlindDateState, eventType: String): SyncResponse = api.sync(
        userId, sessionId, SyncRequest(state.toSnapshot(), listOf(GameEventPayload(eventId(state, eventType), eventType, buildJsonObject { put("source", "android"); put("scene", state.story.sceneNumber) })))
    )
}

/** Future offline build replacement; it intentionally does not persist any important state. */
class NoPersistenceGameRepository : GameRepository {
    override suspend fun bootstrap(context: Context) = BootstrapResponse("temporary-session", false)
    override suspend fun recordConsent(userId: String) = Unit
    override suspend fun loadCharacters(userId: String) = emptyList<FictionalAdult>()
    override suspend fun saveCharacter(userId: String, character: FictionalAdult) = character.toCloudCharacter().copy(id = character.id)
    override suspend fun loadCircles(userId: String) = emptyList<Circle>()
    override suspend fun saveCircle(userId: String, circle: Circle) = circle.copy(id = circle.id.ifBlank { UUID.randomUUID().toString() })
    override suspend fun resumeActiveSession(userId: String): CloudSession? = null
    override suspend fun createStorySession(userId: String, characterIds: List<String>, state: BlindDateState) = CloudSession("temporary-session")
    override suspend fun syncSession(userId: String, sessionId: String, state: BlindDateState, eventType: String) = SyncResponse(sessionId, "", 0)
}

object ApiFactory {
    fun create(): StarryNightsApi {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logger).connectTimeout(10, TimeUnit.SECONDS).build()
        return retrofit2.Retrofit.Builder()
            .baseUrl(ApiConfiguration.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(StarryNightsApi::class.java)
    }
}

private fun installId(context: Context): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

private fun FictionalAdult.toCloudCharacter(): CloudCharacter {
    fun strings(values: Map<String, String>): JsonObject = buildJsonObject { values.forEach { (key, value) -> put(key, value) } }
    return CloudCharacter(
        name = name, age = age, gender = gender, pronouns = pronouns.ifBlank { null }, description = description.ifBlank { null },
        avatarKey = avatarReference.ifBlank { null },
        personality = buildJsonObject { personality.forEach { (key, value) -> put(key, value) } },
        communicationProfile = strings(mapOf("style" to resolvedSocialProfile().communicationStyle, "affectionStyle" to resolvedSocialProfile().affectionStyle, "attentionStyle" to resolvedSocialProfile().attentionStyle)),
        flirtProfile = strings(mapOf("style" to resolvedSocialProfile().flirtingStyle)),
        intimacyProfile = strings(mapOf("energy" to intimacyEnergy, "pace" to resolvedPrivateProfile().pace.name, "intensity" to resolvedPrivateProfile().intensity.name, "style" to resolvedPrivateProfile().style.joinToString("|"), "initiation" to resolvedPrivateProfile().initiation.name, "privacy" to resolvedPrivateProfile().privacy.name)),
        fantasyProfile = strings(desireProfile.interests.mapValues { it.value.name }), boundaries = strings(boundaries.mapValues { it.value.name }),
        hiddenTraits = strings(hiddenTraits.associateWith { "UNKNOWN" })
    )
}

private fun CloudCharacter.toFictionalAdult(): FictionalAdult? {
    val cloudId = id ?: return null
    val traits = personality.mapNotNull { (key, value) -> value.jsonPrimitive.intOrNull?.let { key to it } }.toMap()
    return FictionalAdult(
        id = cloudId, name = name, age = age, gender = gender, pronouns = pronouns.orEmpty(), avatarReference = avatarKey.orEmpty(), description = description.orEmpty(),
        personality = traits.ifEmpty { mapOf("Mysterious" to 3) },
        communication = communicationProfile["style"]?.jsonPrimitive?.contentOrNull ?: "Soft-spoken",
        flirtingStyle = flirtProfile["style"]?.jsonPrimitive?.contentOrNull ?: "Slow Burn",
        intimacyEnergy = intimacyProfile["energy"]?.jsonPrimitive?.contentOrNull ?: "Romantic",
        pace = intimacyProfile["pace"]?.jsonPrimitive?.contentOrNull ?: "Slow",
        socialProfile = parseSocialProfile(communicationProfile, flirtProfile),
        privateProfile = parsePrivateProfile(intimacyProfile),
        desireProfile = parseDesireProfile(fantasyProfile),
        boundaries = boundaries.mapNotNull { (key, value) -> runCatching { Boundary.valueOf(value.jsonPrimitive.content) }.getOrNull()?.let { key to it } }.toMap(),
        hiddenTraits = hiddenTraits.filterValues { it.jsonPrimitive.contentOrNull == "UNKNOWN" }.keys
    )
}

private fun BlindDateState.toSnapshot(): JsonObject = Json.encodeToJsonElement(this).jsonObject
private fun Circle.toCloudCircle(): CloudCircle = CloudCircle(
    id = id.ifBlank { null }, name = name, startingRelationship = startingRelationship.name,
    configuration = buildJsonObject { configuration.forEach { (key, value) -> put(key, value) } },
    characterIds = characterIds
)
private inline fun <reified T : Enum<T>> enumValue(values: JsonObject, key: String, fallback: T): T =
    values[key]?.jsonPrimitive?.contentOrNull?.let { raw -> enumValues<T>().firstOrNull { it.name == raw.uppercase().replace(' ', '_') } } ?: fallback
private fun parseSocialProfile(communication: JsonObject, flirt: JsonObject): SocialProfile = SocialProfile(
    communicationStyle = communication["style"]?.jsonPrimitive?.contentOrNull ?: "Soft-spoken",
    flirtingStyle = flirt["style"]?.jsonPrimitive?.contentOrNull ?: "Slow Burn",
    affectionStyle = communication["affectionStyle"]?.jsonPrimitive?.contentOrNull ?: "Thoughtful",
    attentionStyle = communication["attentionStyle"]?.jsonPrimitive?.contentOrNull ?: "Focused"
)
private fun parsePrivateProfile(values: JsonObject): PrivateProfile {
    val styles = values["style"]?.jsonPrimitive?.contentOrNull.orEmpty().split('|').filter(String::isNotBlank).toSet()
    return PrivateProfile(
        pace = enumValue(values, "pace", PrivatePace.SLOW),
        intensity = enumValue(values, "intensity", PrivateIntensity.GENTLE),
        style = styles.ifEmpty { setOf("Romantic") },
        initiation = enumValue(values, "initiation", PrivateInitiation.SOMETIMES),
        privacy = enumValue(values, "privacy", PrivacyPreference.PRIVATE)
    )
}
private fun parseDesireProfile(values: JsonObject): DesireProfile = DesireProfile(
    values.mapNotNull { (key, value) -> runCatching { InterestLevel.valueOf(value.jsonPrimitive.content) }.getOrNull()?.let { key to it } }.toMap()
)
private fun eventId(state: BlindDateState, eventType: String): String =
    state.log.lastOrNull()?.id ?: UUID.nameUUIDFromBytes("${eventType}:${state.story.sceneNumber}".toByteArray()).toString()

