package com.megical.easyaccess.example.ui.main

import androidx.lifecycle.*
import com.megical.easyaccess.example.ClientData
import com.megical.easyaccess.playground.OpenIdClientDataResponse
import com.megical.easyaccess.playground.PlaygroundRestApi
import com.megical.easyaccess.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import com.megical.easyaccess.sdk.Callback as MegicalCallback

const val REDIRECT_URL = "com.megical.ea.example:/oauth-callback"

enum class ViewState {
    RegisterClient,
    Loading,
    Operate,
    EasyAccess,
    AuthResult,
    SignResult
}

class ExampleViewModel : ViewModel() {

    private val playgroundRestApi = PlaygroundRestApi()
    private val megicalAuthApi = MegicalAuthApi()
    private val megicalSignApi = MegicalSignApi()

    private val viewState: MutableLiveData<ViewState> by lazy {
        MutableLiveData<ViewState>(ViewState.RegisterClient)
    }

    private val metadata: MutableLiveData<Metadata> by lazy {
        MutableLiveData<Metadata>()
    }

    private val loginState: MutableLiveData<LoginState> by lazy {
        MutableLiveData<LoginState>()
    }

    private val clientData: MutableLiveData<ClientData> by lazy {
        MutableLiveData<ClientData>()
    }

    private val loginData: MutableLiveData<LoginData> by lazy {
        MutableLiveData<LoginData>()
    }

    private val tokenSet: MutableLiveData<TokenSet> by lazy {
        MutableLiveData<TokenSet>()
    }

    private val signingResult: MutableLiveData<SigningData> by lazy {
        MutableLiveData<SigningData>()
    }

    private val dataToSign by lazy {
        MutableLiveData<String>()
    }

    fun getDataToSign(): LiveData<String> = dataToSign

    fun getViewState(): LiveData<ViewState> {
        return viewState
    }

    fun getHealthcheck() = liveData(Dispatchers.IO) {
        try {
            emit(playgroundRestApi.healthcheck())
        } catch (error: Exception) {
            Timber.e(error)
            emit(null)
        }
    }

