extends Node3D

const Water = preload("res://park_water.gdshader")
const Ground = preload("res://park_ground.gdshader")
const Guide = preload("res://trail_guide.gd")

const GATHERING := Vector2(0.0, -3.0)
const PARK_RADIUS := 39.0
const LAKE_CENTER := Vector2(28, 10)
const WATER_RADIUS := 5.2
const ISLAND_RADIUS := 10.8
const ISLAND_SEATS := [
	Vector2(-2.5, -3.0), Vector2(2.5, -3.0),
	Vector2(-1.65, -5.0), Vector2(1.65, -5.0),
	Vector2(-1.65, -1.1), Vector2(1.65, -1.1),
]
const PARK_BENCHES := [
	Vector2(-10, 3), Vector2(10, 3), Vector2(-9, -8), Vector2(9, -8),
	Vector2(-3, -14.5), Vector2(3, -14.5),
]

var private_island := false
var _materials: Dictionary = {}
var _camera_obstacles: Array[AABB] = []
var _path_markers: Array[Vector3] = []
var _flowers: Array[Transform3D] = []
var _flower_colors: Array[Color] = []
var _rounded_mesh: SphereMesh
var _grass_material: ShaderMaterial
var _trail_material: ShaderMaterial
var _droplets: MultiMesh
var _fountain_time := 0.0
var _sails: Node3D
var _sail_speed := 0.12
var _notes: Dictionary = {}
var _waypoint: Label3D


func _ready() -> void:
	_make_daylight()
	_grass_material = _ground_material("#6d775d", "#90977a")
	_trail_material = _ground_material("#a6927d", "#c8bba7")
	var radius := ISLAND_RADIUS if private_island else PARK_RADIUS
	if private_island:
		var sea := _box(self, Vector3(0, -0.50, 0), Vector3(360, 0.10, 360), "#73c4bf")
		sea.material_override = _water_material(Color("#719097"), false)
		sea.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		_cylinder(self, Vector3(0, -0.40, 0), radius + 1.05, 0.62, "#c1b29b", 64)
	else:
		var field := _box(self, Vector3(0, -0.37, 0), Vector3(215, 0.65, 215), "#84a473")
		field.material_override = _grass_material
	_make_horizon()
	var turf := _cylinder(self, Vector3(0, -0.21, 0), radius + 0.47, 0.42, "#90b66e", 64)
	turf.material_override = _grass_material
	var courtyard := _cylinder(self, Vector3(0, 0.009, -3), 5.05, 0.07, "#d8c6a1", 42)
	courtyard.material_override = _trail_material
	_path([Vector2(0, 1), Vector2(-0.28, 3), Vector2(0.20, 6.4), Vector2(-0.2, 9.2)], 2.25)
	_make_fountain()
	for point: Vector2 in ISLAND_SEATS:
		_bench(point)
	if private_island:
		_flower_bed(Vector3(-4.8, 0, 2.2), 7)
		_flower_bed(Vector3(4.5, 0, 2.7), 5)
		_make_picnic(Vector3(-4.8, 0, 4.5))
		_make_shell_cove()
		_sign("Private island", Vector3(-1.8, 0, 1.7))
	else:
		_make_park()
	_make_woods(radius)
	_make_ground_cover(radius)


func _make_daylight() -> void:
	var day := WorldEnvironment.new()
	var settings := Environment.new()
	var atmosphere := ProceduralSkyMaterial.new()
	atmosphere.sky_top_color = Color("#9aaab2")
	atmosphere.sky_horizon_color = Color("#dfded3")
	atmosphere.sky_curve = 0.24
	atmosphere.ground_horizon_color = Color("#dfded3")
	atmosphere.ground_bottom_color = Color("#909c8e")
	atmosphere.sun_angle_max = 21.0
	var sky := Sky.new()
	sky.sky_material = atmosphere
	settings.background_mode = Environment.BG_SKY
	settings.sky = sky
	settings.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	settings.ambient_light_color = Color("#dddcd5")
	settings.ambient_light_energy = 0.56
	day.environment = settings
	add_child(day)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-42, -33, 0)
	sun.light_color = Color("#f2e8d8")
	sun.light_energy = 0.54
	sun.shadow_enabled = true
	sun.directional_shadow_max_distance = 48.0
	add_child(sun)


func _ground_material(base: String, detail: String) -> ShaderMaterial:
	var material := ShaderMaterial.new()
	material.shader = Ground
	material.set_shader_parameter("base_color", Color(base))
	material.set_shader_parameter("detail_color", Color(detail))
	return material


