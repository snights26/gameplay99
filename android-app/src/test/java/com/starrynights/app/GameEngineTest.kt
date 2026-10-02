package com.starrynights.app

import com.starrynights.app.engine.IntimacyCompatibilityEngine
import com.starrynights.app.engine.KotlinGameEngine
import com.starrynights.app.model.*
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    @Test fun `adult check and disabled boundary prevent a private offer`() {
        val adult = demoAdults().first().copy(boundaries = mapOf("private_moment" to Boundary.DISABLED))
        val state = BlindDateState(adult, DateLocation.CAFE, heat = 90, relationship = RelationshipState(90, 90, 90, desire = 90))
        assertFalse(IntimacyCompatibilityEngine().canOfferPrivateMoment(state))
    }

    @Test fun `respectful step back increases comfort`() {
        val engine = KotlinGameEngine()
        val start = engine.start(demoAdults().first(), DateLocation.CAFE)
        val next = engine.perform(start, GameAction.STEP_BACK)
        assertTrue(next.relationship.comfort > start.relationship.comfort)
    }

    @Test fun `dates can end without activating a private scene`() {
        val engine = KotlinGameEngine()
        val ended = engine.perform(engine.start(demoAdults().first(), DateLocation.CAFE), GameAction.END_DATE)
        assertTrue(ended.ended)
        assertFalse(ended.inPrivateMoment)
    }
}