    fun createClient(token: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            viewState.postValue(ViewState.Loading)
            val uuidToken = UUID.fromString(token)
            val openIdClientData = playgroundRestApi.openIdClientData(uuidToken)
            registerClientWithAuthServer(openIdClientData)
        } catch (error: Exception) {
            Timber.e(error)
            viewState.postValue(ViewState.RegisterClient)
        }
    }

    private fun registerClientWithAuthServer(
        openIdClientData: OpenIdClientDataResponse,
    ) {
        if (!openIdClientData.redirectUrls.contains(REDIRECT_URL)) {
            Timber.e("Invalid redirect url")
            return viewState.postValue(ViewState.RegisterClient)
        }
        val deviceId = UUID.randomUUID().toString()
        val (clientToken, authEnvUrl, _, _, appId, _) = openIdClientData
        megicalAuthApi.registerClient(
            authEnvUrl,
            clientToken,
            deviceId,
            keyId = appId,
            callback = object : MegicalCallback<Client> {
                override fun onSuccess(response: Client) {
                    setClientData(
                        ClientData(
                            clientId = response.clientId,
                            appId = openIdClientData.appId,
                            audience = openIdClientData.audience,
                            authEnvUrl = openIdClientData.authEnvUrl,
                            authEnv = openIdClientData.authEnv,
                        )
                    )
                }

                override fun onFailure(error: MegicalException) {
                    Timber.e(error)
                    viewState.postValue(ViewState.RegisterClient)
                }
            }
        )
    }

    fun getClientData(): LiveData<ClientData> {
        return clientData
    }

    fun setClientData(value: ClientData) {
        clientData.postValue(value)
        viewState.postValue(ViewState.Operate)
    }

    fun getAuthentication(): LiveData<LoginData> {
        return loginData
    }

    fun sign(data: String) {
        viewState.postValue(ViewState.Loading)
        dataToSign.postValue(data)
        megicalSignApi.initSign(
            getClientData().value!!.authEnv,
            "https://playground.megical.com/easyaccess/api/v1/sign/signature",
            data,
            object : MegicalCallback<SigningData> {

                override fun onSuccess(response: SigningData) {
                    signingResult.postValue(response)
                }

                override fun onFailure(error: MegicalException) {
                    Timber.e(error)
                    viewState.postValue(ViewState.Operate)
                }

            })
    }

    fun getSigningResult(): LiveData<SigningData> {
        return signingResult
    }

    fun authenticate() {
        getClientData().value!!.let { clientData ->
            viewState.postValue(ViewState.Loading)
            megicalAuthApi.initAuthentication(
                clientData.authEnv,
                clientData.authEnvUrl,
                clientData.appId,
                clientData.clientId,
                clientData.audience,
                REDIRECT_URL,
                object : MegicalCallback<LoginData> {
                    override fun onSuccess(response: LoginData) {
                        loginData.postValue(response)
                    }

                    override fun onFailure(error: MegicalException) {
                        Timber.e(error)
                        viewState.postValue(ViewState.Operate)
                    }
                })
        }
    }

    fun getMetadata(): LiveData<Metadata> {
        return metadata
    }

    fun fetchMetadata() {
        viewState.postValue(ViewState.Loading)
        val loginCode = loginData.value!!.loginCode
        megicalAuthApi.metadata(loginCode, object : MegicalCallback<Metadata> {
            override fun onSuccess(response: Metadata) {
                metadata.postValue(response)
                viewState.postValue(ViewState.EasyAccess)
            }

            override fun onFailure(error: MegicalException) {
                Timber.e(error)
            }
        })
    }

    fun getLoginState(): LiveData<LoginState> {
        return loginState
    }

    fun fetchLoginState() {
        val loginCode = loginData.value!!.loginCode
        megicalAuthApi.state(loginCode, object : MegicalCallback<LoginState> {
            override fun onSuccess(response: LoginState) {
                loginState.postValue(response)
            }

            override fun onFailure(error: MegicalException) {
                Timber.e(error)
                viewState.postValue(ViewState.Operate)
            }
        })
    }

    fun getTokenSet(): LiveData<TokenSet> {
        return tokenSet
    }

    fun verifyLoginData() {
        viewModelScope.launch(Dispatchers.IO) {
            megicalAuthApi.verifyAuthentication(
                object : MegicalCallback<TokenSet> {
                    override fun onSuccess(response: TokenSet) {
                        tokenSet.postValue(response)
                    }

                    override fun onFailure(error: MegicalException) {
                        Timber.e(error)
                        viewState.postValue(ViewState.Operate)
                    }
                })
        }
    }

    fun clearOperation() {
        viewState.postValue(ViewState.Operate)
    }

    fun showEasyAccessRequest() {
        viewState.postValue(ViewState.EasyAccess)
    }
    
    fun showSigningResult() {
        viewState.postValue(ViewState.SignResult)
    }

    fun fetchMessageFromTestService(accessToken: String) = liveData(Dispatchers.IO) {
        try {
            emit(playgroundRestApi.hello(accessToken))
            viewState.postValue(ViewState.AuthResult)
        } catch (error: Exception) {
            Timber.e(error)
            viewState.postValue(ViewState.Operate)
            emit(null)
        }
    }

    fun deregisterClient() {
        clientData.value?.let {
            megicalAuthApi.deleteClient(
                it.authEnvUrl,
                it.clientId,
                it.appId,
                object : MegicalCallback<Unit> {
                    override fun onSuccess(response: Unit) {
                        Timber.i("Client deleted")
                    }

                    override fun onFailure(error: MegicalException) {
                        Timber.e(error)
                    }
                })
        }
        clientData.postValue(null)
        viewState.postValue(ViewState.RegisterClient)
    }
}