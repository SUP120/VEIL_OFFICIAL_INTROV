extends Node3D

const Art = preload("res://plaza_art.gd")
const Avatar = preload("res://avatar.gd")
const Hud = preload("res://plaza_ui.gd")
const ParkCamera = preload("res://park_camera.gd")
const Matchmaking = preload("res://private_matchmaking.gd")
const Admission = preload("res://online_admission.gd")
const Guide = preload("res://trail_guide.gd")
const PORT := 7132
const ONLINE_PORT := 7133
const PARK_CAPACITY := 25
const SERVER_CAPACITY := 100
const WALK_SPEED := 4.0
const UPDATE_INTERVAL := 1.0 / 15.0
const INPUT_TIMEOUT := 500
const CONNECT_TIMEOUT := 8.0
const ONLINE_CONNECT_TIMEOUT := 20.0
const WAKE_WINDOW_MS := 100000
const WAKE_RETRY_MS := 4000
const MAX_ISLANDS := 6
const START := Vector2(0, 5.7)

enum Mode { SOLO, CONNECTING, CLIENT, SERVER, LEAVING, WAKING }

var _mode := Mode.SOLO
var _players: Dictionary[int, Dictionary] = {}
var _islands: Dictionary[String, int] = {}
var _matchmaking := Matchmaking.new()
var _next_match := 1
var _random := Crypto.new()
var _avatars: Dictionary[int, Avatar] = {}
var _art: Art
var _area := ""
var _hud: Hud
var _camera: ParkCamera
var _peer: MultiplayerPeer
var _admission: Admission
var _online_client := false
var _session_check := 0.0
var _bridge: Object
var _alias := "Wanderer"
var _update_clock := 0.0
var _connect_clock := 0.0
var _wake_deadline := 0
var _retry_at := 0
var _auth_started := false
var _sent_input := Vector2.ZERO
var _guide := Guide.new()
var _target := ""
var _guide_clock := 0.0
var _chimes: Dictionary = {}
var _last_effect: Dictionary = {}
var _endpoint := ""
var _host_branch: Node
var embedded_server := false


func _ready() -> void:
	if embedded_server:
		return
	var args := OS.get_cmdline_user_args()
	var endpoint := ""
	var bind_address := "*"
	var journal := "desktop"
	for arg in args:
		if arg.begins_with("--alias="):
			_alias = _clean_alias(arg.trim_prefix("--alias="))
		elif arg.begins_with("--connect="):
			endpoint = arg.trim_prefix("--connect=")
		elif arg.begins_with("--bind="):
			bind_address = arg.trim_prefix("--bind=")
		elif arg.begins_with("--journal="):
			journal = arg.trim_prefix("--journal=")
	if "--online-server" in args:
		_host_online()
		return
	if "--server" in args:
		_host_playtest(bind_address)
		return

	if Engine.has_singleton("VeilWorldBridge"):
		_bridge = Engine.get_singleton("VeilWorldBridge")
	_guide.open(journal)
	_endpoint = endpoint
	_show_area("")
	_camera = ParkCamera.new()
	add_child(_camera)
	_camera.follow(Vector3(START.x, 0, START.y), _art, 0.0)
	_hud = Hud.new()
	_hud.light = "--light-ui" in args
	add_child(_hud)
	_hud.connect_requested.connect(_join_playtest)
	_hud.host_requested.connect(_host_with_friends)
	_hud.trail_selected.connect(_track_stop)
	_hud.trail_action_requested.connect(_try_trail.bind(false))
	_hud.meetup_requested.connect(_try_trail.bind(true))
	_hud.walk_requested.connect(_toggle_walk)
	_hud.disconnect_requested.connect(_return_to_solo)
	_hud.sit_requested.connect(_try_seat)
	_hud.wave_requested.connect(_try_wave)
	_hud.island_requested.connect(_try_island_action)
	_hud.match_requested.connect(_try_match_action)
	_hud.whisper_requested.connect(_open_whisper)
	_hud.exit_requested.connect(_leave_game)
	_hud.orbit_requested.connect(_camera.orbit)
	_hud.zoom_requested.connect(_camera.zoom)
	_hud.reset_view_requested.connect(_camera.reset_view)

	multiplayer.connected_to_server.connect(_on_connected)
	multiplayer.connection_failed.connect(_on_connection_failed)
	multiplayer.server_disconnected.connect(_on_server_disconnected)
	var api := multiplayer as SceneMultiplayer
	api.peer_authenticating.connect(_send_online_identity)
	api.peer_authentication_failed.connect(_on_online_auth_failed)
	_return_to_solo()
	_refresh_guide()
	if not endpoint.is_empty():
		_hud.set_preferred_host(endpoint)
		if endpoint.begins_with("wss://") and _bridge != null and String(_bridge.call("getWorldIdToken")).is_empty():
			_hud.toast("Solo and Wi-Fi available. Reopen the world online to refresh sign-in.")
		else:
			_join_playtest(endpoint)


