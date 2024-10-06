package com.felicio.geobottle

data class Message(
    val text: String = "",
    val senderId: String = "",
    val recipientEmail: String = "",  // Adicione este campo
    val timestamp: Long = 0L
)
