package com.natancarff.acsdev

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View // Importar View para controlar a visibilidade da ProgressBar
import android.webkit.JavascriptInterface // Importar para a interface JavaScript
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var myWebView: WebView
    private lateinit var progressBar: ProgressBar // Adicionado ProgressBar

    private val PERMISSION_REQUEST_CODE = 101 // Código para permissões iniciais do app
    private val FILE_CHOOSER_REQUEST_CODE = 102 // Código para startActivityForResult do seletor de arquivos (câmera)
    private val WEBRTC_PERMISSION_REQUEST_CODE = 103 // Novo código para permissões WebRTC

    // Para lidar com o resultado do seletor de arquivos do WebView
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null // Para armazenar o caminho da foto tirada pela câmera

    // Permissões que precisam ser solicitadas em tempo de execução
    private val DANGEROUS_PERMISSIONS = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS) // ADICIONADO: Se sua web app usar notificações push
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10, 11, 12
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else { // Android 9 e inferior
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray() // Converte para Array<String>


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myWebView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar) // Inicialização do ProgressBar

        val webSettings: WebSettings = myWebView.settings

        // Configurações de WebSettings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.mediaPlaybackRequiresUserGesture = false // Essencial para WebRTC e reprodução automática

        // Melhorar o cache e o modo de carregamento
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT // Permite usar cache quando disponível, mas recarrega se não


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Suporte a Dark Mode para API 29+
            webSettings.forceDark = WebSettings.FORCE_DARK_AUTO
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // Permitir mixed content se necessário (HTTPS com recursos HTTP)
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        // Adiciona um user agent customizado para identificar o aplicativo
        // webSettings.userAgentString = webSettings.userAgentString + " YourAppNameWebView/1.0" // Opcional

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // API 19
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Adiciona a interface JavaScript para comunicação bidirecional
        myWebView.addJavascriptInterface(JavaScriptInterface(this, progressBar), "Android")

        // Configurar WebViewClient para lidar com a navegação e erros
        myWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url != null) {
                    if (!url.startsWith("https://acsdevs.wlanfibra.com.br")) {
                        // Trata esquemas específicos (tel:, mailto:, sms:, geo:)
                        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("geo:")) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                Log.e("WebViewClient", "Não foi possível abrir app externo para: $url", e)
                                Toast.makeText(this@MainActivity, "Não foi possível abrir o link externo.", Toast.LENGTH_SHORT).show()
                                return true // Indica que a URL foi tratada, mesmo com erro
                            }
                        } else {
                            // Abre outras URLs externas no navegador padrão
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                Log.e("WebViewClient", "Não foi possível abrir URL externa: $url", e)
                                Toast.makeText(this@MainActivity, "Não foi possível abrir o link externo.", Toast.LENGTH_SHORT).show()
                                return true
                            }
                        }
                    }
                }
                return false // Deixa a WebView carregar a URL
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE // Mostrar barra de progresso geral
                progressBar.progress = 0 // Resetar progresso
                Log.d("WebViewDebug", "Página iniciou o carregamento: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE // Esconder barra de progresso geral
                Log.d("WebViewDebug", "Página terminou o carregamento: $url")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val errorMessage = "Erro de carregamento: ${error?.description} (Código: ${error?.errorCode})"
                    Log.e("WebViewError", errorMessage)
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    // Opcional: carregar uma página de erro local para erros de main frame
                    // view?.loadUrl("file:///android_asset/error.html")
                }
                super.onReceivedError(view, request, error)
            }

            @Deprecated("Deprecated in API 23")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                val errorMessage = "Erro de carregamento (legacy): $description (Código: $errorCode}"
                Log.e("WebViewError", errorMessage)
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    val httpError = "Erro HTTP: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase} para ${request.url}"
                    Log.e("WebViewHttpError", httpError)
                    Toast.makeText(this@MainActivity, httpError, Toast.LENGTH_LONG).show()
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        // Configurar WebChromeClient para lidar com alerts, console logs, e permissões da Web
        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d("WebViewConsole",
                        "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                    )
                }
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // Atualiza o progresso da barra de carregamento da página
                if (progressBar.visibility == View.VISIBLE) { // Só atualiza se a barra geral estiver visível
                    progressBar.progress = newProgress
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                // Verifica a permissão de localização nativa antes de conceder ao site
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback?.invoke(origin, true, false)
                    Log.d("WebViewChrome", "Permissão de geolocalização concedida para: $origin")
                } else {
                    // Se não tiver permissão nativa, solicita
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSION_REQUEST_CODE // Pode ser um código específico para localização se preferir
                    )
                    callback?.invoke(origin, false, false) // Nega temporariamente até o usuário responder
                    Log.w("WebViewChrome", "Permissão de geolocalização nativa solicitada para: $origin")
                }
            }

            // MODIFICADO: Para mostrar APENAS a opção da câmera e verificar permissão antes de iniciar a intent
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Garante que qualquer callback anterior seja limpo para evitar múltiplos retornos
                if (mFilePathCallback != null) {
                    mFilePathCallback?.onReceiveValue(null)
                    mFilePathCallback = null
                }
                mFilePathCallback = filePathCallback

                // Verifica a permissão da câmera antes de tentar iniciar a intent
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.CAMERA),
                        FILE_CHOOSER_REQUEST_CODE // Reutiliza o mesmo código para o callback em onActivityResult
                    )
                    // Nega temporariamente até que a permissão seja concedida ou negada nativamente
                    mFilePathCallback?.onReceiveValue(null)
                    mFilePathCallback = null
                    Toast.makeText(this@MainActivity, "Permissão de câmera necessária para tirar fotos.", Toast.LENGTH_LONG).show()
                    return false
                }

                // Se a permissão já foi concedida, prossegue com a criação do arquivo e intent
                var takePictureIntent: Intent? = null
                try {
                    val photoFile: File? = createImageFile()
                    if (photoFile != null) {
                        val photoURI: Uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "com.natancarff.acsdev.fileprovider",
                            photoFile
                        )
                        takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    }
                } catch (ex: IOException) {
                    Log.e("FileChooser", "Não foi possível criar arquivo de imagem para a câmera", ex)
                    Toast.makeText(this@MainActivity, "Erro ao preparar câmera.", Toast.LENGTH_SHORT).show()
                }

                // Se a intent da câmera foi criada com sucesso, inicia a atividade.
                // Caso contrário, informa ao WebView que nenhuma seleção foi feita.
                if (takePictureIntent != null) {
                    startActivityForResult(takePictureIntent, FILE_CHOOSER_REQUEST_CODE)
                } else {
                    mFilePathCallback?.onReceiveValue(null) // Indica ao WebView que nenhuma seleção foi feita
                    mFilePathCallback = null // Limpa o callback
                    return false // Retorna false se não foi possível iniciar a câmera
                }
                return true
            }

            // MODIFICADO: Para acesso em tempo real à câmera/microfone (WebRTC, getUserMedia)
            override fun onPermissionRequest(request: PermissionRequest?) {
                val resources = request?.resources
                val permissionsToGrant = mutableListOf<String>()
                val permissionsToRequestNatively = mutableListOf<String>()

                if (resources != null) {
                    for (resource in resources) {
                        when (resource) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    permissionsToGrant.add(resource)
                                    Log.d("WebViewChrome", "Permissão CAMERA já concedida para WebRTC.")
                                } else {
                                    permissionsToRequestNatively.add(Manifest.permission.CAMERA)
                                    Log.d("WebViewChrome", "Solicitando permissão nativa CAMERA para WebRTC.")
                                }
                            }
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    permissionsToGrant.add(resource)
                                    Log.d("WebViewChrome", "Permissão RECORD_AUDIO já concedida para WebRTC.")
                                } else {
                                    permissionsToRequestNatively.add(Manifest.permission.RECORD_AUDIO)
                                    Log.d("WebViewChrome", "Solicitando permissão nativa RECORD_AUDIO para WebRTC.")
                                }
                            }
                            // Adicione outros recursos se necessário, como PROJETION, MIDI, etc.
                            else -> {
                                Log.w("WebViewChrome", "Recurso WebRTC desconhecido ou não suportado: $resource")
                                // Por padrão, nega recursos não explicitamente suportados
                                request?.deny() // Nega recursos não explicitamente tratados
                            }
                        }
                    }
                }

                if (permissionsToRequestNatively.isNotEmpty()) {
                    // Armazena o request para uso posterior em onRequestPermissionsResult
                    if (request != null) {
                        sPendingPermissionRequest = request
                    }
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        permissionsToRequestNatively.toTypedArray(),
                        WEBRTC_PERMISSION_REQUEST_CODE
                    )
                } else if (permissionsToGrant.isNotEmpty()) {
                    request?.grant(permissionsToGrant.toTypedArray())
                } else {
                    request?.deny()
                    Toast.makeText(this@MainActivity, "Permissão de mídia negada para o site.", Toast.LENGTH_SHORT).show()
                    Log.w("WebViewChrome", "Permissões WebRTC negadas ou nenhum recurso solicitado.")
                }
            }
        }

        // Restaura o estado da WebView se houver um savedInstanceState
        savedInstanceState?.let {
            myWebView.restoreState(it)
        } ?: run {
            checkAndRequestPermissions() // Só carrega o conteúdo da WebView se não houver estado salvo
        }
    }

    // Variável estática para armazenar temporariamente o PermissionRequest do WebRTC
    // Isso é uma solução simplificada. Em apps complexos, use um ViewModel ou um mapa.
    companion object {
        private var sPendingPermissionRequest: PermissionRequest? = null
    }

    // Criar um arquivo de imagem temporário para a câmera
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // Usar Environment.DIRECTORY_PICTURES dentro do armazenamento privado do app
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            mCameraPhotoPath = absolutePath
            Log.d("FileChooser", "Caminho da foto da câmera: $mCameraPhotoPath")
        }
    }

    // Tratar o resultado das Intents (câmera)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            var results: Array<Uri>? = null

            if (resultCode == Activity.RESULT_OK) {
                // Resultado da câmera, se mCameraPhotoPath foi definido
                if (mCameraPhotoPath != null) {
                    val file = File(mCameraPhotoPath!!)
                    if (file.exists()) { // Verifica se o arquivo realmente foi criado
                        val uri: Uri = FileProvider.getUriForFile(
                            this,
                            "com.natancarff.acsdev.fileprovider",
                            file
                        )
                        results = arrayOf(uri)
                        Log.d("FileChooser", "Arquivo da câmera retornado: $uri")
                    } else {
                        Log.w("FileChooser", "Arquivo da câmera não encontrado em $mCameraPhotoPath")
                    }
                } else {
                    val dataString = data?.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                        Log.d("FileChooser", "Arquivo único selecionado (improvável com a mudança): $dataString")
                    } else {
                        // Lidar com múltiplas seleções (ClipData) se por algum motivo for retornado
                        data?.clipData?.let { clipData ->
                            val uriList = mutableListOf<Uri>()
                            for (i in 0 until clipData.itemCount) {
                                uriList.add(clipData.getItemAt(i).uri)
                            }
                            results = uriList.toTypedArray()
                            Log.d("FileChooser", "Múltiplos arquivos selecionados (improvável): ${uriList.size} itens")
                        }
                    }
                }
            } else {
                Log.d("FileChooser", "Seleção de arquivo cancelada (resultCode: $resultCode)")
            }

            mFilePathCallback?.onReceiveValue(results)
            mFilePathCallback = null // Limpar o callback
            mCameraPhotoPath = null // Limpar o caminho da foto
        }
    }

    // Função para verificar se há conexão com a internet
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    // Função para verificar e solicitar permissões perigosas na inicialização
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in DANGEROUS_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d("Permissões", "Todas as permissões essenciais já foram concedidas na inicialização.")
            loadWebViewContent()
        }
    }

    // Callback para o resultado da solicitação de permissões nativas
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> { // Permissões iniciais do app
                var allPermissionsGranted = true
                val deniedPermissions = mutableListOf<String>()

                for (i in permissions.indices) {
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        allPermissionsGranted = false
                        deniedPermissions.add(permissions[i])
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                            Toast.makeText(this, "Permissão ${permissions[i]} negada. Algumas funcionalidades podem não funcionar.", Toast.LENGTH_LONG).show()
                        } else {
                            // O usuário marcou "Não perguntar novamente"
                            Toast.makeText(this, "Permissão ${permissions[i]} negada permanentemente. Por favor, habilite nas configurações do aplicativo.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                if (allPermissionsGranted) {
                    Toast.makeText(this, "Todas as permissões iniciais concedidas!", Toast.LENGTH_SHORT).show()
                    Log.d("Permissões", "Todas as permissões iniciais concedidas após a solicitação.")
                } else {
                    Toast.makeText(this, "Algumas permissões foram negadas. O aplicativo pode não funcionar plenamente.", Toast.LENGTH_LONG).show()
                    Log.w("Permissões", "Permissões iniciais negadas: ${deniedPermissions.joinToString()}")
                }
                loadWebViewContent() // Tenta carregar mesmo que algumas tenham sido negadas
            }
            WEBRTC_PERMISSION_REQUEST_CODE -> { // Permissões solicitadas via WebRTC
                val grantedResources = mutableListOf<String>()
                val deniedResources = mutableListOf<String>()

                // Itera sobre as permissões que foram solicitadas e seus resultados
                for (i in permissions.indices) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        when (permissions[i]) {
                            Manifest.permission.CAMERA -> grantedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                            Manifest.permission.RECORD_AUDIO -> grantedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        }
                    } else {
                        when (permissions[i]) {
                            Manifest.permission.CAMERA -> deniedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                            Manifest.permission.RECORD_AUDIO -> deniedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        }
                        Toast.makeText(this, "Permissão para ${permissions[i]} negada para WebRTC.", Toast.LENGTH_SHORT).show()
                    }
                }

                sPendingPermissionRequest?.let { request ->
                    if (grantedResources.isNotEmpty()) {
                        request.grant(grantedResources.toTypedArray())
                        Log.d("WebViewChrome", "Permissões WebRTC concedidas após solicitação nativa: ${grantedResources.joinToString()}")
                    } else {
                        request.deny()
                        Log.w("WebViewChrome", "Permissões WebRTC negadas após solicitação nativa.")
                    }
                    sPendingPermissionRequest = null // Limpa o request pendente
                }
            }
            FILE_CHOOSER_REQUEST_CODE -> { // Permissão da câmera para onShowFileChooser
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("FileChooser", "Permissão CAMERA concedida para onShowFileChooser.")
                    // Se a permissão foi concedida, tente iniciar a câmera novamente
                    // Esta é uma simplificação; idealmente, você recriaria e iniciaria a intent aqui
                    // ou chamaria a lógica onShowFileChooser de novo.
                    // Por agora, vamos apenas recarregar a página para o usuário tentar novamente.
                    // Ou, uma solução melhor seria armazenar os parâmetros e chamá-los novamente.
                    Toast.makeText(this, "Permissão de câmera concedida. Tente novamente o upload.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("FileChooser", "Permissão CAMERA negada para onShowFileChooser.")
                    Toast.makeText(this, "Permissão de câmera negada. Não foi possível acessar a câmera.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Função para encapsular a lógica de carregamento do WebView
    private fun loadWebViewContent() {
        if (isNetworkAvailable()) {
            myWebView.loadUrl("https://acsdevs.wlanfibra.com.br/#/login")
        } else {
            // Alterado para carregar o arquivo HTML local
            myWebView.loadUrl("file:///android_asset/no_internet.html")
            Log.e("WebViewError", "Sem conexão com a Internet. Carregando página de erro local.")
        }
    }

    override fun onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Classe da interface JavaScript para comunicação com o WebView
    class JavaScriptInterface(private val context: Context, private val progressBar: ProgressBar) {

        @JavascriptInterface
        @Suppress("unused")
        fun setUploadProgress(progress: Int) {
            // Certifique-se de que a atualização da UI ocorra na thread principal
            (context as Activity).runOnUiThread {
                progressBar.progress = progress
                if (progress < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
                Log.d("UploadProgress", "Progresso do upload: $progress%")
            }
        }

        @JavascriptInterface
        @Suppress("unused")
        fun startUploadProgress() {
            (context as Activity).runOnUiThread {
                progressBar.progress = 0
                progressBar.visibility = View.VISIBLE
                Log.d("UploadProgress", "Iniciando upload...")
            }
        }

        @JavascriptInterface
        @Suppress("unused")
        fun finishUploadProgress() {
            (context as Activity).runOnUiThread {
                progressBar.progress = 100
                progressBar.visibility = View.GONE
                Log.d("UploadProgress", "Upload finalizado.")
            }
        }
    }
}