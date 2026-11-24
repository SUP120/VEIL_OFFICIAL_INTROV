extends CanvasLayer

signal connect_requested(address: String)
signal disconnect_requested
signal sit_requested
signal wave_requested
signal whisper_requested
signal exit_requested
signal island_requested(action: String, value: String)
signal match_requested(action: String, offer_id: int)
signal orbit_requested(drag: Vector2)
signal zoom_requested(amount: float)
signal reset_view_requested
signal host_requested
signal trail_action_requested
signal trail_selected(id: String)
signal meetup_requested
signal walk_requested

const Guide = preload("res://trail_guide.gd")
const TrailMap = preload("res://trail_map.gd")
var _guide_menu: CenterContainer
var _map: TrailMap
var _places: VBoxContainer
var _journal: Label
var _tracking: Label
var _activity: Button
var _meet: Button
var _meet_suggestion: Button
var _toast: Label
var _toast_until := 0
var _suggestion := ""
var _suggestion_until := 0
var _guide_title: Label
var _walk: Button
var _host_button: Button
var _lan_connect: Button
var _copy_address: Button
var _wifi_address := ""
var light := false

const SECONDARY_TEXT := Color("#d6cec5")

var _status: Label
var _sit_button: Button
var _whisper_button: Button
var _network_button: Button
var _public_park: Button
var _joining := false
var _network_menu: CenterContainer
var _address: LineEdit
var _address_status: Label
var _island_button: Button
var _island_menu: CenterContainer
var _island_public: VBoxContainer
var _island_private: VBoxContainer
var _island_code: LineEdit
var _island_count: Label
var _island_status: Label
var _match_button: Button
var _match_menu: CenterContainer
var _match_status: Label
var _match_search: Button
var _match_accept: Button
var _match_cancel: Button
var _match_leave: Button
var _match_state := "idle"
var _match_offer := 0
var _in_match := false
var _in_island := false
var _preferred_host := ""
var _move_pad: Control
var _knob: Control
var _touch_index := -1
var _touch_vector := Vector2.ZERO
var _look_touches: Dictionary[int, Vector2] = {}
var _mouse_orbiting := false
var _connected := false


