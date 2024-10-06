package com.felicio.geobottle

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker

class InfoWindowAdapter(private val inflater: LayoutInflater) : GoogleMap.InfoWindowAdapter {

    override fun getInfoWindow(marker: Marker): View? {
        // Retorna null para usar o layout de conteúdo personalizado
        return null
    }

    override fun getInfoContents(marker: Marker): View {
        // Infla o layout personalizado para a InfoWindow
        val view = inflater.inflate(R.layout.activity_info_window_adapter, null)

        // Obtém as referências dos TextViews no layout
        val titleView = view.findViewById<TextView>(R.id.title)
        val snippetView = view.findViewById<TextView>(R.id.snippet)

        // Define o título e o snippet (mensagem) no layout da InfoWindow
        titleView.text = marker.title
        snippetView.text = marker.snippet

        return view
    }
}