func _host_playtest(bind_address: String) -> void:
	if bind_address != "*" and not bind_address.is_valid_ip_address():
		push_error("VEIL World bind address must be an IP address.")
		get_tree().quit(1)
		return
	var lan := ENetMultiplayerPeer.new()
	lan.set_bind_ip(bind_address)
	var result := lan.create_server(PORT, SERVER_CAPACITY)
	if result != OK:
		push_error("VEIL World server could not bind UDP %d: %s" % [PORT, error_string(result)])
		get_tree().quit(1)
		return
	_start_server(lan)
	print("VEIL World park server ready on UDP ", PORT)


func _host_with_friends() -> void:
	if _mode != Mode.SOLO or _host_branch != null:
		return
	var addresses: Array[String] = []
	for address in IP.get_local_addresses():
		var parts := address.split(".")
		if parts.size() == 4 and (address.begins_with("192.168.") or address.begins_with("10.") or
			(parts[0] == "172" and int(parts[1]) >= 16 and int(parts[1]) <= 31)):
			addresses.append(address)
	if addresses.is_empty():
		_hud.show_connection_message("Connect to the same Wi-Fi as your friends, then try again.")
		return
	var lan := ENetMultiplayerPeer.new()
	var error := lan.create_server(PORT, SERVER_CAPACITY)
	if error != OK:
		_hud.show_connection_message("Could not host on this device. Join a friend’s park instead.")
		return
	# Use the same server and room rules as a dedicated host. This phone is also a normal client.
	_host_branch = Node.new()
	_host_branch.name = "WifiHost"
	get_tree().root.add_child(_host_branch)
	get_tree().set_multiplayer(SceneMultiplayer.new(), _host_branch.get_path())
	var server = get_script().new()
	server.embedded_server = true
	server.name = name
	_host_branch.add_child(server)
	server._start_server(lan)
	_hud.set_host_address(", ".join(addresses))
	_join_playtest("127.0.0.1")


func _stop_host() -> void:
	if not is_instance_valid(_host_branch) or not _host_branch.is_inside_tree():
		return
	var server = _host_branch.get_child(0)
	server._close_connection(Mode.LEAVING)
	get_tree().set_multiplayer(null, _host_branch.get_path())
	_host_branch.get_parent().remove_child(_host_branch)
	_host_branch.queue_free()
	_host_branch = null


func _host_online() -> void:
	var project := OS.get_environment("VEIL_FIREBASE_PROJECT_ID").strip_edges()
	var api_key := OS.get_environment("VEIL_FIREBASE_WEB_API_KEY").strip_edges()
	if project.is_empty() or api_key.is_empty():
		push_error("Set VEIL_FIREBASE_PROJECT_ID and VEIL_FIREBASE_WEB_API_KEY for online admission.")
		get_tree().quit(1)
		return
	var bind_ip := OS.get_environment("VEIL_WORLD_BIND")
	var port := ONLINE_PORT
	if bind_ip.is_empty():
		bind_ip = "127.0.0.1"
	else:
		var setting := OS.get_environment("PORT")
		if not bind_ip.is_valid_ip_address() or not setting.is_valid_int() or int(setting) < 1 or int(setting) > 65535:
			push_error("Invalid VEIL_WORLD_BIND or PORT for online hosting.")
			get_tree().quit(1)
			return
		port = int(setting)
	var online := WebSocketMultiplayerPeer.new()
	var result := online.create_server(port, bind_ip)
	if result != OK:
		push_error("VEIL World could not listen on %s:%d: %s" % [bind_ip, port, error_string(result)])
		get_tree().quit(1)
		return
	_admission = Admission.new()
	add_child(_admission)
	_admission.configure(multiplayer as SceneMultiplayer, project, api_key, SERVER_CAPACITY)
	_start_server(online)
	print("VEIL World authenticated lobby listening on %s:%d" % [bind_ip, port])