func _make_horizon() -> void:
	var random := RandomNumberGenerator.new()
	random.seed = 17
	for index in range(16):
		var angle := TAU * index / 16.0
		var radial := Vector3(cos(angle), 0, sin(angle))
		var distance := random.randf_range(77.0, 103.0)
		var hill := _sphere(self, radial * distance + Vector3.DOWN * 2.6,
			Vector3(random.randf_range(26, 42), random.randf_range(8, 19), random.randf_range(24, 39)),
			["#9ebbab", "#90afa7", "#9dbab6"][index % 3])
		hill.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		if not private_island:
			var ridge := _sphere(self, radial * random.randf_range(56, 70) + Vector3.DOWN * 3.0,
				Vector3(random.randf_range(19, 30), random.randf_range(7, 11), 21), "#7b8b75")
			ridge.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		if index % 2 == 0:
			for puff in range(3):
				var cloud := _sphere(self, radial * 91 + Vector3(puff * 7.0, 25.0 + puff * 0.7, index * 0.3),
					Vector3(12.5, 3.0 + puff * 0.6, 7.0), "#f3f8ef")
				cloud.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF


func camera_clearance(origin: Vector3, destination: Vector3) -> float:
	var distance := origin.distance_to(destination)
	for obstacle: AABB in _camera_obstacles:
		if obstacle.has_point(origin):
			continue
		var hit = obstacle.intersects_segment(origin, destination)
		if hit is Vector3:
			distance = minf(distance, maxf(origin.distance_to(hit) - 0.22, 0.65))
	return distance


func _make_park() -> void:
	_path([Vector2(-3.1, 0), Vector2(-6.7, 2.4), Vector2(-10.0, 2.7), Vector2(-11.0, -4.2), Vector2(-9.1, -9.0)], 1.7)
	_path([Vector2(3.1, 0), Vector2(6.1, 2.1), Vector2(10.0, 2.7), Vector2(11.4, -3.8), Vector2(9.1, -9.0)], 1.7)
	_path([Vector2(-9.1, -9.0), Vector2(-6.2, -10.2), Vector2(0, -9.8), Vector2(6.1, -9.9), Vector2(9.1, -9.0)], 1.7)
	_path([Vector2(0, -7.5), Vector2(0, -10.2), Vector2(1.2, -13.1), Vector2(0, -15.2)], 1.75)
	_cylinder(self, Vector3(0, 0.018, -15), 3.65, 0.075, "#959a7d", 28)
	for point: Vector2 in PARK_BENCHES:
		_bench(point)
	_make_pavilion(Vector3(-9, 0, -7))
	_make_meadow(Vector3(9, 0, -6))
	_tree(Vector3(-2.2, 0, -16.2), 2.32, false)
	_tree(Vector3(2.7, 0, -16.0), 1.95, false)
	_flower_bed(Vector3(-1.5, 0, -15.8), 6)
	_flower_bed(Vector3(1.7, 0, -15.7), 5)
	_flower_bed(Vector3(-2.9, 0, 2.1), 8)
	_flower_bed(Vector3(2.9, 0, 2.4), 8)
	_rock(Vector3(-2.56, 0, 5.38), 0.48)
	_rock(Vector3(2.72, 0, 6.31), 0.63)
	_shrub(Vector3(-2.19, 0, 5.04))
	_shrub(Vector3(2.32, 0, 5.90))
	_flower_bed(Vector3(-1.86, 0, 4.66), 9, 0.74)
	_flower_bed(Vector3(1.85, 0, 5.47), 9, 0.68)
	_make_picnic(Vector3(7.1, 0, -3.3))
	for at: Vector3 in [Vector3(-4.6, 0, 1.6), Vector3(4.6, 0, 1.7), Vector3(-8.1, 0, -9.3), Vector3(7.7, 0, -8.7)]:
		_lantern(at)
	_sign("Whisper circle", Vector3(-1.85, 0, 1.45))
	_sign("Garden pavilion", Vector3(-9, 0, -3.9))
	_sign("Wildflower meadow", Vector3(9, 0, -2.7))
	_sign("Woodland clearing", Vector3(0, 0, -13.4))
	_path([Vector2(9, 3), Vector2(14, 6), Vector2(18, 10), Guide.STOPS.lake.at], 1.8)
	_path([Vector2(-10, 3), Vector2(-16, 5), Vector2(-20, 10), Guide.STOPS.mill.at], 1.8)
	_path([Vector2(0, -15), Vector2(-3, -20), Vector2(1, -25), Guide.STOPS.ridge.at], 1.45)
	_path([Vector2(-25, 11), Vector2(-16, 17), Vector2(-3, 19), Vector2(12, 17), Vector2(22, 10)], 1.35)
	_make_jetty()
	_make_orchard()
	_make_hide()
	for i in range(5):
		var x := -9.5 + i * 0.25
		_cylinder(self, Vector3(x, 2.14, -6.2), 0.012, 0.58, "#675f54", 5)
		_cylinder(self, Vector3(x, 1.67, -6.2), 0.065, 0.64 - i * 0.07, "#ae9d78", 8)


