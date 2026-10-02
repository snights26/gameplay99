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
    const val BlindDate = "blind-date"
    const val Scene = "scene"
    const val PrivateMoment = "private-moment"
    const val Web = "relationship-web"
}

private val Ink = Color(0xFF090613)
private val Panel = Color(0xFF171127)
private val Purple = Color(0xFF9A63FF)
private val Magenta = Color(0xFFFF4FA3)
private val Red = Color(0xFFFF4F6D)
private val TextSoft = Color(0xFFDCD1F7)

private val StarryScheme = darkColorScheme(
    primary = Magenta, secondary = Purple, tertiary = Red,
    background = Ink, surface = Panel, onPrimary = Color.White,
    onBackground = Color.White, onSurface = Color.White
)

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
        composable(Route.Consent) { ConsentScreen(state, viewModel::acceptConsent) { nav.navigate(Route.Splash) { popUpTo(Route.Consent) { inclusive = true } } } }
        composable(Route.Splash) { SplashScreen { nav.navigate(Route.Home) { popUpTo(Route.Splash) { inclusive = true } } } }
        composable(Route.Home) { HomeScreen(state, onNewStory = { nav.navigate(Route.NewStory) }, onBlindDate = { nav.navigate(Route.BlindDate) }, onResume = { nav.navigate(Route.Scene) }, onWeb = { nav.navigate(Route.Web) }) }
        composable(Route.NewStory) { NewStoryScreen(onBlindDate = { nav.navigate(Route.BlindDate) }, onCreator = { nav.navigate(Route.Creator) }, onBack = nav::popBackStack) }
        composable(Route.Creator) { CharacterCreatorScreen(onSave = { viewModel.addCharacter(it); nav.popBackStack() }, onBack = nav::popBackStack) }
        composable(Route.BlindDate) { BlindDateSetupScreen(state, viewModel::selectCharacter, viewModel::selectLocation, onCreate = { nav.navigate(Route.Creator) }, onStart = { viewModel.beginBlindDate(); nav.navigate(Route.Scene) }, onBack = nav::popBackStack) }
        composable(Route.Scene) { SceneScreen(state.game, viewModel::perform, onPrivate = { viewModel.enterPrivateMoment(); nav.navigate(Route.PrivateMoment) }, onWeb = { nav.navigate(Route.Web) }, onBack = nav::popBackStack) }
        composable(Route.PrivateMoment) { PrivateMomentScreen(state.game, viewModel.availableCards(), viewModel::selectIntimacyCard, viewModel::privateChoice, onDone = nav::popBackStack) }
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
private fun ConsentScreen(state: AppUiState, onAccept: () -> Unit, onAccepted: () -> Unit) {
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
            state.backendMessage?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Red, fontSize = 12.sp) }
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
private fun HomeScreen(state: AppUiState, onNewStory: () -> Unit, onBlindDate: () -> Unit, onResume: () -> Unit, onWeb: () -> Unit) {
    Page {
        Text("STARRY NIGHTS", color = Magenta, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 22.sp)
        Text("Create the chemistry. Discover what happens after dark.", color = TextSoft)
        Spacer(Modifier.height(28.dp))
        HomeOption("NEW STORY", "Choose a social world or make a first connection.", onNewStory)
        if (state.game != null && state.sessionId != null) HomeOption("RESUME STORY", "Continue your active Blind Date from its cloud snapshot.", onResume)
        HomeOption("BLIND DATE", "A major playable scenario: discover someone trait by trait.", onBlindDate)
        HomeOption("CHARACTERS", "${state.characters.size} fictional adults ready for a story.", onNewStory)
        HomeOption("SCENARIOS", "Blind Date, Midnight Circle, Masquerade, and After Dark Lounge.", onNewStory)
        HomeOption("RELATIONSHIP WEB", "Read discovered connections, tension, and trust.", onWeb)
        state.backendMessage?.let { Spacer(Modifier.height(16.dp)); Text(it, color = Red, fontSize = 12.sp) }
    }
}

@Composable
private fun HomeOption(title: String, subtitle: String, onClick: () -> Unit) = NeonCard(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(subtitle, color = TextSoft, fontSize = 13.sp) }; Text("›", color = Magenta, fontSize = 32.sp) }
}

