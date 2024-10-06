@file:Suppress("DEPRECATION")

package com.felicio.geobottle

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    // Variáveis de autenticação e cliente de login com Google
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // Código de requisição para o Google Sign-In
    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this) // Inicializa o Firebase
        setContentView(R.layout.activity_login)

        // Inicializa o FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Inicializa os componentes da UI
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val googleLoginButton = findViewById<Button>(R.id.googleLoginButton)
        val revokeAccessButton = findViewById<Button>(R.id.revokeAccessButton)

        // Configuração do Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Substitua com o ID correto do cliente OAuth2 no Firebase Console
            .requestEmail()
            .build()

        // Inicializa o Google Sign-In Client
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Configura o comportamento dos botões
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            Log.d("LoginActivity", "Email: $email, Senha: $password")

            login(email, password)
        }

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            register(email, password)
        }

        googleLoginButton.setOnClickListener {
            signInWithGoogle()
        }

        revokeAccessButton.setOnClickListener {
            revokeGoogleAccess()
        }
    }

    // Inicia o fluxo de login com Google
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // Processa o resultado do Google Sign-In
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("LoginActivity", "firebaseAuthWithGoogle: " + account?.id)
                firebaseAuthWithGoogle(account?.idToken!!)
            } catch (e: ApiException) {
                Log.w("LoginActivity", "Google sign in failed", e)
                Toast.makeText(this, "Falha no login com Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Autentica com Firebase usando o ID Token do Google
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login com Google bem-sucedido, inicialize currentUser e passe para a MainActivity
                    val currentUser = auth.currentUser
                    Log.d("LoginActivity", "Login com Google bem-sucedido: ${currentUser?.displayName}")

                    // Inicia a MainActivity passando as informações do usuário
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("user_name", currentUser?.displayName)
                        putExtra("user_email", currentUser?.email)
                        putExtra("user_uid", currentUser?.uid)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Log.w("LoginActivity", "Falha na autenticação com Google", task.exception)
                    Toast.makeText(this, "Falha na autenticação", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Revoga o acesso do Google e desloga o usuário
    private fun revokeGoogleAccess() {
        googleSignInClient.revokeAccess().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Desloga o usuário do Firebase e do Google
                auth.signOut()
                Toast.makeText(this, "Acesso ao Google revogado e usuário desconectado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Falha ao revogar o acesso ao Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun login(email: String, password: String) {
        if (email.isEmpty()) {
            Toast.makeText(this, "Por favor, insira um e-mail válido.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Por favor, insira uma senha.", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val currentUser = auth.currentUser
                    if (currentUser != null && currentUser.isEmailVerified) {
                        // O email foi verificado, permitir o login
                        Log.d("LoginActivity", "Login bem-sucedido: ${currentUser.displayName}")

                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("user_name", currentUser.displayName)
                            putExtra("user_email", currentUser.email)
                            putExtra("user_uid", currentUser.uid)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // O email ainda não foi verificado
                        Toast.makeText(this, "Por favor, verifique seu e-mail antes de fazer login.", Toast.LENGTH_LONG).show()
                        auth.signOut()  // Deslogar o usuário não verificado
                    }
                } else {
                    Toast.makeText(this, "Falha no login: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Realiza cadastro com email e senha e envia email de verificação
    private fun register(email: String, password: String) {
        if (email.isEmpty()) {
            Toast.makeText(this, "Por favor, insira um e-mail válido.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Por favor, insira uma senha.", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Enviar e-mail de verificação
                    val user = auth.currentUser
                    user?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                        if (verifyTask.isSuccessful) {
                            Toast.makeText(this, "Cadastro realizado com sucesso! Verifique seu e-mail para confirmar a conta.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Falha ao enviar e-mail de verificação.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Falha no cadastro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