func _make_jetty() -> void:
	var water := _cylinder(self, Vector3(28, 0.03, 10), WATER_RADIUS, 0.05, "#79969a", 48)
	water.material_override = _water_material(Color("#729397"), false)
	for i in range(11):
		_box(self, Vector3(21.4 + i * 0.27, 0.11, 10), Vector3(0.25, 0.15, 1.7), "#b69a78" if i % 3 else "#a68b70")
	for i in range(12):
		var angle := TAU * i / 12.0
		if cos(angle) > -0.8:
			_rock(Vector3(28 + cos(angle)*5.3, 0, 10 + sin(angle)*5.3), 0.46)
	_lantern(Vector3(21, 0, 8.9))
	_sign("Stillwater jetty", Vector3(20.5, 0, 11.2))


func _make_orchard() -> void:
	for i in range(6):
		var at := Vector3(-21 - (i % 3)*3, 0, 6 if i < 3 else 15)
		_tree(at, 1.95, false)
		for j in range(4):
			var angle := TAU * j / 4.0
			_sphere(self, at + Vector3(cos(angle)*1.1, 2.06, sin(angle)*1.1), Vector3.ONE*0.24, "#a76351")
	var center := Vector3(-26.8, 0, 10.2)
	_cylinder(self, center + Vector3.UP*1.25, 1.05, 2.5, "#c7bba1", 8, "", 0.78)
	_cylinder(self, center + Vector3.UP*2.85, 1.16, 0.8, "#836e61", 8, "", 0.08)
	_sails = Node3D.new()
	_sails.position = center + Vector3(0, 2.15, 0.97)
	add_child(_sails)
	for i in range(4):
		var arm := Node3D.new()
		arm.rotation.z = PI * i / 2.0
		_sails.add_child(arm)
		_box(arm, Vector3(0, 0.82, 0), Vector3(0.07, 1.65, 0.06), "#776754")
		_box(arm, Vector3(0.13, 1.13, 0), Vector3(0.27, 0.95, 0.045), "#e0d6b9")
	_sign("Apple orchard", Vector3(-23, 0, 12.5))


func _make_hide() -> void:
	var at := Vector3(0, 0, -33)
	for side: float in [-1, 1]:
		_box(self, at + Vector3(side*1.5, 1, 0), Vector3(0.17, 2, 0.2), "#806e5c")
	for row in range(6):
		if row != 4:
			_box(self, at + Vector3(0, 0.2 + row*0.33, 0), Vector3(3.4, 0.31, 0.13), "#a28a6b")
	_box(self, at + Vector3.UP*2.15, Vector3(3.8, 0.13, 2.0), "#778574").rotation.x = -0.1
	_sign("Birdwatch clearing", Vector3(2.0, 0, -29.3))


func _make_shell_cove() -> void:
	for i in range(7):
		var at := Vector3(6.4 + sin(i * 2.3)*0.9, 0.10, -4.5 + cos(i*1.8)*0.75)
		var shell := _sphere(self, at, Vector3(0.44, 0.21, 0.35), "#dfcfb6" if i % 2 else "#c7ac9c")
		shell.rotation.y = i
	_sign("Shell cove", Vector3(5.7, 0, -5.6))


func mark_trail(id: String) -> void:
	if _waypoint == null:
		_waypoint = Label3D.new()
		_waypoint.billboard = BaseMaterial3D.BILLBOARD_ENABLED
		_waypoint.pixel_size = 0.012
		_waypoint.font_size = 40
		_waypoint.modulate = Color("#faf1da")
		_waypoint.outline_modulate = Color("#344439")
		add_child(_waypoint)
	_waypoint.visible = Guide.STOPS.has(id)
	if _waypoint.visible:
		var pos: Vector2 = Guide.STOPS[id].at
		_waypoint.position = Vector3(pos.x, 3.9, pos.y)
		_waypoint.text = Guide.STOPS[id].title + "  ◆"


