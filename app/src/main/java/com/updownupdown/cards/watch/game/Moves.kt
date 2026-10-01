package com.updownupdown.cards.watch.game

/** Plays [next] with a buzz, or buzzes "no" when the move isn't allowed (null). */
fun <S> Feedback.tryMove(next: S?, play: (S) -> Unit) {
    if (next == null) {
        reject()
    } else {
        move()
        play(next)
    }
}
