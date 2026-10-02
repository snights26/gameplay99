package com.starrynights.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.starrynights.app.model.*
import kotlinx.coroutines.delay

private object Route {
    const val Consent = "consent"
    const val Splash = "splash"
    const val Home = "home"
    const val NewStory = "new-story"
    const val Creator = "creator"
    const val Library = "character-library"
    const val CircleBuilder = "circle-builder"
    const val ScenarioSelection = "scenario-selection"
    const val BlindDate = "blind-date"
    const val Scene = "scene"
    const val PrivateMoment = "private-moment"
    const val Roleplay = "roleplay"
    const val Aftermath = "aftermath"
    const val Web = "relationship-web"
}

private val Ink = StarryDesign.black
private val Panel = StarryDesign.charcoal
private val Purple = StarryDesign.purple
private val Magenta = StarryDesign.magenta
private val Red = StarryDesign.deepRed
private val TextSoft = StarryDesign.softText
private val StarryScheme = StarryDesign.scheme

@Composable
fun StarryNightsApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    MaterialTheme(colorScheme = StarryScheme) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            StarField()
            if (state.loading) LoadingScreen()
            else AppNav(nav, viewModel, state)
        }
    }
}

@Composable
private fun AppNav(nav: NavHostController, viewModel: GameViewModel, state: AppUiState) {
    NavHost(navController = nav, startDestination = Route.Consent, modifier = Modifier.fillMaxSize()) {
        composable(Route.Consent) { ConsentScreen(state, viewModel::acceptConsent, viewModel::retryConnection) { nav.navigate(Route.Splash) { popUpTo(Route.Consent) { inclusive = true } } } }
        composable(Route.Splash) { SplashScreen { nav.navigate(Route.Home) { popUpTo(Route.Splash) { inclusive = true } } } }
        composable(Route.Home) { HomeScreen(state, onNewStory = { nav.navigate(Route.NewStory) }, onBlindDate = { nav.navigate(Route.BlindDate) }, onResume = { nav.navigate(Route.Scene) }, onWeb = { nav.navigate(Route.Web) }, onLibrary = { nav.navigate(Route.Library) }, onScenarios = { nav.navigate(Route.ScenarioSelection) }, onRetry = viewModel::retryConnection) }
        composable(Route.NewStory) { NewStoryScreen(onBlindDate = { nav.navigate(Route.BlindDate) }, onCircle = { nav.navigate(Route.CircleBuilder) }, onCreator = { nav.navigate(Route.Creator) }, onScenarios = { nav.navigate(Route.ScenarioSelection) }, onBack = nav::popBackStack) }
        composable(Route.Creator) { CharacterCreatorScreen(onSave = { viewModel.addCharacter(it); nav.popBackStack() }, onBack = nav::popBackStack) }
        composable(Route.Library) { CharacterLibraryScreen(state.characters, onCreate = { nav.navigate(Route.Creator) }, onSelect = viewModel::selectCharacter, onBack = nav::popBackStack) }
        composable(Route.CircleBuilder) { CircleBuilderScreen(state, viewModel::selectCircle, viewModel::saveCircle, onCreate = { nav.navigate(Route.Creator) }, onStart = { viewModel.beginCircleStory(); nav.navigate(Route.Scene) }, onRoleplay = { nav.navigate(Route.Roleplay) }, onBack = nav::popBackStack) }
        composable(Route.ScenarioSelection) { ScenarioSelectionScreen(state, viewModel::selectScenario, onStartOneOnOne = { nav.navigate(Route.BlindDate) }, onStartCircle = { nav.navigate(Route.CircleBuilder) }, onBack = nav::popBackStack) }
        composable(Route.BlindDate) { BlindDateSetupScreen(state, viewModel::selectCharacter, viewModel::selectLocation, onCreate = { nav.navigate(Route.Creator) }, onStart = { viewModel.beginBlindDate(); nav.navigate(Route.Scene) }, onBack = nav::popBackStack) }
        composable(Route.Scene) { SceneScreen(state.game, state.saving, state.backendMessage, viewModel::perform, viewModel::focusCharacter, viewModel::applyDirectorCard, viewModel::continueStory, onPrivate = { location -> viewModel.enterPrivateMoment(location); nav.navigate(Route.PrivateMoment) }, onRoleplay = { nav.navigate(Route.Roleplay) }, onAftermath = { nav.navigate(Route.Aftermath) }, onWeb = { nav.navigate(Route.Web) }, onBack = nav::popBackStack) }
        composable(Route.PrivateMoment) { PrivateMomentScreen(state.game, viewModel.availableCards(), viewModel::selectIntimacyCard, viewModel::privateChoice, onDone = nav::popBackStack) }
        composable(Route.Roleplay) { RoleplayScreen(state, viewModel::assignRole, onBack = nav::popBackStack) }
        composable(Route.Aftermath) { AftermathScreen(state.game, viewModel::continueStory, onWeb = { nav.navigate(Route.Web) }, onBack = nav::popBackStack) }
        composable(Route.Web) { RelationshipWebScreen(state.game, onBack = nav::popBackStack) }
    }
}