func play_activity(id: String, duet: bool) -> void:
	var point: Vector2 = Guide.STOPS[id].at
	var at := Vector3(point.x, 0, point.y)
	match id:
		"chimes", "cove":
			for i in range(5 if duet else 3):
				_tone(at, [523, 659, 784, 880, 1046][i] if id == "chimes" else 330 + 55*i, i*0.16)
				_ripple(at + Vector3.UP*0.14, i*0.22)
		"lake":
			var stone := _sphere(self, at + Vector3.UP, Vector3(0.18, 0.085, 0.14), "#706d62")
			var flight := stone.create_tween()
			for i in range(3):
				var splash := at + Vector3(1.2 + i*1.35, 0.12, 0)
				flight.tween_property(stone, "position", splash + Vector3(-0.48, 0.65/(i+1), 0), 0.14)
				flight.tween_property(stone, "position", splash, 0.15)
				_ripple(splash, (i+1)*0.29)
			flight.tween_callback(stone.queue_free)
		"mill":
			_sail_speed = 3.5
		"ridge", "meadow":
			for i in range(6):
				var flier := Node3D.new()
				add_child(flier)
				flier.position = at + Vector3(sin(i*2.2), 0.4, cos(i*2.2))
				var bird := id == "ridge"
				for side: float in [-1, 1]:
					var wing := _sphere(flier, Vector3(side*0.10, 0.04, 0), Vector3(0.25, 0.07, 0.17), "#70695e" if bird else "#c49c78")
					wing.rotation.z = side*0.46
					var flap := wing.create_tween().set_loops(9)
					flap.tween_property(wing, "rotation:z", -side*0.58, 0.12)
					flap.tween_property(wing, "rotation:z", side*0.46, 0.12)
				var flight := flier.create_tween()
				flight.tween_property(flier, "position", flier.position + Vector3(sin(i)*3, 2.0+i*0.2, -2), 1.2)
				flight.tween_property(flier, "position", flier.position + Vector3(sin(i)*6, 5 if bird else 0.15, -4), 1.1)
				flight.tween_callback(flier.queue_free)
			if id == "ridge":
				_tone(at, 1175, 0)
				_tone(at, 988, 0.19)
		"picnic":
			for i in range(3):
				var cup := _cylinder(self, at + Vector3(-0.2+i*0.32, 0.15, 0.34), 0.10, 0.16, "#c8d1bf", 12)
				var steam := _sphere(cup, Vector3.UP*0.2, Vector3(0.06, 0.16, 0.06), "#e7e0d3")
				var rise := steam.create_tween().set_loops(4)
				rise.tween_property(steam, "position:y", 0.48, 0.55).from(0.2)
				var clear := cup.create_tween()
				clear.tween_interval(2.5)
				clear.tween_callback(cup.queue_free)


func _ripple(at: Vector3, delay: float) -> void:
	var ring := TorusMesh.new()
	ring.inner_radius = 0.30
	ring.outer_radius = 0.34
	ring.rings = 24
	ring.ring_segments = 5
	var mesh := _mesh(self, ring, at, "#d8ded0")
	mesh.scale = Vector3.ONE * 0.01
	var tween := mesh.create_tween()
	tween.tween_interval(delay)
	tween.tween_property(mesh, "scale", Vector3(3.2, 0.25, 3.2), 0.7)
	tween.tween_callback(mesh.queue_free)


func _tone(at: Vector3, hz: int, delay: float) -> void:
	if not _notes.has(hz):
		var sample := AudioStreamWAV.new()
		sample.mix_rate = 22050
		sample.format = AudioStreamWAV.FORMAT_16_BITS
		var pcm := PackedByteArray()
		pcm.resize(22050)
		for i in range(11025):
			var t := float(i)/22050.0
			var envelope := minf(t*55.0, 1.0) * exp(-8.0*t)
			pcm.encode_s16(i*2, int(12500*sin(TAU*hz*t)*envelope))
		sample.data = pcm
		_notes[hz] = sample
	var bell := AudioStreamPlayer3D.new()
	bell.stream = _notes[hz]
	bell.position = at + Vector3.UP
	bell.max_distance = 18
	add_child(bell)
	bell.finished.connect(bell.queue_free)
	var tween := bell.create_tween()
	tween.tween_interval(delay)
	tween.tween_callback(bell.play)


func _path(route: Array, width: float) -> void:
	var curve := Curve3D.new()
	curve.bake_interval = 0.45
	for index in range(route.size()):
		var point: Vector2 = route[index]
		var direction: Vector2 = (route[mini(index + 1, route.size() - 1)] - route[maxi(index - 1, 0)]) / 5.0
		var tangent := Vector3(direction.x, 0.0, direction.y)
		curve.add_point(Vector3(point.x, 0.043, point.y), -tangent, tangent)
	var points := curve.get_baked_points()
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	var left: Array[Vector3] = []
	var right: Array[Vector3] = []
	for index in range(points.size()):
		var point := points[index]
		var heading := (points[mini(index + 1, points.size() - 1)] - points[maxi(index - 1, 0)]).normalized()
		var half_width := width * 0.5 * (1.0 + sin(index * 0.61) * 0.043)
		var edge := Vector3(-heading.z, 0.0, heading.x) * half_width
		left.append(point + edge)
		right.append(point - edge)
		_path_markers.append(Vector3(point.x, half_width, point.z))
	for index in range(points.size() - 1):
		for vertex: Vector3 in [left[index], right[index], left[index + 1], right[index], right[index + 1], left[index + 1]]:
			surface.set_normal(Vector3.UP)
			surface.add_vertex(vertex)
	var trail := _mesh(self, surface.commit(), Vector3.ZERO, "#d5c09a")
	trail.material_override = _trail_material
	trail.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF


