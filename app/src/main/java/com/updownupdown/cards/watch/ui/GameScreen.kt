package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.game.GameSession

@Composable
fun <S : Any> GameScreen(
    session: GameSession<S>,
    onHome: () -> Unit,
    onRules: () -> Unit,
) {
    val haptics = rememberHaptics()
    val game = session.game
    val state = session.state
    val won = session.isWon

    // Buzz when a move wins, but not when opening a game that's already won.
    var wasWon by remember { mutableStateOf(won) }
    LaunchedEffect(won) {
        if (won && !wasWon) haptics.win()
        wasWon = won
    }

    GameLayout(
        onHome = {
            haptics.press()
            onHome()
        },
        onUndo = { if (session.undo()) haptics.move() else haptics.reject() },
        onUndoHold = {
            haptics.longPress()
            session.newGame()
        },
        canUndo = session.canUndo,
        onRules = {
            haptics.press()
            onRules()
        },
        bottom = { width, height ->
            val action = game.actions(state).firstOrNull()
            when {
                won -> ActionButton("New game", { session.newGame() }, width, height)
                action != null -> ActionButton(
                    label = action.label,
                    onClick = {
                        haptics.move()
                        session.play(action.perform(state))
                    },
                    width = width,
                    height = height,
                    enabled = action.enabled,
                )
                else -> game.status(state)?.let { StatusText(it) }
            }
        },
    ) {
        game.Board(state, session::play, haptics, Modifier.fillMaxSize())
        if (won) BoardBanner(game.winMessage(state))
    }
}