func _start_server(peer: MultiplayerPeer) -> void:
	_peer = peer
	_mode = Mode.SERVER
	# The server owns every game action. Clients do not need to discover or relay to each other.
	(multiplayer as SceneMultiplayer).server_relay = false
	multiplayer.peer_connected.connect(_add_peer)
	multiplayer.peer_disconnected.connect(_erase_peer)
	_matchmaking.status.connect(_send_match_status)
	_matchmaking.matched.connect(_start_match)
	multiplayer.multiplayer_peer = _peer


func _join_playtest(address: String) -> void:
	if _mode != Mode.SOLO:
		return
	if address.begins_with("wss://") and address == _endpoint:
		_wake_deadline = Time.get_ticks_msec() + WAKE_WINDOW_MS
	_connect_transport(address)


func _connect_transport(address: String) -> void:
	var result: Error
	if address.begins_with("wss://"):
		if address != _endpoint:
			_hud.show_connection_message("Enter your friend’s Wi-Fi IP address.")
			return
		var online := WebSocketMultiplayerPeer.new()
		_auth_started = false
		result = online.create_client(address, TLSOptions.client())
		_peer = online
		_online_client = result == OK
		if _online_client:
			var api := multiplayer as SceneMultiplayer
			api.auth_timeout = 15.0
			api.auth_callback = _receive_online_auth
	else:
		address = "127.0.0.1" if address.to_lower() == "localhost" else address
		if not address.is_valid_ip_address():
			_hud.show_connection_message("Enter the host computer’s IP address.")
			return
		var lan := ENetMultiplayerPeer.new()
		result = lan.create_client(address, PORT)
		_peer = lan
	if result != OK:
		_peer = null
		if _wake_deadline != 0:
			_retry_or_finish_online()
			return
		_hud.set_status("Park unavailable · Solo")
		_hud.show_connection_message("Could not open the connection.")
		return
	_mode = Mode.CONNECTING
	_hud.set_joining(true)
	_connect_clock = 0.0
	_hud.show_connection_message("Joining the park…")
	_hud.set_status("Waking the public park…" if _online_client else "Connecting to the forest park…")
	multiplayer.multiplayer_peer = _peer


func _receive_online_auth(_peer_id: int, _data: PackedByteArray) -> void:
	pass


func _send_online_identity(peer_id: int) -> void:
	if not _online_client or _mode != Mode.CONNECTING or peer_id != 1:
		return
	_auth_started = true
	_hud.set_status("Verifying your VEIL sign-in…")
	var token := ""
	if _bridge != null:
		token = String(_bridge.call("getWorldIdToken"))
	if token.is_empty():
		_on_online_auth_failed(peer_id)
		return
	var api := multiplayer as SceneMultiplayer
	if api.send_auth(peer_id, token.to_utf8_buffer()) == OK:
		api.complete_auth(peer_id)


func _on_online_auth_failed(_peer_id: int) -> void:
	if _online_client and _mode == Mode.CONNECTING:
		_auth_started = true
		_on_connection_failed()


func _on_connected() -> void:
	_mode = Mode.CLIENT
	_wake_deadline = 0
	_sent_input = Vector2.ZERO
	_players.clear()
	_hud.update_connection(true)
	if not _online_client:
		_set_alias.rpc_id(1, _alias)
	print("Connected to VEIL World")
	if _host_branch != null:
		_hud.toast("Park open · Tap Room for the address to give your friends.")


func _on_connection_failed() -> void:
	if _mode != Mode.CONNECTING:
		return
	if _online_client:
		_retry_or_finish_online()
		return
	_return_to_solo()
	_hud.set_status("Park unavailable · Solo")
	_hud.show_connection_message("Couldn’t reach the host. Check the server and UDP port %d." % PORT)


func _retry_or_finish_online() -> void:
	var now := Time.get_ticks_msec()
	if not _auth_started and now + WAKE_RETRY_MS < _wake_deadline:
		_close_connection(Mode.WAKING)
		_retry_at = now + WAKE_RETRY_MS
		_hud.set_joining(true)
		_hud.set_status("Waking the public park · retrying…")
		return
	var reached_auth := _auth_started
	_return_to_solo()
	_hud.set_status("Online park unavailable · Solo")
	_hud.toast("Could not verify entry. Reopen the world to refresh sign-in." if reached_auth else "Public park unavailable. Try again in Play together.")


func _on_server_disconnected() -> void:
	if _mode != Mode.CLIENT:
		return
	_return_to_solo()
	_hud.set_status("Lost connection · Solo")


