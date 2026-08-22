package com.ns.findyourintroverfriend.game.drawguess

import com.google.firebase.firestore.DocumentId

data class DrawGuessRoom(
    @DocumentId val id: String = "",
    val inviteType       : String = "private",
    val matchId          : String = "",
    val hostUid          : String = "",
    val hostAlias        : String = "",
    val guestUid         : String = "",
    val guestAlias       : String = "",
    val guestJoined      : Boolean = false,  // true only after guest opens the waiting room
    val name             : String = "",
    val hasPassword      : Boolean = false,
    val status           : String = "waiting",
    val maxRounds        : Int = 5,
    val category         : String = "animals",
    val currentRound     : Int = 0,
    val drawerUid        : String = "",
    val currentWordMasked: String = "",
    val wordChoices      : List<String> = emptyList(),
    val roundPhase       : String = "picking",
    val roundStartedAt   : Long = 0L,
    val hintRevealedAt   : Long = 0L,
    val guessedCorrectly : Boolean = false,
    val guesserGuessAt   : Long = 0L,
    val scores           : Map<String, Int> = emptyMap(),
    val inviteStatus     : String = "pending",
    val createdAt        : Long = 0L
)

data class DrawGuessChat(
    val uid    : String = "",
    val alias  : String = "",
    val text   : String = "",
    val sentAt : Long = 0L,
    val isGuess: Boolean = false
)

data class DrawStroke(
    val points  : List<Map<String, Float>> = emptyList(),
    val color   : String = "#000000",
    val width   : Float = 8f,
    val eraser  : Boolean = false,
    val strokeAt: Long = 0L
)

data class DrawGuessUiState(
    val room                : DrawGuessRoom? = null,
    val chatMessages        : List<DrawGuessChat> = emptyList(),
    val strokes             : List<DrawStroke> = emptyList(),
    val wordChoices         : List<String> = emptyList(),
    val myUid               : String = "",
    val myAlias             : String = "",
    val loading             : Boolean = true,
    val error               : String? = null,
    val timerSecondsLeft    : Int = 80,
    val hintMasked          : String = "",
    val guessResult         : String = "",  // "correct" | "close" | "timeout" | ""
    val currentWordForDrawer: String = ""   // only populated for the drawer
)

// Word pools
object DrawGuessWords {
    val ANIMALS   = listOf("elephant","giraffe","penguin","octopus","kangaroo","flamingo","chameleon","dolphin","porcupine","platypus")
    val FOOD      = listOf("spaghetti","avocado","sushi","burrito","croissant","pancake","mango","dumpling","pretzel","tiramisu")
    val MOVIES    = listOf("titanic","inception","avatar","frozen","interstellar","coco","parasite","moana","gravity","whiplash")
    val OBJECTS   = listOf("umbrella","telescope","compass","lantern","bicycle","hourglass","anchor","hammock","periscope","kaleidoscope")
    val EMOTIONS  = listOf("jealousy","nostalgia","excitement","confusion","pride","grief","hope","euphoria","melancholy","serenity")
    val FEELINGS  = listOf(
        "Overthinking","Missing someone","Loneliness","First crush",
        "Social anxiety","Heartbreak","Butterflies","Daydreaming",
        "Being ignored","Falling in love","Boredom","Nostalgia",
        "Feeling lost","Sunday sadness","Late night thoughts","Waiting for a reply"
    )

    fun threePicks(category: String): List<String> =
        when (category) {
            "food"     -> FOOD
            "movies"   -> MOVIES
            "objects"  -> OBJECTS
            "emotions" -> EMOTIONS
            "feelings" -> FEELINGS
            else       -> ANIMALS
        }.shuffled().take(3)


    fun maskWord(word: String): String =
        word.map { if (it == ' ') ' ' else '_' }.joinToString(" ")

    fun revealOneHint(word: String, currentMask: String): String {
        val unrevealedIndexes = word.indices.filter { word[it] != ' ' && currentMask.replace(" ","")[it] == '_' }
        if (unrevealedIndexes.isEmpty()) return currentMask
        val revealIdx = unrevealedIndexes.random()
        val chars = word.map { if (it == ' ') ' ' else '_' }.toMutableList()
        // Copy already revealed
        val maskChars = currentMask.replace(" ","")
        word.indices.forEach { i -> if (maskChars.getOrNull(i) == word[i]) chars[i] = word[i] }
        chars[revealIdx] = word[revealIdx]
        return chars.joinToString(" ")
    }
}
