package com.felicio.geobottle

import android.Manifest
import android.os.CountDownTimer
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.android.billingclient.api.*
import android.content.Intent
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.util.Log  // Para o Log





class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val addedMarkers = mutableSetOf<String>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private lateinit var addMessageButton: Button
    private lateinit var restrictedBounds: LatLngBounds
    private lateinit var billingClient: BillingClient
    private lateinit var unlockAreaButton: Button
    private var isAreaUnlocked = false // Variável para controlar se o mapa está desbloqueado



    // Adicione essas variáveis na classe MainActivity
    private var unlockTimer: CountDownTimer? = null
    private val unlockDuration = 2 * 60 * 1000L  // 5 minutos em milissegundos

    companion object {
        private const val LOCATION_REQUEST_CODE = 1
        private const val EXPIRATION_HOURS = 24 // Tempo de expiração das mensagens
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa o FirebaseAuth e o usuário atual
        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "Usuário não autenticado. Por favor, faça login novamente.", Toast.LENGTH_SHORT).show()
            finish() // Finaliza a Activity se o usuário não está autenticado
            return
        }

        // Inicializa o Firestore ANTES de qualquer uso
        firestore = FirebaseFirestore.getInstance()

        // Verifica o acesso ao desbloqueio no início, após garantir que firestore e currentUser estão inicializados
        unlockFreeMapMovementIfUserHasAccess()

        // Inicializa o FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Chama a função para obter a última localização conhecida
        getLastKnownLocation()

        // Inicializa o botão de adicionar mensagem com verificação de acesso
        addMessageButton = findViewById(R.id.addMessageButton)
        addMessageButton.setOnClickListener {
            if (isAreaUnlocked) {
                // Se o mapa está desbloqueado, o usuário pode adicionar mensagens em qualquer lugar
                mMap.setOnMapClickListener { latLng ->
                    showAddMessageDialog(latLng)
                }
                Toast.makeText(this, "Você pode adicionar mensagens em qualquer lugar do mapa.", Toast.LENGTH_SHORT).show()
            } else {
                // Caso contrário, adicione mensagem apenas perto da localização atual
                addMessageAtCurrentLocation()
            }
        }

        // Inicializa o fragmento do mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_container) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.map_container, it)
                    .commit()
            }

        // Configura o mapa de forma assíncrona
        mapFragment.getMapAsync(this)

        // Inicializa o BillingClient
        initializeBillingClient()

        // Inicializa o botão de desbloquear área com verificação de acesso
        unlockAreaButton = findViewById(R.id.unlockAreaButton)
        unlockAreaButton.setOnClickListener {
            checkUserHasUnlockedFeature { hasAccess ->
                if (hasAccess) {
                    // Se o usuário já tem acesso, desbloqueia diretamente
                    unlockFreeMapMovement()
                } else {
                    // Se não, inicia o pagamento para desbloquear a área
                    initiatePaymentForUnlockingArea()
                }
            }
        }

        // Inicializa e inicia o temporizador para limitar o tempo de uso
        unlockTimer = object : CountDownTimer(unlockDuration, 1000) {  // Altere de UNLOCK_DURATION para unlockDuration
            override fun onTick(millisUntilFinished: Long) {
                // Código executado a cada segundo (opcional)
            }

            override fun onFinish() {
                // Restaurar comportamento normal após 5 minutos
                restoreNormalMapBehavior()
                Toast.makeText(this@MainActivity, "O tempo de movimentação livre e leitura de mensagens terminou.", Toast.LENGTH_SHORT).show()
            }
        }.start()

        // NÃO chame enableUserLocation() aqui. Isso será feito no onMapReady().
    }





    // Função para verificar se o usuário tem acesso ao desbloqueio no Firestore
    private fun checkUserHasUnlockedFeature(onResult: (Boolean) -> Unit) {
        // Adiciona log para identificar a verificação de acesso
        Log.d("checkUserHasUnlockedFeature", "Verificando se o usuário ${currentUser.uid} possui acesso ao recurso.")

        // Acessa a coleção "unlockedUsers" no Firestore e verifica se o usuário tem o recurso desbloqueado
        firestore.collection("unlockedUsers").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Verifica se o campo "mapUnlocked" está presente e se é verdadeiro
                    val isUnlocked = document.getBoolean("mapUnlocked") ?: false
                    Log.d("checkUserHasUnlockedFeature", "Acesso encontrado: $isUnlocked para o usuário ${currentUser.uid}.")
                    onResult(isUnlocked)
                } else {
                    Log.d("checkUserHasUnlockedFeature", "Usuário ${currentUser.uid} não possui acesso desbloqueado.")
                    onResult(false)
                }
            }
            .addOnFailureListener { exception ->
                // Log de erro ao acessar o Firestore
                Log.e("checkUserHasUnlockedFeature", "Erro ao verificar o acesso do usuário ${currentUser.uid}: ${exception.message}")
                onResult(false)
            }
    }


    // Função para desbloquear movimentação livre e leitura de mensagens se o usuário tiver acesso
    private fun unlockFreeMapMovementIfUserHasAccess() {
        checkUserHasUnlockedFeature { hasAccess ->
            if (hasAccess) {
                unlockFreeMapMovement()
            } else {
                Toast.makeText(this, "Você não tem acesso ao desbloqueio do mapa e leitura de mensagens.", Toast.LENGTH_SHORT).show()
            }
        }
    }





    private fun getLastKnownLocation() {
        // Verifique se a permissão foi concedida
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            // Obtenha a última localização conhecida
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // Lógica para usar a localização
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Erro ao obter a localização", Toast.LENGTH_SHORT).show()
                }

        } else {
            // Se a permissão não foi concedida, solicite a permissão ao usuário
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }


    // Inicialização do BillingClient
    private fun initializeBillingClient() {
        // Cria uma instância do BillingClient com o contexto atual
        billingClient = BillingClient.newBuilder(this)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    // Se a resposta for OK, processa todas as compras retornadas
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                } else {
                    Log.e("BillingClient", "Erro no listener de BillingClient: ${billingResult.debugMessage}")
                }
            }
            .enablePendingPurchases()
            .build()

        // Inicia a conexão com o BillingClient
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingClient", "BillingClient conectado com sucesso.")
                } else {
                    Log.e("BillingClient", "Erro ao conectar BillingClient: ${billingResult.responseCode}")
                    Toast.makeText(this@MainActivity, "Erro ao conectar BillingClient", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingClient", "Conexão perdida. Tentando reconectar...")
                reconnectBillingClient() // Chama a função de reconexão ao perder a conexão
            }
        })
    }

    // Função para gerenciar a reconexão do BillingClient
    private fun reconnectBillingClient() {
        // Verifica se o BillingClient já está pronto antes de tentar reconectar
        if (::billingClient.isInitialized && !billingClient.isReady) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("BillingClient", "Reconectado ao BillingClient com sucesso.")
                    } else {
                        Log.e("BillingClient", "Erro ao reconectar BillingClient: ${billingResult.responseCode}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.e("BillingClient", "Falha na reconexão. Tentando novamente...")
                    reconnectBillingClient()  // Tenta reconectar novamente
                }
            })
        } else {
            Log.d("BillingClient", "BillingClient já está pronto ou não inicializado.")
        }
    }



    // Inicia o pagamento para desbloquear a área
    private fun initiatePaymentForUnlockingArea() {
        checkUserHasUnlockedFeature { hasAccess ->
            if (hasAccess) {
                unlockFreeMapMovement() // Chama a função para desbloquear o movimento
            } else {
                // Lista com o ID do produto no Google Play Console
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("unlock_map_movement") // Substitua pelo ID correto do produto
                        .setProductType(BillingClient.ProductType.INAPP) // Compra única no aplicativo
                        .build()
                )

                // Configura os parâmetros de consulta de detalhes do produto
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                // Consulta os detalhes do produto
                billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                        val productDetails = productDetailsList.firstOrNull()
                        productDetails?.let {
                            // Configura os parâmetros do fluxo de pagamento
                            val flowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(
                                    listOf(
                                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                            .setProductDetails(it)
                                            .build()
                                    )
                                )
                                .build()

                            // Inicia o fluxo de pagamento
                            billingClient.launchBillingFlow(this@MainActivity, flowParams)
                        } ?: run {
                            // Produto não encontrado
                            Toast.makeText(this, "Produto não encontrado.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Tratamento de erro ao consultar os detalhes do produto
                        Toast.makeText(this, "Erro ao consultar o produto.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }




    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verifica se a compra já foi reconhecida para evitar duplicação
            if (!purchase.isAcknowledged) {
                acknowledgeAndProcessPurchase(purchase)
            } else {
                // Caso já esteja reconhecida, consome o produto diretamente
                consumeProduct(purchase.purchaseToken)
            }
        } else {
            Log.w("handlePurchase", "Compra não completada ou em estado inválido: ${purchase.purchaseState}")
        }
    }

    // Função separada para reconhecer e processar a compra
    private fun acknowledgeAndProcessPurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("handlePurchase", "Compra reconhecida com sucesso: ${purchase.products}")

                // Realiza a ação apropriada para cada produto comprado após reconhecimento
                processProduct(purchase)
            } else {
                Log.e("handlePurchase", "Erro ao reconhecer a compra: ${billingResult.responseCode}")
            }
        }
    }

    // Função para processar o produto comprado
    private fun processProduct(purchase: Purchase) {
        when (purchase.products.firstOrNull()) {
            "unlock_map_movement" -> {
                unlockFreeMapMovement() // Desbloqueia o movimento no mapa
                consumeProduct(purchase.purchaseToken) // Consome o produto após desbloqueio
            }
            "destroy_message" -> {
                destroyMessage() // Destrói uma mensagem
                consumeProduct(purchase.purchaseToken) // Consome o produto após destruir a mensagem
            }
            else -> Log.w("handlePurchase", "Produto desconhecido: ${purchase.products}")
        }
    }


    // Função separada para consumir um produto, facilitando a organização do código
    private fun consumeProduct(purchaseToken: String) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, token ->  // Alterado de purchaseToken para token
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("handlePurchase", "Produto consumido com sucesso. Token: $token")
                // Aqui você pode adicionar ações adicionais, se necessário
            } else {
                Log.e("handlePurchase", "Erro ao consumir produto: ${billingResult.responseCode}")
            }
        }
    }






    // Função para desbloquear o movimento no mapa e permitir leitura de mensagens
    private fun unlockFreeMapMovement() {
        isAreaUnlocked = true
        Toast.makeText(this, "Movimentação livre e leitura de mensagens desbloqueadas! Você pode adicionar e ler mensagens em qualquer lugar do mapa por 5 minutos.", Toast.LENGTH_SHORT).show()

        // Remove restrições do mapa para permitir a livre navegação
        mMap.setLatLngBoundsForCameraTarget(null)

        // Permitir ao usuário adicionar mensagens em qualquer lugar do mapa enquanto desbloqueado
        mMap.setOnMapClickListener { latLng ->
            if (isAreaUnlocked) {
                showAddMessageDialog(latLng)  // Exibe o diálogo para adicionar uma nova mensagem
            }
        }

        // Permitir clicar em qualquer marcador para ver a mensagem sem restrição de distância
        mMap.setOnMarkerClickListener { marker ->
            if (isAreaUnlocked) {
                showMarkerDialog(marker)  // Exibir o diálogo da mensagem no marcador
            }
            true  // Retorna true para indicar que o clique foi tratado
        }

        // Inicia um temporizador para garantir que o desbloqueio expire após 5 minutos
        unlockTimer = object : CountDownTimer(unlockDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Código que pode ser executado a cada segundo (opcional)
            }

            override fun onFinish() {
                // Restaurar o comportamento normal do mapa após o tempo expirar
                restoreNormalMapBehavior()
                Toast.makeText(this@MainActivity, "O tempo de movimentação livre e leitura de mensagens terminou.", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }


    // Função separada para exibir o diálogo do marcador ao clicar
    private fun showMarkerDialog(marker: Marker) {
        // Exibir o conteúdo da mensagem em um diálogo ao clicar no marcador
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Mensagem da Garrafinha")

        // Mostra o conteúdo da mensagem no corpo do diálogo
        builder.setMessage(marker.snippet)  // A mensagem está no snippet

        // Botão para iniciar a conversa
        builder.setPositiveButton("Conversar") { dialog, _ ->
            // Abre o ChatActivity passando o ID da garrafinha
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("garrafinhaId", marker.snippet)  // Supondo que o snippet contenha o ID
            startActivity(intent)
            dialog.dismiss()
        }

        // Botão para destruir a mensagem (adiciona a funcionalidade de pagamento)
        builder.setNeutralButton("Destruir (Pago)") { dialog, _ ->
            initiatePaymentForDestruction(marker)  // Função que inicia o pagamento e destruição diretamente
            dialog.dismiss()
        }

        // Mostra o diálogo
        val dialog = builder.create()
        dialog.show()
    }



    // Função para restaurar o comportamento normal do mapa após o tempo de uso liberado
    private fun restoreNormalMapBehavior() {
        Toast.makeText(this, "Movimentação e leitura de mensagens bloqueadas novamente.", Toast.LENGTH_SHORT).show()
        isAreaUnlocked = false

        // Cancelar o temporizador, se ele estiver ativo
        unlockTimer?.cancel()

        Log.d("restoreNormalMapBehavior", "Comportamento normal do mapa sendo restaurado...")

        // Restaurar a posição do usuário e limitar novamente o movimento do mapa
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    restrictCameraToArea(currentLatLng)  // Reaplica a restrição de movimento ao redor do usuário
                    Log.d("restoreNormalMapBehavior", "Movimento do mapa restrito à área ao redor do usuário.")
                }
            }
        }

        // Remover listeners de clique no mapa
        mMap.setOnMapClickListener(null)
        Log.d("restoreNormalMapBehavior", "Listener de clique no mapa removido.")

        // Definir o comportamento padrão ao clicar nos marcadores
        mMap.setOnMarkerClickListener { marker ->
            // Retornar a lógica para leitura de mensagens apenas dentro de 50 metros
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val userLocation = Location("").apply {
                            latitude = location.latitude
                            longitude = location.longitude
                        }
                        val markerLocation = Location("").apply {
                            latitude = marker.position.latitude
                            longitude = marker.position.longitude
                        }
                        val distance = userLocation.distanceTo(markerLocation)

                        if (distance > 50) {
                            val toast = Toast.makeText(
                                this,
                                "Você está a ${"%.2f".format(distance)} metros da mensagem. Aproximar-se até 50 metros para visualizar.",
                                Toast.LENGTH_LONG
                            )
                            toast.setGravity(Gravity.CENTER, 0, 0)
                            toast.show()
                        } else {
                            // Exibir o conteúdo da mensagem em um diálogo ao clicar na garrafinha
                            val builder = AlertDialog.Builder(this)
                            builder.setTitle("Mensagem da Garrafinha")
                            builder.setMessage(marker.snippet)  // A mensagem está no snippet

                            // Botão para iniciar a conversa
                            builder.setPositiveButton("Conversar") { dialog, _ ->
                                // Abre o ChatActivity passando o ID da garrafinha
                                val intent = Intent(this, ChatActivity::class.java)
                                intent.putExtra("garrafinhaId", marker.snippet)  // Supondo que o snippet contenha o ID
                                startActivity(intent)
                                dialog.dismiss()
                            }
                            val dialog = builder.create()
                            dialog.show()
                        }
                    } else {
                        Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Erro ao obter a localização", Toast.LENGTH_SHORT).show()
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_REQUEST_CODE
                )
            }

            true  // Retorna true para indicar que o clique foi tratado
        }

        Log.d("restoreNormalMapBehavior", "Comportamento padrão ao clicar em marcadores restaurado.")
    }




    // Função para iniciar o fluxo de pagamento e permitir a destruição da mensagem
    private fun initiatePaymentForDestruction(marker: Marker) {
        initiateBillingFlow(marker)
    }

    // Função para iniciar o fluxo de pagamento e permitir o consumo do produto após a compra
    private fun initiateBillingFlow(marker: Marker? = null, productId: String = "destroy_message") {
        // Cria a lista com o produto que será comprado, baseado no ID fornecido
        val productDetailsParamsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)  // ID do produto a ser comprado
                .setProductType(BillingClient.ProductType.INAPP)  // Tipo de compra no aplicativo
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productDetailsParamsList)
            .build()

        // Consulta os detalhes do produto no Google Play Console
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList.first()

                // Configuração dos parâmetros do fluxo de pagamento
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                // Inicia o fluxo de pagamento
                val flowResult = billingClient.launchBillingFlow(this, billingFlowParams)

                // Verifica se o fluxo de pagamento foi iniciado com sucesso
                if (flowResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.e("BillingClient", "Erro no fluxo de pagamento: ${flowResult.responseCode}")
                    Toast.makeText(this, "Erro ao iniciar o pagamento.", Toast.LENGTH_SHORT).show()
                } else {
                    // Se a compra for para destruir mensagem e um marker for fornecido, executa a destruição
                    if (productId == "destroy_message" && marker != null) {
                        destroyMessage(marker)
                        Log.d("BillingClient", "Iniciando fluxo de pagamento para destruir a mensagem do marcador.")
                    } else {
                        Log.d("BillingClient", "Iniciando fluxo de pagamento para o produto: $productId.")
                    }
                }
            } else {
                Log.e("BillingClient", "Erro ao consultar produto: ${billingResult.responseCode}")
                Toast.makeText(this, "Erro ao iniciar o pagamento.", Toast.LENGTH_SHORT).show()
            }
        }
    }







    // Função para destruir a mensagem de garrafinha no Firestore e remover o marcador do mapa
    private fun destroyMessage(marker: Marker) {
        val messageId = marker.snippet // Verifica se o snippet contém o ID da mensagem

        if (!messageId.isNullOrBlank()) {
            firestore.collection("garrafinhas").document(messageId)
                .delete()
                .addOnSuccessListener {
                    // Remove o marcador do mapa e da lista de marcadores adicionados
                    marker.remove()
                    addedMarkers.remove("${marker.position.latitude},${marker.position.longitude}")
                    Toast.makeText(this, "Mensagem de garrafinha destruída!", Toast.LENGTH_SHORT).show()
                    Log.d("destroyMessage", "Mensagem destruída com sucesso: $messageId")
                }
                .addOnFailureListener { exception ->
                    Log.e("destroyMessage", "Erro ao destruir a mensagem de garrafinha: ${exception.message}")
                    Toast.makeText(this, "Erro ao destruir a mensagem de garrafinha", Toast.LENGTH_SHORT).show()
                }
        } else {
            Log.e("destroyMessage", "ID da mensagem não encontrado no snippet do marcador.")
            Toast.makeText(this, "ID da mensagem não encontrado. Não foi possível destruir a mensagem.", Toast.LENGTH_SHORT).show()
        }
    }









    // Função para adicionar uma mensagem na localização atual
    private fun addMessageAtCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        // Chama o diálogo para adicionar a mensagem
                        showAddMessageDialog(currentLatLng)
                    } else {
                        Toast.makeText(
                            this,
                            "Não foi possível obter a localização atual. Tente novamente.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Erro ao obter a localização: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            // Solicita permissão de localização se ainda não foi concedida
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }


    // Função para exibir o diálogo de adicionar mensagem
    private fun showAddMessageDialog(latLng: LatLng) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_add_message, findViewById(android.R.id.content), false)
        val editTextMessage = dialogLayout.findViewById<EditText>(R.id.editTextMessage)
        val editTextRecipient = dialogLayout.findViewById<EditText>(R.id.editTextRecipient)

        builder.apply {
            setTitle("Adicionar Mensagem")
            setView(dialogLayout)
            setPositiveButton("OK") { _, _ ->
                val message = editTextMessage.text.toString()
                val recipient = editTextRecipient.text.toString().ifEmpty { null }

                if (message.isNotEmpty()) {
                    // Chama a função que salva no Firebase e adiciona o marcador ao mapa após o sucesso
                    saveMessageToFirebase(latLng, message, recipient)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Mensagem não pode ser vazia",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
        }
        builder.show()
    }




    // Função para adicionar o marcador da garrafinha ao mapa
    private fun addMarkerToMap(latLng: LatLng, message: String, senderEmail: String) {
        // Usa a latitude e longitude como chave para evitar duplicação
        val markerId = "${latLng.latitude},${latLng.longitude}"

        // Verifica se o marcador já foi adicionado para evitar duplicações
        if (addedMarkers.contains(markerId)) {
            Log.d("addMarkerToMap", "Marcador já existente para a posição: $markerId. Ignorando duplicação.")
            return
        }

        // Adiciona o marcador ao mapa e ao conjunto de marcadores
        addedMarkers.add(markerId)
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Mensagem de garrafinha de $senderEmail")  // Título do marcador
                .snippet(message)  // Mensagem de texto no snippet
                .icon(getBottleIcon())  // Aplica o ícone da garrafinha
        )

        // Adiciona um listener ao clique no marcador para exibir a mensagem
        marker?.tag = markerId  // Associa o ID do marcador como uma tag
        Log.d("addMarkerToMap", "Marcador adicionado: $markerId com a mensagem: $message.")
    }




    // Função para salvar a mensagem no Firebase e adicionar o marcador ao mapa após o sucesso
    private fun saveMessageToFirebase(latLng: LatLng, message: String, recipient: String?) {
        val senderEmail = currentUser.email ?: "Anônimo"
        val adjustedLatLng = getAdjustedLatLng(latLng) // Adiciona um pequeno deslocamento para evitar sobreposição exata

        // Cria um identificador único para a mensagem no Firestore
        val messageId = firestore.collection("garrafinhas").document().id

        val msg = hashMapOf(
            "messageId" to messageId, // Adiciona o ID da mensagem no documento
            "text" to message,
            "latitude" to adjustedLatLng.latitude,  // Usa a latitude ajustada
            "longitude" to adjustedLatLng.longitude, // Usa a longitude ajustada
            "sender" to senderEmail,
            "recipient" to recipient,
            "timestamp" to Timestamp.now()
        )

        // Salva a mensagem no Firestore na coleção "garrafinhas"
        firestore.collection("garrafinhas").document(messageId)
            .set(msg)
            .addOnSuccessListener {
                // Adiciona o marcador ao mapa após o sucesso no salvamento
                addMarkerToMap(adjustedLatLng, message, senderEmail)
                Log.d("saveMessageToFirebase", "Mensagem de garrafinha adicionada com sucesso: $messageId")
                Toast.makeText(this, "Mensagem de garrafinha adicionada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e("saveMessageToFirebase", "Erro ao adicionar mensagem de garrafinha: ${exception.message}")
                Toast.makeText(this, "Erro ao adicionar mensagem de garrafinha", Toast.LENGTH_SHORT).show()
            }
    }






    // Função para adicionar um deslocamento aleatório à posição para evitar sobreposição
    private fun getAdjustedLatLng(latLng: LatLng): LatLng {
        val randomOffset = 0.00005 // Um pequeno deslocamento em lat/lng
        val newLat = latLng.latitude + (Math.random() * randomOffset - randomOffset / 2)
        val newLng = latLng.longitude + (Math.random() * randomOffset - randomOffset / 2)
        return LatLng(newLat, newLng)
    }



    // Função para restringir a câmera em torno da área do usuário
    private fun restrictCameraToArea(center: LatLng) {
        val latLngBounds = LatLngBounds.Builder()
        val latOffset = 0.0018  // Ajuste esses valores para o tamanho da área desejada
        val lngOffset = 0.0018

        // Define os limites com base na localização do usuário
        latLngBounds.include(LatLng(center.latitude - latOffset, center.longitude - lngOffset))
        latLngBounds.include(LatLng(center.latitude + latOffset, center.longitude + lngOffset))

        restrictedBounds = latLngBounds.build()
        mMap.setLatLngBoundsForCameraTarget(restrictedBounds)
    }


    // Função para carregar mensagens de garrafinhas do Firestore e adicionar ao mapa
    private fun loadMessages() {
        firestore.collection("garrafinhas")
            .get()
            .addOnSuccessListener { result ->
                val currentTime = Timestamp.now()
                Log.d("loadMessages", "Carregando mensagens de garrafinhas...")

                for (document in result) {
                    val timestamp = document.getTimestamp("timestamp")
                    val messageAgeInMillis = timestamp?.toDate()?.time?.let { currentTime.toDate().time - it }
                    val messageAgeInHours = messageAgeInMillis?.div(1000 * 60 * 60)

                    // Verifica se a mensagem não expirou
                    if (messageAgeInHours != null && messageAgeInHours > EXPIRATION_HOURS) {
                        document.reference.delete()
                            .addOnSuccessListener {
                                Log.d("loadMessages", "Mensagem expirada removida: ${document.id}")
                            }
                            .addOnFailureListener { exception ->
                                Log.e("loadMessages", "Erro ao remover mensagem expirada: ${exception.message}")
                            }
                    } else {
                        val lat = document.getDouble("latitude")
                        val lng = document.getDouble("longitude")
                        val text = document.getString("text")
                        val recipient = document.getString("recipient")
                        val sender = document.getString("sender")

                        // Exibe apenas as mensagens que são visíveis para o usuário atual
                        if (lat != null && lng != null && (recipient == null || recipient == currentUser.email)) {
                            val location = LatLng(lat, lng)
                            addMarkerToMap(location, text ?: "", sender ?: "Anônimo")
                            Log.d("loadMessages", "Mensagem adicionada ao mapa: ${document.id} - $text")
                        }
                    }
                }

                Log.d("loadMessages", "Mensagens de garrafinhas carregadas com sucesso.")
            }
            .addOnFailureListener { exception ->
                Log.e("loadMessages", "Erro ao carregar mensagens de garrafinhas: ${exception.message}")
                Toast.makeText(this, "Erro ao carregar mensagens de garrafinhas", Toast.LENGTH_SHORT).show()
            }
    }






    // Remove os parâmetros width e height
    private fun getBottleIcon(): BitmapDescriptor {
        return resizeIcon(R.drawable.garrafinha)
    }

    // Atualiza a função resizeIcon sem os parâmetros
    private fun resizeIcon(resourceId: Int): BitmapDescriptor {
        val imageBitmap = BitmapFactory.decodeResource(resources, resourceId)
        val resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, 80, 120, false)
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap)
    }



    // Função para gerenciar o ciclo de vida do BillingClient
    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }


    // Função onMapReady
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Configura o adaptador de InfoWindow customizado, se houver
        mMap.setInfoWindowAdapter(InfoWindowAdapter(layoutInflater))

        // Verifica e habilita a localização do usuário se a permissão foi concedida
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()

            // Obtenha a última localização conhecida e trave o mapa em torno dessa área
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    restrictCameraToArea(currentLatLng)  // Chama a função para restringir a área ao redor do usuário

                    // Centraliza a câmera na localização do usuário com um zoom adequado
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }

        // Carrega as mensagens salvas do Firestore no mapa
        loadMessages()

        // Define o comportamento ao clicar nos marcadores
        mMap.setOnMarkerClickListener { marker ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val userLocation = Location("").apply {
                            latitude = location.latitude
                            longitude = location.longitude
                        }
                        val markerLocation = Location("").apply {
                            latitude = marker.position.latitude
                            longitude = marker.position.longitude
                        }
                        val distance = userLocation.distanceTo(markerLocation)

                        if (distance > 50) {
                            val toast = Toast.makeText(
                                this,
                                "Você está a ${"%.2f".format(distance)} metros da mensagem. Aproximar-se até 50 metros para visualizar.",
                                Toast.LENGTH_LONG
                            )
                            toast.setGravity(Gravity.CENTER, 0, 0)
                            toast.show()
                        } else {
                            // Verifica se o marcador tem uma mensagem associada no snippet
                            val messageText = marker.snippet  // O snippet deve conter a mensagem da garrafinha

                            if (messageText.isNullOrBlank()) {
                                Log.d("MainActivity", "Erro: Mensagem da garrafinha não encontrada no marcador.")
                                Toast.makeText(this, "Mensagem inválida. Não é possível abrir o chat.", Toast.LENGTH_SHORT).show()
                            } else {
                                // Exibir o conteúdo da mensagem em um diálogo ao clicar na garrafinha
                                val builder = AlertDialog.Builder(this)
                                builder.setTitle("Mensagem da Garrafinha")

                                // Mostra o conteúdo da mensagem no corpo do diálogo
                                builder.setMessage(marker.snippet)  // A mensagem está no snippet

                                // Botão para iniciar a conversa
                                builder.setPositiveButton("Conversar") { dialog, _ ->
                                    // Abre o ChatActivity passando o ID da garrafinha
                                    val intent = Intent(this, ChatActivity::class.java)
                                    intent.putExtra("garrafinhaId", marker.snippet)  // Supondo que o snippet contenha o ID
                                    startActivity(intent)
                                    dialog.dismiss()
                                }

                                // Botão para destruir a mensagem (adiciona a funcionalidade de pagamento)
                                builder.setNeutralButton("Destruir (Pago)") { dialog, _ ->
                                    initiatePaymentForDestruction(marker)  // Função que inicia o pagamento e destruição diretamente
                                    dialog.dismiss()
                                }
                                // Mostra o diálogo
                                val dialog = builder.create()
                                dialog.show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Erro ao obter a localização", Toast.LENGTH_SHORT).show()
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_REQUEST_CODE
                )
            }

            true  // Retorna true para indicar que o clique foi tratado
        }
    }





    // Habilita a localização do usuário no mapa
    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true  // Ativa a localização no mapa
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }





    // Lida com a resposta da solicitação de permissão
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()  // Habilita a localização do usuário após a permissão
        }
    }




    // Função para destruir a mensagem no Firestore após o pagamento
    private fun destroyMessage() {
        firestore.collection("messages")
            .whereEqualTo("user", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.delete() // Exclui a mensagem
                }
                Toast.makeText(this, "Mensagem destruída!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao destruir a mensagem.", Toast.LENGTH_SHORT).show()
            }
    }
}


