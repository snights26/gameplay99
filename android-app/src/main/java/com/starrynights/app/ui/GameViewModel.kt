package com.starrynights.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starrynights.app.data.ApiFactory
import com.starrynights.app.data.ApiConfiguration
import com.starrynights.app.data.CloudGameRepository
import com.starrynights.app.data.GameRepository
import com.starrynights.app.engine.KotlinGameEngine
import com.starrynights.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

data class AppUiState(
    val loading: Boolean = true,
    val userId: String? = null,
    val consentKnown: Boolean = false,
    val hasConsent: Boolean = false,
    val backendMessage: String? = null,
    val characters: List<FictionalAdult> = demoAdults(),
    val selectedCharacter: FictionalAdult = demoAdults().first(),
    val selectedLocation: DateLocation = DateLocation.CAFE,
    val scenarios: List<ScenarioDefinition> = starterScenarios,
    val selectedScenario: ScenarioDefinition = starterScenarios.first(),
    val selectedStartingRelationship: StartingRelationship = StartingRelationship.COMPLETE_STRANGERS,
    val circles: List<Circle> = emptyList(),
    val selectedCircle: Circle? = null,
    val assignedRoles: Map<String, String> = emptyMap(),
    val game: BlindDateState? = null,
    val sessionId: String? = null,
    val cloudCharacterIds: Set<String> = emptySet(),
    val saving: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GameRepository = CloudGameRepository(ApiFactory.create())
    private val engine = KotlinGameEngine()
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    init { bootstrap() }

    fun retryConnection() = bootstrap()

    private fun bootstrap() = viewModelScope.launch {
        ApiConfiguration.validationMessage()?.let { message ->
            _ui.value = _ui.value.copy(loading = false, consentKnown = true, backendMessage = message)
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, backendMessage = null)
        try {
            val result = withContext(Dispatchers.IO) { repository.bootstrap(getApplication()) }
            val characters = withContext(Dispatchers.IO) { repository.loadCharacters(result.userId) }
            val circles = if (result.hasConsent) withContext(Dispatchers.IO) { repository.loadCircles(result.userId) } else emptyList()
            val activeSession = if (result.hasConsent) withContext(Dispatchers.IO) { repository.resumeActiveSession(result.userId) } else null
            val restoreAttempt: Result<BlindDateState>? = activeSession?.currentState?.let { snapshot ->
                runCatching { Json { ignoreUnknownKeys = true }.decodeFromJsonElement<BlindDateState>(snapshot) }
            }
            val restoredGame = restoreAttempt?.getOrNull()
            val restoreMessage = if (restoreAttempt?.exceptionOrNull() != null) {
                "The saved story could not be restored. Your cloud library is still available; begin a new story without exposing hidden traits."
            } else null
            _ui.value = _ui.value.copy(
                loading = false, userId = result.userId, consentKnown = true, hasConsent = result.hasConsent,
                characters = if (characters.isEmpty()) _ui.value.characters else characters,
                selectedCharacter = restoredGame?.character ?: characters.firstOrNull() ?: _ui.value.selectedCharacter,
                selectedLocation = restoredGame?.location ?: _ui.value.selectedLocation,
                selectedScenario = restoredGame?.scenario ?: _ui.value.selectedScenario,
                selectedStartingRelationship = restoredGame?.relationshipStart ?: _ui.value.selectedStartingRelationship,
                cloudCharacterIds = characters.map(FictionalAdult::id).toSet(),
                circles = circles,
                selectedCircle = restoredGame?.circle ?: circles.firstOrNull(),
                assignedRoles = restoredGame?.assignedRoles.orEmpty(),
                game = restoredGame,
                sessionId = activeSession?.id,
                backendMessage = restoreMessage
            )
        } catch (_: Exception) {
            _ui.value = _ui.value.copy(
                loading = false, consentKnown = true, hasConsent = false,
                backendMessage = "Cloud connection is unavailable. Retry when the API is reachable; important progress is saved only after a successful cloud sync."
            )
        }
    }

    fun acceptConsent() = viewModelScope.launch {
        val userId = _ui.value.userId
        if (userId == null) {
            _ui.value = _ui.value.copy(backendMessage = "Connect the trial API before consent can be recorded.")
            return@launch
        }
        try {
            withContext(Dispatchers.IO) { repository.recordConsent(userId) }
            _ui.value = _ui.value.copy(hasConsent = true, backendMessage = null)
        } catch (_: Exception) {
            _ui.value = _ui.value.copy(backendMessage = "Consent could not be saved. Check the API address and try again.")
        }
    }

    fun selectCharacter(character: FictionalAdult) { _ui.value = _ui.value.copy(selectedCharacter = character) }
    fun selectLocation(location: DateLocation) { _ui.value = _ui.value.copy(selectedLocation = location) }
    fun selectScenario(scenario: ScenarioDefinition) { _ui.value = _ui.value.copy(selectedScenario = scenario) }
    fun selectStartingRelationship(context: StartingRelationship) { _ui.value = _ui.value.copy(selectedStartingRelationship = context) }
    fun selectCircle(circle: Circle?) { _ui.value = _ui.value.copy(selectedCircle = circle) }
    fun assignRole(characterId: String, roleId: String?) {
        val roles = if (roleId == null) _ui.value.assignedRoles - characterId else _ui.value.assignedRoles + (characterId to roleId)
        _ui.value = _ui.value.copy(assignedRoles = roles)
        _ui.value.game?.let { game -> updateGame(game.copy(assignedRoles = roles), "ROLE_ASSIGNMENT") }
    }

    fun addCharacter(character: FictionalAdult) {
        require(character.isAdult) { "All fictional characters must be adults." }
        _ui.value = _ui.value.copy(characters = _ui.value.characters + character, selectedCharacter = character)
        val userId = _ui.value.userId ?: return
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { repository.saveCharacter(userId, character) }
                saved.id?.let { cloudId ->
                    val cloudCharacter = character.copy(id = cloudId)
                    _ui.value = _ui.value.copy(
                        characters = _ui.value.characters.map { if (it.id == character.id) cloudCharacter else it },
                        selectedCharacter = if (_ui.value.selectedCharacter.id == character.id) cloudCharacter else _ui.value.selectedCharacter,
                        cloudCharacterIds = _ui.value.cloudCharacterIds + cloudId
                    )
                }
            }
            catch (_: Exception) { _ui.value = _ui.value.copy(backendMessage = "Character created for this session, but cloud save failed.") }
        }
    }

    fun beginBlindDate() = beginStory(
        participants = listOf(_ui.value.selectedCharacter), scenario = _ui.value.selectedScenario.takeIf { it.minimumParticipants == 1 } ?: starterScenarios.first(),
        mode = StoryMode.ONE_ON_ONE, circle = null, roles = emptyMap()
    )

    fun beginCircleStory() {
        val circle = _ui.value.selectedCircle
        val selected = circle?.characterIds?.mapNotNull { id -> _ui.value.characters.firstOrNull { it.id == id } }.orEmpty()
        if (selected.size !in 2..5) {
            _ui.value = _ui.value.copy(backendMessage = "Choose a saved Circle with two to five fictional adults before starting a group scene.")
            return
        }
        beginStory(selected, _ui.value.selectedScenario, StoryMode.CIRCLE, circle, _ui.value.assignedRoles.filterKeys { key -> key in selected.map(FictionalAdult::id) })
    }

    private fun beginStory(
        participants: List<FictionalAdult>, scenario: ScenarioDefinition, mode: StoryMode, circle: Circle?, roles: Map<String, String>
    ) = viewModelScope.launch {
        val userId = _ui.value.userId
        if (userId == null || !_ui.value.hasConsent) {
            _ui.value = _ui.value.copy(backendMessage = "Adult consent and the trial API are required before starting a cloud story.")
            return@launch
        }
        _ui.value = _ui.value.copy(saving = true, backendMessage = null)
        try {
            val cloudParticipants = withContext(Dispatchers.IO) { ensureCloudCharacters(userId, participants) }
            val cloudIdByOriginalId = participants.zip(cloudParticipants).associate { (original, persisted) -> original.id to persisted.id }
            val remappedRoles = roles.mapNotNull { (oldId, role) ->
                cloudIdByOriginalId[oldId]?.let { it to role }
            }.toMap()
            val persistedCircle = if (mode == StoryMode.CIRCLE && circle != null) {
                val remapped = circle.copy(characterIds = cloudParticipants.map(FictionalAdult::id))
                withContext(Dispatchers.IO) { repository.saveCircle(userId, remapped) }
            } else null
            val start = engine.startStory(
                cloudParticipants, scenario, _ui.value.selectedLocation, mode, _ui.value.selectedStartingRelationship,
                persistedCircle, remappedRoles
            )
            _ui.value = _ui.value.copy(
                game = start, selectedCharacter = cloudParticipants.first(),
                circles = if (persistedCircle == null) _ui.value.circles else _ui.value.circles.map { if (it.id == persistedCircle.id || it.id == circle?.id) persistedCircle else it }.ifEmpty { listOf(persistedCircle) },
                selectedCircle = persistedCircle ?: _ui.value.selectedCircle,
                assignedRoles = remappedRoles
            )
            val session = withContext(Dispatchers.IO) { repository.createStorySession(userId, cloudParticipants.map(FictionalAdult::id), start) }
            _ui.value = _ui.value.copy(sessionId = session.id)
            withContext(Dispatchers.IO) { repository.syncSession(userId, session.id, start, "SESSION_STARTED") }
        } catch (_: Exception) {
            _ui.value = _ui.value.copy(backendMessage = "The story could not be saved to the cloud. Check the API and try again.")
        } finally {
            _ui.value = _ui.value.copy(saving = false)
        }
    }

    fun saveCircle(name: String, memberIds: List<String>, relationshipStart: StartingRelationship) = viewModelScope.launch {
        if (memberIds.size !in 2..5) {
            _ui.value = _ui.value.copy(backendMessage = "A Circle needs two to five fictional adults.")
            return@launch
        }
        val userId = _ui.value.userId ?: run {
            _ui.value = _ui.value.copy(backendMessage = "Connect the trial API before saving a Circle.")
            return@launch
        }
        _ui.value = _ui.value.copy(saving = true)
        try {
            val members = _ui.value.characters.filter { it.id in memberIds }
            val cloudMembers = withContext(Dispatchers.IO) { ensureCloudCharacters(userId, members) }
            val saved = withContext(Dispatchers.IO) {
                repository.saveCircle(userId, Circle(id = _ui.value.selectedCircle?.id.orEmpty(), name = name.ifBlank { "My Circle" }, characterIds = cloudMembers.map(FictionalAdult::id), startingRelationship = relationshipStart))
            }
            _ui.value = _ui.value.copy(circles = (_ui.value.circles.filterNot { it.id == saved.id } + saved), selectedCircle = saved, backendMessage = null)
        } catch (_: Exception) {
            _ui.value = _ui.value.copy(backendMessage = "Circle save failed. Your selections are still on screen; try again when the API is available.")
        } finally {
            _ui.value = _ui.value.copy(saving = false)
        }
    }

    fun perform(action: GameAction, timing: Float, targetId: String? = _ui.value.game?.focusCharacterId) { _ui.value.game?.let { updateGame(engine.perform(it, action, timing, targetId), "PLAYER_${action.name}") } }
    fun focusCharacter(characterId: String) { _ui.value.game?.let { updateGame(engine.focusCharacter(it, characterId), "FOCUS_CHARACTER") } }
    fun applyDirectorCard(card: DirectorCard) { _ui.value.game?.let { updateGame(engine.applyDirectorCard(it, card), "DIRECTOR_${card.id.uppercase()}") } }
    fun continueStory(action: GameAction) { _ui.value.game?.let { updateGame(engine.continueStory(it, action), "STORY_${action.name}") } }
    fun returnToGroup() { _ui.value.game?.let { updateGame(engine.returnToGroup(it), "RETURN_TO_GROUP") } }
    fun enterPrivateMoment(location: String = privateMomentLocations.first()) { _ui.value.game?.let { updateGame(engine.enterPrivateMoment(it, location), "PRIVATE_MOMENT_STARTED") } }
    fun selectIntimacyCard(card: IntimacyCard) { _ui.value.game?.let { updateGame(engine.selectCard(it, card), "INTIMACY_CARD_SELECTED") } }
    fun privateChoice(choice: PrivateChoice) { _ui.value.game?.let { updateGame(engine.privateChoice(it, choice), "PRIVATE_${choice.name}") } }
    fun availableCards(): List<IntimacyCard> = _ui.value.game?.let(engine::availableIntimacyCards).orEmpty()

    private fun updateGame(game: BlindDateState, eventType: String) {
        _ui.value = _ui.value.copy(game = game)
        val userId = _ui.value.userId ?: return
        val sessionId = _ui.value.sessionId ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repository.syncSession(userId, sessionId, game, eventType) } }
            catch (_: Exception) { _ui.value = _ui.value.copy(backendMessage = "The latest scene update could not be synced yet.") }
        }
    }

    private suspend fun ensureCloudCharacters(userId: String, characters: List<FictionalAdult>): List<FictionalAdult> {
        val resolved = characters.map { character ->
            if (character.id in _ui.value.cloudCharacterIds) character else {
                val saved = repository.saveCharacter(userId, character)
                character.copy(id = requireNotNull(saved.id) { "Cloud character save did not return an id." })
            }
        }
        val idByOriginal = characters.zip(resolved).toMap()
        val allCharacters = _ui.value.characters.map { existing -> idByOriginal[existing]?.let { it } ?: existing }
        _ui.value = _ui.value.copy(
            characters = allCharacters,
            selectedCharacter = idByOriginal[_ui.value.selectedCharacter] ?: _ui.value.selectedCharacter,
            cloudCharacterIds = _ui.value.cloudCharacterIds + resolved.map(FictionalAdult::id)
        )
        return resolved
    }
}

