# واسط امن «دستیار من» (Dastyar Relay)

این یک Cloudflare Worker کوچک و **امن** است که به برنامهٔ اندرویدی «دستیار من»
اجازه می‌دهد درخواست‌های هوش مصنوعی را از مسیری عبور دهد که روی شبکه‌های مسدود
کار کند. این یک **پراکسی عمومی نیست**:

- فقط میزبان‌های مجاز (OpenRouter، Groq، CodeCraft، Atria) قابل عبورند.
- هر درخواست باید یک **توکن مشترک** (`X-Gateway-Token`) داشته باشد.
- محدودیت نرخ، محدودیت حجم بدنه و timeout دارد.
- کلیدهای واقعی ارائه‌دهنده‌ها در **Secrets سرور** می‌مانند و هرگز وارد APK نمی‌شوند.

> ⚠️ نکتهٔ مهم: هیچ تضمینی نیست که خودِ `*.workers.dev` از شبکهٔ شما مسدود نباشد.
> اگر مسدود بود، باید یک **دامنهٔ شخصی** به Worker وصل کنید (بخش «دامنهٔ اختصاصی»).
> همچنین ادعای «رفع فیلترینگ» فقط پس از آزمایش روی گوشی **بدون VPN** معتبر است.

---

## پیش‌نیازها

1. یک حساب Cloudflare (رایگان کافی است).
2. Node.js نسخهٔ ۱۸ یا بالاتر روی کامپیوتری که استقرار را انجام می‌دهید.
3. کلید API برای هر ارائه‌دهنده‌ای که می‌خواهید واسط از آن پشتیبانی کند
   (مثلاً کلید OpenRouter و کلید Groq).

هیچ‌کدام از این‌ها را در فایل‌های ریپو قرار ندهید.

---

## گام‌به‌گام استقرار

### ۱) نصب ابزار wrangler
در پوشهٔ `backend/relay-worker`:

```bash
npm install
npx wrangler login
```

`wrangler login` یک مرورگر باز می‌کند تا به حساب Cloudflare وارد شوید.

### ۲) ساخت یک توکن مشترک (GATEWAY_TOKEN)
یک رشتهٔ تصادفی و بلند بسازید (مثلاً با `openssl rand -hex 32`). این همان توکنی
است که در تنظیمات برنامه وارد می‌کنید.

### ۳) تنظیم Secrets (هرگز در کد)
هر کلید را جداگانه و فقط روی سرور ذخیره کنید:

```bash
npx wrangler secret put GATEWAY_TOKEN        # توکن مشترک مرحلهٔ ۲
npx wrangler secret put OPENROUTER_API_KEY   # کلید OpenRouter
npx wrangler secret put GROQ_API_KEY         # کلید Groq
# اختیاری:
# npx wrangler secret put CODECRAFT_API_KEY
# npx wrangler secret put ATRIA_API_KEY
# اختیاری: محدودیت نرخ در دقیقه (پیش‌فرض ۶۰)
# npx wrangler secret put RATE_LIMIT_PER_MIN
```

### ۴) استقرار

```bash
npx wrangler deploy
```

در خروجی، آدرسی مثل `https://dastyar-relay.<subdomain>.workers.dev` می‌بینید.
این همان **آدرس واقعی Backend** است.

### ۵) آزمایش سریع سلامت

```bash
curl -s https://dastyar-relay.<...>.workers.dev/health
# انتظار: {"ok":true,"service":"dastyar-relay"}
```

آزمایش احراز هویت (باید ۴۰۱ بدهد چون توکن ندارد):

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  https://dastyar-relay.<...>.workers.dev/proxy/api.groq.com/openai/v1/models
# انتظار: 401
```

آزمایش با توکن (باید لیست مدل‌ها را برگرداند):

```bash
curl -s -H "X-Gateway-Token: <GATEWAY_TOKEN>" \
  https://dastyar-relay.<...>.workers.dev/proxy/api.groq.com/openai/v1/models
```

### ۶) وصل‌کردن برنامه به واسط
در برنامه: **تنظیمات → مسیر اتصال (Gateway)**:
1. حالت را روی «از طریق واسط (relay)» بگذارید.
2. آدرس واسط را همان `https://...workers.dev` وارد کنید.
3. توکن واسط را همان `GATEWAY_TOKEN` وارد کنید.
4. ذخیره کنید.

از این پس درخواست‌های متنی از مسیر واسط می‌روند.

---

## دامنهٔ اختصاصی (اگر `workers.dev` مسدود بود)

1. یک دامنه در Cloudflare اضافه کنید (Domains).
2. در داشبورد Worker → **Settings → Domains & Routes → Add → Custom Domain**،
   مثلاً `relay.example.com` را وصل کنید.
3. همان آدرس دامنه را در تنظیمات برنامه وارد کنید.

---

## نکات امنیتی

- `GATEWAY_TOKEN` را فقط در «تنظیمات» برنامه و در Secret سرور نگه دارید.
- توکن را هرگز در GitHub، APK، چت یا لاگ قرار ندهید.
- اگر توکن لو رفت، با `wrangler secret put GATEWAY_TOKEN` عوضش کنید.
- برای سهمیهٔ سخت‌گیرانه‌تر، از Rate Limiting Rules کلادفلر هم استفاده کنید.
- برای افزودن ارائه‌دهندهٔ جدید OpenAI-compatible، فقط یک خط به `PROVIDERS`
  در `src/index.js` اضافه کنید و Secret آن را تنظیم کنید؛ برنامه تغییری لازم ندارد.
