extends RefCounted

signal status(peer_id: int, state: String, offer_id: int, message: String)
signal matched(first: int, second: int)

const OFFER_LIFETIME := 20000

var _queue: Array[int] = []
var _offers: Dictionary[int, Dictionary] = {}
var _peer_offers: Dictionary[int, int] = {}
var _next_offer := 1


func enqueue(peer_id: int, now: int) -> void:
	if _queue.has(peer_id) or _peer_offers.has(peer_id):
		return
	_queue.append(peer_id)
	if _queue.size() < 2:
		status.emit(peer_id, "queued", 0, "Looking for another player. You can keep exploring.")
		return

	var first: int = _queue.pop_front()
	var second: int = _queue.pop_front()
	var offer_id := _next_offer
	_next_offer += 1
	var peers: Array[int] = [first, second]
	_offers[offer_id] = {"peers": peers, "accepted": [], "expires": now + OFFER_LIFETIME}
	for id: int in peers:
		_peer_offers[id] = offer_id
		status.emit(id, "offer", offer_id, "Someone is available. Accept within 20 seconds to meet.")


func accept(peer_id: int, offer_id: int, now: int) -> void:
	if offer_id == 0 or _peer_offers.get(peer_id, 0) != offer_id:
		return
	var offer := _offers[offer_id]
	if now >= int(offer.expires):
		_end_offer(offer_id, "Match offer expired. You can search again.")
		return
	var accepted: Array = offer.accepted
	if accepted.has(peer_id):
		return
	accepted.append(peer_id)
	if accepted.size() < 2:
		status.emit(peer_id, "accepted", offer_id, "Waiting for the other player to accept.")
		return

	var peers: Array = offer.peers
	_clear_offer(offer_id)
	matched.emit(peers[0], peers[1])


func cancel(peer_id: int, offer_id: int) -> void:
	var current_offer := int(_peer_offers.get(peer_id, 0))
	if (offer_id != 0 and offer_id != current_offer) or (current_offer == 0 and not _queue.has(peer_id)):
		return
	remove(peer_id)
	status.emit(peer_id, "idle", 0, "Search cancelled.")


func remove(peer_id: int) -> void:
	_queue.erase(peer_id)
	var offer_id := int(_peer_offers.get(peer_id, 0))
	if offer_id == 0:
		return
	var peers: Array = _offers[offer_id].peers
	_clear_offer(offer_id)
	for id: int in peers:
		if id != peer_id:
			status.emit(id, "idle", 0, "The other player left the match. You can search again.")


func expire(now: int) -> void:
	for offer_id: int in _offers.keys():
		if now >= int(_offers[offer_id].expires):
			_end_offer(offer_id, "Match offer expired. You can search again.")


func _end_offer(offer_id: int, message: String) -> void:
	var peers: Array = _offers[offer_id].peers
	_clear_offer(offer_id)
	for id: int in peers:
		status.emit(id, "idle", 0, message)


func _clear_offer(offer_id: int) -> void:
	for id: int in _offers[offer_id].peers:
		_peer_offers.erase(id)
	_offers.erase(offer_id)