func _make_fountain() -> void:
	var center := Vector3(GATHERING.x, 0, GATHERING.y)
	_cylinder(self, center + Vector3.UP * 0.09, 1.28, 0.18, "#918f80", 36)
	_cylinder(self, center + Vector3.UP * 0.17, 1.23, 0.19, "#c5b79c", 36, "", 1.19)
	var rim := TorusMesh.new()
	rim.inner_radius = 1.08
	rim.outer_radius = 1.28
	rim.rings = 36
	rim.ring_segments = 12
	_mesh(self, rim, center + Vector3.UP * 0.33, "#e2d2b2")
	var pool := _cylinder(self, center + Vector3.UP * 0.28, 1.15, 0.02, "#829da0", 36)
	pool.material_override = _water_material(Color("#789397"), true)
	_cylinder(self, center + Vector3.UP * 0.49, 0.22, 0.46, "#bdac92", 18, "", 0.14)
	_cylinder(self, center + Vector3.UP * 0.72, 0.29, 0.12, "#e2d2b2", 20, "", 0.37)
	_cylinder(self, center + Vector3.UP * 0.79, 0.31, 0.045, "#869c9b", 20)
	_sphere(self, center + Vector3.UP * 0.85, Vector3(0.17, 0.13, 0.17), "#d7ccb0")
	var drop := SphereMesh.new()
	drop.radial_segments = 6
	drop.rings = 3
	drop.radius = 0.022
	drop.height = 0.085
	drop.material = _material("#c9d4d1")
	_droplets = MultiMesh.new()
	_droplets.transform_format = MultiMesh.TRANSFORM_3D
	_droplets.mesh = drop
	_droplets.instance_count = 28
	_droplets.custom_aabb = AABB(center + Vector3(-1.3, 0.24, -1.3), Vector3(2.6, 1.2, 2.6))
	var spray := MultiMeshInstance3D.new()
	spray.multimesh = _droplets
	spray.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(spray)
	_process(0.0)


func _process(delta: float) -> void:
	if _sails != null:
		_sails.rotation.z += delta * _sail_speed
		_sail_speed = move_toward(_sail_speed, 0.12, delta * 0.75)
	_fountain_time = fmod(_fountain_time + delta, 20.0)
	for index in range(_droplets.instance_count):
		var t := fposmod(_fountain_time * 1.35 + floorf(index / 4.0) * 0.143, 1.0)
		var angle := TAU * float(index % 4) / 4.0 + 0.4
		var point := Vector3(GATHERING.x + cos(angle) * t * 0.94,
			0.86 + 1.47 * t - 2.02 * t * t, GATHERING.y + sin(angle) * t * 0.94)
		_droplets.set_instance_transform(index, Transform3D(Basis.IDENTITY, point))


func _water_material(color: Color, fountain: bool) -> ShaderMaterial:
	var water := ShaderMaterial.new()
	water.shader = Water
	water.set_shader_parameter("water_color", color)
	water.set_shader_parameter("fountain", fountain)
	return water


func _bench(at: Vector2) -> void:
	var bench := Node3D.new()
	bench.position = Vector3(at.x, 0, at.y)
	bench.rotation.y = atan2(at.x - GATHERING.x, at.y - GATHERING.y)
	add_child(bench)
	for x: float in [-0.47, 0.47]:
		_box(bench, Vector3(x, 0.18, 0), Vector3(0.065, 0.32, 0.47), "#4d6053")
		_box(bench, Vector3(x, 0.51, 0.285), Vector3(0.075, 0.37, 0.07), "#4d6053")
		_box(bench, Vector3(x, 0.52, 0.09), Vector3(0.085, 0.05, 0.43), "#65766b")
	for z: float in [-0.2, 0, 0.2]:
		_box(bench, Vector3(0, 0.35, z), Vector3(1.26, 0.075, 0.176), "#ad7952")
	for y: float in [0.49, 0.64]:
		_box(bench, Vector3(0, y, 0.28), Vector3(1.22, 0.105, 0.10), "#ac7d57")


