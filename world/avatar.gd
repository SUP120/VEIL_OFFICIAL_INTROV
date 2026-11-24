extends Node3D

const COATS := ["#aa8491", "#7f9698", "#b4a082", "#9090a8", "#879986", "#a995a7"]

var _outfit: Node3D
var _label: Label3D
var _legs: Array[MeshInstance3D] = []
var _wave_arm: MeshInstance3D
var _elapsed := 0.0


func configure(alias: String, player_id: int, local: bool) -> void:
	if _outfit != null:
		_label.text = alias
		return
	_outfit = Node3D.new()
	add_child(_outfit)
	var coat := Color(COATS[player_id % COATS.size()])
	_capsule(_outfit, Vector3(0, 0.71, 0), 0.34, 0.84, coat)
	_capsule(_outfit, Vector3(0, 1.30, 0), 0.30, 0.60, Color("#f5c5a7"))
	var cap := _capsule(_outfit, Vector3(0, 1.53, 0.016), 0.31, 0.62, coat.darkened(0.36))
	cap.scale.y = 0.39
	var brim := _capsule(_outfit, Vector3(0, 1.48, -0.221), 0.21, 0.42, coat.darkened(0.46))
	brim.scale = Vector3(1.08, 0.16, 0.94)
	_capsule(_outfit, Vector3(0, 1.05, 0.01), 0.28, 0.16, Color("#f6e2d8"))
	var backpack := _capsule(_outfit, Vector3(0, 0.71, 0.27), 0.28, 0.63, Color("#98714e"))
	backpack.scale = Vector3(0.86, 0.92, 0.57)
	var pocket := _capsule(_outfit, Vector3(0, 0.61, 0.402), 0.17, 0.34, Color("#ad9272"))
	pocket.scale = Vector3(1.08, 0.57, 0.28)
	var flap := _capsule(_outfit, Vector3(0, 0.86, 0.337), 0.20, 0.40, Color("#b9a185"))
	flap.scale = Vector3(1.12, 0.43, 0.42)
	for side: float in [-1, 1]:
		_capsule(_outfit, Vector3(side * 0.108, 1.30, -0.278), 0.033, 0.066, Color("#312536"))
		var arm := _capsule(_outfit, Vector3(side * 0.35, 0.66, 0.0), 0.105, 0.39, coat.darkened(0.12))
		if side > 0:
			_wave_arm = arm
		_legs.append(_capsule(_outfit, Vector3(side * 0.14, 0.24, 0.0), 0.105, 0.45, Color("#3b354c")))
	_label = Label3D.new()
	_label.text = alias
	_label.font_size = 32
	_label.pixel_size = 0.011
	_label.outline_size = 5
	_label.outline_modulate = Color("#2d2138")
	_label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	_label.visible = not local
	add_child(_label)


func present(delta: float, target: Vector2, facing: float, seated: bool, waving: bool) -> void:
	var destination := Vector3(target.x, 0, target.y)
	var moving := not seated and position.distance_squared_to(destination) > 0.008
	position = destination if seated else position.lerp(destination, 1.0 - exp(-15.0 * delta))
	_outfit.rotation.y = lerp_angle(_outfit.rotation.y, facing, 1.0 - exp(-17.0 * delta))
	_elapsed += delta * 15.0 if moving else delta * 3.0
	var wave_angle := -0.92 + sin(Time.get_ticks_msec() * 0.02) * 0.32 if waving else 0.0
	var wave_height := 1.22 if waving else 0.66
	_wave_arm.rotation.z = lerpf(_wave_arm.rotation.z, wave_angle, 1.0 - exp(-15.0 * delta))
	_wave_arm.position.y = lerpf(_wave_arm.position.y, wave_height, 1.0 - exp(-15.0 * delta))
	_outfit.position.y = 0.15 if seated else (absf(sin(_elapsed)) * 0.06 if moving else 0.0)
	for index in range(_legs.size()):
		var leg := _legs[index]
		var swing := sin(_elapsed + PI * index) * 0.40 if moving else 0.0
		leg.rotation.x = lerpf(leg.rotation.x, PI * 0.43 if seated else swing, 1.0 - exp(-12.0 * delta))
		leg.position.z = -0.17 if seated else 0.0
	_label.position.y = 1.99 if seated else 1.91


func _capsule(parent: Node3D, at: Vector3, radius: float, height: float, color: Color) -> MeshInstance3D:
	var shape := CapsuleMesh.new()
	shape.radius = radius
	shape.height = maxf(height, radius * 2.0)
	shape.radial_segments = 12
	shape.rings = 5
	var material := StandardMaterial3D.new()
	material.albedo_color = color
	material.roughness = 0.9
	var instance := MeshInstance3D.new()
	instance.mesh = shape
	instance.material_override = material
	instance.position = at
	parent.add_child(instance)
	return instance