func _return_to_solo() -> void:
	_close_connection(Mode.SOLO)
	_players.clear()
	_players[1] = _spawn(_alias, START)
	_show_area("")
	_camera.snap_follow()
	_hud.update_connection(false)
	_hud.update_island("", 0, 0)
	_hud.set_status("Solo · Forest park")
	_hud.show_connection_message("On the same Wi-Fi, host a park or enter a friend’s address.")


func _close_connection(next_mode: Mode) -> void:
	_mode = next_mode
	if next_mode != Mode.WAKING:
		_wake_deadline = 0
	_stop_host()
	if _online_client:
		_online_client = false
		(multiplayer as SceneMultiplayer).auth_callback = Callable()
	if _peer != null:
		multiplayer.multiplayer_peer = null
		_peer.close()
		_peer = null


func _add_peer(peer_id: int) -> void:
	var park := _available_park()
	var alias := "Wanderer"
	if _admission != null:
		alias = _clean_alias(_admission.identity(peer_id).get("alias", ""))
	var actor := _spawn(alias, _park_spawn(_population(park)))
	actor.area = park
	actor.last_park = park
	_players[peer_id] = actor
	print("VEIL World peers: ", _players.size())


func _erase_peer(peer_id: int) -> void:
	if _admission != null:
		_admission.remove(peer_id)
	var area := String(_players.get(peer_id, {}).get("area", ""))
	_forget_chime(area, peer_id)
	_players.erase(peer_id)
	_matchmaking.remove(peer_id)
	_remove_empty_island(area)
	if _is_match(area):
		_end_match(area)
	print("VEIL World peers: ", _players.size())


func _spawn(alias: String, start: Vector2) -> Dictionary:
	return {"alias": alias, "pos": start, "facing": 0.0, "seat": -1,
		"input": Vector2.ZERO, "last_input": 0, "wave_until": 0, "area": "", "last_park": "",
		"last_island_action": -1000, "last_match_queue": -1000, "trail_time": -3000, "meet_time": -15000}


func _population(area: String) -> int:
	var count := 0
	for player: Dictionary in _players.values():
		if player.area == area:
			count += 1
	return count


func _available_park(preferred := "") -> String:
	if not preferred.is_empty() and _population(preferred) < PARK_CAPACITY:
		return preferred
	var number := 1
	while _population("park:%d" % number) >= PARK_CAPACITY:
		number += 1
	return "park:%d" % number


func _park_spawn(occupied: int) -> Vector2:
	return START + Vector2(float(occupied % 5 - 2) * 0.7, floorf(occupied / 5.0) * 0.65)


func _is_private(area: String) -> bool:
	return not area.is_empty() and not area.begins_with("park:")


func _is_match(area: String) -> bool:
	return area.begins_with("match:")


func _show_area(area: String) -> void:
	if _art != null and _area == area:
		return
	var rebuild := _art == null or _is_private(_area) != _is_private(area)
	if rebuild and _art != null:
		remove_child(_art)
		_art.queue_free()
	_area = area
	_guide.walk = []
	_target = ""
	_last_effect.clear()
	if _hud != null:
		_hud.clear_meetup()
		_refresh_guide()
	for avatar: Avatar in _avatars.values():
		avatar.queue_free()
	_avatars.clear()
	if rebuild:
		_art = Art.new()
		_art.private_island = _is_private(area)
		add_child(_art)
	if _camera != null:
		_camera.snap_follow()
	if _art != null and not rebuild:
		_art.mark_trail("")


func _process(delta: float) -> void:
	if _hud == null or _mode == Mode.LEAVING:
		return
	if _mode == Mode.WAKING and Time.get_ticks_msec() >= _retry_at:
		if Time.get_ticks_msec() >= _wake_deadline:
			_retry_or_finish_online()
		else:
			_connect_transport(_endpoint)
	elif _mode == Mode.CONNECTING:
		_connect_clock += delta
		if _connect_clock >= (ONLINE_CONNECT_TIMEOUT if _online_client else CONNECT_TIMEOUT) or (_online_client and Time.get_ticks_msec() >= _wake_deadline):
			_on_connection_failed()

	var local_id := _local_id()
	for id: int in _avatars.keys():
		if not _players.has(id):
			_avatars[id].queue_free()
			_avatars.erase(id)
	for id: int in _players:
		var actor: Dictionary = _players[id]
		var avatar := _avatars.get(id) as Avatar
		if avatar == null:
			avatar = Avatar.new()
			avatar.position = Vector3(actor.pos.x, 0, actor.pos.y)
			add_child(avatar)
			_avatars[id] = avatar
		avatar.configure(actor.alias, id, id == local_id)
		var waving := bool(actor.get("waving", int(actor.get("wave_until", 0)) > Time.get_ticks_msec()))
		avatar.present(delta, actor.pos, actor.facing, actor.seat >= 0, waving)
	if _players.has(local_id):
		var local: Dictionary = _players[local_id]
		var at: Vector2 = local.pos
		_camera.follow(_avatars[local_id].position, _art, delta)
		var near_bench := _benches(_area).any(func(point: Vector2) -> bool: return at.distance_to(point) < 2.4)
		_hud.update_gathering(near_bench, local.seat >= 0,
			_bridge != null and not _is_private(_area) and at.distance_to(Art.GATHERING) <= 4.3)
		_guide_clock -= delta
		if _guide_clock <= 0.0:
			_guide_clock = 0.12
			var others: Array[Vector2] = []
			for id: int in _players:
				if id != local_id:
					others.append(_players[id].pos)
			_hud.update_trail_position(at, Guide.nearby(at, _is_private(_area)), _target, others)


