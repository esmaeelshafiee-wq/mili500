package com.milli.watcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private lateinit var price: EditText
    private lateinit var profit: EditText
    private lateinit var qty: EditText
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        root.addView(TextView(this).apply { text="Milli Watcher ۰٫۴"; textSize=25f })
        root.addView(TextView(this).apply { text="پایش میلی + هدف سود ۱٪ + هشدار"; textSize=16f })
        price=EditText(this).apply{hint="قیمت معامله هر میلی (تومان)";inputType=2}; root.addView(price)
        qty=EditText(this).apply{hint="مقدار طلا (میلی‌گرم)";inputType=2}; root.addView(qty)
        profit=EditText(this).apply{hint="سود هدف (%)";inputType=2;setText("1")}; root.addView(profit)
        val rg=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
        val sold=RadioButton(this).apply{text="من فروخته‌ام → هشدار خرید";id=1;isChecked=true}
        val bought=RadioButton(this).apply{text="من خریده‌ام → هشدار فروش";id=2}
        rg.addView(sold);rg.addView(bought);root.addView(rg)
        val calc=Button(this).apply{text="محاسبه هدف ۱٪"};root.addView(calc)
        val start=Button(this).apply{text="شروع پایش"};root.addView(start)
        val stop=Button(this).apply{text="توقف پایش"};root.addView(stop)
        status=TextView(this).apply{textSize=17f};root.addView(status)
        calc.setOnClickListener{calculate(rg.checkedRadioButtonId)}
        start.setOnClickListener{startWatch(rg.checkedRadioButtonId)}
        stop.setOnClickListener{stopService(Intent(this,PriceWatchService::class.java));status.text="پایش متوقف شد"}
        setContentView(root)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
    }
    private fun calcTarget(p:Double,g:Double,sold:Boolean):Double = if(sold) p*.995/1.005/(1+g/100.0) else p*1.005/.995*(1+g/100.0)
    private fun calculate(id:Int){
        val p=price.text.toString().toDoubleOrNull(); val g=profit.text.toString().toDoubleOrNull()?:1.0
        if(p==null||p<=0){status.text="قیمت را وارد کن";return}
        val sold=id==1; val target=calcTarget(p,g,sold)
        status.text=if(sold) "خرید با هدف ${g}% سود: ${fmt(target)} تومان\nسر‌به‌سر: ${fmt(p*.995/1.005)} تومان" else "فروش با هدف ${g}% سود: ${fmt(target)} تومان\nسر‌به‌سر: ${fmt(p*1.005/.995)} تومان"
    }
    private fun startWatch(id:Int){
        val p=price.text.toString().toDoubleOrNull(); val g=profit.text.toString().toDoubleOrNull()?:1.0
        if(p==null||p<=0){status.text="قیمت معامله را وارد کن";return}
        val sold=id==1; val target=calcTarget(p,g,sold)
        val i=Intent(this,PriceWatchService::class.java).apply{putExtra("mode",if(sold)"BUY" else "SELL");putExtra("threshold",target);putExtra("profit",g);putExtra("qty",qty.text.toString().toDoubleOrNull()?:0.0)}
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
        status.text="پایش فعال شد\nهدف: ${fmt(target)} تومان\nهر ۶۰ ثانیه بررسی می‌شود"
    }
    private fun fmt(x:Double)=String.format("%,.0f",x)
}