@Composable
private fun StarField() = Canvas(Modifier.fillMaxSize()) {
    val stars = listOf(0.05f to .10f, .15f to .26f, .32f to .12f, .48f to .35f, .66f to .18f, .81f to .08f, .92f to .32f, .12f to .73f, .74f to .67f, .90f to .82f, .42f to .84f, .57f to .58f)
    stars.forEachIndexed { index, (x, y) -> drawCircle(if (index % 3 == 0) Magenta.copy(alpha = .35f) else Purple.copy(alpha = .28f), radius = if (index % 3 == 0) 3.5f else 2f, center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)) }
}

@Composable
private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✦", color = Magenta, fontSize = 48.sp)
        Text("STARRY NIGHTS", fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Spacer(Modifier.height(20.dp)); CircularProgressIndicator(color = Magenta)
    }
}

@Composable
private fun ConsentScreen(state: AppUiState, onAccept: () -> Unit, onRetry: () -> Unit, onAccepted: () -> Unit) {
    if (state.hasConsent) LaunchedEffect(Unit) { onAccepted() }
    var checked by remember { mutableStateOf(false) }
    CenteredPage {
        Text("STARRY NIGHTS", color = Magenta, fontWeight = FontWeight.Black, letterSpacing = 5.sp, fontSize = 22.sp)
        Spacer(Modifier.height(26.dp))
        NeonCard {
            Text("ADULTS ONLY — 18+", fontSize = 25.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Text("Starry Nights is an adults-only fictional interactive game containing mature themes: flirting, attraction, relationships, fantasy, roleplay, and abstract intimacy preferences. Every character is fictional and 18+.", color = TextSoft, lineHeight = 21.sp)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { checked = !checked }) {
                Checkbox(checked = checked, onCheckedChange = { checked = it }, colors = CheckboxDefaults.colors(checkedColor = Magenta))
                Text("I confirm that I am at least 18 years old and consent to secure cloud storage for this private trial.", color = TextSoft, modifier = Modifier.padding(start = 8.dp))
            }
            state.backendMessage?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Red, fontSize = 12.sp); TextButton(onClick = onRetry) { Text("RETRY CONNECTION") } }
            Spacer(Modifier.height(16.dp))
            NeonButton("ENTER STARRY NIGHTS", enabled = checked && state.userId != null, onClick = onAccept)
        }
    }
}

@Composable
private fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1400); onDone() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", color = Magenta, fontSize = 80.sp)
            Text("STARRY NIGHTS", fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 5.sp)
            Text("Explore Your Dark Desire", color = TextSoft, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, onNewStory: () -> Unit, onBlindDate: () -> Unit, onResume: () -> Unit, onWeb: () -> Unit, onLibrary: () -> Unit, onScenarios: () -> Unit, onRetry: () -> Unit) {
    Page {
        Text("STARRY NIGHTS", color = Magenta, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 22.sp)
        Text("Create the chemistry. Discover what happens after dark.", color = TextSoft)
        Spacer(Modifier.height(28.dp))
        HomeOption("NEW STORY", "Choose a social world or make a first connection.", onNewStory)
        if (state.game != null && state.sessionId != null) HomeOption("RESUME STORY", "Continue your active Blind Date from its cloud snapshot.", onResume)
        HomeOption("BLIND DATE", "A major playable scenario: discover someone trait by trait.", onBlindDate)
        HomeOption("CHARACTER LIBRARY", "${state.characters.size} fictional adults with distinct public and private profiles.", onLibrary)
        HomeOption("SCENARIOS", "${state.scenarios.size} data-driven scenes, including roleplay and group stories.", onScenarios)
        HomeOption("RELATIONSHIP WEB", "Read discovered connections, tension, and trust.", onWeb)
        state.backendMessage?.let { Spacer(Modifier.height(16.dp)); Text(it, color = Red, fontSize = 12.sp); TextButton(onClick = onRetry) { Text("RETRY CONNECTION") } }
    }
}

@Composable
private fun HomeOption(title: String, subtitle: String, onClick: () -> Unit) = NeonCard(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(subtitle, color = TextSoft, fontSize = 13.sp) }; Text("›", color = Magenta, fontSize = 32.sp) }
}

@Composable
private fun NewStoryScreen(onBlindDate: () -> Unit, onCircle: () -> Unit, onCreator: () -> Unit, onScenarios: () -> Unit, onBack: () -> Unit) {
    Page {
        BackTitle("NEW STORY", onBack)
        Text("Choose the shape of the night.", color = TextSoft)
        Spacer(Modifier.height(22.dp))
        NeonCard(Modifier.fillMaxWidth().clickable(onClick = onBlindDate)) { Text("ONE-ON-ONE", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Magenta); Text("One fictional adult, a scenario, and an action-driven first connection.", color = TextSoft) }
        Spacer(Modifier.height(12.dp))
        NeonCard(Modifier.fillMaxWidth().clickable(onClick = onCircle)) { Text("CREATE YOUR CIRCLE", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Purple); Text("Build 2–5 fictional adults. They keep interacting while attention shifts around the room.", color = TextSoft) }
        Spacer(Modifier.height(12.dp))
        NeonCard(Modifier.fillMaxWidth().clickable(onClick = onScenarios)) { Text("SCENARIO LIBRARY", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Purple); Text("Choose a setting, roleplay frame, and starting relationship without resetting character history.", color = TextSoft) }
        Spacer(Modifier.height(24.dp)); OutlinedButton(onClick = onCreator, modifier = Modifier.fillMaxWidth()) { Text("CREATE A FICTIONAL ADULT") }
    }
}