func _ready() -> void:
	var screen := Control.new()
	if light:
		var theme := Theme.new()
		for type in ["Label", "Button", "OptionButton", "LineEdit"]:
			theme.set_color("font_color", type, Color("#292e28"))
		for state in ["font_hover_color", "font_pressed_color", "font_focus_color", "font_hover_pressed_color"]:
			theme.set_color(state, "Button", Color("#26392d"))
		theme.set_color("font_placeholder_color", "LineEdit", Color("#555e54"))
		theme.set_color("caret_color", "LineEdit", Color("#303b32"))
		for type in ["LineEdit", "OptionButton"]:
			theme.set_stylebox("normal", type, _panel_style(Color("#e3e7dd")))
		for state in ["hover", "pressed"]:
			theme.set_stylebox(state, "OptionButton", _panel_style(Color("#d5ded0")))
			theme.set_color("font_" + state + "_color", "OptionButton", Color("#26392d"))
		screen.theme = theme
	screen.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(screen)
	screen.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

	var brand_panel := PanelContainer.new()
	brand_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	brand_panel.add_theme_stylebox_override("panel", _panel_style())
	screen.add_child(brand_panel)
	brand_panel.position = Vector2(22, 18)
	var brand := VBoxContainer.new()
	brand.mouse_filter = Control.MOUSE_FILTER_IGNORE
	brand.add_theme_constant_override("separation", 3)
	brand_panel.add_child(brand)
	var title := Label.new()
	title.text = "VEIL World"
	title.add_theme_font_size_override("font_size", 24)
	brand.add_child(title)
	_status = Label.new()
	_status.text = "Solo · Forest park"
	_status.modulate = SECONDARY_TEXT
	_status.add_theme_font_size_override("font_size", 15)
	brand.add_child(_status)
	_tracking = Label.new()
	_tracking.add_theme_font_size_override("font_size", 15)
	brand.add_child(_tracking)

	var menu := HBoxContainer.new()
	menu.add_theme_constant_override("separation", 8)
	screen.add_child(menu)
	menu.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT)
	menu.offset_left = -620
	menu.offset_right = -22
	menu.offset_top = 18
	_button(menu, "Trail map", func() -> void: _guide_menu.show())
	_network_button = _button(menu, "Play together", func() -> void:
		if _joining:
			disconnect_requested.emit()
		else:
			_network_menu.show()
	)
	_network_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_island_button = _button(menu, "Islands", func() -> void: _island_menu.show())
	_island_button.visible = false
	_match_button = _button(menu, "Match", func() -> void: _match_menu.show())
	_match_button.visible = false
	_button(menu, "Exit world", func() -> void: exit_requested.emit()).size_flags_horizontal = Control.SIZE_EXPAND_FILL

	var controls := HBoxContainer.new()
	controls.alignment = BoxContainer.ALIGNMENT_END
	controls.add_theme_constant_override("separation", 8)
	screen.add_child(controls)
	controls.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT)
	controls.offset_left = -665
	controls.offset_right = -22
	controls.offset_top = -77
	controls.offset_bottom = -22
	_button(controls, "Reset view", func() -> void: reset_view_requested.emit())
	_activity = _button(controls, "", func() -> void: trail_action_requested.emit())
	_activity.hide()
	_whisper_button = _button(controls, "Whisper", func() -> void: whisper_requested.emit())
	_sit_button = _button(controls, "Sit", func() -> void: sit_requested.emit())
	_sit_button.custom_minimum_size.x = 110
	_button(controls, "Wave", func() -> void: wave_requested.emit())
	controls.mouse_filter = Control.MOUSE_FILTER_IGNORE

	var uses_touch := OS.has_feature("android") or DisplayServer.is_touchscreen_available()
	if uses_touch:
		_build_move_pad(screen)
	var hint := Label.new()
	hint.text = "Drag to look  ·  Pinch to zoom" if uses_touch else "WASD to move  ·  Right-drag to look"
	hint.add_theme_color_override("font_color", Color("#ede6dc"))
	hint.add_theme_color_override("font_outline_color", Color("#26252b"))
	hint.add_theme_constant_override("outline_size", 5)
	screen.add_child(hint)
	hint.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT)
	hint.offset_left = 174 if uses_touch else 22
	hint.offset_top = -53

	_build_network_menu(screen)
	_build_island_menu(screen)
	_build_match_menu(screen)
	_build_guide(screen)
	update_gathering(false, false, false)


func _build_move_pad(screen: Control) -> void:
	var pad := Panel.new()
	_move_pad = pad
	pad.mouse_filter = Control.MOUSE_FILTER_IGNORE
	pad.add_theme_stylebox_override("panel", _circle_style(Color("#c5b8b830"), 1))
	screen.add_child(pad)
	pad.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT)
	pad.offset_left = 22
	pad.offset_right = 162
	pad.offset_top = -162
	pad.offset_bottom = -22
	var knob := Panel.new()
	_knob = knob
	knob.mouse_filter = Control.MOUSE_FILTER_IGNORE
	knob.add_theme_stylebox_override("panel", _circle_style(Color("#d8cbcd95"), 1))
	pad.add_child(knob)
	knob.size = Vector2(51, 51)
	knob.position = (pad.size - knob.size) * 0.5


func _build_network_menu(screen: Control) -> void:
	_network_menu = _modal(screen)
	var panel := PanelContainer.new()
	panel.custom_minimum_size.x = 385
	panel.add_theme_stylebox_override("panel", _panel_style())
	_network_menu.add_child(panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 12)
	panel.add_child(column)
	var title := Label.new()
	title.text = "Play together"
	title.add_theme_font_size_override("font_size", 24)
	column.add_child(title)
	_public_park = _button(column, "Join public world", func() -> void: connect_requested.emit(_preferred_host))
	_public_park.hide()
	_address = LineEdit.new()
	_address.placeholder_text = "Host’s Wi-Fi address"
	_address.max_length = 45
	_address.custom_minimum_size.y = 48
	column.add_child(_address)
	_address.text_submitted.connect(func(value: String) -> void:
		connect_requested.emit(value.strip_edges())
	)
	_address_status = Label.new()
	_address_status.text = "On the same Wi-Fi, host a park or enter a friend’s address."
	_address_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_address_status.modulate = SECONDARY_TEXT
	column.add_child(_address_status)
	var actions := HBoxContainer.new()
	actions.add_theme_constant_override("separation", 8)
	column.add_child(actions)
	_button(actions, "Close", func() -> void: _network_menu.hide())
	_host_button = _button(actions, "Host on Wi-Fi", func() -> void: host_requested.emit())
	_copy_address = _button(actions, "Copy address", func() -> void:
		DisplayServer.clipboard_set(_wifi_address.get_slice(", ", 0))
		_address_status.text = "Address copied. Friends can paste it in Play together."
	)
	_lan_connect = _button(actions, "Join park", func() -> void:
		if _connected:
			disconnect_requested.emit()
			_network_menu.hide()
		else:
			connect_requested.emit(_address.text.strip_edges())
	)
	_network_menu.hide()


