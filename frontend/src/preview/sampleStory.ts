/*
 * SAMPLE DATA FOR THE DESIGN PREVIEW ONLY.
 *
 * Representative text in the style of a real session (Last Lantern -> Bell Passage, a Bone Warden,
 * a natural-language attack and defense). It is hand-written for reviewing typography and layout:
 * it was not produced by the game, is never sent to the server and is never saved anywhere.
 *
 * It follows the real rules: HP is the only lasting harm (injury escalation is deferred, so every
 * body part stays HEALTHY), a defense-only turn never provokes a new attack, and an enemy attacks
 * only after a turn in which the player did something other than defend.
 */
import type { CharacterView, NarrationView, SceneView } from '../api/types';

export type SampleEntry =
  | { kind: 'introduction'; narration: NarrationView }
  | { kind: 'scene'; scene: string; zone: string; region?: string }
  | { kind: 'player'; text: string; command?: boolean }
  | { kind: 'narration'; narration: NarrationView }
  | { kind: 'attack'; attacker: string; cueText: string; narration: NarrationView };

const ai = (text: string): NarrationView => ({ text, source: 'AI' });

export const SAMPLE_CHARACTER: CharacterView = {
  name: 'Wren',
  hp: 17,
  maxHp: 24,
  stats: { MIGHT: 5, AGILITY: 8, PERCEPTION: 6, ARCANA: 4, RESOLVE: 6 },
  fated: 1,
  fatedBand: 'TOUCHED',
  body: [
    { part: 'HEAD', severity: 'HEALTHY' }, { part: 'CHEST', severity: 'HEALTHY' },
    { part: 'LEFT_ARM', severity: 'HEALTHY' }, { part: 'RIGHT_ARM', severity: 'HEALTHY' },
  ],
  weapons: [{ alias: 'weapon_1', name: 'Longsword' }],
  items: [{ alias: 'item_1', name: 'Restorative Salve' }, { alias: 'item_2', name: 'Lockpicks' }],
  abilities: [{ alias: 'ability_1', name: 'Warding Sigil' }],
  passive: 'Light Foot',
};

export const SAMPLE_SCENE: SceneView = {
  zones: [
    { alias: 'zone_1', name: 'Bell Landing' }, { alias: 'zone_2', name: 'Rope Gallery' },
    { alias: 'zone_3', name: 'Cracked Belfry Stair' },
  ],
  connections: [{ zoneA: 'zone_1', zoneB: 'zone_2' }, { zoneA: 'zone_2', zoneB: 'zone_3' }],
  creatures: [{ alias: 'entity_1', name: 'Bone Warden', zone: 'zone_2', condition: 'ACTIVE' }],
  objects: [{ alias: 'object_1', name: 'Fallen Bell', zone: 'zone_2' }],
  hazards: [{ alias: 'hazard_1', name: 'Rotten Floorboards', zone: 'zone_3' }],
  exits: [{ alias: 'exit_1', zone: 'zone_1' }, { alias: 'exit_2', zone: 'zone_3' }],
};

export const SAMPLE_PLACE = { region: 'Hollow Chapel', scene: 'Bell Passage', zone: { alias: 'zone_1', name: 'Bell Landing' } };

export const SAMPLE_ATTACK = {
  attacker: 'Bone Warden',
  cueText: 'Incoming: an overhead strike coming down from above.',
};

export const SAMPLE_STORY: SampleEntry[] = [
  {
    kind: 'introduction',
    narration: ai('The flame that holds the world together is guttering, and in its failing light the dead have begun to rise. '
      + 'You are Wren, a Bound Soul, quick on your feet where others are strong in the arm.\n\n'
      + 'The Last Lantern is the final refuge on the road to the Hollow Chapel: a ring of warm stone, a kettle that never '
      + 'quite boils, and a door that opens only onward. You carry a Longsword, a pot of Restorative Salve and a set of '
      + 'lockpicks wrapped in oilcloth. Something about you has brushed against fate, and fate has not forgotten.'),
  },
  { kind: 'scene', scene: 'The Last Lantern', zone: 'Lantern Hearth' },
  { kind: 'player', text: '/move zone_1', command: true },
  { kind: 'narration', narration: ai('You leave the hearth\'s warmth behind and step onto the Chapel Road. The lantern light thins '
      + 'to a smear of amber at your back, and ahead the road climbs into fog.') },
  { kind: 'player', text: '/move exit_1', command: true },
  { kind: 'scene', scene: 'Bell Passage', zone: 'Bell Landing', region: 'Hollow Chapel' },
  { kind: 'narration', narration: ai('The passage swallows sound. Ropes hang from the dark like the roots of some upturned tree, '
      + 'and somewhere above a cracked bell turns slowly on its chain without ringing.\n\n'
      + 'At the far end of the Rope Gallery a shape unfolds from the shadow — a Bone Warden, its ribs bound with rusted '
      + 'wire, a war hammer resting across its shoulders as if it has been waiting a long time for you.') },
  { kind: 'player', text: 'I slash at the Bone Warden with my Longsword, aiming for the wire that binds its ribs.' },
  { kind: 'narration', narration: ai('Your Longsword sings across the Warden\'s chest. Wire parts with a sound like a snapped '
      + 'harp string and a rib splinters away, clattering into the dark. The creature staggers but does not fall; '
      + 'its skull turns toward you with terrible patience.') },
  {
    kind: 'attack',
    attacker: 'Bone Warden',
    cueText: SAMPLE_ATTACK.cueText,
    narration: ai('The Bone Warden raises its War Hammer high above its head, the iron catching what little light there is, '
      + 'and brings it down toward you with the whole weight of its long death behind it.'),
  },
  { kind: 'player', text: 'I raise my Longsword to parry the Bone Warden\'s blow.' },
  { kind: 'narration', narration: ai('You lift your blade, but the hammer is heavier than any living arm could swing. It drives '
      + 'through your guard and clips your shoulder, and pain flares bright and hot.\n\n'
      + 'You stumble back across the landing and find your footing. The Warden draws itself upright, its empty '
      + 'sockets fixed on you, waiting to see what you will do.') },
];

/** The same defense outcome as the deterministic fallback would tell it ("Plain telling" state). */
export const SAMPLE_FALLBACK: NarrationView = {
  text: 'The Bone Warden\'s attack hits you for 7 damage.',
  source: 'FALLBACK',
};

/** A later exchange ending in death: the player attacks, the Warden answers, and the defense fails. */
export const SAMPLE_ENDING: SampleEntry[] = [
  { kind: 'player', text: 'I duck under its guard and drive my blade up beneath its jaw.' },
  { kind: 'narration', narration: ai('Your thrust skates off the Warden\'s jawbone in a spray of grave dust. It barely seems to notice.') },
  {
    kind: 'attack',
    attacker: 'Bone Warden',
    cueText: 'Incoming: a wide swing coming from the side.',
    narration: ai('The Warden sweeps its hammer in a long, flat arc, the iron whistling through the dark at the height of your ribs.'),
  },
  { kind: 'player', text: 'I throw myself flat beneath the swing.' },
  { kind: 'narration', narration: ai('You are a heartbeat too slow. The hammer finds you before the floor does. The cold comes '
      + 'quickly after, and above you the cracked bell, at last, begins to ring.') },
];
