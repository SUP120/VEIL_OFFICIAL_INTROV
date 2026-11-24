extends Camera3D

const Art = preload("res://plaza_art.gd")
const DEFAULT_PITCH := 0.28
const DEFAULT_DISTANCE := 5.6

var _yaw := 0.0
var _pitch := DEFAULT_PITCH
var _target_yaw := 0.0
var _target_pitch := DEFAULT_PITCH
var _zoom := DEFAULT_DISTANCE
var _distance := DEFAULT_DISTANCE
var _follow_point := Vector3.ZERO
var _snap_follow := true


func _ready() -> void:
	fov = 61.0
	near = 0.08
	far = 220.0
	make_current()


func orbit(drag: Vector2) -> void:
	var sensitivity := 3.0 / maxf(get_viewport().get_visible_rect().size.y, 1.0)
	_target_yaw = wrapf(_target_yaw - drag.x * sensitivity, -PI, PI)
	_target_pitch = clampf(_target_pitch + drag.y * sensitivity, 0.10, 1.12)


func zoom(amount: float) -> void:
	_zoom = clampf(_zoom * exp(amount), 2.9, 10.5)


func reset_view() -> void:
	_target_yaw = 0.0
	_target_pitch = DEFAULT_PITCH
	_zoom = DEFAULT_DISTANCE


func snap_follow() -> void:
	_snap_follow = true


func world_movement(input: Vector2) -> Vector2:
	return input.rotated(-_yaw)


func follow(avatar_position: Vector3, art: Art, delta: float) -> void:
	var turn_weight := 1.0 - exp(-23.0 * delta)
	_yaw = lerp_angle(_yaw, _target_yaw, turn_weight)
	_pitch = lerpf(_pitch, _target_pitch, turn_weight)
	var focus := avatar_position + Vector3.UP * 1.24
	if _snap_follow:
		_follow_point = focus
	else:
		_follow_point = _follow_point.lerp(focus, 1.0 - exp(-16.0 * delta))
	var direction := Vector3(sin(_yaw) * cos(_pitch), sin(_pitch), cos(_yaw) * cos(_pitch))
	var arm_length := _zoom if _snap_follow else lerpf(_distance, _zoom, 1.0 - exp(-11.0 * delta))
	var clearance := art.camera_clearance(_follow_point, _follow_point + direction * maxf(_zoom, arm_length))
	_distance = minf(arm_length, clearance)
	_snap_follow = false
	position = _follow_point + direction * _distance
	look_at(_follow_point + Vector3.UP * 0.20, Vector3.UP)
