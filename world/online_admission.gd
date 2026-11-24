extends Node

const FIREBASE_AUTH := "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key="
const FIRESTORE := "https://firestore.googleapis.com/v1/projects/"
const MAX_PENDING := 24

var _api: SceneMultiplayer
var _project: String
var _api_key: String
var _capacity: int
var _pending: Dictionary[int, HTTPRequest] = {}
var _identities: Dictionary[int, Dictionary] = {}


func configure(api: SceneMultiplayer, project: String, api_key: String, capacity: int) -> void:
	_api = api
	_project = project
	_api_key = api_key
	_capacity = capacity
	_api.auth_timeout = 15.0
	_api.auth_callback = _authenticate
	_api.peer_authenticating.connect(_limit_connections)
	_api.peer_authentication_failed.connect(remove)


func identity(peer_id: int) -> Dictionary:
	return _identities.get(peer_id, {})


func remove(peer_id: int) -> void:
	var request := _pending.get(peer_id) as HTTPRequest
	_pending.erase(peer_id)
	if request != null:
		request.cancel_request()
		request.queue_free()
	_identities.erase(peer_id)


func _limit_connections(peer_id: int) -> void:
	if _api.get_authenticating_peers().size() > MAX_PENDING or _identities.size() >= _capacity:
		_reject(peer_id)


func _authenticate(peer_id: int, data: PackedByteArray) -> void:
	if _pending.has(peer_id):
		return
	if data.size() < 64 or data.size() > 4096:
		_reject(peer_id)
		return
	var token := data.get_string_from_utf8()
	var claims := _token_claims(token)
	var uid = claims.get("sub")
	if not uid is String or uid.is_empty() or uid.length() > 128 or claims.get("aud") != _project or claims.get("iss") != "https://securetoken.google.com/" + _project:
		_reject(peer_id)
		return
	var expires = claims.get("exp")
	if not expires is float or expires <= Time.get_unix_time_from_system():
		_reject(peer_id)
		return

	var request := HTTPRequest.new()
	request.timeout = 4.0
	request.body_size_limit = 128 * 1024
	request.max_redirects = 0
	add_child(request)
	_pending[peer_id] = request
	var account := await _json_request(request, FIREBASE_AUTH + _api_key.uri_encode(),
		["Content-Type: application/json"], HTTPClient.METHOD_POST, JSON.stringify({"idToken": token}))
	if not _pending.has(peer_id):
		return
	var users: Array = account.get("body", {}).get("users", [])
	if account.code != 200 or users.size() != 1 or users[0].get("localId") != uid or users[0].get("disabled", false):
		_reject(peer_id)
		return

	var root := FIRESTORE + _project.uri_encode() + "/databases/(default)/documents/"
	var headers := PackedStringArray(["Authorization: Bearer " + token])
	var restriction := await _json_request(request, root + "safetyRestrictions/" + uid.uri_encode(), headers)
	if not _pending.has(peer_id):
		return
	if restriction.code not in [200, 404] or _is_restricted(restriction.body.get("fields", {})):
		_reject(peer_id)
		return

	var profile := await _json_request(request, root + "users/" + uid.uri_encode() + "?mask.fieldPaths=alias", headers)
	if not _pending.has(peer_id):
		return
	var already_connected := _identities.values().any(func(user: Dictionary) -> bool: return user.uid == uid)
	if profile.code != 200 or _identities.size() >= _capacity or already_connected:
		_reject(peer_id)
		return
	_pending.erase(peer_id)
	request.queue_free()
	var alias: Dictionary = profile.body.get("fields", {}).get("alias", {})
	_identities[peer_id] = {"uid": uid, "alias": alias.get("stringValue", ""), "expires": expires}
	if _api.complete_auth(peer_id) != OK:
		_reject(peer_id)


func _reject(peer_id: int) -> void:
	remove(peer_id)
	# The client's auth-complete packet can still be waiting in the current poll.
	_disconnect_pending.call_deferred(peer_id)


func _disconnect_pending(peer_id: int) -> void:
	if _api.get_authenticating_peers().has(peer_id):
		_api.disconnect_peer(peer_id)


func _json_request(request: HTTPRequest, url: String, headers: PackedStringArray,
		method := HTTPClient.METHOD_GET, body := "") -> Dictionary:
	if request.request(url, headers, method, body) != OK:
		return {"code": 0, "body": {}}
	var response: Array = await request.request_completed
	var payload = JSON.parse_string(response[3].get_string_from_utf8())
	return {"code": response[1] if response[0] == HTTPRequest.RESULT_SUCCESS else 0,
		"body": payload if payload is Dictionary else {}}


func _token_claims(token: String) -> Dictionary:
	var parts := token.split(".")
	if parts.size() != 3:
		return {}
	for character in parts[1]:
		if not character in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_":
			return {}
	var base64 := parts[1].replace("-", "+").replace("_", "/")
	base64 += "=".repeat((4 - base64.length() % 4) % 4)
	var claims = JSON.parse_string(Marshalls.base64_to_utf8(base64))
	return claims if claims is Dictionary else {}


func _is_restricted(fields: Dictionary) -> bool:
	if fields.get("permanent", {}).get("booleanValue", false):
		return true
	var until := String(fields.get("until", {}).get("timestampValue", ""))
	if until.is_empty():
		return false
	return Time.get_unix_time_from_datetime_string(until.trim_suffix("Z").get_slice(".", 0)) > Time.get_unix_time_from_system()
