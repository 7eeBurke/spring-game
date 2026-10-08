import { Fragment } from 'react';
import type { CharacterView, SceneView, ZoneView } from '../api/types';
import { sheetStyles as styles } from './Sheet';

const words = (constant: string) => constant.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());

/**
 * Only what the GameView confirms about the character: nothing derived or invented. Body lists only
 * parts the backend reports as not HEALTHY.
 */
export function CharacterPanel({ character }: { character: CharacterView }) {
  const injured = character.body.filter((b) => b.severity !== 'HEALTHY');
  return (
    <>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>{character.name}</h3>
        <dl className={styles.rows}>
          <dt>Health</dt><dd>{character.hp} / {character.maxHp}</dd>
          <dt>Fate</dt><dd>{words(character.fatedBand)}</dd>
        </dl>
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Attributes</h3>
        <dl className={styles.rows}>
          {Object.entries(character.stats).map(([stat, value]) => (
            <Fragment key={stat}><dt>{words(stat)}</dt><dd>{value}</dd></Fragment>
          ))}
        </dl>
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Equipment &amp; Inventory</h3>
        {character.weapons.length + character.items.length === 0 ? <p className={styles.muted}>Nothing carried.</p> : (
          <ul className={styles.list}>
            {character.weapons.map((w) => <li key={w.alias}><span>{w.name}</span><span className={styles.muted}>weapon</span></li>)}
            {character.items.map((i) => <li key={i.alias}><span>{i.name}</span><span className={styles.muted}>item</span></li>)}
          </ul>
        )}
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Ability</h3>
        {character.abilities.map((a) => <p key={a.alias}>{a.name}</p>)}
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Passive Gift</h3>
        <p>{character.passive}</p>
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Body</h3>
        {injured.length === 0
          ? <p className={styles.muted}>Unhurt.</p>
          : (
            <ul className={styles.list}>
              {injured.map((b) => <li key={b.part}><span>{words(b.part)}</span><span className={styles.injured}>{words(b.severity)}</span></li>)}
            </ul>
          )}
      </section>
    </>
  );
}

/** Only what the player can see: visible zones, creatures, objects, hazards and known exits. */
export function ScenePanel({ scene, currentZone }: { scene: SceneView; currentZone: ZoneView }) {
  const name = (alias: string) => scene.zones.find((z) => z.alias === alias)?.name ?? alias;
  const neighbours = scene.connections
    .filter((c) => c.zoneA === currentZone.alias || c.zoneB === currentZone.alias)
    .map((c) => name(c.zoneA === currentZone.alias ? c.zoneB : c.zoneA));
  return (
    <>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Where you stand</h3>
        <p className={styles.here}>{currentZone.name}</p>
        <p className={styles.muted}>{neighbours.length ? `Open to ${neighbours.join(', ')}.` : 'No open way from here.'}</p>
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Places</h3>
        <ul className={styles.list}>
          {scene.zones.map((z) => (
            <li key={z.alias}><span className={z.alias === currentZone.alias ? styles.here : undefined}>{z.name}</span>
              <span className={styles.muted}>{scene.exits.filter((x) => x.zone === z.alias).length ? 'a way onward' : ''}</span></li>
          ))}
        </ul>
      </section>
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Creatures</h3>
        {scene.creatures.length === 0 ? <p className={styles.muted}>None that you can see.</p> : (
          <ul className={styles.list}>
            {scene.creatures.map((c) => (
              <li key={c.alias}><span className={c.condition === 'FALLEN' ? styles.fallen : undefined}>{c.name}</span>
                <span className={styles.muted}>{c.condition === 'FALLEN' ? 'fallen' : name(c.zone)}</span></li>
            ))}
          </ul>
        )}
      </section>
      {(scene.objects.length > 0 || scene.hazards.length > 0) && (
        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>Around you</h3>
          <ul className={styles.list}>
            {scene.objects.map((o) => <li key={o.alias}><span>{o.name}</span><span className={styles.muted}>{name(o.zone)}</span></li>)}
            {scene.hazards.map((h) => <li key={h.alias}><span className={styles.injured}>{h.name}</span><span className={styles.muted}>{name(h.zone)}</span></li>)}
          </ul>
        </section>
      )}
    </>
  );
}