func _make_pavilion(center: Vector3) -> void:
	_box(self, center + Vector3.UP * 0.04, Vector3(4.7, 0.10, 4.0), "#bca585")
	for plank in range(9):
		_box(self, center + Vector3((plank - 4) * 0.49, 0.112, 0), Vector3(0.48, 0.055, 3.9),
			"#dbc49a" if plank % 3 == 0 else "#d2b78d")
	for x: float in [-1.8, 1.8]:
		for z: float in [-1.45, 1.45]:
			_box(self, center + Vector3(x, 1.29, z), Vector3(0.18, 2.55, 0.18), "#9a6e51")
			_box(self, center + Vector3(x, 0.14, z), Vector3(0.31, 0.24, 0.31), "#c3baa3")
	for z: float in [-1.52, 1.52]:
		_box(self, center + Vector3(0.0, 2.43, z), Vector3(4.35, 0.14, 0.18), "#8e644a")
		for side: float in [-1, 1]:
			var strut := _box(self, center + Vector3(side * 1.51, 2.12, z), Vector3(0.11, 0.84, 0.12), "#a97657")
			strut.rotation.z = -side * 0.72
	for side: float in [-1, 1]:
		for slat in range(7):
			var roof := _box(self, center + Vector3(side * 1.08, 2.86, (slat - 3) * 0.58),
				Vector3(2.40, 0.085, 0.59), "#96746a" if slat % 2 == 0 else "#a98a7b")
			roof.rotation.z = -side * 0.27
	_box(self, center + Vector3.UP * 3.17, Vector3(0.23, 0.11, 4.15), "#955746")
	_camera_obstacles.append(AABB(center + Vector3(-2.35, 2.47, -2.10), Vector3(4.70, 0.82, 4.20)))
	_flower_bed(center + Vector3(-2.80, 0, 1.62), 5)


func _make_meadow(center: Vector3) -> void:
	_cylinder(self, center + Vector3.UP * 0.009, 2.45, 0.035, "#909679", 30)
	for index in range(6):
		var angle := TAU * float(index) / 6.0
		_flower_bed(center + Vector3(cos(angle) * 1.60, 0, sin(angle) * 1.49), 5, 0.62)


func _make_picnic(at: Vector3) -> void:
	_box(self, at + Vector3.UP * 0.022, Vector3(2.0, 0.042, 1.55), "#f2dfbc")
	for row in range(5):
		for column in range(6):
			if (row + column) % 2 == 0:
				_box(self, at + Vector3((column - 2.5) * 0.32, 0.045, (row - 2.0) * 0.29),
					Vector3(0.32, 0.01, 0.29), "#b49085")
	var basket := _box(self, at + Vector3(-0.51, 0.19, -0.31), Vector3(0.46, 0.28, 0.34), "#a77954")
	basket.rotation.y = 0.20
	_box(basket, Vector3(0, 0.14, 0), Vector3(0.50, 0.06, 0.38), "#d5a568")
	var handle := TorusMesh.new()
	handle.inner_radius = 0.13
	handle.outer_radius = 0.16
	handle.rings = 12
	handle.ring_segments = 6
	_mesh(basket, handle, Vector3(0, 0.14, 0), "#bc9765").rotation.x = PI * 0.5


func _lantern(at: Vector3) -> void:
	_cylinder(self, at + Vector3.UP * 0.09, 0.21, 0.17, "#aaa48d", 9)
	_cylinder(self, at + Vector3.UP * 0.91, 0.065, 1.68, "#566659", 7)
	_box(self, at + Vector3.UP * 1.75, Vector3(0.23, 0.31, 0.23), "#f1dfaa")
	for x: float in [-0.125, 0.125]:
		for z: float in [-0.125, 0.125]:
			_box(self, at + Vector3(x, 1.75, z), Vector3(0.042, 0.34, 0.042), "#566659")
	_cylinder(self, at + Vector3.UP * 1.97, 0.224, 0.17, "#65796a", 4, "", 0.052).rotation.y = PI * 0.25


func _make_woods(radius: float) -> void:
	var random := RandomNumberGenerator.new()
	random.seed = 42
	var count := 25 if private_island else 61
	for index in range(count):
		var angle := TAU * float(index) / float(count) + random.randf_range(-0.055, 0.055)
		var distance := random.randf_range(radius * 0.66, radius * 0.95)
		var at := Vector3(cos(angle) * distance, 0, sin(angle) * distance)
		if not private_island and (_on_path(Vector2(at.x, at.z)) or Vector2(at.x, at.z).distance_to(LAKE_CENTER) < 7.0):
			continue
		if absf(at.x) < 1.8 and at.z > 4:
			continue
		_tree(at, random.randf_range(1.8, 3.2), index % 3 == 0)
		if index % 4 == 0:
			_rock(at + Vector3(1.2, 0, -0.4), random.randf_range(0.28, 0.60))
	if not private_island:
		for at: Vector3 in [Vector3(-6, 0, 5), Vector3(5.9, 0, 5.4), Vector3(-5.8, 0, -4.2), Vector3(6, 0, -3.7)]:
			_tree(at, 2.25, false)
			_rock(at + Vector3(-0.5, 0, 0.7), 0.4)


