shader_type spatial;
render_mode cull_disabled;

uniform vec4 water_color : source_color = vec4(0.45, 0.56, 0.59, 1.0);
uniform bool fountain = false;
varying vec2 water_position;

void vertex() {
	water_position = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xz;
}

void fragment() {
	float ripple;
	if (fountain) {
		float radius = length(water_position - vec2(0.0, -3.0));
		ripple = pow(max(sin(radius * 38.0 - TIME * 4.0), 0.0), 7.0);
	} else {
		float wave = sin(water_position.x * 3.2 + sin(water_position.y * 2.1) + TIME * 0.8);
		ripple = pow(max(sin(water_position.y * 3.8 + wave * 1.4 - TIME), 0.0), 9.0);
	}
	ALBEDO = mix(water_color.rgb, vec3(0.72, 0.76, 0.73), ripple * 0.25);
	ROUGHNESS = 0.37;
}