func _physics_process(delta: float) -> void:
	if _mode in [Mode.SOLO, Mode.CONNECTING, Mode.WAKING]:
		if _hud != null:
			_step_actor(_players[1], _camera.world_movement(_hud.movement()), delta)
		return
	if _mode == Mode.SERVER:
		var now := Time.get_ticks_msec()
		if _admission != null:
			_session_check += delta
			if _session_check >= 5.0:
				_session_check = 0.0
				_expire_online_sessions()
		for entry: Dictionary in _players.values():
			var input: Vector2 = entry.input if now - int(entry.last_input) <= INPUT_TIMEOUT else Vector2.ZERO
			_step_actor(entry, input, delta)
	_update_clock += delta
	if _update_clock < UPDATE_INTERVAL:
		return
	_update_clock = 0.0
	if _mode == Mode.SERVER and not _players.is_empty():
		_matchmaking.expire(Time.get_ticks_msec())
		_send_world_state()
	elif _mode == Mode.CLIENT:
		var input := _camera.world_movement(_hud.movement())
		if not input.is_zero_approx() or not _sent_input.is_zero_approx():
			_set_input.rpc_id(1, input)
		_sent_input = input


func _expire_online_sessions() -> void:
	var now := Time.get_unix_time_from_system()
	for id: int in _players.keys():
		if _admission.identity(id).get("expires", 0) <= now:
			(multiplayer as SceneMultiplayer).disconnect_peer(id)
			if _players.has(id):
				_erase_peer(id)


func _step_actor(actor: Dictionary, input: Vector2, delta: float) -> void:
	if actor.seat >= 0 or input.is_zero_approx():
		return
	var previous: Vector2 = actor.pos
	var radius := Art.ISLAND_RADIUS if _is_private(actor.area) else Art.PARK_RADIUS
	var next := (previous + input * WALK_SPEED * delta).limit_length(radius)
	if not _is_private(actor.area) and next.distance_to(Art.LAKE_CENTER) < Art.WATER_RADIUS:
		# The wooden jetty extends into the water from the west.
		if next.x > 24.1 or absf(next.y - Art.LAKE_CENTER.y) > 0.8:
			var bank := Art.LAKE_CENTER + (next - Art.LAKE_CENTER).normalized() * Art.WATER_RADIUS
			next = previous if previous.distance_to(Art.LAKE_CENTER) < Art.WATER_RADIUS else bank
	if next.distance_to(Art.GATHERING) < 1.28:
		next = Art.GATHERING + (previous - Art.GATHERING).normalized() * 1.28
	actor.pos = next
	actor.facing = atan2(-input.x, -input.y)


func _local_id() -> int:
	return multiplayer.get_unique_id() if _mode == Mode.CLIENT else 1


func _try_seat() -> void:
	if _mode == Mode.CLIENT:
		_request_seat.rpc_id(1)
	elif _mode == Mode.SOLO:
		_change_seat(1)


func _try_wave() -> void:
	if _mode == Mode.CLIENT:
		_request_wave.rpc_id(1)
	elif _mode == Mode.SOLO:
		_players[1].wave_until = Time.get_ticks_msec() + 1050


func _track_stop(id: String) -> void:
	if id in Guide.places(_is_private(_area)):
		_target = id
		_art.mark_trail(id)
		_refresh_guide()


func _refresh_guide() -> void:
	_hud.load_trail(_guide, _is_private(_area), _target)