func _tree(at: Vector3, height: float, pine: bool) -> void:
	var trunk := _cylinder(self, at + Vector3.UP * height * 0.39, height * 0.084, height * 0.78,
		"#806548" if pine else "#9b7c5d", 7, "", height * 0.047)
	trunk.rotation.z = sin(at.x + at.z) * 0.065
	_camera_obstacles.append(AABB(at + Vector3(-0.31, 0, -0.31), Vector3(0.62, height * 0.87, 0.62)))
	if pine:
		_cylinder(self, at + Vector3.UP * height * 0.84, height * 0.46, height * 0.81, "#566d60", 8, "", 0.16)
		_cylinder(self, at + Vector3.UP * height * 1.19, height * 0.36, height * 0.72, "#687e6e", 8, "", 0.09)
		_cylinder(self, at + Vector3.UP * height * 1.47, height * 0.26, height * 0.66, "#839381", 8, "", 0.02)
	else:
		var crown := Color("#657861").lerp(Color("#828c71"), 0.5 + sin(at.x * 1.4 + at.z) * 0.5)
		var branch := _cylinder(self, at + Vector3(height * 0.15, height * 0.67, 0), 0.085,
			height * 0.64, "#9b7c5d", 6, "", 0.05)
		branch.rotation.z = -0.66
		_sphere(self, at + Vector3.UP * height * 1.10, Vector3(1.27, 0.89, 1.13) * height, crown.to_html(false))
		_sphere(self, at + Vector3(-0.31, height * 1.29, -0.28), Vector3(0.88, 0.76, 0.85) * height, crown.lightened(0.15).to_html(false))
		_sphere(self, at + Vector3(height * 0.43, height * 1.02, 0.19), Vector3(0.85, 0.65, 0.83) * height, crown.darkened(0.08).to_html(false))
	_camera_obstacles.append(AABB(at + Vector3(-0.37, 0.81, -0.37) * height, Vector3(0.74, 0.52, 0.74) * height))


func _rock(at: Vector3, scale: float) -> void:
	var stone := _sphere(self, at + Vector3.UP * scale * 0.24, Vector3(1.40, 0.71, 1.0) * scale, "#a5a995")
	stone.rotation.y = at.x * 0.6
	_sphere(self, at + Vector3(scale * 0.48, scale * 0.1, -scale * 0.4), Vector3(0.85, 0.46, 0.71) * scale, "#aeb09c")


func _shrub(at: Vector3) -> void:
	_sphere(self, at + Vector3(-0.25, 0.24, -0.13), Vector3(0.70, 0.52, 0.60), "#6d7c67")
	_sphere(self, at + Vector3(0.19, 0.31, -0.29), Vector3(0.78, 0.64, 0.64), "#869078")
	_sphere(self, at + Vector3(0.28, 0.16, 0.13), Vector3(0.65, 0.40, 0.53), "#74816b")


func _flower_bed(center: Vector3, count: int, radius := 0.80) -> void:
	var random := RandomNumberGenerator.new()
	random.seed = hash(center)
	var colors := [Color("#dfd6c6"), Color("#bd9991"), Color("#b4a2b5"), Color("#c9b697")]
	for index in range(count):
		var angle := TAU * index / count + random.randf_range(-0.32, 0.32)
		var reach := random.randf_range(0.2, radius)
		var at := center + Vector3(cos(angle) * reach, 0.01, sin(angle) * reach)
		var size := random.randf_range(0.84, 1.42)
		_flowers.append(Transform3D(Basis(Vector3.UP, angle).scaled(Vector3.ONE * size), at))
		_flower_colors.append(colors[index % colors.size()])


func _make_ground_cover(radius: float) -> void:
	var random := RandomNumberGenerator.new()
	random.seed = 81
	var grass: Array[Transform3D] = []
	var shades: Array[Color] = []
	var count := 1350 if private_island else 3600
	for index in range(count):
		var angle := random.randf() * TAU
		var reach := sqrt(random.randf()) * radius
		var point := Vector2(cos(angle), sin(angle)) * reach
		if point.distance_to(GATHERING) < 5.13 or _on_path(point):
			continue
		var size := random.randf_range(0.48, 1.25)
		grass.append(Transform3D(Basis(Vector3.UP, angle).scaled(Vector3.ONE * size), Vector3(point.x, 0.008, point.y)))
		shades.append(Color("#687659").lerp(Color("#a1a78b"), random.randf()))
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	for index in range(3):
		var blade := Basis(Vector3.UP, TAU * index / 3.0)
		_triangle(surface, blade * Vector3(-0.078, 0, -0.05), blade * Vector3(0.037, 0, 0),
			blade * Vector3(0.12, 0.27 + 0.04 * index, 0.045))
	_instances(surface.commit(), grass, shades, Color.WHITE)

	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	for side: Vector3 in [Vector3.RIGHT, Vector3.BACK]:
		_triangle(surface, -side * 0.020, side * 0.020, Vector3.UP * 0.34)
	_instances(surface.commit(), _flowers, [], Color("#71816a"))

	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	for index in range(5):
		var petal := Basis(Vector3.UP, TAU * index / 5.0)
		_triangle(surface, Vector3.UP * 0.315, petal * Vector3(-0.065, 0.36, 0.09),
			petal * Vector3(0.061, 0.36, 0.09))
	_instances(surface.commit(), _flowers, _flower_colors, Color.WHITE)

	var pebbles: Array[Transform3D] = []
	var stone_colors: Array[Color] = []
	for marker: Vector3 in _path_markers:
		for index in range(3):
			var angle := random.randf() * TAU
			var spread := random.randf_range(0.10, marker.y * 0.85)
			var at := Vector3(marker.x + cos(angle) * spread, 0.048, marker.z + sin(angle) * spread)
			var size := random.randf_range(0.018, 0.058)
			pebbles.append(Transform3D(Basis(Vector3.UP, angle).scaled(Vector3(size, 0.019, size * 0.78)), at))
			stone_colors.append(Color("#b6a487").lerp(Color("#e8dabc"), random.randf()))
	_instances(_rounded_mesh, pebbles, stone_colors, Color.WHITE)


