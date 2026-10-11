package com.leeburke.springgame.ai.narration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.leeburke.springgame.ai.narration.Perception.Beside;
import com.leeburke.springgame.ai.narration.Perception.PlaceRef;
import com.leeburke.springgame.ai.narration.Perception.SeenCreature;
import com.leeburke.springgame.ai.narration.Perception.SeenThing;
import com.leeburke.springgame.ai.narration.Perception.WayOut;
import com.leeburke.springgame.content.world.PlaceTexts;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.world.ZoneConnection;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Describes one scene as the player knows it, in the world's words: authored phrases and
 * descriptions for places, passages and things, looked up by the scene's code and local IDs. Works
 * only from the player-known view, so nothing hidden or unseen can appear. Where a text is missing
 * (content written before texts existed), a plain fallback from the label is used, never an invention.
 */
public final class PlaceDescriber {

	private final WorldContentCatalog world;
	private final String sceneCode;
	private final PlayerSceneView view;
	private final NarrationNames names;
	private final Set<String> fallen;
	private final Map<String, String> exitLabels;
	private final Function<String, String> itemName;
	private final Optional<PlaceTexts.SceneTexts> texts;

	/**
	 * @param sceneCode  the scene's archetype or fixed-scene code
	 * @param fallen     visible creatures at 0 HP
	 * @param exitLabels where each known exit leads, as the player knows it
	 * @param itemName   display name of an item code
	 */
	public PlaceDescriber(WorldContentCatalog world, String sceneCode, PlayerSceneView view, Set<String> fallen,
			Map<String, String> exitLabels, Function<String, String> itemName) {
		this.world = Objects.requireNonNull(world, "world");
		this.sceneCode = Objects.requireNonNull(sceneCode, "sceneCode");
		this.view = Objects.requireNonNull(view, "view");
		this.names = NarrationNames.of(view, world);
		this.fallen = Set.copyOf(fallen);
		this.exitLabels = Map.copyOf(exitLabels);
		this.itemName = Objects.requireNonNull(itemName, "itemName");
		this.texts = world.texts().scene(sceneCode);
	}

	/** The same describer, with one container as it now is (after an earlier step of the same turn changed it). */
	public PlaceDescriber withContainer(PlayerSceneView.VisibleContainer changed) {
		return new PlaceDescriber(world, sceneCode, view.withContainer(changed), fallen, exitLabels, itemName);
	}

	public PlayerSceneView view() {
		return view;
	}

	public String sceneDescription() {
		return texts.map(PlaceTexts.SceneTexts::description).orElse("");
	}

	/**
	 * Leads as the player knows them, in the world's words: ways by their passages, places by their
	 * phrases, each with how far it is and the first place to go through.
	 */
	public java.util.List<com.leeburke.springgame.ai.narration.NarrationFact.Lead> ways(
			java.util.List<com.leeburke.springgame.world.view.ExplorationLeads.Way> ways) {
		String here = view.currentZoneId();
		return ways.stream().map(w -> new NarrationFact.Lead(wayPassage(w.exitId()), w.zoneId().equals(here) ? "here" : place(w.zoneId()).phrase(),
				w.steps(), w.via().map(v -> place(v).phrase()), Optional.of(wayLeadsTo(w.exitId())).filter(to -> !to.isBlank()))).toList();
	}

	public java.util.List<NarrationFact.Lead> places(java.util.List<com.leeburke.springgame.world.view.ExplorationLeads.Place> places) {
		return places.stream().map(p -> new NarrationFact.Lead(place(p.zoneId()).phrase(), place(p.zoneId()).phrase(), p.steps(),
				p.via().map(v -> place(v).phrase()), Optional.empty())).toList();
	}

	public PlaceRef place(String zoneId) {
		String label = names.zone(zoneId).orElse("somewhere unseen");
		Optional<PlaceTexts.ZoneText> text = texts.map(t -> t.zones().get(zoneId));
		return new PlaceRef(label, text.map(PlaceTexts.ZoneText::phrase).orElse("the " + label.toLowerCase(Locale.ROOT)),
				text.map(PlaceTexts.ZoneText::description).orElse(""));
	}

