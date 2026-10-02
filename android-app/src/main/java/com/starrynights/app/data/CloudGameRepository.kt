package com.starrynights.app.data

import android.content.Context
import android.provider.Settings
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.starrynights.app.BuildConfig
import com.starrynights.app.model.BlindDateState
import com.starrynights.app.model.Boundary
import com.starrynights.app.model.FictionalAdult
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
@Serializable data class CloudSession(
    val id: String,
    val status: String = "ACTIVE",
    @SerialName("current_state") val currentState: JsonObject? = null
)
@Serializable data class GameEventPayload(
    val eventType: String,
    val payload: JsonObject,
    val actorParticipantId: String? = null,
    val targetParticipantId: String? = null
)
@Serializable data class SyncRequest(val state: JsonObject, val events: List<GameEventPayload> = emptyList())
@Serializable data class SessionRequest(val scenarioId: String, val mode: String, val participantCharacterIds: List<String>, val relationshipStart: String, val state: JsonObject)

interface StarryNightsApi {
    @POST("api/v1/trial/bootstrap") suspend fun bootstrap(@Body request: BootstrapRequest): BootstrapResponse
    @POST("api/v1/consent") suspend fun consent(@Header("X-Trial-User-Id") userId: String, @Body request: ConsentRequest)
    @GET("api/v1/characters") suspend fun characters(@Header("X-Trial-User-Id") userId: String): List<CloudCharacter>
    @POST("api/v1/characters") suspend fun createCharacter(@Header("X-Trial-User-Id") userId: String, @Body character: CloudCharacter): CloudCharacter
    @PUT("api/v1/characters/{id}") suspend fun updateCharacter(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String, @Body character: CloudCharacter): CloudCharacter
    @DELETE("api/v1/characters/{id}") suspend fun deleteCharacter(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String)
    @GET("api/v1/sessions/active") suspend fun activeSession(@Header("X-Trial-User-Id") userId: String): CloudSession?
    @POST("api/v1/sessions") suspend fun createSession(@Header("X-Trial-User-Id") userId: String, @Body request: SessionRequest): CloudSession
    @POST("api/v1/sessions/{id}/sync") suspend fun sync(@Header("X-Trial-User-Id") userId: String, @Path("id") id: String, @Body snapshot: SyncRequest)
}

/** Important state goes through the API. No database credentials or save files live in the APK. */
interface GameRepository {
    suspend fun bootstrap(context: Context): BootstrapResponse
    suspend fun recordConsent(userId: String)
    suspend fun loadCharacters(userId: String): List<FictionalAdult>
    suspend fun saveCharacter(userId: String, character: FictionalAdult): CloudCharacter
    suspend fun resumeActiveSession(userId: String): CloudSession?
    suspend fun createBlindDateSession(userId: String, characterId: String, state: BlindDateState): CloudSession
    suspend fun syncSession(userId: String, sessionId: String, state: JsonObject, eventType: String)
}

class CloudGameRepository(private val api: StarryNightsApi) : GameRepository {
    override suspend fun bootstrap(context: Context): BootstrapResponse = api.bootstrap(BootstrapRequest(installId(context)))
    override suspend fun recordConsent(userId: String) = api.consent(userId, ConsentRequest(true, true, "trial-1"))
    override suspend fun loadCharacters(userId: String): List<FictionalAdult> = api.characters(userId).mapNotNull(CloudCharacter::toFictionalAdult)
    override suspend fun saveCharacter(userId: String, character: FictionalAdult): CloudCharacter = api.createCharacter(userId, character.toCloud())
    override suspend fun resumeActiveSession(userId: String): CloudSession? = api.activeSession(userId)
    override suspend fun createBlindDateSession(userId: String, characterId: String, state: BlindDateState): CloudSession = api.createSession(userId, SessionRequest(BLIND_DATE_ID, "ONE_ON_ONE", listOf(characterId), "Complete Strangers", snapshot(state)))
    override suspend fun syncSession(userId: String, sessionId: String, state: JsonObject, eventType: String) = api.sync(
        userId, sessionId, SyncRequest(state, listOf(GameEventPayload(eventType, buildJsonObject { put("source", "android") })))
    )
}

/** Future offline build replacement; it intentionally does not persist any important state. */
class NoPersistenceGameRepository : GameRepository {
    override suspend fun bootstrap(context: Context) = BootstrapResponse("temporary-session", false)
    override suspend fun recordConsent(userId: String) = Unit
    override suspend fun loadCharacters(userId: String) = emptyList<FictionalAdult>()
    override suspend fun saveCharacter(userId: String, character: FictionalAdult) = character.toCloud().copy(id = character.id)
    override suspend fun resumeActiveSession(userId: String): CloudSession? = null
    override suspend fun createBlindDateSession(userId: String, characterId: String, state: BlindDateState) = CloudSession("temporary-session")
    override suspend fun syncSession(userId: String, sessionId: String, state: JsonObject, eventType: String) = Unit
}

object ApiFactory {
    fun create(): StarryNightsApi {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logger).connectTimeout(10, TimeUnit.SECONDS).build()
        return retrofit2.Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(StarryNightsApi::class.java)
    }
}

private fun installId(context: Context): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

private fun FictionalAdult.toCloud(): CloudCharacter {
    fun strings(values: Map<String, String>): JsonObject = buildJsonObject { values.forEach { (key, value) -> put(key, value) } }
    return CloudCharacter(
        name = name, age = age, gender = gender, pronouns = pronouns.ifBlank { null }, description = description.ifBlank { null },
        personality = buildJsonObject { personality.forEach { (key, value) -> put(key, value) } },
        communicationProfile = strings(mapOf("style" to communication)),
        flirtProfile = strings(mapOf("style" to flirtingStyle)), intimacyProfile = strings(mapOf("energy" to intimacyEnergy, "pace" to pace)),
        fantasyProfile = buildJsonObject {}, boundaries = strings(boundaries.mapValues { it.value.name }),
        hiddenTraits = strings(hiddenTraits.associateWith { "UNKNOWN" })
    )
}

private fun CloudCharacter.toFictionalAdult(): FictionalAdult? {
    val cloudId = id ?: return null
    val traits = personality.mapNotNull { (key, value) -> value.jsonPrimitive.intOrNull?.let { key to it } }.toMap()
    return FictionalAdult(
        id = cloudId, name = name, age = age, gender = gender, pronouns = pronouns.orEmpty(), description = description.orEmpty(),
        personality = traits.ifEmpty { mapOf("Mysterious" to 3) },
        communication = communicationProfile["style"]?.jsonPrimitive?.contentOrNull ?: "Soft-spoken",
        flirtingStyle = flirtProfile["style"]?.jsonPrimitive?.contentOrNull ?: "Slow Burn",
        intimacyEnergy = intimacyProfile["energy"]?.jsonPrimitive?.contentOrNull ?: "Romantic",
        pace = intimacyProfile["pace"]?.jsonPrimitive?.contentOrNull ?: "Slow",
        boundaries = boundaries.mapNotNull { (key, value) -> runCatching { Boundary.valueOf(value.jsonPrimitive.content) }.getOrNull()?.let { key to it } }.toMap(),
        hiddenTraits = hiddenTraits.filterValues { it.jsonPrimitive.contentOrNull == "UNKNOWN" }.keys
    )
}

private fun snapshot(state: BlindDateState): JsonObject = Json.encodeToJsonElement(state).jsonObject
private const val BLIND_DATE_ID = "0d0bda7e-0001-4db0-8c00-000000000001"

