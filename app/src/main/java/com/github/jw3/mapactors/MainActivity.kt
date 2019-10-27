package com.github.jw3.mapactors

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay


sealed class Msg
data class Move(val lat: Double, val lon: Double) : Msg()
data class Signal(val strength: Float, val quality: Float) : Msg()
data class PingDelay(val sz: Delayed) : Msg()
enum class Delayed(val rgb: Int, val style: SimpleMarkerSymbol.Style) {
    None(-0xff0100, SimpleMarkerSymbol.Style.CIRCLE),
    Short(-0x100, SimpleMarkerSymbol.Style.TRIANGLE),
    Long(-0x10000, SimpleMarkerSymbol.Style.X)
}

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView.map = ArcGISMap(Basemap.Type.IMAGERY, 0.0, 0.0, 5)
        val locationsLayer = GraphicsOverlay()
        mapView.graphicsOverlays.add(locationsLayer)

        ///

        val (g, c) = a("frist")
        locationsLayer.graphics.add(g)

        c.offer(Move(1.0, 1.0))
    }

    fun lastPing(t: Long, channel: Channel<Msg>) = async {
        val millis: Long = 1000 * 30
        delay(millis)
        channel.send(PingDelay(Delayed.Short))
        delay(millis * 4)
        channel.send(PingDelay(Delayed.Long))
    }

    fun a(id: String): Pair<Graphic, SendChannel<Msg>> {
        val symbol = SimpleMarkerSymbol(Delayed.None.style, Delayed.None.rgb, 15.0f)
        val g = Graphic(Point(0.0, 0.0, SpatialReferences.getWgs84()), symbol)
        val c = actor<Msg> {
            var timer = lastPing(0, channel)

            for (e in channel) { // iterate over incoming messages
                when (e) {
                    is Move -> {
                        timer.cancel()
                        timer = lastPing(System.currentTimeMillis(), channel)
                        symbol.color = Delayed.None.rgb
                        symbol.style = Delayed.None.style
                        g.geometry = Point(e.lon, e.lat)
                    }
                    is PingDelay -> {
                        symbol.color = e.sz.rgb
                        symbol.style = e.sz.style
                    }
                    is Signal -> println(e.strength)
                }
            }
        }
        return Pair(g, c)
    }
}
