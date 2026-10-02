package com.starrynights.app

import com.starrynights.app.engine.KotlinGameEngine
import com.starrynights.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorySimulationEngineTest {
    private val engine = KotlinGameEngine()

    @Test fun `circle starts with directional state for every distinct participant pair`() {
        val people = demoAdults()
        val circle = Circle(name = "Rooftop", characterIds = people.map(FictionalAdult::id))
        val state = engine.startStory(
            characters = people, scenario = starterScenarios.first { it.name == "House Party" }, location = DateLocation.ROOFTOP_RESTAURANT,
            mode = StoryMode.CIRCLE, relationshipStart = StartingRelationship.PARTY_GROUP, circle = circle
        )
        assertEquals(12, state.directionalRelationships.size) // player + 3 adults, with a direction for each distinct pair
        assertEquals(StoryMode.CIRCLE, state.mode)
        assertTrue(state.discoveredTraitsByCharacter.values.all { traits -> traits.values.all { it == TraitKnowledge.UNKNOWN } })
    }

    @Test fun `focus does not freeze remaining circle participants`() {
        val people = demoAdults()
        val state = engine.startStory(people, starterScenarios.first { it.name == "House Party" }, DateLocation.ROOFTOP_RESTAURANT, StoryMode.CIRCLE, StartingRelationship.PARTY_GROUP)
        val next = engine.perform(state, GameAction.FOCUS_CHARACTER, .7f, people.first().id)
        assertTrue(next.directionalRelationships.getValue(relationshipKey(people[1].id, people.first().id)).jealousy > 0)
    }

    @Test fun `director card changes circumstances without forcing private route`() {
        val start = engine.start(demoAdults().first(), DateLocation.CAFE)
        val next = engine.applyDirectorCard(start, starterDirectorCards.first { it.id == "lights_out" })
        assertTrue(next.directorCards.getValue("lights_out").applied)
        assertFalse(next.inPrivateMoment)
    }
}
