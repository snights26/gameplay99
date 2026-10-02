import assert from "node:assert/strict";
import test from "node:test";
import { circleSchema, sessionSchema } from "../src/validation.ts";

const first = "00000000-0000-4000-8000-000000000001";
const second = "00000000-0000-4000-8000-000000000002";
const scenario = "0d0bda7e-0001-4db0-8c00-000000000001";

test("one-on-one sessions reject more than one fictional adult", () => {
  const result = sessionSchema.safeParse({ scenarioId: scenario, mode: "ONE_ON_ONE", participantCharacterIds: [first, second], relationshipStart: "COMPLETE_STRANGERS", state: {} });
  assert.equal(result.success, false);
});

test("circle sessions and Circle configuration accept two to five owned character references", () => {
  const session = sessionSchema.parse({ scenarioId: scenario, mode: "CIRCLE", participantCharacterIds: [first, second], relationshipStart: "PARTY_GROUP", state: {} });
  const circle = circleSchema.parse({ name: "Night Shift", characterIds: [first, second], startingRelationship: "PARTY_GROUP", configuration: { music: "low" } });
  assert.equal(session.mode, "CIRCLE");
  assert.equal(circle.configuration.music, "low");
});
