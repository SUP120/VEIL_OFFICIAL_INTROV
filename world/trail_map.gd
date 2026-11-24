extends Control

const Guide = preload("res://trail_guide.gd")
var island := false
var player := Vector2.ZERO
var target := ""
var peers: Array[Vector2] = []


func point(at: Vector2) -> Vector2:
	var scale_factor := minf(size.x, size.y) / (24.0 if island else 80.0)
	return size * 0.5 + at * scale_factor


func _draw() -> void:
	var paper := Color("#ddd8c6")
	var ink := Color("#354d43")
	draw_rect(Rect2(Vector2.ZERO, size), paper)
	draw_circle(point(Vector2.ZERO), point(Vector2(10 if island else 37, 0)).x - size.x * 0.5, Color("#b7c1a8"), true, -1, true)
	var routes := [[Vector2(-4.8, 4.5), Vector2(0, 4), Vector2(0, -3), Vector2(6, -4)]] if island else [
		[Vector2(-25, 11), Vector2(-10, 3), Vector2(-9, -7), Vector2(0, -10), Vector2(9, -6), Vector2(10, 3), Vector2(23, 10)],
		[Vector2(-25, 11), Vector2(-14, 17), Vector2(-3, 19), Vector2(12, 17), Vector2(23, 10)],
		[Vector2(0, 9), Vector2(0, -3), Vector2(0, -15), Vector2(-3, -20), Vector2(0, -31)],
		[Vector2(-10, 3), Vector2(0, -1), Vector2(10, 3)],
	]
	for route: Array in routes:
		for i in range(1, route.size()):
			draw_line(point(route[i-1]), point(route[i]), Color("#f1e8d4"), 3, true)
	draw_string(ThemeDB.fallback_font, Vector2(10, 18), "N ↑", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, ink)
	draw_string(ThemeDB.fallback_font, Vector2(10, size.y-8), "● You", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#7e4435"))
	draw_string(ThemeDB.fallback_font, Vector2(59, size.y-8), "● Players", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#705b77"))
	for id: String in Guide.places(island):
		var here := point(Guide.STOPS[id].at)
		draw_circle(here, 9 if id == target else 5, ink, true, -1, true)
		if id == target:
			draw_circle(here, 4, paper, true, -1, true)
		var title: String = Guide.STOPS[id].title
		var font := ThemeDB.fallback_font
		var width := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, 13).x
		draw_string(font, Vector2(clampf(here.x - width / 2, 3, size.x-width-3), here.y + (22 if id != "chimes" else -10)), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 13, ink)
	for at: Vector2 in peers:
		draw_circle(point(at), 3, Color("#705b77"), true, -1, true)
	draw_circle(point(player), 6, Color.WHITE, true, -1, true)
	draw_circle(point(player), 4, Color("#7e4435"), true, -1, true)
