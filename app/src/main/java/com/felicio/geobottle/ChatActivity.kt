package com.felicio.geobottle

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.felicio.geobottle.databinding.ActivityChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val messages = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter
    private val tag = "ChatActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Infla o layout da activity
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializa o RecyclerView e o adaptador antes de qualquer operação
        setupRecyclerView()

        // Obtém o ID da garrafinha e o e-mail do usuário atual
        val garrafinhaId = intent.getStringExtra("garrafinhaId")
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        // Adiciona logs para verificar o valor do ID da garrafinha e o e-mail do usuário
        Log.d(tag, "garrafinhaId: $garrafinhaId")
        Log.d(tag, "currentUserEmail: $currentUserEmail")

        // Verifica se o ID da garrafinha e o e-mail do usuário atual são válidos
        if (garrafinhaId.isNullOrBlank() || currentUserEmail == null) {
            Log.e(tag, "Erro: ID da garrafinha ou e-mail do usuário atual é inválido.")
            Toast.makeText(
                this,
                "Erro ao carregar o chat. Por favor, tente novamente.",
                Toast.LENGTH_SHORT
            ).show()
            finish() // Encerra a atividade para evitar mais erros
            return
        }

        // Verifica se o documento da garrafinha existe antes de configurar o chat
        checkOrCreateGarrafinha(garrafinhaId, currentUserEmail)

        // Configura o botão de voltar ao menu
        binding.buttonBackToMenu.setOnClickListener {
            finish()
        }
    }

    // Função para configurar o RecyclerView e o adaptador
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages, FirebaseAuth.getInstance().currentUser?.email ?: "")
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = chatAdapter
    }

    // Verifica se o documento da garrafinha existe; se não existir, cria um novo
    private fun checkOrCreateGarrafinha(garrafinhaId: String, userEmail: String) {
        firestore.collection("garrafinhas").document(garrafinhaId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(tag, "Documento da garrafinha encontrado: $garrafinhaId")

                    // Verificar acesso do usuário e número de usuários permitidos
                    val allowedUsers = (document.get("allowedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (allowedUsers.contains(userEmail)) {
                        // Se o usuário já está na lista de permitidos, iniciar o chat
                        setupChat(garrafinhaId, userEmail)
                    } else if (allowedUsers.size >= 5) {
                        // Se já houver 5 usuários, impedir o novo usuário de acessar o chat
                        Toast.makeText(this, "O chat já atingiu o limite de 5 usuários.", Toast.LENGTH_SHORT).show()
                        finish() // Bloqueia o acesso e encerra a Activity
                    } else {
                        // Adicionar novo usuário à lista de allowedUsers
                        val updatedUsers = allowedUsers + userEmail
                        firestore.collection("garrafinhas").document(garrafinhaId)
                            .update("allowedUsers", updatedUsers)
                            .addOnSuccessListener {
                                Log.d(tag, "Usuário $userEmail adicionado à garrafinha.")
                                setupChat(garrafinhaId, userEmail)
                            }
                            .addOnFailureListener { e ->
                                Log.e(tag, "Erro ao adicionar o usuário: ${e.message}")
                                Toast.makeText(this, "Erro ao adicionar o usuário ao chat.", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                    }
                } else {
                    // Se a garrafinha não existir, criar uma nova com o primeiro usuário
                    Log.d(tag, "Documento da garrafinha não encontrado. Criando um novo documento...")

                    val garrafinhaData = hashMapOf(
                        "allowedUsers" to listOf(userEmail)
                    )

                    firestore.collection("garrafinhas").document(garrafinhaId)
                        .set(garrafinhaData)
                        .addOnSuccessListener {
                            Log.d(tag, "Novo documento da garrafinha criado com sucesso.")
                            setupChat(garrafinhaId, userEmail)
                        }
                        .addOnFailureListener { e ->
                            Log.e(tag, "Erro ao criar o documento da garrafinha: ${e.message}")
                            Toast.makeText(this, "Erro ao criar a garrafinha. Por favor, tente novamente.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(tag, "Erro ao verificar a existência da garrafinha: ${exception.message}")
                Toast.makeText(this, "Erro ao verificar a existência da garrafinha. Por favor, tente novamente.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }



    // Função para verificar se o usuário atual tem acesso à garrafinha
    private fun checkUserAccess(garrafinhaId: String, userEmail: String) {
        firestore.collection("garrafinhas").document(garrafinhaId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val allowedUsers = (document.get("allowedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    Log.d(tag, "Lista de usuários permitidos: $allowedUsers")

                    if (allowedUsers.contains(userEmail)) {
                        setupChat(garrafinhaId, userEmail)
                    } else {
                        Toast.makeText(this, "Você não tem acesso a essa garrafinha.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Log.e(tag, "Erro: Documento da garrafinha não encontrado.")
                    Toast.makeText(this, "Erro ao carregar a garrafinha. Por favor, tente novamente.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(tag, "Erro ao verificar acesso do usuário: ${exception.message}")
                Toast.makeText(this, "Erro ao verificar o acesso. Por favor, tente novamente.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupChat(garrafinhaId: String, currentUserEmail: String) {
        // Inicia a escuta das mensagens da garrafinha específica
        listenForMessages(garrafinhaId)

        // Desabilita o botão para evitar múltiplos cliques enquanto o envio é processado
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString()
            if (messageText.isNotEmpty()) {
                binding.buttonSend.isEnabled = false // Desabilita o botão temporariamente
                sendMessage(messageText, garrafinhaId) // Envia a mensagem associada à garrafinha
                binding.editTextMessage.text.clear() // Limpa o campo de texto após o envio
            } else {
                Toast.makeText(this, "A mensagem não pode estar vazia.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForMessages(garrafinhaId: String) {
        firestore.collection("garrafinhas").document(garrafinhaId).collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(tag, "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val newMessages = mutableListOf<Message>()

                    // Processa as mudanças nos documentos
                    for (dc in snapshots.documentChanges) {
                        val message = dc.document.toObject(Message::class.java)

                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                // Adiciona a mensagem à nova lista, evitando duplicatas
                                val messageExists = messages.any {
                                    it.timestamp == message.timestamp && it.text == message.text
                                }
                                if (!messageExists) {
                                    newMessages.add(message)
                                }
                            }

                            DocumentChange.Type.MODIFIED -> {
                                Log.d(tag, "Mensagem modificada")
                            }

                            DocumentChange.Type.REMOVED -> {
                                Log.d(tag, "Mensagem removida")
                            }

                            else -> {
                                Log.d(tag, "Tipo de mudança desconhecido")
                            }
                        }
                    }

                    // Atualiza o adaptador apenas se houver novas mensagens
                    if (newMessages.isNotEmpty()) {
                        // Atualiza a lista de mensagens no adaptador
                        val updatedMessagesList = messages + newMessages
                        chatAdapter.updateMessages(updatedMessagesList)
                        // Atualiza a lista original de mensagens para refletir as novas
                        messages.addAll(newMessages)
                        // Move para a última mensagem
                        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
    }





    // Função para enviar uma mensagem associada à garrafinha
    private fun sendMessage(messageText: String, garrafinhaId: String) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        if (currentUserEmail == null) {
            Log.e(tag, "Erro: E-mail do usuário atual é nulo.")
            Toast.makeText(this, "Erro de autenticação. Por favor, faça login novamente.", Toast.LENGTH_SHORT).show()
            return
        }

        val message = Message(
            senderId = currentUserEmail,
            text = messageText,
            timestamp = System.currentTimeMillis()
        )

        firestore.collection("garrafinhas").document(garrafinhaId).collection("messages")
            .add(message)
            .addOnSuccessListener {
                Log.d(tag, "Mensagem enviada com sucesso!")
                messages.add(message)
                chatAdapter.notifyItemInserted(messages.size - 1)
                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                binding.buttonSend.isEnabled = true // Reabilita o botão após o envio
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Erro ao enviar a mensagem", e)
                Toast.makeText(this, "Erro ao enviar mensagem. Por favor, tente novamente.", Toast.LENGTH_SHORT).show()
                binding.buttonSend.isEnabled = true // Reabilita o botão mesmo em caso de erro
            }
    }
}