	/** The passage between two zones, as authored for the connection that joins them ("" when none is authored). */
	public String passage(String zoneA, String zoneB) {
		List<ZoneConnection> connections = world.findArchetype(sceneCode).map(a -> a.connections())
				.or(() -> world.findFixedScene(sceneCode).map(f -> f.connections())).orElse(List.of());
		return connections.stream()
				.filter(c -> c.zoneA().equals(zoneA) && c.zoneB().equals(zoneB) || c.zoneA().equals(zoneB) && c.zoneB().equals(zoneA))
				.findFirst().flatMap(c -> texts.map(t -> t.connections().get(c.id())))
				.orElse("");
	}

	/** The exit back out to the road from a region's first scene. */
	public static final String ROAD_EXIT_ID = "lantern_road";

	/**
	 * How one known way out looks, from the zone it leaves. The road exit of a region's first scene
	 * is the scene's authored entrance (the doors the player came in by). Every other way out takes
	 * the zone's passages in exit-ID order, so two ways out of the same place never read alike.
	 */
	public String wayPassage(String exitId) {
		Optional<PlayerSceneView.KnownExit> exit = view.exits().stream().filter(x -> x.id().equals(exitId)).findFirst();
		if (exit.isEmpty()) {
			return "a way out";
		}
		Optional<String> entrance = world.texts().entrance(sceneCode);
		if (exitId.equals(ROAD_EXIT_ID) && entrance.isPresent()) {
			return entrance.get();
		}
		String zone = exit.get().zoneId();
		List<String> passages = texts.map(t -> t.exits().get(zone)).filter(Objects::nonNull).orElse(List.of());
		List<String> fromHere = view.exits().stream().filter(x -> x.zoneId().equals(zone))
				.filter(x -> !(x.id().equals(ROAD_EXIT_ID) && entrance.isPresent())).map(PlayerSceneView.KnownExit::id).sorted().toList();
		int index = fromHere.indexOf(exitId);
		return index >= 0 && index < passages.size() ? passages.get(index) : "a way out";
	}

	/** The zone a known exit leaves from. */
	public Optional<String> exitZone(String exitId) {
		return view.exits().stream().filter(x -> x.id().equals(exitId)).map(PlayerSceneView.KnownExit::zoneId).findFirst();
	}

	/**
	 * Where a way leads, for telling it: its label, or "" when the way's own passage already names that
	 * place ("the sagging west doors into the Hollow Chapel" needs no "to the road to the Hollow
	 * Chapel"), so a sentence never says the same place twice. The label itself is unchanged.
	 */
	public String wayLeadsTo(String exitId) {
		String label = exitLabel(exitId);
		String destination = label.replaceFirst("(?i)^the (road|way) to (the )?", "");
		if (!destination.equals(label) && !destination.isBlank()
				&& wayPassage(exitId).toLowerCase(Locale.ROOT).contains(destination.toLowerCase(Locale.ROOT))) {
			return "";
		}
		return label;
	}

	/**
	 * The known ways leaving from a place, as leads from where the player stands: each way's passage,
	 * "here" or the place it leaves from, how many places away, and where it leads.
	 */
	public List<NarrationFact.Lead> waysFrom(String zoneId) {
		Map<String, Integer> steps = view.stepsFrom(view.currentZoneId());
		return view.exits().stream().filter(x -> x.zoneId().equals(zoneId)).sorted(Comparator.comparing(PlayerSceneView.KnownExit::id))
				.map(x -> new NarrationFact.Lead(wayPassage(x.id()), zoneId.equals(view.currentZoneId()) ? "here" : place(zoneId).phrase(),
						steps.getOrDefault(zoneId, 0), Optional.empty(),
						Optional.of(wayLeadsTo(x.id())).filter(to -> !to.isBlank())))
				.toList();
	}

	public String exitLabel(String exitId) {
		return exitLabels.getOrDefault(exitId, Surroundings.UNEXPLORED);
	}

	/** Zones joined to this one by a passage the player can see. */
	public List<String> beside(String zoneId) {
		return view.connections().stream()
				.map(c -> c.zoneA().equals(zoneId) ? c.zoneB() : c.zoneB().equals(zoneId) ? c.zoneA() : null)
				.filter(Objects::nonNull).distinct().sorted().toList();
	}