func _on_path(point: Vector2) -> bool:
	for marker: Vector3 in _path_markers:
		if point.distance_squared_to(Vector2(marker.x, marker.z)) < marker.y * marker.y:
			return true
	return false


func _triangle(surface: SurfaceTool, a: Vector3, b: Vector3, c: Vector3) -> void:
	for vertex: Vector3 in [a, b, c]:
		surface.set_normal(Vector3.UP)
		surface.add_vertex(vertex)


func _instances(shape: Mesh, transforms: Array[Transform3D], colors: Array[Color], base: Color) -> void:
	var batch := MultiMesh.new()
	batch.transform_format = MultiMesh.TRANSFORM_3D
	batch.use_colors = not colors.is_empty()
	batch.mesh = shape
	batch.instance_count = transforms.size()
	for index in range(transforms.size()):
		batch.set_instance_transform(index, transforms[index])
		if batch.use_colors:
			batch.set_instance_color(index, colors[index])
	var material := StandardMaterial3D.new()
	material.vertex_color_use_as_albedo = batch.use_colors
	material.albedo_color = base
	material.cull_mode = BaseMaterial3D.CULL_DISABLED
	material.roughness = 1.0
	var patches := MultiMeshInstance3D.new()
	patches.multimesh = batch
	patches.material_override = material
	patches.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(patches)


func _sign(text: String, at: Vector3) -> void:
	var width := maxf(1.30, text.length() * 0.102)
	_box(self, at + Vector3.UP * 0.53, Vector3(0.075, 1.06, 0.075), "#8d7156")
	_box(self, at + Vector3.UP * 0.95, Vector3(width, 0.36, 0.10), "#685d48")
	var label := Label3D.new()
	label.text = text
	label.font_size = 35
	label.pixel_size = 0.005
	label.modulate = Color("#fff5d8")
	label.outline_size = 0
	label.position = at + Vector3(0, 0.95, 0.056)
	add_child(label)


func _box(parent: Node3D, pos: Vector3, size: Vector3, color: String, emission := "") -> MeshInstance3D:
	var shape := BoxMesh.new()
	shape.size = size
	return _mesh(parent, shape, pos, color, emission)


func _cylinder(parent: Node3D, pos: Vector3, radius: float, height: float, color: String,
		segments: int, emission := "", top := -1.0) -> MeshInstance3D:
	var shape := CylinderMesh.new()
	shape.top_radius = radius if top < 0 else top
	shape.bottom_radius = radius
	shape.height = height
	shape.radial_segments = segments
	return _mesh(parent, shape, pos, color, emission)


func _sphere(parent: Node3D, pos: Vector3, size: Vector3, color: String, emission := "") -> MeshInstance3D:
	if _rounded_mesh == null:
		_rounded_mesh = SphereMesh.new()
		_rounded_mesh.radial_segments = 12
		_rounded_mesh.rings = 6
	var instance := _mesh(parent, _rounded_mesh, pos, color, emission)
	instance.scale = size
	return instance


func _material(color: String, emission := "") -> StandardMaterial3D:
	var key := color + emission
	if not _materials.has(key):
		var material := StandardMaterial3D.new()
		material.albedo_color = Color(color)
		material.roughness = 0.9
		if not emission.is_empty():
			material.emission_enabled = true
			material.emission = Color(emission)
		_materials[key] = material
	return _materials[key]


func _mesh(parent: Node3D, shape: Mesh, pos: Vector3, color: String, emission := "") -> MeshInstance3D:
	var instance := MeshInstance3D.new()
	instance.mesh = shape
	instance.material_override = _material(color, emission)
	instance.position = pos
	parent.add_child(instance)
	return instance
