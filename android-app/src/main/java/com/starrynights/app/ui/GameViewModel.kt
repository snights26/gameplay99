package com.starrynights.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starrynights.app.data.ApiFactory
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
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

data class AppUiState(
    val loading: Boolean = true,
    val userId: String? = null,
    val consentKnown: Boolean = false,
    val hasConsent: Boolean = false,
    val backendMessage: String? = null,
    val characters: List<FictionalAdult> = demoAdults(),
    val selectedCharacter: FictionalAdult = demoAdults().first(),
    val selectedLocation: DateLocation = DateLocation.CAFE,
    val game: BlindDateState? = null,
    val sessionId: String? = null,
    val cloudCharacterIds: Set<String> = emptySet()
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GameRepository = CloudGameRepository(ApiFactory.create())
    private val engine = KotlinGameEngine()
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    init { bootstrap() }

    private fun bootstrap() = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) { repository.bootstrap(getApplication()) }
            val characters = withContext(Dispatchers.IO) { repository.loadCharacters(result.userId) }
            val activeSession = if (result.hasConsent) withContext(Dispatchers.IO) { repository.resumeActiveSession(result.userId) } else null
            val restoredGame = activeSession?.currentState?.let { snapshot -> runCatching { Json.decodeFromJsonElement<BlindDateState>(snapshot) }.getOrNull() }
            _ui.value = _ui.value.copy(
                loading = false, userId = result.userId, consentKnown = true, hasConsent = result.hasConsent,
                characters = if (characters.isEmpty()) _ui.value.characters else characters,
                selectedCharacter = characters.firstOrNull() ?: _ui.value.selectedCharacter,
                cloudCharacterIds = characters.map(FictionalAdult::id).toSet(),
                game = restoredGame,
                sessionId = activeSession?.id
            )
        } catch (_: Exception) {
            _ui.value = _ui.value.copy(
                loading = false, consentKnown = true, hasConsent = false,
                backendMessage = "Cloud sync is unavailable. You can explore this session, but important progress cannot be saved until the API is configured."
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

    fun beginBlindDate() {
        val start = engine.start(_ui.value.selectedCharacter, _ui.value.selectedLocation)
        _ui.value = _ui.value.copy(game = start)
        val userId = _ui.value.userId
        if (userId != null) viewModelScope.launch {
            try {
                val characterId = if (start.character.id in _ui.value.cloudCharacterIds) {
                    start.character.id
                } else {
                    val saved = withContext(Dispatchers.IO) { repository.saveCharacter(userId, start.character) }
                    val savedId = requireNotNull(saved.id) { "Cloud character save did not return an id." }
                    val cloudCharacter = start.character.copy(id = savedId)
                    _ui.value = _ui.value.copy(
                        characters = _ui.value.characters.map { if (it.id == start.character.id) cloudCharacter else it },
                        selectedCharacter = if (_ui.value.selectedCharacter.id == start.character.id) cloudCharacter else _ui.value.selectedCharacter,
                        cloudCharacterIds = _ui.value.cloudCharacterIds + savedId
                    )
                    savedId
                }
                val session = withContext(Dispatchers.IO) { repository.createBlindDateSession(userId, characterId, start) }
                _ui.value = _ui.value.copy(sessionId = session.id)
                val latestState = _ui.value.game ?: start
                withContext(Dispatchers.IO) { repository.syncSession(userId, session.id, Json.encodeToJsonElement(latestState).jsonObject, "SESSION_STARTED") }
            } catch (_: Exception) { _ui.value = _ui.value.copy(backendMessage = "The scene is playable, but its cloud session could not be created.") }
        }
    }
    fun perform(action: GameAction, timing: Float) { _ui.value.game?.let { updateGame(engine.perform(it, action, timing), "PLAYER_${action.name}") } }
    fun enterPrivateMoment() { _ui.value.game?.let { updateGame(engine.enterPrivateMoment(it), "PRIVATE_MOMENT_STARTED") } }
    fun selectIntimacyCard(card: IntimacyCard) { _ui.value.game?.let { updateGame(engine.selectCard(it, card), "INTIMACY_CARD_SELECTED") } }
    fun privateChoice(choice: PrivateChoice) { _ui.value.game?.let { updateGame(engine.privateChoice(it, choice), "PRIVATE_${choice.name}") } }
    fun availableCards(): List<IntimacyCard> = _ui.value.game?.let(engine::availableIntimacyCards).orEmpty()

    private fun updateGame(game: BlindDateState, eventType: String) {
        _ui.value = _ui.value.copy(game = game)
        val userId = _ui.value.userId ?: return
        val sessionId = _ui.value.sessionId ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repository.syncSession(userId, sessionId, Json.encodeToJsonElement(game).jsonObject, eventType) } }
            catch (_: Exception) { _ui.value = _ui.value.copy(backendMessage = "The latest scene update could not be synced yet.") }
        }
    }
}

