package co.netguru.baby.monitor.client.feature.client.configuration

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import co.netguru.baby.monitor.client.R
import kotlinx.android.synthetic.main.fragment_configuration_failed.*

class ConfigurationFailedFragment : Fragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_configuration_failed, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurationFailedBackButton.setOnClickListener {
            findNavController().navigate(R.id.actionConfigurationFailedToWelcome)
        }

        configurationFailedTryAgainButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }
}