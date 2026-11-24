extends RefCounted

const REACH := 3.0
# The map, scenery and authoritative interaction checks share these coordinates.
const STOPS := {
	"chimes": {"title": "Chime pavilion", "at": Vector2(-9, -7), "action": "Ring chimes",
		"hint": "Play a note. A second player can answer within six seconds to make a duet."},
	"meadow": {"title": "Butterfly meadow", "at": Vector2(9, -6), "action": "Stir butterflies",
		"hint": "Step into the flower ring and send the butterflies up from the grass."},
	"lake": {"title": "Stillwater jetty", "at": Vector2(23, 10), "action": "Skip a stone",
		"hint": "Follow the eastern path to the wooden jetty and skip a stone across the lake."},
	"mill": {"title": "Apple orchard", "at": Vector2(-25, 11), "action": "Turn the sails",
		"hint": "Walk west through the apple trees and catch the breeze in the old mill."},
	"ridge": {"title": "Birdwatch clearing", "at": Vector2(0, -31), "action": "Call the birds",
		"hint": "Follow the north trail past the woods. Listen for an answer at the bird hide."},
	"picnic": {"title": "Island picnic", "at": Vector2(-4.8, 4.5), "action": "Pour tea",
		"hint": "Find the checkered blanket and pour a cup for the picnic."},
	"cove": {"title": "Shell cove", "at": Vector2(6, -4), "action": "Sound the shell",
		"hint": "Find the pale shells on the eastern shore and listen to their call."},
}
const PARK := ["chimes", "meadow", "lake", "mill", "ridge"]
const ISLAND := ["picnic", "cove"]
const FOREST_WALK := ["lake", "mill", "ridge", "lake"]
const SHORE_WALK := ["picnic", "cove", "picnic"]
var stamps := ConfigFile.new()
var _path: String
var walk: Array = []
var leg := 0
var started := 0


func open(profile: String) -> void:
	_path = "user://trail_%s.cfg" % profile.sha256_text()
	if FileAccess.file_exists(_path):
		stamps.load(_path)


func collect(stop: String) -> bool:
	if stamps.has_section_key("visits", stop):
		return false
	stamps.set_value("visits", stop, true)
	if stamps.save(_path) != OK:
		push_warning("Could not save the trail journal to this device.")
	return true


func begin_walk(island: bool) -> void:
	walk = SHORE_WALK if island else FOREST_WALK
	leg = 0
	started = 0


func advance_walk(id: String, island: bool) -> String:
	if walk.is_empty() or walk[leg] != id:
		return ""
	if leg == 0:
		started = Time.get_ticks_msec()
	leg += 1
	if leg < walk.size():
		return "Next stop · " + String(STOPS[walk[leg]].title)
	var elapsed := maxi(1, Time.get_ticks_msec() - started)
	var key := "shore" if island else "forest"
	var best := int(stamps.get_value("walks", key, 0))
	walk = []
	if best == 0 or elapsed < best:
		stamps.set_value("walks", key, elapsed)
		if stamps.save(_path) != OK:
			push_warning("Could not save this trail time.")
		return "Personal best · " + format_time(elapsed)
	return "Walk complete · " + format_time(elapsed)


static func format_time(ms: int) -> String:
	return "%d:%02d" % [floori(ms / 60000.0), floori(ms / 1000.0) % 60]


static func places(island: bool) -> Array:
	return ISLAND if island else PARK


static func nearby(pos: Vector2, island: bool) -> String:
	for id: String in places(island):
		if pos.distance_to(STOPS[id].at) <= REACH:
			return id
	return ""
