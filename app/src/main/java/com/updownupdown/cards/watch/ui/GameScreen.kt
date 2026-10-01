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
    val outcome = session.outcome

    // Buzz when a move ends the game, but not when opening an ended game.
    var wasOver by remember { mutableStateOf(outcome != null) }
    LaunchedEffect(outcome) {
        if (outcome != null && !wasOver) {
            if (outcome.won) haptics.win() else haptics.lose()
        }
        wasOver = outcome != null
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
        bottom = { size ->
            if (outcome != null) {
                GameButton(
                    label = "New game",
                    onClick = { haptics.move(); session.newGame() },
                    width = size.width,
                    height = size.height,
                )
            } else {
                game.Bottom(state, session::play, haptics, size)
            }
        },
    ) {
        game.Board(state, session::play, haptics, Modifier.fillMaxSize())
        if (outcome != null) BoardBanner(outcome.message)
    }
}
