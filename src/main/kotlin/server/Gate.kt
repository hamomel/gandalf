package server

class Gate {

    fun open(userId: String) {
        Log.d("trying to open: $userId")
        getCardPosition(userId)?.let {

        }
    }
}