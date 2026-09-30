package com.updownupdown.cards.watch.cards

import kotlin.random.Random

/** A full 52-card deck, in suit then rank order. */
fun fullDeck(): List<Card> = Suit.entries.flatMap { suit -> Rank.entries.map { Card(it, suit) } }

fun shuffledDeck(random: Random): List<Card> = fullDeck().shuffled(random)

val Suit.isRed: Boolean get() = this == Suit.Hearts || this == Suit.Diamonds
val Card.isRed: Boolean get() = suit.isRed
