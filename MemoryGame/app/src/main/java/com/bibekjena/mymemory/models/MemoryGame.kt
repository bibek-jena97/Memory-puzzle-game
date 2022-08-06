package com.bibekjena.mymemory.models

import com.bibekjena.mymemory.utils.DEFAULT_ICON

class MemoryGame(
    private val boardSize: BoardSize,
    private val customImages: List<String>?){


    val cards:List<MemoryCard>
    var numPairsFound = 0
    private var numFlips = 0

    private var indexOfSingleSelectedCard : Int? = null

    init {
        if (customImages == null){
            val chosenImages = DEFAULT_ICON.shuffled().take(boardSize.getNumPairs())
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it) }
        }
        else{
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it.hashCode(),it) }
        }

    }

    fun flipCard(position: Int):Boolean {
        numFlips++
        val card = cards[position]
        var foundMatch = false
        if (indexOfSingleSelectedCard == null ){
            // 0 or 2 cards flipped over
            restoreCards()
            indexOfSingleSelectedCard = position
        }
        else{
            // exactly 1 card flipped
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!,position)
            indexOfSingleSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return  foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier){
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        return true
    }

    private fun restoreCards() {
        for (card in cards){
            if (!card.isMatched){
                card.isFaceUp = false
            }

        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numFlips
    }
}