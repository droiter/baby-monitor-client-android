package co.netguru.baby.monitor.client.feature.client.home

import android.app.Service
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import androidx.navigation.findNavController
import co.netguru.baby.monitor.client.R
import co.netguru.baby.monitor.client.application.GlideApp
import co.netguru.baby.monitor.client.common.extensions.setVisible
import co.netguru.baby.monitor.client.data.client.home.ToolbarState
import co.netguru.baby.monitor.client.feature.communication.websocket.ClientHandlerService
import co.netguru.baby.monitor.client.feature.communication.websocket.RxWebSocketClient
import co.netguru.baby.monitor.client.feature.communication.websocket.WebSocketClientService
import co.netguru.baby.monitor.client.feature.settings.DefaultDrawerObserver
import com.bumptech.glide.request.RequestOptions
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_client_home.*
import kotlinx.android.synthetic.main.toolbar_child.*
import kotlinx.android.synthetic.main.toolbar_default.*
import timber.log.Timber
import java.net.URI
import javax.inject.Inject

class ClientHomeActivity : DaggerAppCompatActivity(), ServiceConnection {

    @Inject
    internal lateinit var factory: ViewModelProvider.Factory
    @Inject
    internal lateinit var sendFirebaseTokenUseCase: SendFirebaseTokenUseCase

    private val homeViewModel by lazy {
        ViewModelProviders.of(this, factory)[ClientHomeViewModel::class.java]
    }
    private val compositeDisposable = CompositeDisposable()
    private var childServiceBinder: ClientHandlerService.ChildServiceBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_home)

        setupView()
        getData()
        bindService()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
        childServiceBinder?.let {
            childServiceBinder?.stopService()
        }
        unbindService(this)
    }

    override fun onSupportNavigateUp() =
            findNavController(R.id.clientDashboardNavigationHostFragment).navigateUp()

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.i("service disconnected $name")
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service is WebSocketClientService.Binder) {
            handleWebSocketClientServiceBinder(service)
        }
    }

    private fun handleWebSocketClientServiceBinder(binder: WebSocketClientService.Binder) {
        homeViewModel.selectedChild.observe(this, Observer { child ->
            if (child != null) {
                Timber.i("Opening socket to ${child.address}.")
                binder.client().events(URI.create(child.address))
                    .subscribeBy(
                        onNext = { event ->
                            Timber.i("Consuming event: $event.")
                            if (event is RxWebSocketClient.Event.Open) {
                                homeViewModel.selectedChildAvailability.postValue(true)
                                sendFirebaseTokenUseCase.sendFirebaseToken(binder.client())
                                    .subscribeOn(Schedulers.io())
                                    .subscribeBy(
                                        onComplete = {
                                            Timber.d("Firebase token sent successfully.")
                                        }, onError = { error ->
                                            Timber.w(error, "Error sending Firebase token.")
                                        })
                                    .addTo(compositeDisposable)
                            }
                            if (event is RxWebSocketClient.Event.Close) {
                                homeViewModel.selectedChildAvailability.postValue(false)
                            }
                        },
                        onError = { error ->
                            Timber.i("Websocket error: $error.")
                        }
                    )
                    .addTo(compositeDisposable)
            }
        })
    }

    private fun setupView() {
        toolbarSettingsIbtn.setOnClickListener {
            homeViewModel.shouldDrawerBeOpen.postValue(true)
        }
        toolbarBackBtn.setOnClickListener {
            findNavController(R.id.clientDashboardNavigationHostFragment).navigateUp()
        }
        backIbtn.setOnClickListener {
            findNavController(R.id.clientDashboardNavigationHostFragment).navigateUp()
        }
        homeViewModel.selectedChildAvailability.postValue(false)
    }

    private fun getData() {
        homeViewModel.fetchLogData()
        homeViewModel.selectedChild.observe(this, Observer {
            it ?: return@Observer
            setSelectedChildName(it.name ?: "")
            GlideApp.with(this)
                    .load(it.image)
                    .placeholder(R.drawable.child)
                    .apply(RequestOptions.circleCropTransform())
                    .into(toolbarChildMiniatureIv)
        })
        homeViewModel.toolbarState.observe(this, Observer(this::handleToolbarStateChange))
        homeViewModel.backButtonShouldBeVisible.observe(this, Observer { backIbtn.setVisible(it == true) })
        homeViewModel.shouldDrawerBeOpen.observe(this, Observer { shouldClose ->
            if (shouldClose == true) {
                client_drawer.openDrawer(Gravity.END)
            } else {
                client_drawer.closeDrawer(Gravity.END)
            }
        })
        client_drawer.isDrawerOpen(Gravity.END)

        client_drawer.addDrawerListener(DefaultDrawerObserver(
                onDrawerisClosing = {
                    homeViewModel.saveChildNameRequired.postValue(true)
                }
        )
        )
    }

    private fun bindService() {
        bindService(Intent(this, WebSocketClientService::class.java), this, Service.BIND_AUTO_CREATE)
    }

    private fun setSelectedChildName(name: String) {
        toolbarChildTv.text = if (!name.isEmpty()) {
            name
        } else {
            getString(R.string.no_name)
        }
    }

    private fun handleToolbarStateChange(state: ToolbarState?) {
        when (state) {
            ToolbarState.HIDDEN -> {
                childToolbarLayout.setVisible(false)
                defaultToolbarLayout.setVisible(false)
            }
            ToolbarState.HISTORY -> {
                childToolbarLayout.setVisible(false)
                defaultToolbarLayout.setVisible(true)
                toolbarTitleTv.text = getString(R.string.latest_activity)
            }
            ToolbarState.DEFAULT -> {
                defaultToolbarLayout.setVisible(false)
                childToolbarLayout.setVisible(true)
            }
        }
    }
}