func _build_guide(screen: Control) -> void:
	_toast = Label.new()
	screen.add_child(_toast)
	_toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_toast.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP)
	_toast.offset_left = -350
	_toast.offset_right = 350
	_toast.offset_top = 116
	_toast.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_toast.add_theme_constant_override("outline_size", 6)
	_toast.add_theme_color_override("font_color", Color("#fcf7e7"))
	_toast.add_theme_color_override("font_outline_color", Color("#26372e"))
	_guide_menu = _modal(screen)
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", _panel_style())
	_guide_menu.add_child(panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 10)
	panel.add_child(column)
	_guide_title = Label.new()
	_guide_title.add_theme_font_size_override("font_size", 24)
	column.add_child(_guide_title)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 14)
	column.add_child(row)
	_map = TrailMap.new()
	_map.custom_minimum_size = Vector2(330, 295)
	_map.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(_map)
	_places = VBoxContainer.new()
	row.add_child(_places)
	_journal = Label.new()
	_journal.custom_minimum_size.x = 550
	_journal.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(_journal)
	_meet_suggestion = _button(column, "", func() -> void:
		if not _suggestion.is_empty():
			trail_selected.emit(_suggestion)
			_guide_menu.hide()
	)
	_meet_suggestion.hide()
	var actions := HBoxContainer.new()
	column.add_child(actions)
	_meet = _button(actions, "Invite room to meet here", func() -> void: _guide_menu.hide(); meetup_requested.emit())
	_walk = _button(actions, "Start trail walk", func() -> void: _guide_menu.hide(); walk_requested.emit())
	_button(actions, "Close map", func() -> void: _guide_menu.hide())
	_guide_menu.hide()


func load_trail(guide: Guide, island: bool, target: String) -> void:
	_map.island = island
	_map.target = target
	var stops := Guide.places(island)
	var count := 0
	for child in _places.get_children():
		_places.remove_child(child)
		child.queue_free()
	for id: String in stops:
		var visited := guide.stamps.has_section_key("visits", id)
		count += int(visited)
		var b := _button(_places, ("%s  " % ("✓" if visited else "○")) + Guide.STOPS[id].title,
			func() -> void: trail_selected.emit(id); _guide_menu.hide())
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	_guide_title.text = "%s   ·   %d/%d stamps" % ["Island journal" if island else "Forest journal", count, stops.size()]
	_journal.text = Guide.STOPS[target].hint if not target.is_empty() else "Pick a place to mark its trail. Use its activity to earn the stamp."
	if count == stops.size():
		_journal.text = "Journal complete. Revisit any stop or invite someone to explore it with you."
	var best := int(guide.stamps.get_value("walks", "shore" if island else "forest", 0))
	_walk.text = "End trail walk" if not guide.walk.is_empty() else "Start trail walk"
	if not guide.walk.is_empty():
		_journal.text = "Trail walk · Stop %d of %d · %s" % [guide.leg + 1, guide.walk.size(), Guide.STOPS[guide.walk[guide.leg]].title]
	if best > 0:
		_journal.text += "   ·   Best " + Guide.format_time(best)
	_map.queue_redraw()


func update_trail_position(at: Vector2, nearby: String, target: String, others: Array[Vector2]) -> void:
	_activity.visible = not nearby.is_empty()
	if _activity.visible:
		_activity.text = Guide.STOPS[nearby].action
	_meet.visible = _connected and _activity.visible
	_tracking.text = "Choose a walk in Trail map" if target.is_empty() else "%s  ·  %d m" % [Guide.STOPS[target].title, ceili(at.distance_to(Guide.STOPS[target].at))]
	if _guide_menu.visible:
		_map.player = at
		_map.peers = others
		_map.queue_redraw()
	var now := Time.get_ticks_msec()
	_toast.visible = now < _toast_until
	if now >= _suggestion_until:
		clear_meetup()


