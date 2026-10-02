export type Boundary = "COMFORTABLE" | "NEEDS_TRUST" | "HIGH_CHEMISTRY_ONLY" | "DISABLED";

export type PrivateMomentInput = {
  attraction: number;
  comfort: number;
  trust: number;
  heat: number;
  compatibleDesire: boolean;
  boundaries: Boundary[];
};

/** A narrative offer only; the player must separately opt in and can stop at any time. */
export function canOfferPrivateMoment(input: PrivateMomentInput): boolean {
  if (input.boundaries.includes("DISABLED")) return false;
  const trustMinimum = input.boundaries.includes("NEEDS_TRUST") ? 62 : 45;
  const heatMinimum = input.boundaries.includes("HIGH_CHEMISTRY_ONLY") ? 65 : 48;
  return input.attraction >= 55 && input.comfort >= 55 && input.trust >= trustMinimum &&
    input.heat >= heatMinimum && input.compatibleDesire;
}

export function clamp(value: number): number {
  return Math.max(0, Math.min(100, Math.round(value)));
}