	/** What the player perceives standing in this zone: here and beside it, nothing farther. */
	public Perception perceive(String zoneId) {
		List<String> besideIds = beside(zoneId);
		List<Beside> beside = besideIds.stream().map(z -> new Beside(place(z), passage(zoneId, z))).toList();
		List<SeenThing> things = new ArrayList<>();
		view.objects().stream().sorted(Comparator.comparing(PlayerSceneView.VisibleObject::id))
				.filter(o -> o.zoneId().equals(zoneId) || besideIds.contains(o.zoneId()))
				.forEach(o -> things.add(thing(o.id(), o.definitionCode(), o.zoneId(), zoneId)));
		List<SeenThing> hazards = new ArrayList<>();
		view.hazards().stream().sorted(Comparator.comparing(PlayerSceneView.VisibleHazard::id))
				.filter(h -> h.zoneId().equals(zoneId) || besideIds.contains(h.zoneId()))
				.forEach(h -> hazards.add(new SeenThing(names.hazard(h.id()).orElse("something"), elementText(h.definitionCode()),
						where(h.zoneId(), zoneId), h.zoneId().equals(zoneId), Optional.empty())));
		List<SeenCreature> creatures = view.entities().stream().sorted(Comparator.comparing(PlayerSceneView.VisibleEntity::id))
				.filter(e -> e.zoneId().equals(zoneId) || besideIds.contains(e.zoneId()))
				.map(e -> new SeenCreature(names.entity(e.id()).orElse("something unseen"), where(e.zoneId(), zoneId),
						e.zoneId().equals(zoneId), fallen.contains(e.id())))
				.toList();
		List<WayOut> ways = view.exits().stream().sorted(Comparator.comparing(PlayerSceneView.KnownExit::id))
				.filter(x -> x.zoneId().equals(zoneId) || besideIds.contains(x.zoneId()))
				.map(x -> new WayOut(wayLeadsTo(x.id()), wayPassage(x.id()), where(x.zoneId(), zoneId), x.zoneId().equals(zoneId)))
				.toList();
		return new Perception(Optional.of(place(zoneId)), beside, things, hazards, creatures, ways);
	}

	/** An object as the player can perceive it from this zone; empty when it is out of sight from here. */
	public Optional<SeenThing> object(String objectId, String fromZone) {
		List<String> sight = new ArrayList<>(beside(fromZone));
		sight.add(fromZone);
		return view.objects().stream().filter(o -> o.id().equals(objectId) && sight.contains(o.zoneId())).findFirst()
				.map(o -> thing(o.id(), o.definitionCode(), o.zoneId(), fromZone));
	}

	public String objectName(String objectId) {
		return names.object(objectId).orElse("something");
	}

	/** The phrase of the zone an object is in, if the player knows it. */
	public Optional<String> objectPlace(String objectId) {
		return view.objects().stream().filter(o -> o.id().equals(objectId)).findFirst().map(o -> place(o.zoneId()).phrase());
	}

	private SeenThing thing(String id, String code, String zone, String fromZone) {
		return new SeenThing(names.object(id).orElse("something"), elementText(code), where(zone, fromZone), zone.equals(fromZone),
				view.container(id).map(this::containerState));
	}

	/** "closed", "open, holding a Bandage" or "open and empty". */
	public String containerState(PlayerSceneView.VisibleContainer container) {
		if (!container.open()) {
			return "closed";
		}
		return container.contents().isEmpty() ? "open and empty"
				: "open, holding " + String.join(" and ", container.contents().stream().map(code -> article(itemName.apply(code))).toList());
	}

	public String itemName(String code) {
		return itemName.apply(code);
	}

	private String where(String zone, String fromZone) {
		return zone.equals(fromZone) ? "here" : place(zone).phrase();
	}

	private String elementText(String code) {
		return world.texts().element(code).orElse("");
	}

	static String article(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return (lower.startsWith("a") || lower.startsWith("e") || lower.startsWith("i") || lower.startsWith("o")
				|| lower.startsWith("u") ? "an " : "a ") + name;
	}
}