@Composable
private fun NewStoryScreen(onBlindDate: () -> Unit, onCreator: () -> Unit, onBack: () -> Unit) {
    Page {
        BackTitle("NEW STORY", onBack)
        Text("Choose the shape of the night.", color = TextSoft)
        Spacer(Modifier.height(22.dp))
        NeonCard(Modifier.fillMaxWidth().clickable(onClick = onBlindDate)) { Text("ONE-ON-ONE", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Magenta); Text("You and one fictional adult. Blind Date is ready to play.", color = TextSoft) }
        Spacer(Modifier.height(12.dp))
        NeonCard { Text("CREATE YOUR CIRCLE", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Purple); Text("Build 2–5 fictional adults and let their relationships develop autonomously. This trial's full Circle scene is next in the content roadmap.", color = TextSoft) }
        Spacer(Modifier.height(24.dp)); OutlinedButton(onClick = onCreator, modifier = Modifier.fillMaxWidth()) { Text("CREATE A FICTIONAL ADULT") }
    }
}

@Composable
private fun CharacterCreatorScreen(onSave: (FictionalAdult) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("25") }
    var communication by remember { mutableStateOf("Soft-spoken") }
    var flirt by remember { mutableStateOf("Slow Burn") }
    var energy by remember { mutableStateOf("Romantic") }
    var pace by remember { mutableStateOf("Slow") }
    var chosenTraits by remember { mutableStateOf(setOf("Mysterious", "Curious")) }
    val age = ageText.toIntOrNull() ?: 0
    Page {
        BackTitle("CREATE CHARACTER", onBack)
        Text("Every character is a fictional adult. Personality is separate from gender.", color = TextSoft, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ageText, { ageText = it.filter(Char::isDigit) }, label = { Text("Age — must be 18+") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text("PERSONALITY — tap traits to add them", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        TraitSelector(chosenTraits) { trait -> chosenTraits = if (trait in chosenTraits) chosenTraits - trait else chosenTraits + trait }
        Spacer(Modifier.height(10.dp))
        SettingRow("COMMUNICATION", communication, listOf("Soft-spoken", "Direct", "Witty", "Teasing")) { communication = it }
        SettingRow("FLIRTING", flirt, listOf("Rarely initiates", "Slow Burn", "Playful", "Confident", "Very Forward")) { flirt = it }
        SettingRow("PRIVATE ENERGY", energy, listOf("Romantic", "Playful", "Passionate", "Intense", "Experimental", "Assertive", "Responsive")) { energy = it }
        SettingRow("PACE", pace, listOf("Slow", "Balanced", "Fast")) { pace = it }
        Spacer(Modifier.height(16.dp))
        NeonButton("SAVE CHARACTER", enabled = name.isNotBlank() && age >= 18 && chosenTraits.isNotEmpty()) {
            onSave(FictionalAdult(name = name.trim(), age = age, personality = chosenTraits.associateWith { 3 }, communication = communication, flirtingStyle = flirt, intimacyEnergy = energy, pace = pace, hiddenTraits = chosenTraits.take(1).toSet()))
        }
        if (ageText.isNotBlank() && age < 18) Text("Characters under 18 cannot be created.", color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TraitSelector(selected: Set<String>, toggle: (String) -> Unit) {
    val traits = listOf("Shy", "Confident", "Mysterious", "Flirty", "Playful", "Romantic", "Reserved", "Teasing", "Bold", "Curious", "Adventurous", "Competitive", "Protective", "Independent", "Jealous", "Emotionally Expressive", "Emotionally Guarded", "Calm", "Unpredictable", "Intense", "Slow Burn", "Social", "Private", "Impulsive", "Patient", "Risk Taking", "Observant")
    Row(Modifier.horizontalScroll(rememberScrollState())) { traits.forEach { trait -> FilterChip(selected = trait in selected, onClick = { toggle(trait) }, label = { Text(trait) }, modifier = Modifier.padding(end = 6.dp)) } }
}

@Composable
private fun SettingRow(title: String, current: String, options: List<String>, select: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) { Text(title, color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState())) { options.forEach { FilterChip(selected = current == it, onClick = { select(it) }, label = { Text(it, maxLines = 1) }, modifier = Modifier.padding(end = 6.dp)) } } }
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
private fun SceneScreen(game: BlindDateState?, onAction: (GameAction, Float) -> Unit, onPrivate: () -> Unit, onWeb: () -> Unit, onBack: () -> Unit) {
    if (game == null) { BackOnly(onBack); return }
    var timing by remember { mutableFloatStateOf(.7f) }
    Page(outerPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹") }; Column(Modifier.weight(1f)) { Text("BLIND DATE • ${game.location.label.uppercase()}", fontWeight = FontWeight.Bold); Text(if (game.inPrivateMoment) "PRIVATE MOMENT" else "FIRST MEETING", color = Magenta, fontSize = 12.sp) }; TextButton(onClick = onWeb) { Text("WEB") } }
        HeatMeter(game.heat)
        RelationshipClues(game)
        Spacer(Modifier.height(6.dp))
        NeonCard(Modifier.fillMaxWidth().heightIn(min = 315.dp)) {
            LazyColumn(reverseLayout = false, modifier = Modifier.fillMaxWidth()) { items(game.log, key = { it.id }) { entry -> SceneLogLine(entry) } }
        }
        if (!game.ended) {
            Spacer(Modifier.height(10.dp))
            Text("REACTION TIMING", color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Slider(value = timing, onValueChange = { timing = it }, colors = SliderDefaults.colors(thumbColor = Magenta, activeTrackColor = Magenta))
            Text(if (timing in .58f..82f) "In the moment" else "A little off the rhythm", color = if (timing in .58f..82f) Purple else TextSoft, fontSize = 12.sp)
            ActionGrid(onAction = { onAction(it, timing) })
            AnimatedVisibility(game.privateOfferVisible) { NeonButton("CONTINUE PRIVATE MOMENT", modifier = Modifier.padding(top = 8.dp), onClick = onPrivate) }
        } else Text("END-DATE OUTCOME — the story can continue another day, with the connection left intact.", color = Purple, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun HeatMeter(heat: Int) {
    val label = when (heat) { in 0..20 -> "SOCIAL"; in 21..40 -> "FLIRTY"; in 41..60 -> "CHEMISTRY"; in 61..80 -> "INTENSE"; else -> "AFTER DARK" }
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
private fun ActionGrid(onAction: (GameAction) -> Unit) {
    val actions = listOf(GameAction.OBSERVE, GameAction.EYE_CONTACT, GameAction.SMILE, GameAction.APPROACH, GameAction.TEASE, GameAction.FLIRT, GameAction.COMPLIMENT, GameAction.CHANGE_TOPIC, GameAction.MOVE_CLOSER, GameAction.ORDER_DRINK, GameAction.CHANGE_LOCATION, GameAction.INVITE_CONTINUE, GameAction.STEP_BACK, GameAction.END_DATE)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp)) { actions.forEach { action -> Button(onClick = { onAction(action) }, colors = ButtonDefaults.buttonColors(containerColor = if (action == GameAction.STEP_BACK || action == GameAction.END_DATE) Color(0xFF33233B) else Panel), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), modifier = Modifier.padding(end = 6.dp)) { Text(action.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
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
                    val a = androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .55f); val b = androidx.compose.ui.geometry.Offset(size.width * .72f, size.height * .38f)
                    drawLine(Purple.copy(alpha = .6f), a, b, strokeWidth = 5f)
                    drawCircle(Magenta, 35f, a); drawCircle(Purple, 35f, b)
                }
                Text("YOU", modifier = Modifier.align(Alignment.CenterStart).padding(start = 33.dp), fontWeight = FontWeight.Bold)
                Text(game?.character?.name?.uppercase() ?: "DATE", modifier = Modifier.align(Alignment.TopEnd).padding(top = 67.dp, end = 23.dp), fontWeight = FontWeight.Bold)
                Text("👀  ✨  ${if ((game?.relationship?.tension ?: 0) > 40) "🔥" else ""}", modifier = Modifier.align(Alignment.Center))
            }
        }
        if (game != null) { Spacer(Modifier.height(12.dp)); NeonCard { Text("READ THE SIGNALS", color = Magenta, fontWeight = FontWeight.Bold); Text("${game.character.name}'s discovered traits: ${game.discoveries.filterValues { it == TraitKnowledge.DISCOVERED }.keys.joinToString().ifBlank { "none yet" }}", color = TextSoft) } }
    }
}

@Composable
private fun CenteredPage(content: @Composable ColumnScope.() -> Unit) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, content = content) }

@Composable
private fun Page(outerPadding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().padding(outerPadding).verticalScroll(rememberScrollState()), content = content)

@Composable
private fun NeonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .94f))) { Column(Modifier.padding(16.dp), content = content) }

@Composable
private fun NeonButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) = Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Magenta, disabledContainerColor = Panel)) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ BACK") }; Text(title, fontSize = 23.sp, fontWeight = FontWeight.Black, color = Magenta) }

@Composable
private fun BackOnly(onBack: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = onBack) { Text("BACK") } }

