package com.ns.findyourintroverfriend.game

import androidx.compose.runtime.Composable

// GameInviteScreen is replaced by SyncGameScreen.
// Data classes (CircleMate, IncomingInvite) live in GameInviteViewModel.kt.
// This file kept only for nav-graph build compatibility.

@Composable
fun GameInviteScreen(
    matchId      : String    = "",
    partnerAlias : String    = "Partner",
    onBack       : () -> Unit,
    onGameStart  : (gameId: String) -> Unit = {}
) {
    SyncGameScreen(
        matchId      = matchId,
        partnerAlias = partnerAlias,
        onBack       = onBack
    )
}
