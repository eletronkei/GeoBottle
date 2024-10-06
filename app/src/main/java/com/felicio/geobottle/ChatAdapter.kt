package com.felicio.geobottle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.felicio.geobottle.databinding.ItemMessageBinding

class ChatAdapter(
    private var messages: List<Message>,
    private val currentUserEmail: String
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.senderId == currentUserEmail) {
            holder.bindSentMessage(message)
        } else {
            holder.bindReceivedMessage(message)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindSentMessage(message: Message) {
            binding.sentMessageContainer.visibility = View.VISIBLE
            binding.receivedMessageContainer.visibility = View.GONE
            binding.textViewSentMessage.text = message.text
        }

        fun bindReceivedMessage(message: Message) {
            binding.sentMessageContainer.visibility = View.GONE
            binding.receivedMessageContainer.visibility = View.VISIBLE
            binding.textViewReceivedMessage.text = message.text

            // Exibe o e-mail do remetente
            binding.textViewSenderNameReceived.visibility = View.VISIBLE
            binding.textViewSenderNameReceived.text = message.senderId
        }
    }

    fun updateMessages(newMessages: List<Message>) {
        val oldMessageCount = messages.size
        messages = newMessages
        val newMessageCount = newMessages.size

        when {
            newMessageCount > oldMessageCount -> {
                // Novas mensagens foram adicionadas
                notifyItemRangeInserted(oldMessageCount, newMessageCount - oldMessageCount)
            }

            newMessageCount < oldMessageCount -> {
                // Mensagens foram removidas
                notifyItemRangeRemoved(newMessageCount, oldMessageCount - newMessageCount)
            }

            else -> {
                // O número de mensagens é o mesmo, mas pode haver atualizações
                for (i in newMessages.indices) {
                    if (newMessages[i] != messages[i]) {
                        notifyItemChanged(i)
                    }
                }
            }
        }
    }
}
