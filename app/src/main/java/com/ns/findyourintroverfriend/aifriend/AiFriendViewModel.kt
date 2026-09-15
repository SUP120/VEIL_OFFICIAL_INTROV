package com.ns.findyourintroverfriend.aifriend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  VEIL Friends — ViewModel
// ─────────────────────────────────────────────────────────────────────────────

sealed class ChatState {
    object Idle    : ChatState()
    object Sending : ChatState()
    data class Error(val message: String) : ChatState()
}

class AiFriendViewModel : ViewModel() {

    private val repo = AiFriendRepository()

    // ── Current conversation ───────────────────────────────────────────────
    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    // ── Memories ──────────────────────────────────────────────────────────
    private val _memories = MutableStateFlow<List<AiMemory>>(emptyList())
    val memories: StateFlow<List<AiMemory>> = _memories.asStateFlow()

    // ── Past conversations list ───────────────────────────────────────────
    private val _conversations = MutableStateFlow<List<AiConversation>>(emptyList())
    val conversations: StateFlow<List<AiConversation>> = _conversations.asStateFlow()

    // ── Init with a friend (open latest or new conversation) ─────────────
    fun initFriend(friendId: String, conversationId: String) {
        if (conversationId != "new" && conversationId.isNotBlank()) {
            openConversation(conversationId)
        } else {
            viewModelScope.launch {
                // Try to resume the last conversation
                val latest = repo.getLatestConversation(friendId)
                if (latest != null) {
                    openConversation(latest.id)
                } else {
                    // Fresh start
                    _conversationId.value = null
                    _messages.value = emptyList()
                }
            }
        }
    }

    private fun openConversation(convId: String) {
        _conversationId.value = convId
        viewModelScope.launch {
            repo.streamMessages(convId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    // ── Send a message ────────────────────────────────────────────────────
    fun sendMessage(friendId: String, text: String) {
        if (text.isBlank() || _chatState.value is ChatState.Sending) return
        viewModelScope.launch {
            _chatState.value = ChatState.Sending

            // Optimistically show the user's message immediately
            val tempMsg = AiMessage(
                id        = "pending_${System.currentTimeMillis()}",
                sender    = "user",
                content   = text.trim(),
                createdAt = System.currentTimeMillis(),
            )
            _messages.value = _messages.value + tempMsg

            val result = repo.sendMessage(friendId, _conversationId.value, text.trim())

            result.onSuccess { (_, convId) ->
                // Update conversationId if this was a new conversation
                if (_conversationId.value == null) {
                    _conversationId.value = convId
                    // Start streaming real messages now that we have an ID
                    viewModelScope.launch {
                        repo.streamMessages(convId).collect { msgs -> _messages.value = msgs }
                    }
                }
                _chatState.value = ChatState.Idle
            }.onFailure { err ->
                // Remove optimistic message on failure
                _messages.value = _messages.value.filter { it.id != tempMsg.id }
                _chatState.value = ChatState.Error(err.message ?: "Failed to send. Try again.")
            }
        }
    }

    // ── Load memories ─────────────────────────────────────────────────────
    fun loadMemories(friendId: String) {
        viewModelScope.launch {
            _memories.value = repo.getMemories(friendId)
        }
    }

    // ── Delete a single memory ────────────────────────────────────────────
    fun deleteMemory(friendId: String, memoryId: String) {
        viewModelScope.launch {
            repo.deleteMemory(memoryId)
            _memories.value = _memories.value.filter { it.id != memoryId }
        }
    }

    // ── Forget everything ─────────────────────────────────────────────────
    fun forgetEverything(friendId: String) {
        viewModelScope.launch {
            repo.deleteAllMemories(friendId)
            _memories.value = emptyList()
        }
    }

    // ── Load conversation list ────────────────────────────────────────────
    fun loadConversations(friendId: String) {
        viewModelScope.launch {
            _conversations.value = repo.getConversations(friendId)
        }
    }

    fun clearError() {
        if (_chatState.value is ChatState.Error) _chatState.value = ChatState.Idle
    }
}