func toast(message: String) -> void:
	_toast.text = message
	_toast.visible = true
	_toast_until = Time.get_ticks_msec() + 4500


func suggest_meetup(id: String, alias: String) -> void:
	_suggestion = id
	_suggestion_until = Time.get_ticks_msec() + 60000
	_meet_suggestion.text = "Meet %s at %s" % [alias, Guide.STOPS[id].title]
	_meet_suggestion.show()
	toast("%s invited the room to %s · Open Trail map" % [alias, Guide.STOPS[id].title])


func clear_meetup() -> void:
	_suggestion = ""
	_meet_suggestion.hide()


func _build_island_menu(screen: Control) -> void:
	_island_menu = _modal(screen)
	var panel := PanelContainer.new()
	panel.custom_minimum_size.x = 420
	panel.add_theme_stylebox_override("panel", _panel_style())
	_island_menu.add_child(panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 10)
	panel.add_child(column)
	var title := Label.new()
	title.text = "Private islands"
	title.add_theme_font_size_override("font_size", 24)
	column.add_child(title)

	_island_public = VBoxContainer.new()
	_island_public.add_theme_constant_override("separation", 8)
	column.add_child(_island_public)
	var creation := HBoxContainer.new()
	creation.add_theme_constant_override("separation", 8)
	_island_public.add_child(creation)
	var capacity := OptionButton.new()
	for size in range(2, 6):
		capacity.add_item("%d players" % size, size)
	creation.add_child(capacity)
	_button(creation, "Create island", func() -> void:
		island_requested.emit("create", str(capacity.get_selected_id()))
	)

	var joining := HBoxContainer.new()
	joining.add_theme_constant_override("separation", 8)
	_island_public.add_child(joining)
	var invite := LineEdit.new()
	invite.placeholder_text = "Enter invite code"
	invite.max_length = 8
	invite.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	joining.add_child(invite)
	invite.text_submitted.connect(func(value: String) -> void:
		island_requested.emit("join", value.strip_edges())
	)
	_button(joining, "Join island", func() -> void:
		island_requested.emit("join", invite.text.strip_edges())
	)

	_island_private = VBoxContainer.new()
	_island_private.add_theme_constant_override("separation", 8)
	column.add_child(_island_private)
	_island_count = Label.new()
	_island_private.add_child(_island_count)
	_island_code = LineEdit.new()
	_island_code.editable = false
	_island_code.tooltip_text = "Share this invite code with your friends"
	_island_private.add_child(_island_code)
	var actions := HBoxContainer.new()
	actions.add_theme_constant_override("separation", 8)
	_island_private.add_child(actions)
	_button(actions, "Copy invite code", func() -> void:
		DisplayServer.clipboard_set(_island_code.text)
		show_island_message("Invite code copied.")
	)
	_button(actions, "Return to park", func() -> void: island_requested.emit("leave", ""))
	_island_private.hide()

	_island_status = Label.new()
	_island_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_island_status.modulate = SECONDARY_TEXT
	column.add_child(_island_status)
	_button(column, "Close", func() -> void: _island_menu.hide())
	_island_menu.hide()


func _modal(screen: Control) -> CenterContainer:
	var backdrop := ColorRect.new()
	backdrop.color = Color("#25232b99")
	backdrop.mouse_filter = Control.MOUSE_FILTER_IGNORE
	screen.add_child(backdrop)
	backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

	var center := CenterContainer.new()
	center.mouse_filter = Control.MOUSE_FILTER_STOP
	screen.add_child(center)
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	center.visibility_changed.connect(func() -> void:
		backdrop.visible = center.visible
		if center.visible:
			reset_movement()
	)
	return center