@Composable
private fun CharacterCreatorScreen(onSave: (FictionalAdult) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("25") }
    var gender by remember { mutableStateOf("Unspecified") }
    var pronouns by remember { mutableStateOf("") }
    var avatarReference by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var communication by remember { mutableStateOf("Soft-spoken") }
    var flirt by remember { mutableStateOf("Slow Burn") }
    var affection by remember { mutableStateOf("Thoughtful") }
    var attention by remember { mutableStateOf("Focused") }
    var energy by remember { mutableStateOf("Romantic") }
    var pace by remember { mutableStateOf("Slow") }
    var intensity by remember { mutableStateOf("Gentle") }
    var style by remember { mutableStateOf(setOf("Romantic")) }
    var initiation by remember { mutableStateOf("Sometimes") }
    var privacy by remember { mutableStateOf("Private") }
    var boundary by remember { mutableStateOf(Boundary.NEEDS_TRUST) }
    var chosenTraits by remember { mutableStateOf(mapOf("Mysterious" to 3, "Curious" to 3)) }
    var desires by remember { mutableStateOf(mapOf("Romantic" to InterestLevel.INTERESTED, "Slow Burn" to InterestLevel.CURIOUS)) }
    val age = ageText.toIntOrNull() ?: 0
    Page {
        BackTitle("CREATE CHARACTER", onBack)
        Text("Every character is a fictional adult. Public style, private preferences, boundaries, and personality remain separate.", color = TextSoft, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ageText, { ageText = it.filter(Char::isDigit) }, label = { Text("Age — must be 18+") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        SettingRow("GENDER", gender, listOf("Unspecified", "Woman", "Man", "Non-binary", "Custom")) { gender = it }
        OutlinedTextField(pronouns, { pronouns = it }, label = { Text("Pronouns — optional") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(avatarReference, { avatarReference = it }, label = { Text("Avatar reference — optional") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text("PERSONALITY — tap a trait to add/remove; use ± to set its weight", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        TraitWeightEditor(chosenTraits, onToggle = { trait -> chosenTraits = if (trait in chosenTraits) chosenTraits - trait else chosenTraits + (trait to 3) }, onWeight = { trait, delta -> chosenTraits[trait]?.let { chosenTraits = chosenTraits + (trait to (it + delta).coerceIn(1, 5)) } })
        Spacer(Modifier.height(10.dp))
        SettingRow("COMMUNICATION", communication, listOf("Soft-spoken", "Direct", "Witty", "Teasing")) { communication = it }
        SettingRow("FLIRTING", flirt, listOf("Rarely initiates", "Slow Burn", "Playful", "Confident", "Very Forward")) { flirt = it }
        SettingRow("AFFECTION", affection, listOf("Thoughtful", "Expressive", "Subtle", "Protective", "Playful")) { affection = it }
        SettingRow("ATTENTION", attention, listOf("Focused", "Inclusive", "Observant", "Independent", "Social")) { attention = it }
        Text("PRIVATE PROFILE", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        SettingRow("PACE", pace, listOf("Slow", "Balanced", "Fast")) { pace = it }
        SettingRow("INTENSITY", intensity, listOf("Gentle", "Passionate", "Intense")) { intensity = it }
        MultiSettingRow("STYLE", style, listOf("Romantic", "Playful", "Experimental", "Assertive", "Responsive", "Dominant-energy", "Submissive-energy")) { item -> style = if (item in style) style - item else style + item }
        SettingRow("INITIATION", initiation, listOf("Rarely", "Sometimes", "Often")) { initiation = it }
        SettingRow("PRIVACY", privacy, listOf("Private", "Flexible", "Adventurous")) { privacy = it }
        SettingRow("PRIVATE BOUNDARY", boundary.name, Boundary.entries.map(Boundary::name)) { boundary = Boundary.valueOf(it) }
        Text("DESIRE / FANTASY PROFILE — high-level fictional interests only", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        DesireEditor(desires) { category ->
            val next = when (desires[category] ?: InterestLevel.NOT_INTERESTED) {
                InterestLevel.NOT_INTERESTED -> InterestLevel.CURIOUS
                InterestLevel.CURIOUS -> InterestLevel.INTERESTED
                InterestLevel.INTERESTED -> InterestLevel.HIGH_INTEREST
                InterestLevel.HIGH_INTEREST -> InterestLevel.NOT_INTERESTED
            }
            desires = if (next == InterestLevel.NOT_INTERESTED) desires - category else desires + (category to next)
        }
        Spacer(Modifier.height(16.dp))
        NeonButton("SAVE CHARACTER", enabled = name.isNotBlank() && age >= 18 && chosenTraits.isNotEmpty() && style.isNotEmpty()) {
            val privateProfile = PrivateProfile(PrivatePace.valueOf(pace.uppercase()), PrivateIntensity.valueOf(intensity.uppercase()), style, PrivateInitiation.valueOf(initiation.uppercase()), PrivacyPreference.valueOf(privacy.uppercase()))
            onSave(FictionalAdult(
                name = name.trim(), age = age, gender = gender, pronouns = pronouns.trim(), avatarReference = avatarReference.trim(), description = description.trim(), personality = chosenTraits,
                communication = communication, flirtingStyle = flirt, intimacyEnergy = style.first(), pace = pace,
                socialProfile = SocialProfile(communication, flirt, affection, attention), privateProfile = privateProfile,
                desireProfile = DesireProfile(desires), boundaries = mapOf("private_moment" to boundary), hiddenTraits = chosenTraits.keys.take((chosenTraits.size / 2).coerceAtLeast(1)).toSet()
            ))
        }
        if (ageText.isNotBlank() && age < 18) Text("Characters under 18 cannot be created.", color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TraitWeightEditor(selected: Map<String, Int>, onToggle: (String) -> Unit, onWeight: (String, Int) -> Unit) {
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState())) { allTraitNames.forEach { trait -> FilterChip(selected = trait in selected, onClick = { onToggle(trait) }, label = { Text(trait) }, modifier = Modifier.padding(end = 6.dp)) } }
        selected.entries.sortedBy { it.key }.forEach { (trait, weight) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(trait, modifier = Modifier.weight(1f), color = TextSoft); Text("$weight / 5", color = Purple, fontSize = 12.sp)
                TextButton(onClick = { onWeight(trait, -1) }) { Text("−") }; TextButton(onClick = { onWeight(trait, 1) }) { Text("+") }
            }
        }
    }
}

@Composable
private fun MultiSettingRow(title: String, selected: Set<String>, options: List<String>, toggle: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) { Text(title, color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState())) { options.forEach { FilterChip(selected = it in selected, onClick = { toggle(it) }, label = { Text(it, maxLines = 1) }, modifier = Modifier.padding(end = 6.dp)) } } }
}

@Composable
private fun DesireEditor(values: Map<String, InterestLevel>, cycle: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) { desireCategories.forEach { category ->
        val level = values[category] ?: InterestLevel.NOT_INTERESTED
        FilterChip(selected = level != InterestLevel.NOT_INTERESTED, onClick = { cycle(category) }, label = { Text("$category: ${level.name.replace('_', ' ')}", maxLines = 1) }, modifier = Modifier.padding(end = 6.dp))
    } }
}

@Composable
private fun SettingRow(title: String, current: String, options: List<String>, select: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) { Text(title, color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState())) { options.forEach { FilterChip(selected = current == it, onClick = { select(it) }, label = { Text(it, maxLines = 1) }, modifier = Modifier.padding(end = 6.dp)) } } }
}

@Composable
private fun CharacterLibraryScreen(characters: List<FictionalAdult>, onCreate: () -> Unit, onSelect: (FictionalAdult) -> Unit, onBack: () -> Unit) {
    Page {
        BackTitle("CHARACTER LIBRARY", onBack)
        Text("Every entry is a fictional adult. Public personality, private profile, desires, and boundaries are stored separately.", color = TextSoft)
        Spacer(Modifier.height(12.dp)); NeonButton("CREATE FICTIONAL ADULT", onClick = onCreate)
        characters.forEach { character ->
            NeonCard(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onSelect(character); onBack() }) {
                Text(character.name.uppercase(), fontWeight = FontWeight.Black, color = Magenta); Text("${character.age} • ${character.gender}${if (character.pronouns.isBlank()) "" else " • ${character.pronouns}"}", color = TextSoft, fontSize = 12.sp)
                Text(character.description.ifBlank { "No description yet." }, color = TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text("SOCIAL: ${character.resolvedSocialProfile().communicationStyle} • ${character.resolvedSocialProfile().flirtingStyle}", color = Purple, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                Text("PRIVATE: ${character.resolvedPrivateProfile().pace.name.lowercase()} pace • ${character.resolvedPrivateProfile().style.joinToString()}", color = Purple, fontSize = 11.sp)
                Text("TRAITS: ${character.personality.entries.joinToString { "${it.key} ${it.value}" }}", color = TextSoft, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CircleBuilderScreen(
    state: AppUiState, selectCircle: (Circle?) -> Unit, saveCircle: (String, List<String>, StartingRelationship) -> Unit,
    onCreate: () -> Unit, onStart: () -> Unit, onRoleplay: () -> Unit, onBack: () -> Unit
) {
    var name by remember { mutableStateOf(state.selectedCircle?.name ?: "My Circle") }
    var members by remember { mutableStateOf(state.selectedCircle?.characterIds ?: state.characters.take(2).map(FictionalAdult::id)) }
    var context by remember { mutableStateOf(state.selectedCircle?.startingRelationship ?: StartingRelationship.PARTY_GROUP) }
    LaunchedEffect(state.selectedCircle?.id) {
        state.selectedCircle?.let { circle -> name = circle.name; members = circle.characterIds; context = circle.startingRelationship }
    }
    Page {
        BackTitle("CREATE YOUR CIRCLE", onBack)
        Text("Choose 2–5 fictional adults. Their NPC-to-NPC relationships evolve even while you focus elsewhere.", color = TextSoft)
        if (state.circles.isNotEmpty()) {
            Text("SAVED CIRCLES", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) { state.circles.forEach { circle -> FilterChip(selected = circle.id == state.selectedCircle?.id, onClick = { selectCircle(circle) }, label = { Text(circle.name) }, modifier = Modifier.padding(end = 6.dp)) } }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Circle name — optional") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
        Text("STARTING RELATIONSHIP", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) { StartingRelationship.entries.forEach { item -> FilterChip(selected = context == item, onClick = { context = item }, label = { Text(item.label) }, modifier = Modifier.padding(end = 6.dp)) } }
        Text("MEMBERS ${members.size}/5", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        state.characters.forEach { character ->
            val included = character.id in members
            NeonCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(character.name, fontWeight = FontWeight.Bold); Text("${character.age} • ${character.resolvedSocialProfile().flirtingStyle}", color = TextSoft, fontSize = 12.sp) }
                    FilterChip(selected = included, onClick = { members = if (included) members - character.id else if (members.size < 5) members + character.id else members }, label = { Text(if (included) "IN CIRCLE" else "ADD") })
                }
                if (included) {
                    Row { TextButton(onClick = { val index = members.indexOf(character.id); if (index > 0) members = members.toMutableList().also { list -> val item = list.removeAt(index); list.add(index - 1, item) } }) { Text("← REORDER") }; TextButton(onClick = { val index = members.indexOf(character.id); if (index < members.lastIndex) members = members.toMutableList().also { list -> val item = list.removeAt(index); list.add(index + 1, item) } }) { Text("REORDER →") } }
                }
            }
        }
        if (state.characters.size < 2) TextButton(onClick = onCreate) { Text("+ CREATE ANOTHER FICTIONAL ADULT") }
        Spacer(Modifier.height(14.dp)); NeonButton(if (state.saving) "SAVING…" else "SAVE CIRCLE", enabled = members.size in 2..5 && !state.saving, onClick = { saveCircle(name, members, context) })
        OutlinedButton(onClick = onRoleplay, enabled = members.size in 2..5, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("ASSIGN OPTIONAL ROLES") }
        NeonButton(if (state.saving) "PREPARING STORY…" else "START LIVING GROUP SCENE", enabled = state.selectedCircle != null && !state.saving, modifier = Modifier.padding(top = 8.dp), onClick = onStart)
        state.backendMessage?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun ScenarioSelectionScreen(state: AppUiState, select: (ScenarioDefinition) -> Unit, onStartOneOnOne: () -> Unit, onStartCircle: () -> Unit, onBack: () -> Unit) {
    Page {
        BackTitle("SCENARIO LIBRARY", onBack)
        Text("Settings and roles add context. They never erase the characters' base personality, boundaries, or relationship history.", color = TextSoft)
        state.scenarios.forEach { scenario ->
            val selected = scenario.id == state.selectedScenario.id
            NeonCard(Modifier.fillMaxWidth().padding(top = 10.dp).border(if (selected) 1.dp else 0.dp, Magenta, RoundedCornerShape(18.dp)).clickable { select(scenario) }) {
                Text(scenario.name.uppercase(), color = if (selected) Magenta else Color.White, fontWeight = FontWeight.Black)
                Text(scenario.description, color = TextSoft, fontSize = 13.sp)
                Text("${scenario.minimumParticipants}–${scenario.maximumParticipants} characters • ${scenario.locations.take(3).joinToString()}", color = Purple, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                if (scenario.roleplayCompatible.isNotEmpty()) Text("ROLES: ${scenario.roleplayCompatible.joinToString()}", color = TextSoft, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp)); NeonButton("START ONE-ON-ONE", onClick = onStartOneOnOne)
        OutlinedButton(onClick = onStartCircle, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("USE WITH A CIRCLE") }
    }
}

@Composable
private fun RoleplayScreen(state: AppUiState, assignRole: (String, String?) -> Unit, onBack: () -> Unit) {
    val participants = state.game?.participants ?: state.selectedCircle?.characterIds?.mapNotNull { id -> state.characters.firstOrNull { it.id == id } }.orEmpty()
    Page {
        BackTitle("ROLEPLAY", onBack)
        Text("A role is an optional narrative lens. It affects dialogue and scene cues, but never replaces personality, consent, or boundaries.", color = TextSoft)
        if (participants.isEmpty()) Text("Choose a Circle or start a story before assigning roles.", color = TextSoft, modifier = Modifier.padding(top = 18.dp))
        participants.forEach { character ->
            NeonCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(character.name.uppercase(), color = Magenta, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = state.assignedRoles[character.id] == null, onClick = { assignRole(character.id, null) }, label = { Text("NO ROLE") }, modifier = Modifier.padding(end = 6.dp))
                    starterRoles.forEach { role -> FilterChip(selected = state.assignedRoles[character.id] == role.id, onClick = { assignRole(character.id, role.id) }, label = { Text(role.name) }, modifier = Modifier.padding(end = 6.dp)) }
                }
                state.assignedRoles[character.id]?.let { roleId -> Text(starterRoles.firstOrNull { it.id == roleId }?.description.orEmpty(), color = TextSoft, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun AftermathScreen(game: BlindDateState?, continueStory: (GameAction) -> Unit, onWeb: () -> Unit, onBack: () -> Unit) {
    Page {
        BackTitle("AFTERMATH", onBack)
        if (game == null) {
            Text("Start a scene to see its consequences.", color = TextSoft)
        } else {
            Text("The story continues from the relationship state you created. Nothing resets between scenes.", color = TextSoft)
            Spacer(Modifier.height(12.dp)); HeatMeter(game.heat)
            NeonCard(Modifier.fillMaxWidth()) {
                Text("CONSEQUENCES", color = Magenta, fontWeight = FontWeight.Bold)
                Text("${game.character.name}: trust ${game.relationship.trust}, attraction ${game.relationship.attraction}, comfort ${game.relationship.comfort}, attachment ${game.relationship.attachment}.", color = TextSoft)
                Text("DISCOVERED: ${game.discoveries.filterValues { it == TraitKnowledge.DISCOVERED }.keys.joinToString().ifBlank { "No traits yet" }}", color = TextSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp)); listOf(GameAction.CONTINUE_NIGHT, GameAction.NEXT_MORNING, GameAction.NEXT_DAY, GameAction.CHANGE_LOCATION).forEach { action -> OutlinedButton(onClick = { continueStory(action) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(action.label) } }
            NeonButton("RELATIONSHIP WEB", modifier = Modifier.padding(top = 8.dp), onClick = onWeb)
        }
    }
}

@Composable
private fun BlindDateSetupScreen(state: AppUiState, selectCharacter: (FictionalAdult) -> Unit, selectLocation: (DateLocation) -> Unit, onCreate: () -> Unit, onStart: () -> Unit, onBack: () -> Unit) {
    Page {
        BackTitle("BLIND DATE", onBack)
        Text("Select or generate a fictional adult, then choose where the first meeting begins. Their key traits remain UNKNOWN at the start.", color = TextSoft)
        Spacer(Modifier.height(18.dp)); Text("YOUR DATE", color = Magenta, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) { state.characters.forEach { adult ->
            val selected = adult.id == state.selectedCharacter.id
            NeonCard(Modifier.width(180.dp).padding(end = 8.dp).border(if (selected) 1.dp else 0.dp, Magenta, RoundedCornerShape(18.dp)).clickable { selectCharacter(adult) }) {
                Text(adult.name.uppercase(), fontWeight = FontWeight.Bold); Text("${adult.age} • ${adult.communication}", color = TextSoft, fontSize = 12.sp); Text("Traits: UNKNOWN", color = Purple, fontSize = 12.sp)
            }
        } }
        TextButton(onClick = onCreate) { Text("+ GENERATE / CREATE ANOTHER") }
        Text("LOCATION", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) { DateLocation.entries.forEach { location -> FilterChip(selected = location == state.selectedLocation, onClick = { selectLocation(location) }, label = { Text(location.label) }, modifier = Modifier.padding(end = 6.dp)) } }
        Spacer(Modifier.height(22.dp)); NeonButton("BEGIN FIRST MEETING", onClick = onStart)
    }
}

@Composable
private fun SceneScreen(
    game: BlindDateState?, saving: Boolean, backendMessage: String?, onAction: (GameAction, Float, String?) -> Unit,
    onFocus: (String) -> Unit, onDirector: (DirectorCard) -> Unit, onContinue: (GameAction) -> Unit,
    onPrivate: (String) -> Unit, onRoleplay: () -> Unit, onAftermath: () -> Unit, onWeb: () -> Unit, onBack: () -> Unit
) {
    if (game == null) {
        if (saving) CenteredPage { CircularProgressIndicator(color = Magenta); Spacer(Modifier.height(14.dp)); Text("Preparing your cloud-backed story…", color = TextSoft) }
        else CenteredPage { Text(backendMessage ?: "No active story is available.", color = TextSoft); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onBack) { Text("BACK") } }
        return
    }
    var timing by remember { mutableFloatStateOf(.7f) }
    var privateLocation by remember(game.focusCharacterId) { mutableStateOf(privateMomentLocations.first()) }
    Page(outerPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹") }; Column(Modifier.weight(1f)) { Text("${game.scenario.name.uppercase()} • ${game.story.currentLocation.uppercase()}", fontWeight = FontWeight.Bold); Text("${game.mode.name.replace('_', ' ')} • ${game.story.phase.replace('_', ' ')}", color = Magenta, fontSize = 12.sp) }; TextButton(onClick = onWeb) { Text("WEB") } }
        HeatMeter(game.heat)
        RelationshipClues(game)
        if (game.participants.size > 1) {
            Text("FOCUS", color = Magenta, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) { game.participants.forEach { character -> FilterChip(selected = game.focusCharacterId == character.id, onClick = { onFocus(character.id) }, label = { Text(character.name) }, modifier = Modifier.padding(end = 6.dp)) } }
        }
        Spacer(Modifier.height(6.dp))
        NeonCard(Modifier.fillMaxWidth().heightIn(min = 315.dp)) {
            LazyColumn(reverseLayout = false, modifier = Modifier.fillMaxWidth()) { items(game.log, key = { it.id }) { entry -> SceneLogLine(entry) } }
        }
        if (!game.ended) {
            Spacer(Modifier.height(10.dp))
            Text("REACTION TIMING", color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Slider(value = timing, onValueChange = { timing = it }, colors = SliderDefaults.colors(thumbColor = Magenta, activeTrackColor = Magenta))
            Text(if (timing in .58f..82f) "In the moment" else "A little off the rhythm", color = if (timing in .58f..82f) Purple else TextSoft, fontSize = 12.sp)
            ActionGrid(game, onAction = { onAction(it, timing, game.focusCharacterId) })
            if (game.scenario.roleplayCompatible.isNotEmpty()) OutlinedButton(onClick = onRoleplay, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("ROLEPLAY LENS") }
            DirectorCardTray(game, onDirector)
            AnimatedVisibility(game.privateOfferVisible) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("PRIVATE LOCATION", color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState())) { privateMomentLocations.forEach { location -> FilterChip(selected = privateLocation == location, onClick = { privateLocation = location }, label = { Text(location) }, modifier = Modifier.padding(end = 6.dp)) } }
                    NeonButton("CONTINUE PRIVATE MOMENT", onClick = { onPrivate(privateLocation) })
                }
            }
            if (game.story.nextOptions.any { it in setOf("NEXT MORNING", "NEXT DAY", "CHANGE LOCATION") }) OutlinedButton(onClick = onAftermath, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("SEE AFTERMATH / CONTINUE STORY") }
        } else {
            Text("END-DATE OUTCOME — the connection stays in the story rather than resetting.", color = Purple, modifier = Modifier.padding(12.dp))
            NeonButton("CONTINUE STORY", onClick = { onContinue(GameAction.CONTINUE_NIGHT) })
            OutlinedButton(onClick = onAftermath, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("AFTERMATH") }
        }
        backendMessage?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun HeatMeter(heat: Int) {
    val label = heatLabel(heat)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("HEAT $heat", color = Magenta, fontWeight = FontWeight.Bold, fontSize = 12.sp); LinearProgressIndicator(progress = { heat / 100f }, color = if (heat > 70) Red else Magenta, trackColor = Panel, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)); Text(label, color = TextSoft, fontSize = 11.sp) }
}

@Composable
private fun RelationshipClues(game: BlindDateState) = Row(Modifier.horizontalScroll(rememberScrollState())) {
    val clues = listOf("👀" to "interest", "✨" to "curiosity", "😏" to "flirting") + if (game.relationship.tension > 40) listOf("🔥" to "tension") else emptyList()
    clues.forEach { (icon, label) -> AssistChip(onClick = {}, label = { Text("$icon $label") }, modifier = Modifier.padding(end = 6.dp)) }
    game.discoveries.filterValues { it == TraitKnowledge.DISCOVERED }.keys.forEach { trait -> AssistChip(onClick = {}, label = { Text("◇ $trait") }, modifier = Modifier.padding(end = 6.dp)) }
}

@Composable
private fun SceneLogLine(entry: SceneLog) = Row(Modifier.padding(vertical = 7.dp)) { Text(entry.cue, modifier = Modifier.padding(end = 8.dp)); Column { Text(entry.speaker, color = if (entry.isSystem) Purple else Magenta, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(entry.text, color = if (entry.isSystem) TextSoft else Color.White, lineHeight = 19.sp) } }

@Composable
private fun ActionGrid(game: BlindDateState, onAction: (GameAction) -> Unit) {
    val actions = buildList {
        addAll(listOf(GameAction.OBSERVE, GameAction.EYE_CONTACT, GameAction.SMILE, GameAction.APPROACH, GameAction.REACT, GameAction.TEASE, GameAction.FLIRT, GameAction.COMPLIMENT, GameAction.MOVE_CLOSER, GameAction.CHANGE_TOPIC, GameAction.STEP_BACK))
        if (game.participants.size > 1) addAll(listOf(GameAction.JOIN, GameAction.SHIFT_ATTENTION, GameAction.FOCUS_CHARACTER, GameAction.LET_THEM_TALK, GameAction.CHANGE_MOOD, GameAction.CHANGE_MUSIC, GameAction.START_ACTIVITY))
        addAll(listOf(GameAction.CHANGE_LOCATION, GameAction.INVITE_CONTINUE, GameAction.END_DATE))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp)) { actions.forEach { action -> Button(onClick = { onAction(action) }, colors = ButtonDefaults.buttonColors(containerColor = if (action == GameAction.STEP_BACK || action == GameAction.END_DATE) Color(0xFF33233B) else Panel), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), modifier = Modifier.padding(end = 6.dp)) { Text(action.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
}

@Composable
private fun DirectorCardTray(game: BlindDateState, onDirector: (DirectorCard) -> Unit) {
    val available = starterDirectorCards.filter { card -> game.directorCards[card.id]?.available == true && !game.directorCards.getValue(card.id).applied && game.heat >= card.requiredHeat }
    if (available.isEmpty()) return
    Text("DIRECTOR CARDS", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) { available.forEach { card -> AssistChip(onClick = { onDirector(card) }, label = { Text(card.name) }, modifier = Modifier.padding(end = 6.dp)) } }
}

@Composable
private fun PrivateMomentScreen(game: BlindDateState?, cards: List<IntimacyCard>, selectCard: (IntimacyCard) -> Unit, choice: (PrivateChoice) -> Unit, onDone: () -> Unit) {
    if (game == null || !game.inPrivateMoment) { BackOnly(onDone); return }
    Page {
        BackTitle("PRIVATE MOMENT", onDone)
        Text("This is an optional, abstract cinematic route for fictional consenting adults. It never plays automatically. Choose the pace at every beat.", color = TextSoft, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp)); HeatMeter(game.heat)
        Text("INTIMACY STYLE CARDS", color = Magenta, fontWeight = FontWeight.Bold)
        if (cards.isEmpty()) Text("More trust, attraction, comfort, and Heat are needed before a card becomes compatible.", color = TextSoft, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) { cards.forEach { card ->
            NeonCard(Modifier.width(190.dp).padding(end = 8.dp).border(if (game.selectedCard?.id == card.id) 1.dp else 0.dp, Magenta, RoundedCornerShape(18.dp)).clickable { selectCard(card) }) {
                Text(card.icon, color = Magenta, fontSize = 28.sp); Text(card.name, fontWeight = FontWeight.Bold); Text(card.category, color = Purple, fontSize = 11.sp); Text("Intensity ${card.intensity}/5 • closeness ${card.closeness}/5", color = TextSoft, fontSize = 11.sp); Text(card.description, color = TextSoft, fontSize = 12.sp)
            }
        } }
        Text("YOU ARE IN CONTROL", color = Magenta, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) { PrivateChoice.entries.forEach { item -> Button(onClick = { choice(item); if (item == PrivateChoice.STOP_SCENE) onDone() }, colors = ButtonDefaults.buttonColors(containerColor = if (item == PrivateChoice.STOP_SCENE) Red.copy(alpha = .7f) else Panel), modifier = Modifier.padding(end = 7.dp)) { Text(item.label, fontSize = 11.sp) } } }
        Spacer(Modifier.height(16.dp)); NeonCard { Text("AFTERMATH", color = Purple, fontWeight = FontWeight.Bold); Text("Every choice carries forward: trust, attachment, comfort, and future relationship routes can change. The story does not reset.", color = TextSoft) }
    }
}

@Composable
private fun RelationshipWebScreen(game: BlindDateState?, onBack: () -> Unit) {
    Page {
        BackTitle("RELATIONSHIP WEB", onBack)
        Text("Only discovered information is represented here; raw emotional scores stay hidden.", color = TextSoft)
        Spacer(Modifier.height(18.dp))
        NeonCard(Modifier.fillMaxWidth().height(310.dp)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val player = androidx.compose.ui.geometry.Offset(size.width * .20f, size.height * .52f)
                    val people = game?.participants.orEmpty()
                    val locations = people.mapIndexed { index, _ ->
                        val x = if (people.size == 1) .72f else .63f + (index % 2) * .22f
                        val y = if (people.size == 1) .42f else .22f + (index / 2) * .25f
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
                    }
                    locations.forEach { node -> drawLine(Purple.copy(alpha = .55f), player, node, strokeWidth = 5f); drawCircle(Purple, 29f, node) }
                    people.indices.forEach { source -> people.indices.filter { it > source }.forEach { target -> drawLine(Magenta.copy(alpha = .20f), locations[source], locations[target], strokeWidth = 3f) } }
                    drawCircle(Magenta, 33f, player)
                }
                Text("YOU", modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp), fontWeight = FontWeight.Bold)
                Column(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 10.dp), horizontalAlignment = Alignment.End) { game?.participants?.forEach { Text(it.name.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp) } }
                Text("👀  ❤️  🔥  ⚡  😒  ✨  🖤", modifier = Modifier.align(Alignment.Center), color = TextSoft, fontSize = 12.sp)
            }
        }
        if (game != null) {
            Spacer(Modifier.height(12.dp))
            game.participants.forEach { character ->
                val relation = game.directionalRelationships[relationshipKey(PLAYER_PARTICIPANT, character.id)] ?: game.relationship
                val discovered = game.discoveredTraitsByCharacter[character.id].orEmpty().filterValues { it == TraitKnowledge.DISCOVERED }.keys
                NeonCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(character.name.uppercase(), color = Magenta, fontWeight = FontWeight.Bold)
                    Text("DISCOVERED: ${discovered.joinToString().ifBlank { "none yet" }}", color = TextSoft, fontSize = 12.sp)
                    val clues = buildList { if (relation.attraction >= 45) add("❤️ attraction"); if (relation.desire >= 38) add("🔥 chemistry"); if (relation.curiosity >= 52) add("👀 curiosity"); if (relation.tension >= 40) add("⚡ tension"); if (relation.jealousy >= 30) add("😒 jealousy"); if (relation.trust >= 45) add("✨ trust"); if (relation.competition >= 35) add("🖤 rivalry") }
                    Text(clues.joinToString(" • ").ifBlank { "The connection is still forming." }, color = Purple, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CenteredPage(content: @Composable ColumnScope.() -> Unit) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, content = content) }

@Composable
private fun Page(outerPadding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().padding(outerPadding).verticalScroll(rememberScrollState()), content = content)

@Composable
private fun NeonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(modifier, shape = StarryDesign.cardShape, colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .94f))) { Column(Modifier.padding(StarryDesign.cardPadding), content = content) }

@Composable
private fun NeonButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) = Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Magenta, disabledContainerColor = Panel)) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ BACK") }; Text(title, fontSize = 23.sp, fontWeight = FontWeight.Black, color = Magenta) }

@Composable
private fun BackOnly(onBack: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = onBack) { Text("BACK") } }