func _toggle_walk() -> void:
	if _guide.walk.is_empty():
		_guide.begin_walk(_is_private(_area))
		_track_stop(_guide.walk[0])
		_hud.toast("Use the activity at the first stop to start your timer.")
	else:
		_guide.walk = []
		_target = ""
		_art.mark_trail("")
		_refresh_guide()


func _try_trail(invite: bool) -> void:
	if _mode == Mode.CLIENT:
		_request_trail.rpc_id(1, invite)
	elif _mode == Mode.SOLO and not invite:
		_use_stop(1, false)


@rpc("any_peer", "call_remote", "reliable")
func _request_trail(invite: bool) -> void:
	if _mode == Mode.SERVER:
		_use_stop(multiplayer.get_remote_sender_id(), invite)


func _use_stop(id: int, invite: bool) -> void:
	if not _players.has(id):
		return
	var actor := _players[id]
	var area := String(actor.area)
	var stop := Guide.nearby(actor.pos, _is_private(area))
	if stop.is_empty():
		return
	var now := Time.get_ticks_msec()
	var duet := false
	if invite:
		if now - int(actor.meet_time) < 15000:
			return
		actor.meet_time = now
	else:
		if now - int(actor.trail_time) < 2500:
			return
		actor.trail_time = now
		if stop == "chimes" and _mode == Mode.SERVER:
			var last: Dictionary = _chimes.get(area, {})
			duet = not last.is_empty() and last.id != id and now - int(last.time) <= 6000
			_chimes[area] = {"id": id, "time": now}
	if _mode == Mode.SOLO:
		_present_trail(area, id, actor.alias, stop, invite, duet)
		return
	for member: int in _players:
		if _players[member].area == area and _peer_connected(member):
			_trail_event.rpc_id(member, area, id, actor.alias, stop, invite, duet)


@rpc("authority", "call_remote", "reliable")
func _trail_event(area: String, id: int, alias: String, stop: String, invite: bool, duet: bool) -> void:
	if _mode == Mode.CLIENT and _area == area:
		_present_trail(area, id, alias, stop, invite, duet)


func _present_trail(area: String, id: int, alias: String, stop: String, invite: bool, duet: bool) -> void:
	if not stop in Guide.places(_is_private(area)):
		return
	var mine := id == _local_id()
	if invite:
		if mine:
			_hud.toast("Meetup shared with this room.")
		else:
			_hud.suggest_meetup(stop, alias)
		return
	var now := Time.get_ticks_msec()
	if duet or now - int(_last_effect.get(stop, -1000)) > 800:
		_last_effect[stop] = now
		_art.play_activity(stop, duet)
	if mine:
		var first := _guide.collect(stop)
		if stop == _target:
			_target = ""
			_art.mark_trail("")
		var progress := _guide.advance_walk(stop, _is_private(area))
		if not _guide.walk.is_empty():
			_target = _guide.walk[_guide.leg]
			_art.mark_trail(_target)
		_refresh_guide()
		if not progress.is_empty():
			_hud.toast(progress)
		elif first:
			_hud.toast("Journal stamped · %s" % Guide.STOPS[stop].title)
	if duet:
		_hud.toast("Chime duet · Two players made a harmony!")


func _forget_chime(area: String, id: int) -> void:
	if _chimes.get(area, {}).get("id", 0) == id:
		_chimes.erase(area)


func _change_seat(peer_id: int) -> void:
	if not _players.has(peer_id):
		return
	var actor: Dictionary = _players[peer_id]
	if actor.seat >= 0:
		actor.pos = (actor.pos as Vector2).move_toward(Art.GATHERING, -0.52)
		actor.seat = -1
		return
	var at: Vector2 = actor.pos
	var area := String(actor.area)
	var benches := _benches(area)
	var available := -1
	var shortest_distance := 2.4
	for index in range(benches.size()):
		if _players.values().any(func(player: Dictionary) -> bool: return player.area == area and player.seat == index):
			continue
		var distance := at.distance_to(benches[index])
		if distance < shortest_distance:
			available = index
			shortest_distance = distance
	if available >= 0:
		var point: Vector2 = benches[available]
		actor.pos = point
		actor.seat = available
		actor.facing = atan2(point.x - Art.GATHERING.x, point.y - Art.GATHERING.y)


func _benches(area: String) -> Array:
	return Art.ISLAND_SEATS if _is_private(area) else Art.ISLAND_SEATS + Art.PARK_BENCHES


func _try_island_action(action: String, value: String) -> void:
	if _mode == Mode.CLIENT:
		_request_island_action.rpc_id(1, action, value)