func _build_match_menu(screen: Control) -> void:
	_match_menu = _modal(screen)
	var panel := PanelContainer.new()
	panel.custom_minimum_size.x = 420
	panel.add_theme_stylebox_override("panel", _panel_style())
	_match_menu.add_child(panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 10)
	panel.add_child(column)
	var title := Label.new()
	title.text = "Private match"
	title.add_theme_font_size_override("font_size", 24)
	column.add_child(title)
	_match_status = Label.new()
	_match_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(_match_status)
	var actions := HBoxContainer.new()
	actions.add_theme_constant_override("separation", 8)
	column.add_child(actions)
	_match_search = _button(actions, "Find a match", func() -> void: match_requested.emit("queue", 0))
	_match_accept = _button(actions, "Accept", func() -> void: match_requested.emit("accept", _match_offer))
	_match_cancel = _button(actions, "Cancel search", func() -> void: match_requested.emit("cancel", _match_offer))
	_match_leave = _button(actions, "Return to park", func() -> void: match_requested.emit("leave", 0))
	_button(column, "Close", func() -> void: _match_menu.hide())
	show_match_status("idle", 0, "")
	_match_menu.hide()


func _input(event: InputEvent) -> void:
	if _mouse_orbiting:
		if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_RIGHT and not event.pressed:
			_release_mouse()
			get_viewport().set_input_as_handled()
		elif event is InputEventMouseMotion:
			orbit_requested.emit(event.relative)
			get_viewport().set_input_as_handled()
	if _move_pad == null or is_address_open():
		return
	if event is InputEventScreenTouch:
		if event.pressed and _touch_index < 0 and _move_pad.get_global_rect().grow(12.0).has_point(event.position):
			_touch_index = event.index
			_update_touch(event.position)
			get_viewport().set_input_as_handled()
		elif not event.pressed and event.index == _touch_index:
			_reset_move_pad()
			get_viewport().set_input_as_handled()
		elif not event.pressed and _look_touches.erase(event.index):
			get_viewport().set_input_as_handled()
	elif event is InputEventScreenDrag:
		if event.index == _touch_index:
			_update_touch(event.position)
		elif _look_touches.has(event.index):
			_drag_camera_touch(event)
		else:
			return
		get_viewport().set_input_as_handled()


func _unhandled_input(event: InputEvent) -> void:
	if is_address_open():
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_RIGHT:
		_mouse_orbiting = true
		Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_UP:
		zoom_requested.emit(-0.15)
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
		zoom_requested.emit(0.15)
	elif event is InputEventScreenTouch and event.pressed and _move_pad != null and _look_touches.size() < 2:
		_look_touches[event.index] = event.position
	else:
		return
	get_viewport().set_input_as_handled()


func _drag_camera_touch(event: InputEventScreenDrag) -> void:
	var previous := _look_touches[event.index]
	_look_touches[event.index] = event.position
	if _look_touches.size() == 1:
		orbit_requested.emit(event.relative)
		return
	for index: int in _look_touches:
		if index != event.index:
			var other := _look_touches[index]
			var before := maxf(previous.distance_to(other), 20.0)
			var after := maxf(event.position.distance_to(other), 20.0)
			zoom_requested.emit(log(before / after))


func _update_touch(point: Vector2) -> void:
	var center := _move_pad.get_global_rect().get_center()
	_touch_vector = ((point - center) / 54.0).limit_length()
	_knob.position = (_move_pad.size - _knob.size) * 0.5 + _touch_vector * 40.0


func reset_movement() -> void:
	_reset_move_pad()
	_look_touches.clear()
	_release_mouse()


func _release_mouse() -> void:
	if _mouse_orbiting:
		_mouse_orbiting = false
		Input.mouse_mode = Input.MOUSE_MODE_VISIBLE


func _reset_move_pad() -> void:
	_touch_index = -1
	_touch_vector = Vector2.ZERO
	if _knob != null:
		_knob.position = (_move_pad.size - _knob.size) * 0.5


func movement() -> Vector2:
	if is_address_open():
		return Vector2.ZERO
	var keyboard := Vector2(
		float(Input.is_physical_key_pressed(KEY_D) or Input.is_physical_key_pressed(KEY_RIGHT))
			- float(Input.is_physical_key_pressed(KEY_A) or Input.is_physical_key_pressed(KEY_LEFT)),
		float(Input.is_physical_key_pressed(KEY_S) or Input.is_physical_key_pressed(KEY_DOWN))
			- float(Input.is_physical_key_pressed(KEY_W) or Input.is_physical_key_pressed(KEY_UP))
	)
	return (keyboard + _touch_vector).limit_length()


func is_address_open() -> bool:
	return _network_menu.visible or _island_menu.visible or _match_menu.visible or _guide_menu.visible


