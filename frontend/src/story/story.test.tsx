import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { sampleView } from '../test/fakeApi';
import { Composer, DefenseBanner } from './Composer';
import { CharacterPanel, ScenePanel } from './Panels';
import { IncomingAttack, NarrationPassage, PlayerPassage } from './Passages';
import { toParagraphs } from './paragraphs';
import { isLowHp, StoryHeader } from './StoryHeader';

const HOSTILE = '<img src=x onerror="alert(1)"><script>alert(2)</script>';

describe('story passages', () => {
  it('render game text as text, never as markup', () => {
    const { container } = render(
      <>
        <NarrationPassage narration={{ text: HOSTILE, source: 'AI' }} />
        <PlayerPassage text={HOSTILE} kind="FREE_TEXT" />
        <IncomingAttack attacker={HOSTILE} cueText={HOSTILE} narration={{ text: HOSTILE, source: 'AI' }} />
      </>,
    );
    expect(container.querySelector('img, script')).toBeNull();
    expect(screen.getAllByText(HOSTILE, { exact: false }).length).toBeGreaterThan(0);
  });

  it('split narration into paragraphs', () => {
    expect(toParagraphs('One.\n\nTwo.\nThree.\n\n\n')).toEqual(['One.', 'Two.', 'Three.']);
    const { container } = render(<NarrationPassage narration={{ text: 'First.\n\nSecond.', source: 'AI' }} />);
    expect(container.querySelectorAll('p')).toHaveLength(2);
  });

  it('mark a deterministic fallback honestly', () => {
    render(<NarrationPassage narration={{ text: 'The attack hits you for 7 damage.', source: 'FALLBACK' }} />);
    expect(screen.getByText(/told plainly/i)).toBeInTheDocument();
  });

  it('keep the player\'s exact wording and mark commands', () => {
    render(<><PlayerPassage text={'  I parry —\n"now!"  '} kind="FREE_TEXT" /><PlayerPassage text="/hold" kind="COMMAND" /></>);
    expect(screen.getByText('You').parentElement?.textContent).toBe('You  I parry —\n"now!"  ');
    expect(screen.getByText('Command')).toBeInTheDocument();
  });

  it('show the Java cue without its prefix, before any narration', () => {
    render(<IncomingAttack attacker="Bone Warden" cueText="Incoming: an overhead strike coming down from above." narration={null} />);
    expect(screen.getByText('an overhead strike coming down from above.')).toBeInTheDocument();
    expect(screen.getByLabelText('Incoming attack from the Bone Warden')).toBeInTheDocument();
  });
});

describe('header, composer and panels', () => {
  it('flag low HP at 30% or below', () => {
    expect(isLowHp(8, 24)).toBe(false);
    expect(isLowHp(7, 24)).toBe(true);
    expect(isLowHp(3, 10)).toBe(true);
    expect(isLowHp(0, 24)).toBe(true);
    render(<StoryHeader region="Hollow Chapel" scene="Ossuary" name="Wren" hp={5} maxHp={24} />);
    expect(screen.getByRole('button', { name: /Wren, 5 of 24 health/ })).toBeInTheDocument();
    expect(screen.getByText('Hollow Chapel')).toBeInTheDocument();
  });

  it('keep sending disabled until turns exist (15D), with a reason', () => {
    render(<><DefenseBanner attacker="Bone Warden" cueText="Incoming: a thrust." /><Composer note="Not yet." /></>);
    expect(screen.getByRole('button', { name: 'Send action' })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('a thrust.');
    expect(screen.getByLabelText('Your action')).toHaveAttribute('maxlength', '500');
  });

  it('list only body parts the backend reports as hurt', () => {
    const character = { ...sampleView().character, body: [
      { part: 'HEAD', severity: 'HEALTHY' }, { part: 'LEFT_ARM', severity: 'WOUNDED' },
    ] };
    render(<CharacterPanel character={character} />);
    const body = screen.getByRole('heading', { name: 'Body' }).closest('section')!;
    expect(body).toHaveTextContent('Left arm');
    expect(body).toHaveTextContent('Wounded');
    expect(body).not.toHaveTextContent('Head');
  });

  it('show only confirmed character and scene facts', () => {
    const view = sampleView({
      scene: {
        zones: [{ alias: 'zone_1', name: 'Bell Landing' }, { alias: 'zone_2', name: 'Rope Gallery' }],
        connections: [{ zoneA: 'zone_1', zoneB: 'zone_2' }],
        creatures: [{ alias: 'entity_1', name: 'Bone Warden', zone: 'zone_2', condition: 'FALLEN' }],
        objects: [], hazards: [], exits: [{ alias: 'exit_1', zone: 'zone_1' }],
      },
    });
    const { container } = render(
      <><CharacterPanel character={view.character} /><ScenePanel scene={view.scene} currentZone={{ alias: 'zone_1', name: 'Bell Landing' }} /></>,
    );
    expect(screen.getByText('Longsword')).toBeInTheDocument();
    expect(screen.getByText('Unhurt.')).toBeInTheDocument();
    const section = (title: string) => screen.getByRole('heading', { name: title }).closest('section')!;
    expect(section('Equipment & Inventory')).toHaveTextContent('Longsword');
    expect(section('Ability')).toHaveTextContent('Warding Sigil');
    expect(section('Ability')).not.toHaveTextContent('Longsword');
    expect(section('Passive Gift')).toHaveTextContent('Light Foot');
    expect(section('Body')).toHaveTextContent('Unhurt.');
    expect(screen.getByText('fallen')).toBeInTheDocument();
    expect(screen.getByText('Open to Rope Gallery.')).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/exit_1|entity_1|zone_1/);
  });
});
