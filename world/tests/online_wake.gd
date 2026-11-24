extends SceneTree

const Plaza = preload("res://plaza.gd")
var failed := false


func _initialize() -> void:
	call_deferred("_run")


func _check(ok: bool, message: String) -> void:
	if ok:
		print("PASS ", message)
	else:
		push_error(message)
		failed = true


func _wait_for(check: Callable) -> bool:
	var deadline := Time.get_ticks_msec() + 5000
	while not check.call() and Time.get_ticks_msec() < deadline:
		await process_frame
	return check.call()


func _run() -> void:
	var plaza := Plaza.new()
	plaza.name = "Plaza"
	root.add_child(plaza)
	# An unused loopback port simulates the edge dropping the first upgrade.
	plaza._endpoint = "wss://127.0.0.1:47183/world"
	plaza._hud.set_preferred_host(plaza._endpoint)
	plaza._hud._public_park.pressed.emit()
	_check(await _wait_for(func(): return plaza._mode == Plaza.Mode.WAKING), "unavailable edge schedules a retry")
	var deadline := plaza._wake_deadline
	plaza._retry_at = 0
	plaza._process(0.0)
	_check(plaza._mode == Plaza.Mode.CONNECTING and plaza._wake_deadline == deadline, "second attempt retains its overall deadline")
	plaza._hud._network_button.pressed.emit()
	_check(plaza._mode == Plaza.Mode.SOLO and plaza._wake_deadline == 0, "cancel returns to solo and clears retry")
	plaza._hud._public_park.pressed.emit()
	plaza._wake_deadline = Time.get_ticks_msec()
	_check(await _wait_for(func(): return plaza._mode == Plaza.Mode.SOLO), "deadline restores the solo park")
	_check(plaza._hud._host_button.visible and plaza._hud._public_park.visible, "Wi-Fi and online play remain available")
	quit(1 if failed else 0)