func set_status(text: String) -> void:
	_status.text = text


func update_connection(connected: bool) -> void:
	_connected = connected
	_joining = false
	_network_button.text = "Room" if connected else "Play together"
	_public_park.visible = not connected and not _preferred_host.is_empty()
	_address.visible = not connected
	_host_button.visible = not connected
	_copy_address.visible = connected and not _wifi_address.is_empty()
	_lan_connect.text = "Leave park" if connected else "Join park"
	if connected:
		_network_menu.hide()
		_address_status.text = "Your Wi-Fi address: %s\nFriends on this Wi-Fi can enter it to join. Keep the game open." % _wifi_address if not _wifi_address.is_empty() else "You are connected to the shared park."
	else:
		_wifi_address = ""
		_island_menu.hide()
		_match_menu.hide()
		show_match_status("idle", 0, "")
	update_room(false, false)


func update_room(in_island: bool, in_match: bool) -> void:
	if in_match != _in_match or in_island != _in_island:
		_match_menu.hide()
	if in_match:
		_island_menu.hide()
	_in_match = in_match
	_in_island = in_island
	_island_button.visible = _connected and not in_match
	_match_button.visible = _connected and not in_island
	_refresh_match_controls()


func show_match_status(state: String, offer_id: int, message: String) -> void:
	_match_state = state
	_match_offer = offer_id
	_match_status.text = message if not message.is_empty() else "Find one player from any park. You’ll meet on an island if you both accept."
	_refresh_match_controls()


func _refresh_match_controls() -> void:
	match _match_state:
		"offer": _match_button.text = "Match ready"
		"queued": _match_button.text = "Searching…"
		_: _match_button.text = "Match"
	_match_search.visible = not _in_match and _match_state == "idle"
	_match_accept.visible = not _in_match and _match_state == "offer"
	_match_cancel.visible = not _in_match and _match_state != "idle"
	_match_cancel.text = "Cancel search" if _match_state == "queued" else "Decline"
	_match_leave.visible = _in_match


func set_preferred_host(address: String) -> void:
	_preferred_host = address
	_public_park.text = "Join public world" if address.begins_with("wss://") else "Join configured park"
	_public_park.visible = not _connected


func set_joining(joining: bool) -> void:
	_joining = joining
	_network_menu.hide()
	_network_button.text = "Cancel join" if joining else "Play together"


func set_host_address(address: String) -> void:
	_wifi_address = address


func update_island(code: String, count: int, capacity: int) -> void:
	_island_public.visible = code.is_empty()
	_island_private.visible = not code.is_empty()
	if not code.is_empty():
		if _island_code.text != code:
			_island_code.text = code
		_island_count.text = "%d of %d spots filled" % [count, capacity]


func show_island_message(message: String) -> void:
	_island_status.text = message


func show_connection_message(message: String) -> void:
	_address_status.text = message


func update_gathering(near_bench: bool, seated: bool, has_whisper: bool) -> void:
	_sit_button.visible = near_bench or seated
	_sit_button.text = "Stand" if seated else "Sit"
	_whisper_button.visible = has_whisper


func _button(parent: Control, title: String, action: Callable) -> Button:
	var button := Button.new()
	button.text = title
	button.custom_minimum_size = Vector2(95, 48)
	button.add_theme_font_size_override("font_size", 17)
	button.add_theme_stylebox_override("normal", _panel_style(Color("#e1e7dc") if light else Color("#414743")))
	button.add_theme_stylebox_override("hover", _panel_style(Color("#d6dfcf") if light else Color("#505951")))
	button.add_theme_stylebox_override("pressed", _panel_style(Color("#c4d2c0") if light else Color("#5d665e")))
	button.pressed.connect(action)
	parent.add_child(button)
	return button


func _panel_style(fill := Color("#292830ed")) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = Color("#f5f2e9f5") if light and fill == Color("#292830ed") else fill
	style.border_color = Color("#a5afa1") if light else Color("#686568")
	style.set_border_width_all(1)
	style.set_corner_radius_all(13)
	style.content_margin_left = 14
	style.content_margin_right = 14
	style.content_margin_top = 10
	style.content_margin_bottom = 10
	return style


func _circle_style(fill: Color, stroke: int) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = Color("#ded3d09a")
	style.set_border_width_all(stroke)
	style.set_corner_radius_all(80)
	return style
