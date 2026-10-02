import test from "node:test";
import assert from "node:assert/strict";
import { canOfferPrivateMoment } from "../src/rules.js";

test("private moment requires attraction, comfort, trust, heat and desire compatibility", () => {
  assert.equal(canOfferPrivateMoment({ attraction: 68, comfort: 66, trust: 64, heat: 70, compatibleDesire: true, boundaries: ["NEEDS_TRUST"] }), true);
  assert.equal(canOfferPrivateMoment({ attraction: 68, comfort: 66, trust: 50, heat: 70, compatibleDesire: true, boundaries: ["NEEDS_TRUST"] }), false);
  assert.equal(canOfferPrivateMoment({ attraction: 90, comfort: 90, trust: 90, heat: 90, compatibleDesire: true, boundaries: ["DISABLED"] }), false);
});

