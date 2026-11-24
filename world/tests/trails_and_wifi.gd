extends SceneTree

# Run with Godot 4.5.2 --headless --path world --script res://tests/trails_and_wifi.gd
const Plaza = preload("res://plaza.gd")
const Guide = preload("res://trail_guide.gd")
var failures := 0


func _initialize() -> void:
	call_deferred("_run")


func _check(condition: bool, message: String) -> void:
	if condition:
		print("PASS ", message)
	else:
		failures += 1
		push_error(message)


func _client(label: String) -> Plaza:
	var branch := Node.new()
	branch.name = label
	root.add_child(branch)
	set_multiplayer(SceneMultiplayer.new(), branch.get_path())
	var plaza := Plaza.new()
	plaza.name = "Plaza"
	branch.add_child(plaza)
	plaza._guide.open(label + str(Time.get_ticks_usec()))
	return plaza


func _until(check: Callable, seconds := 4.0) -> bool:
	var until := Time.get_ticks_msec() + int(seconds*1000)
	while not check.call() and Time.get_ticks_msec() < until:
		await process_frame
	return check.call()


func _run() -> void:
	var host := _client("Host")
	var visitor := _client("Visitor")
	host._hud._guide_menu.show()
	_check(host._hud.is_address_open(), "map pauses movement")
	host._hud._places.get_child(2).pressed.emit()
	_check(host._target == "lake" and not host._hud.is_address_open(), "choosing the jetty sets a waypoint")
	host._hud.walk_requested.emit()
	for stop: String in Guide.FOREST_WALK:
		host._players[1].pos = Guide.STOPS[stop].at
		host._players[1].trail_time = -3000
		host._hud.trail_action_requested.emit()
	_check(host._guide.walk.is_empty() and host._guide.stamps.has_section_key("walks", "forest"), "completing a circuit records its time")
	var saved := ConfigFile.new()
	_check(saved.load(host._guide._path) == OK and saved.has_section_key("visits", "lake"), "journal survives reload")
	host._hud.host_requested.emit()
	var started := await _until(func(): return host._mode == Plaza.Mode.CLIENT)
	_check(started, "host phone enters its own park")
	if not started:
		print("Host status: ", host._hud._status.text, " / ", host._hud._address_status.text, " / mode ", host._mode)
		quit(1)
		return
	visitor._hud.connect_requested.emit("127.0.0.1")
	_check(await _until(func(): return host._players.size() == 2 and visitor._players.size() == 2), "two clients share a park")
	var server: Plaza = host._host_branch.get_child(0)
	var first := host._local_id()
	var second := visitor._local_id()
	if not server._players.has(second):
		quit(1)
		return
	server._players[first].pos = Guide.STOPS.chimes.at
	server._players[second].pos = Guide.STOPS.chimes.at
	host._hud.meetup_requested.emit()
	_check(await _until(func(): return visitor._hud._suggestion == "chimes"), "room receives meetup pin")
	visitor._hud._meet_suggestion.pressed.emit()
	_check(visitor._target == "chimes", "friend can follow the meetup")
	host._hud.trail_action_requested.emit()
	_check(await _until(func(): return host._guide.stamps.has_section_key("visits", "chimes")), "server confirms first chime")
	visitor._hud.trail_action_requested.emit()
	_check(await _until(func(): return host._hud._toast.text.contains("duet") and visitor._hud._toast.text.contains("duet")), "two players make a shared duet")
	host._hud.island_requested.emit("create", "2")
	_check(await _until(func(): return host._art.private_island), "host enters a private island")
	_check(visitor._players.size() <= 2 and not visitor._art.private_island, "visitor stays in the public park")
	server._players[first].pos = Guide.STOPS.cove.at
	host._hud.meetup_requested.emit()
	visitor._hud.island_requested.emit("join", host._area)
	_check(await _until(func(): return visitor._area == host._area and visitor._players.size() == 2), "friend joins via island code")
	_check(visitor._hud._suggestion.is_empty(), "previous room meetup clears")
	server._players[second].pos = Guide.STOPS.picnic.at
	visitor._hud.trail_action_requested.emit()
	_check(await _until(func(): return visitor._guide.stamps.has_section_key("visits", "picnic")), "island picnic awards a stamp")
	host._hud.disconnect_requested.emit()
	_check(await _until(func(): return visitor._mode == Plaza.Mode.SOLO, 8.0), "guest returns to solo when host leaves")
	quit(1 if failures else 0)