@rpc("any_peer", "call_remote", "reliable")
func _request_island_action(action: String, value: String) -> void:
	var id := multiplayer.get_remote_sender_id()
	if _mode != Mode.SERVER or not _players.has(id) or value.length() > 12:
		return
	var actor: Dictionary = _players[id]
	var now := Time.get_ticks_msec()
	if now - int(actor.last_island_action) < 1000:
		return
	actor.last_island_action = now
	var area := String(actor.area)
	if _is_match(area):
		return
	if action == "leave" and _is_private(area):
		_return_player_to_park(id)
		_island_response.rpc_id(id, "Back in the forest park.")
		return
	if _is_private(area):
		_island_response.rpc_id(id, "Return to the park before joining another island.")
		return
	match action:
		"create":
			if not value.is_valid_int() or int(value) < 2 or int(value) > 5:
				_island_response.rpc_id(id, "Choose space for two to five players.")
				return
			if _islands.size() >= MAX_ISLANDS:
				_island_response.rpc_id(id, "All island spaces are in use.")
				return
			var code := _random.generate_random_bytes(4).hex_encode().to_upper()
			while _islands.has(code):
				code = _random.generate_random_bytes(4).hex_encode().to_upper()
			_islands[code] = int(value)
			_join_invite_island(id, code, "Island created. Copy your code to invite friends.")
		"join":
			var code := value.strip_edges().to_upper()
			if not _islands.has(code):
				_island_response.rpc_id(id, "Island code unavailable.")
				return
			if _population(code) >= _islands[code]:
				_island_response.rpc_id(id, "That island is full.")
				return
			_join_invite_island(id, code, "You joined the island.")


func _join_invite_island(id: int, code: String, message: String) -> void:
	_matchmaking.remove(id)
	_send_match_status(id, "idle", 0, "")
	_move_to_area(id, code)
	_island_response.rpc_id(id, message)


func _move_to_area(peer_id: int, destination: String, start := START) -> void:
	var actor: Dictionary = _players[peer_id]
	var previous := String(actor.area)
	_forget_chime(previous, peer_id)
	var replacement := _spawn(actor.alias, start)
	replacement.area = destination
	replacement.last_park = actor.last_park
	replacement.last_island_action = actor.last_island_action
	replacement.last_match_queue = actor.last_match_queue
	_players[peer_id] = replacement
	_remove_empty_island(previous)


func _return_player_to_park(id: int) -> void:
	var park := _available_park(_players[id].last_park)
	_move_to_area(id, park, _park_spawn(_population(park)))
	_players[id].last_park = park


func _remove_empty_island(area: String) -> void:
	if _islands.has(area) and _population(area) == 0:
		_islands.erase(area)


func _try_match_action(action: String, offer_id: int) -> void:
	if _mode == Mode.CLIENT:
		_request_match_action.rpc_id(1, action, offer_id)


@rpc("any_peer", "call_remote", "reliable")
func _request_match_action(action: String, offer_id: int) -> void:
	var id := multiplayer.get_remote_sender_id()
	if _mode != Mode.SERVER or not _players.has(id):
		return
	var actor := _players[id]
	var area := String(actor.area)
	if _is_private(area):
		if action == "leave" and _is_match(area):
			_end_match(area)
		return
	var now := Time.get_ticks_msec()
	match action:
		"queue":
			if now - int(actor.last_match_queue) >= 1000:
				actor.last_match_queue = now
				_matchmaking.enqueue(id, now)
		"cancel":
			_matchmaking.cancel(id, offer_id)
		"accept":
			_matchmaking.accept(id, offer_id, now)


func _start_match(first: int, second: int) -> void:
	var area := "match:%d" % _next_match
	_next_match += 1
	_move_to_area(first, area, START + Vector2(-0.75, 0))
	_move_to_area(second, area, START + Vector2(0.75, 0))
	for id in [first, second]:
		_send_match_status(id, "idle", 0, "You both accepted. Welcome to your private island.")


func _end_match(area: String) -> void:
	for id: int in _players:
		if _players[id].area == area:
			_return_player_to_park(id)
			_send_match_status(id, "idle", 0, "Match ended. You’re back in the park.")


func _send_match_status(id: int, state: String, offer_id: int, message: String) -> void:
	if _players.has(id) and _peer_connected(id):
		_match_response.rpc_id(id, state, offer_id, message)


@rpc("authority", "call_remote", "reliable")
func _match_response(state: String, offer_id: int, message: String) -> void:
	if _mode == Mode.CLIENT:
		_hud.show_match_status(state, offer_id, message)


