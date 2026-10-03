package com.milli.watcher

import android.app.*
import android.content.Intent
import android.os.IBinder
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern

class PriceWatchService: Service(){
    private var running=true
    private var mode="BUY"
    private var threshold=0.0
    private val channel="milli_watch"
    private val samples=ArrayDeque<Double>()
    override fun onCreate(){super.onCreate();createChannel();startForeground(7,notification("Milli Watcher","در حال پایش میلی"));Thread{loop()}.start()}
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
        mode=i?.getStringExtra("mode")?:mode
        threshold=i?.getDoubleExtra("threshold",threshold)?:threshold
        return START_STICKY
    }
    private fun loop(){
        var alerted=false
        while(running){
            val p=fetchPrice()
            if(p!=null){
                if(samples.size>=30)samples.removeFirst();samples.addLast(p)
                val hit=if(mode=="BUY") p<=threshold else p>=threshold
                if(hit&&!alerted){
                    val range=if(samples.size>2) "\nکف ۳۰دقیقه: ${fmt(samples.minOrNull()!!)} | سقف: ${fmt(samples.maxOrNull()!!)}" else ""
                    notifyNow(if(mode=="BUY")"🟢 زمان بررسی خرید" else "🔴 زمان بررسی فروش","قیمت میلی: ${fmt(p)} تومان | هدف: ${fmt(threshold)} تومان$range")
                    alerted=true
                }
                if(!hit)alerted=false
            }
            try{Thread.sleep(60000)}catch(_:Exception){}
        }
    }
    private fun fetchPrice():Double?=try{
        val c=URL("https://milli.gold/t-price").openConnection() as HttpURLConnection
        c.connectTimeout=12000;c.readTimeout=12000;c.requestMethod="GET";c.setRequestProperty("User-Agent","Mozilla/5.0 (Android) MilliWatcher/0.4")
        val s=c.inputStream.bufferedReader().use{it.readText()};c.disconnect()
        parseMilliPrice(s)
    }catch(_:Exception){null}

    private fun parseMilliPrice(html:String):Double?{
        val text=html.replace("&nbsp;"," ").replace("٬","").replace(",",",")
        val perMg=Pattern.compile("(?is)قیمت\\s*میلی[^0-9]{0,80}([0-9]{1,3}(?:[,،][0-9]{3})*|[0-9]{4,6})\\s*تومان").matcher(text)
        if(perMg.find()) return normalize(perMg.group(1))
        val large=Pattern.compile("(?is)(?:نرخ طلای ۱۸ عیار|قیمت)[^0-9]{0,100}([0-9]{1,3}(?:[,،][0-9]{3})*|[0-9]{7,9})\\s*تومان").matcher(text)
        if(large.find()){
            val v=normalize(large.group(1)); if(v>100000) return v/1000.0; return v
        }
        return null
    }
    private fun normalize(s:String):Double?=s.replace(",","").replace("،","").toDoubleOrNull()
    private fun createChannel(){if(android.os.Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"Milli Watcher",NotificationManager.IMPORTANCE_HIGH))}
    private fun notification(t:String,b:String)=Notification.Builder(this,channel).setContentTitle(t).setContentText(b).setSmallIcon(android.R.drawable.ic_dialog_info).build()
    private fun notifyNow(t:String,b:String){getSystemService(NotificationManager::class.java).notify(99,notification(t,b))}
    private fun fmt(x:Double)=String.format(Locale.US,"%,.0f",x)
    override fun onBind(i:Intent?):IBinder?=null
    override fun onDestroy(){running=false;super.onDestroy()}
}
