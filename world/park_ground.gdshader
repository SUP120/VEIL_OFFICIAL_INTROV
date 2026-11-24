shader_type spatial;
render_mode cull_disabled;

uniform vec4 base_color : source_color = vec4(0.53, 0.66, 0.37, 1.0);
uniform vec4 detail_color : source_color = vec4(0.60, 0.70, 0.43, 1.0);
varying vec2 ground_position;

float grain(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise_at(vec2 p) {
	vec2 cell = floor(p);
	vec2 weight = smoothstep(vec2(0.0), vec2(1.0), fract(p));
	return mix(mix(grain(cell), grain(cell + vec2(1, 0)), weight.x),
		mix(grain(cell + vec2(0, 1)), grain(cell + vec2(1)), weight.x), weight.y);
}

void vertex() {
	ground_position = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xz;
}

void fragment() {
	float patches = noise_at(ground_position * 0.60) * 0.68;
	float flecks = noise_at(ground_position * 15.0) * 0.32;
	ALBEDO = mix(base_color.rgb, detail_color.rgb, patches + flecks);
	ROUGHNESS = 0.96;
}
