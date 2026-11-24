package com.ns.findyourintroverfriend.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.ns.findyourintroverfriend.report.SafetyService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.random.Random

internal data class DirectoryPerson(val uid: String, val alias: String, val username: String)

internal data class PeopleDirectoryState(
    val query: String = "",
    val people: List<DirectoryPerson> = emptyList(),
    val loading: Boolean = true,
    val hasMore: Boolean = false,
    val error: String? = null
)

internal data class PeopleSettings(val username: String = "", val listed: Boolean = true)

internal data class PeopleSettingsStatus(
    val settings: PeopleSettings? = null,
    val loading: Boolean = true,
    val error: String? = null
)

private val reservedUsernames = setOf(
    "admin", "administrator", "support", "moderator", "veil", "solynlabs", "google", "system", "staff", "official"
)
private val usernamePattern = Regex("[a-z][a-z0-9_]{2,19}")

internal fun canonicalUsername(input: String): String = input.trim().removePrefix("@").lowercase(Locale.ROOT)

internal fun peopleProfileFields(alias: String, username: String) =
    mapOf("alias" to alias, "searchAlias" to alias.lowercase(Locale.ROOT), "username" to username)

internal fun usernameValidationError(username: String): String? = when {
    username.isEmpty() -> null
    !username.matches(usernamePattern) -> "Use 3–20 letters, numbers or underscores, starting with a letter."
    username in reservedUsernames -> "That username is reserved. Choose another one."
    else -> null
}

