import os
import time
import requests
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ApplicationBuilder, ContextTypes, CommandHandler, CallbackQueryHandler, MessageHandler, filters

TOKEN = os.getenv("BOT_TOKEN", "TOKEN_HERE")

trade_state = {
    "state": "HOLDING",
    "mg": 1342.0,
    "buy_price": 25480.0,
    "sell_price": 0.0,
    "target_sell": 0.0,
    "target_buy": 0.0
}

def update_targets():
    fee = 0.005
    bp = trade_state["buy_price"]
    trade_state["target_sell"] = bp * ((1 + 0.005) / (1 - fee))

update_targets()

def fetch_milli_price():
    try:
        res = requests.get("https://api.gold-api.com/price/XAU", timeout=10)
        data = res.json()
        ounce_usd = float(data.get("price", 2650))
        dolar_irr = 700000 
        gram_irr = (ounce_usd * dolar_irr) / 31.1035
        raw_mg_irr = gram_irr / 1000
        calibrated_price = raw_mg_irr * 0.27732  
        if calibrated_price < 10000 or calibrated_price > 100000:
            return 26030.0
        return round(calibrated_price, 2)
    except:
        return 26030.0

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    keyboard = [
        [InlineKeyboardButton("📊 وضعیت پورتفو", callback_data="status")],
        [InlineKeyboardButton("🔄 به‌روزرسانی قیمت", callback_data="price")]
    ]
    reply_markup = InlineKeyboardMarkup(keyboard)
    await update.message.reply_text("سلام سام عزیز! ربات دیده‌بان میلی‌گلد فعال شد.", reply_markup=reply_markup)

async def button_handler(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    current_price = fetch_milli_price()
    
    if query.data == "status":
        msg = f"--- وضعیت پورتفو ---\n" \
              f"قیمت بازار: {current_price:,.0f} تومان\n" \
              f"وضعیت: {trade_state['state']}\n"
        if trade_state['state'] == 'HOLDING':
            msg += f"تعداد: {trade_state['mg']} میلی‌گرم\nهدف فروش: {trade_state['target_sell']:,.0f} تومان"
        else:
            msg += f"قیمت فروش قبلی: {trade_state['sell_price']:,.0f} تومان"
        await query.message.reply_text(msg)
    elif query.data == "price":
        await query.message.reply_text(f"قیمت لحظه‌ای هر میلی‌گرم: {current_price:,.0f} تومان")

async def handle_text(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.strip()
    if text.startswith("فروش:"):
        try:
            sell_p = float(text.replace("فروش:", "").strip())
            trade_state["sell_price"] = sell_p
            trade_state["state"] = "CASH"
            trade_state["target_buy"] = sell_p * (1 - 0.012)
            await update.message.reply_text(f"✅ فروش ثبت شد: {int(sell_p):,} تومان\nحد کف خرید: {trade_state['target_buy']:,.0f} تومان")
        except:
            await update.message.reply_text("❌ فرمت اشتباه. مثال: فروش: 25853")
    elif text.startswith("خرید:"):
        try:
            parts = text.replace("خرید:", "").strip().split()
            trade_state["mg"] = float(parts[0])
            trade_state["buy_price"] = float(parts[1])
            trade_state["state"] = "HOLDING"
            update_targets()
            await update.message.reply_text("✅ خرید با موفقیت ثبت شد.")
        except:
            await update.message.reply_text("❌ فرمت اشتباه. مثال: خرید: 1342 25480")

if __name__ == "__main__":
    app = ApplicationBuilder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CallbackQueryHandler(button_handler))
    app.add_handler(MessageHandler(filters.TEXT & (~filters.COMMAND), handle_text))
    app.run_polling()
