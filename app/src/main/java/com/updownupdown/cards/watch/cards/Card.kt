package com.updownupdown.cards.watch.cards

import kotlinx.serialization.Serializable

// Saved games store suits and ranks by their enum names, so don't rename them.

@Serializable
enum class Suit { Hearts, Clubs, Diamonds, Spades }

@Serializable
enum class Rank(val label: String, val value: Int) {
    Ace("A", 1),
    Two("2", 2),
    Three("3", 3),
    Four("4", 4),
    Five("5", 5),
    Six("6", 6),
    Seven("7", 7),
    Eight("8", 8),
    Nine("9", 9),
    Ten("10", 10),
    Jack("J", 11),
    Queen("Q", 12),
    King("K", 13),
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    override fun toString() = "${rank.label}${suit.name.first()}"
}