internal class PeopleDirectoryViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val profiles = db.collection("peopleProfiles")
    private val settingsCollection = db.collection("peopleSettings")
    private val usernames = db.collection("peopleUsernames")
    private val _directory = MutableStateFlow(PeopleDirectoryState())
    val directory = _directory.asStateFlow()
    private val _suggestions = MutableStateFlow(emptyList<DirectoryPerson>())
    val suggestions = _suggestions.asStateFlow()
    private val _mySettings = MutableStateFlow(PeopleSettingsStatus())
    val mySettings = _mySettings.asStateFlow()
    private val _myAlias = MutableStateFlow("Anonymous")
    val myAlias = _myAlias.asStateFlow()
    private val _blocked = MutableStateFlow(emptySet<String>())
    val blocked = _blocked.asStateFlow()
    private var directoryJob: Job? = null
    private var settingsJob: Job? = null
    private var suggestionJob: Job? = null
    private var syncedAccount = false
    private var cursor: DocumentSnapshot? = null

    private val blockListener = uid.takeIf(String::isNotBlank)?.let { owner ->
        SafetyService.blocks(owner).addSnapshotListener { snapshot, _ ->
            snapshot?.let { _blocked.value = it.documents.mapTo(mutableSetOf()) { doc -> doc.id } }
        }
    }

    override fun onCleared() {
        blockListener?.remove()
        super.onCleared()
    }

    fun syncAccount(refresh: Boolean = false) {
        if ((syncedAccount && !refresh) || settingsJob?.isActive == true) return
        _mySettings.value = _mySettings.value.copy(loading = true, error = null)
        if (uid.isBlank()) {
            _mySettings.value = PeopleSettingsStatus(loading = false, error = "Sign in to set your username.")
            return
        }
        settingsJob = viewModelScope.launch {
            try {
                val settingsRef = settingsCollection.document(uid)
                if (syncedAccount) {
                    val current = settingsRef.get().await().toPeopleSettings()
                    _mySettings.value = PeopleSettingsStatus(current, loading = false)
                    return@launch
                }
                val userRef = db.collection("users").document(uid)
                val profileRef = profiles.document(uid)
                val updated = db.runTransaction { transaction ->
                    val alias = transaction.get(userRef).toPublicAlias() ?: return@runTransaction null
                    val stored = transaction.get(settingsRef)
                    val settings = stored.toPeopleSettings()
                    val public = if (settings.listed) transaction.get(profileRef) else null
                    if (!stored.exists()) transaction.set(settingsRef, settings.fields())
                    if (public != null && (
                            public.getString("alias") != alias || public.getString("username") != settings.username
                        )
                    ) transaction.set(profileRef, peopleProfileFields(alias, settings.username))
                    alias to settings
                }.await()
                if (updated == null) {
                    _mySettings.value = PeopleSettingsStatus(
                        loading = false, error = "Finish setting up your VEIL profile to set a username."
                    )
                    return@launch
                }
                val (alias, settings) = updated
                _myAlias.value = alias
                _mySettings.value = PeopleSettingsStatus(settings, loading = false)
                syncedAccount = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val status = _mySettings.value
                _mySettings.value = status.copy(
                    loading = false,
                    error = if (status.settings == null) peopleError(failure, "Couldn’t load your username settings.") else null
                )
            }
        }
    }

    suspend fun saveSettings(username: String?, listed: Boolean?): String? {
        if (uid.isBlank()) return "Sign in to set your username."
        val handle = username?.let(::canonicalUsername)
        if (handle != null) {
            usernameValidationError(handle)?.let { return it }
        }
        return try {
            val userRef = db.collection("users").document(uid)
            val settingsRef = settingsCollection.document(uid)
            val profileRef = profiles.document(uid)
            val updated = db.runTransaction { transaction ->
                val alias = transaction.get(userRef).toPublicAlias() ?: return@runTransaction null
                val previous = transaction.get(settingsRef).toPeopleSettings()
                val next = PeopleSettings(handle ?: previous.username, listed ?: previous.listed)
                if (next.username != previous.username) {
                    if (next.username.isNotEmpty()) transaction.set(usernames.document(next.username), mapOf("uid" to uid))
                    if (previous.username.isNotEmpty()) transaction.delete(usernames.document(previous.username))
                }
                transaction.set(settingsRef, next.fields())
                if (next.listed) transaction.set(profileRef, peopleProfileFields(alias, next.username))
                else transaction.delete(profileRef)
                alias to next
            }.await() ?: return "Finish setting up your VEIL profile first."
            val (alias, settings) = updated
            _myAlias.value = alias
            _mySettings.value = PeopleSettingsStatus(settings, loading = false)
            syncedAccount = true
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if ((failure as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                !handle.isNullOrEmpty() && handle != _mySettings.value.settings?.username
            ) "Couldn’t save that username. It may already be taken."
            else peopleError(failure, "Couldn’t save your People settings. Try again.")
        }
    }

    fun openDirectory() {
        if (directoryJob == null) search("", debounce = false)
    }

    fun search(input: String, debounce: Boolean = true) {
        directoryJob?.cancel()
        cursor = null
        _directory.value = PeopleDirectoryState(query = input)
        directoryJob = viewModelScope.launch {
            if (debounce) delay(300)
            try {
                val term = canonicalUsername(input)
                val byUsername = input.trimStart().startsWith("@")
                if (term.isEmpty() && !byUsername) {
                    val page = profiles.orderBy("searchAlias").limit(24).get().await()
                    cursor = page.documents.lastOrNull()
                    _directory.value = PeopleDirectoryState(input, page.documents.mapNotNull(::person), false, page.size() == 24)
                } else {
                    val matches = if (byUsername) lookup("username", term)
                    else coroutineScope {
                        val handles = async { lookup("username", term) }
                        val aliases = async { lookup("searchAlias", term) }
                        (handles.await() + aliases.await()).distinctBy { it.uid }
                    }
                    _directory.value = PeopleDirectoryState(input, matches.sortedBy { it.username != term }, false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _directory.value = PeopleDirectoryState(
                    input, loading = false, error = peopleError(failure, "Couldn’t load People. Try again.")
                )
            }
        }
    }

    fun loadMore() {
        val after = cursor ?: return
        val current = _directory.value
        if (current.loading || !current.hasMore || current.query.isNotBlank()) return
        _directory.value = current.copy(loading = true, error = null)
        directoryJob = viewModelScope.launch {
            try {
                val page = profiles.orderBy("searchAlias").startAfter(after).limit(24).get().await()
                cursor = page.documents.lastOrNull() ?: after
                _directory.value = current.copy(
                    people = (current.people + page.documents.mapNotNull(::person)).distinctBy { it.uid },
                    loading = false,
                    hasMore = page.size() == 24,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _directory.value = current.copy(
                    loading = false, error = peopleError(failure, "Couldn’t load more people. Try again.")
                )
            }
        }
    }

    fun loadSuggestions() {
        if (uid.isBlank() || suggestionJob?.isActive == true) return
        suggestionJob = viewModelScope.launch {
            try {
                val ordered = profiles.orderBy(FieldPath.documentId())
                val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                val pivot = List(5) { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
                val first = ordered.startAt(pivot).limit(14).get().await().documents
                val rest = if (first.size < 14) ordered.limit((14 - first.size).toLong()).get().await().documents else emptyList()
                _suggestions.value = (first + rest).distinctBy { it.id }.mapNotNull(::person).shuffled()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The timeline can be used independently of suggestions.
            }
        }
    }

    private suspend fun lookup(field: String, term: String): List<DirectoryPerson> {
        if (term.isEmpty()) return emptyList()
        val ordered: Query = profiles.orderBy(field)
        return ordered.startAt(term).endAt(term + "\uDBFF\uDFFF").limit(24).get().await().documents.mapNotNull(::person)
    }

    private fun DocumentSnapshot.toPublicAlias(): String? {
        if (getBoolean("profileComplete") != true || getBoolean("isReviewerAccount") == true) return null
        return getString("alias").orEmpty().trim().take(40).ifBlank { "Anonymous" }
    }

    private fun person(snapshot: DocumentSnapshot): DirectoryPerson? {
        val alias = snapshot.getString("alias") ?: return null
        return DirectoryPerson(snapshot.id, alias, snapshot.getString("username").orEmpty())
    }

    private fun peopleError(failure: Exception, fallback: String): String =
        when ((failure as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "The server hasn’t enabled access to People yet."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Sign in again to use People."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Can’t reach People. Check your connection and retry."
            else -> fallback
        }

    private fun DocumentSnapshot.toPeopleSettings() =
        PeopleSettings(getString("username").orEmpty(), getBoolean("listed") ?: true)

    private fun PeopleSettings.fields() = mapOf("username" to username, "listed" to listed)

}
