package com.ns.findyourintroverfriend.aifriend

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

internal data class AiChat(
    val usage: AiUsage? = null,
    val messages: List<AiMessage> = emptyList(),
    val findingConversation: Boolean = true,
    val conversationError: String? = null,
    val historyError: String? = null,
    val serviceIssue: AiServiceIssue? = null,
    val error: String? = null,
    val statusLoading: Boolean = false,
    val sending: Boolean = false,
    val pending: AiMessage? = null,
    val payment: AiPayment? = null,
    val hasOlder: Boolean = false,
    val loadingOlder: Boolean = false
) {
    val canSend: Boolean
        get() = !sending && !findingConversation && conversationError == null
            && serviceIssue?.blocksSending != true && (usage != null || !statusLoading)
}

internal data class AiServiceIssue(val message: String, val blocksSending: Boolean = false)

internal enum class AiPayment { Confirm, Insufficient }

internal data class AiMemoryState(
    val memories: List<AiMemory> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

private data class AiSendAttempt(val id: String, val message: String, val conversationId: String?)

internal class AiFriendViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val repository = AiFriendRepository()
    private val _chat = MutableStateFlow(AiChat())
    val chat = _chat.asStateFlow()
    private val _memories = MutableStateFlow(AiMemoryState())
    val memories = _memories.asStateFlow()
    val draft = savedState.getStateFlow("draft", "")

    private var friendId: String? = null
    private var routeConversation: String? = null
    private var activeConversation: String? = null
    private var historyJob: Job? = null
    private var statusJob: Job? = null
    private var cursor: DocumentSnapshot? = null
    private var attempt = savedState.get<ArrayList<String>>("attempt")?.let {
        AiSendAttempt(it[0], it[1], it[2].ifBlank { null })
    }

    fun open(friendId: String, conversationId: String) {
        if (this.friendId == friendId) return
        this.friendId = friendId
        routeConversation = conversationId.takeIf { it.isNotBlank() && it != "new" }
        findConversation()
    }

    private fun findConversation() {
        val friend = friendId ?: return
        _chat.update { it.copy(findingConversation = true, conversationError = null) }
        viewModelScope.launch {
            try {
                activeConversation = routeConversation ?: repository.latestConversation(friend)
                activeConversation?.let(::watchHistory)
                _chat.update { it.copy(findingConversation = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    "Chat history is unavailable for this account."
                } else {
                    "Couldn't load chat history. Check your connection."
                }
                _chat.update {
                    it.copy(findingConversation = false, conversationError = message)
                }
            }
        }
    }

    fun retryConnection() {
        refreshUsage()
        val state = _chat.value
        if (state.conversationError != null) {
            findConversation()
        } else if (state.historyError != null && state.messages.isEmpty()) {
            retryHistory()
        }
    }

    fun refreshUsage() {
        if (statusJob?.isActive == true || _chat.value.sending) return
        _chat.update { it.copy(statusLoading = true) }
        statusJob = viewModelScope.launch {
            try {
                val usage = repository.usage()
                _chat.update { it.copy(usage = usage, serviceIssue = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _chat.update { it.copy(serviceIssue = serviceIssue(error)) }
            } finally {
                _chat.update { it.copy(statusLoading = false) }
            }
        }
    }

    fun editDraft(text: String) {
        savedState["draft"] = text.take(AI_MESSAGE_LIMIT)
        _chat.update { it.copy(error = null) }
    }

    fun send() {
        val state = _chat.value
        if (!state.canSend || draft.value.isBlank()) return
        val usage = state.usage
        if (usage?.freeRemaining == 0 && attempt?.message != draft.value.trim()) {
            _chat.update {
                it.copy(payment = if (usage.cookieBalance < usage.cookieCost) AiPayment.Insufficient else AiPayment.Confirm)
            }
        } else {
            send(allowPaid = false)
        }
    }

    fun confirmPayment() {
        if (_chat.value.payment != AiPayment.Confirm) return
        dismissPayment()
        send(allowPaid = true)
    }

    fun dismissPayment() {
        _chat.update { it.copy(payment = null) }
    }

    suspend fun reportReply(messageId: String, reason: String): String? {
        val conversation = activeConversation ?: return "This AI reply is unavailable."
        return try {
            repository.reportReply(conversation, messageId, reason)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            chatError(error)
        }
    }

    private fun send(allowPaid: Boolean) {
        val friend = friendId ?: return
        val message = draft.value.trim()
        if (message.isBlank() || !_chat.value.canSend) return
        val outgoing = attempt?.takeIf { it.message == message }
            ?: AiSendAttempt(UUID.randomUUID().toString(), message, activeConversation)
        attempt = outgoing
        savedState["attempt"] = arrayListOf(outgoing.id, outgoing.message, outgoing.conversationId.orEmpty())
        val pending = AiMessage("${outgoing.id}-user", "user", message, System.currentTimeMillis())
        statusJob?.cancel()
        _chat.update { it.copy(sending = true, pending = pending, error = null) }

        viewModelScope.launch {
            try {
                val reply = repository.send(friend, outgoing.conversationId, outgoing.id, message, allowPaid)
                if (draft.value.trim() == message) savedState["draft"] = ""
                attempt = null
                savedState.remove<ArrayList<String>>("attempt")
                mergeMessages(reply.messages)
                _chat.update { it.copy(usage = reply.usage, serviceIssue = null) }
                if (activeConversation != reply.conversationId) {
                    activeConversation = reply.conversationId
                    watchHistory(reply.conversationId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                handleSendError(error)
            } finally {
                _chat.update { it.copy(sending = false, pending = null) }
            }
        }
    }

    private fun handleSendError(error: Exception) {
        val details = (error as? FirebaseFunctionsException)?.details as? Map<*, *>
        val usage = (details?.get("usage") as? Map<*, *>)?.toUsage()
        val payment = when (details?.get("reason")) {
            "not-enough-cookies" -> AiPayment.Insufficient
            "payment-confirmation" -> if (usage != null && usage.cookieBalance < usage.cookieCost) {
                AiPayment.Insufficient
            } else {
                AiPayment.Confirm
            }
            else -> null
        }
        _chat.update {
            it.copy(
                usage = usage ?: it.usage,
                serviceIssue = if (usage != null) null else it.serviceIssue,
                payment = payment,
                error = if (payment == null) chatError(error) else null
            )
        }
    }

    fun retryHistory() {
        activeConversation?.let(::watchHistory)
    }

    private fun watchHistory(conversationId: String) {
        historyJob?.cancel()
        _chat.update { it.copy(historyError = null) }
        historyJob = viewModelScope.launch {
            try {
                repository.streamMessages(conversationId).collect { page ->
                    if (cursor == null) {
                        cursor = page.oldest
                        _chat.update { it.copy(hasOlder = page.hasMore) }
                    }
                    mergeMessages(page.messages)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _chat.update { it.copy(historyError = "Couldn't sync chat history.") }
            }
        }
    }

    fun loadEarlier() {
        val conversation = activeConversation ?: return
        val oldest = cursor ?: return
        if (_chat.value.loadingOlder) return
        _chat.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            try {
                val page = repository.olderMessages(conversation, oldest)
                cursor = page.oldest ?: oldest
                mergeMessages(page.messages)
                _chat.update { it.copy(hasOlder = page.hasMore, historyError = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _chat.update { it.copy(historyError = "Couldn't load earlier messages.") }
            } finally {
                _chat.update { it.copy(loadingOlder = false) }
            }
        }
    }

    private fun mergeMessages(incoming: List<AiMessage>) {
        _chat.update { state ->
            val messages = (state.messages + incoming).associateBy { it.id }.values
                .sortedWith(compareBy<AiMessage> { it.createdAt }.thenBy { it.id })
            state.copy(messages = messages)
        }
    }

    fun loadMemories() {
        val friend = friendId ?: return
        if (_memories.value.loading) return
        editMemories { repository.memories(friend) }
    }

    fun forgetMemories(ids: List<String>) {
        if (_memories.value.loading) return
        editMemories {
            repository.forgetMemories(ids)
            _memories.value.memories.filterNot { it.id in ids }
        }
    }

    private fun editMemories(action: suspend () -> List<AiMemory>) {
        _memories.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _memories.value = AiMemoryState(memories = action())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _memories.update { it.copy(loading = false, error = "Couldn't update saved memories. Please try again.") }
            }
        }
    }

    private fun chatError(error: Exception): String {
        val failure = error as? FirebaseFunctionsException
        return when (failure?.code) {
            FirebaseFunctionsException.Code.NOT_FOUND -> "VEIL Friends is unavailable. Try reopening this chat."
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again to use VEIL Friends."
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "VEIL Friends is unavailable for this account."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.ABORTED,
            FirebaseFunctionsException.Code.UNAVAILABLE -> failure.message ?: "Couldn't reach VEIL Friends. Try again."
            else -> "Couldn't reach VEIL Friends. Try again."
        }
    }

    private fun serviceIssue(error: Exception): AiServiceIssue {
        val failure = error as? FirebaseFunctionsException
        return when (failure?.code) {
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.UNIMPLEMENTED ->
                AiServiceIssue("VEIL Friends AI replies aren't available yet.", blocksSending = true)
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                AiServiceIssue("VEIL couldn't verify this session. Reopen the app or sign in again.", blocksSending = true)
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                AiServiceIssue(chatError(error), blocksSending = true)
            else -> AiServiceIssue("Couldn't reach VEIL Friends. Check your connection and retry.")
        }
    }
}