@rpc("authority", "call_remote", "reliable")
func _island_response(message: String) -> void:
	if _mode == Mode.CLIENT:
		_hud.show_island_message(message)


func _send_world_state() -> void:
	var groups: Dictionary[String, Array] = {}
	var now := Time.get_ticks_msec()
	for id: int in _players:
		var actor: Dictionary = _players[id]
		var area := String(actor.area)
		if not groups.has(area):
			groups[area] = []
		groups[area].append([id, actor.alias, actor.pos, actor.facing, actor.seat, int(actor.wave_until) > now])
	for id: int in _players:
		if _peer_connected(id, true):
			var area := String(_players[id].area)
			var capacity := PARK_CAPACITY
			if _is_private(area):
				capacity = 2 if _is_match(area) else _islands[area]
			_receive_world_state.rpc_id(id, area, capacity, groups[area])


func _peer_connected(id: int, snapshot := false) -> bool:
	if _peer is WebSocketMultiplayerPeer:
		var socket := (_peer as WebSocketMultiplayerPeer).get_peer(id)
		return (
			socket != null and socket.get_ready_state() == WebSocketPeer.STATE_OPEN
			and (not snapshot or socket.get_current_outbound_buffered_amount() < 16384)
		)
	var packet_peer := (_peer as ENetMultiplayerPeer).get_peer(id)
	return packet_peer != null and packet_peer.get_state() == ENetPacketPeer.STATE_CONNECTED


@rpc("any_peer", "call_remote", "reliable")
func _set_alias(alias: String) -> void:
	var sender := multiplayer.get_remote_sender_id()
	if _mode == Mode.SERVER and _admission == null and _players.has(sender):
		_players[sender].alias = _clean_alias(alias)


@rpc("any_peer", "call_remote", "unreliable_ordered")
func _set_input(input: Vector2) -> void:
	var sender := multiplayer.get_remote_sender_id()
	if _mode == Mode.SERVER and _players.has(sender) and input.is_finite():
		_players[sender].input = input.limit_length()
		_players[sender].last_input = Time.get_ticks_msec()


@rpc("any_peer", "call_remote", "reliable")
func _request_seat() -> void:
	if _mode == Mode.SERVER:
		_change_seat(multiplayer.get_remote_sender_id())


@rpc("any_peer", "call_remote", "reliable")
func _request_wave() -> void:
	var sender := multiplayer.get_remote_sender_id()
	if _mode == Mode.SERVER and _players.has(sender):
		var now := Time.get_ticks_msec()
		if int(_players[sender].wave_until) <= now:
			_players[sender].wave_until = now + 1050


@rpc("authority", "call_remote", "unreliable_ordered")
func _receive_world_state(area: String, capacity: int, state: Array) -> void:
	if _mode != Mode.CLIENT:
		return
	_show_area(area)
	_players.clear()
	for entry: Array in state:
		_players[int(entry[0])] = {
			"alias": entry[1], "pos": entry[2], "facing": entry[3], "seat": entry[4], "waving": entry[5],
		}
	var total := _players.size()
	var in_match := _is_match(area)
	var in_island := _is_private(area) and not in_match
	_hud.update_room(in_island, in_match)
	_hud.update_island(area if in_island else "", total, capacity)
	var room_name := "Forest park %s" % area.trim_prefix("park:")
	if _is_private(area):
		room_name = "Private match" if in_match else "Private island"
	_hud.set_status("%s · %d/%d" % [room_name, total, capacity])


func _clean_alias(alias: String) -> String:
	var cleaned := alias.left(28).replace("\n", " ").replace("\r", " ").replace("\t", " ").strip_edges()
	return "Wanderer" if cleaned.is_empty() else cleaned


func _open_whisper() -> void:
	if _bridge != null:
		_close_connection(Mode.LEAVING)
		_bridge.call("openWhisper")


func _leave_game() -> void:
	_close_connection(Mode.LEAVING)
	if _bridge != null:
		_bridge.call("closeWorld")
	else:
		get_tree().quit()


func _unhandled_key_input(event: InputEvent) -> void:
	if _hud == null or not event.is_pressed() or event.is_echo():
		return
	if event is InputEventKey and not _hud.is_address_open():
		match event.physical_keycode:
			KEY_E: _try_seat()
			KEY_H: _try_wave()
			KEY_F: _try_trail(false)
			KEY_R: _camera.reset_view()


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT and _hud != null:
		_hud.reset_movement()
