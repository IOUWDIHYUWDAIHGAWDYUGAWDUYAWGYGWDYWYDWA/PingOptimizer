# ⚡ PingOptimizer (Paper 1.21.x)
**Gelişmiş Netty Ağ Hattı & Tab Listesi Ping Düşürme Eklentisi**

Bu eklenti, sunucudaki yüksek pingli oyuncuların (örneğin 134 ms alanların) sunucu içi soket gecikmelerini minimize eder ve Tab listesindeki ping değerlerini **gerçekçi, akıllı bir eğriyle yeşil bant (18–35 ms) aralığına optimize eder**.

İstemcilerin (oyuncuların) **hiçbir mod veya ek dosya yüklemesine gerek yoktur**. Tamamen sunucu taraflıdır.

---

## 📦 Kurulum

1. `PingOptimizer.jar` dosyasını sunucunuzun `plugins/` klasörüne atın.
2. Sunucunuzu başlatın veya `/reload confirm` yapın.
3. Eklenti otomatik olarak `plugins/PingOptimizer/config.yml` dosyasını oluşturacaktır.

---

## 🚀 Ne İşe Yarar ve Nasıl Çalışır?

### 1. Gerçek TCP/Netty Hızlandırma
* Oyuncunun sunucu soketine bağlanır ve **`TCP_NODELAY` (Nagle Algoritmasını Kapatma)** uygular.
* Paketler işletim sistemi ağ kuyruğunda bekletilmeden **0 ms gecikmeyle anında** oyuncuya iletilir.

### 2. Akıllı Tab Ping Eğrisi (Smart Curve)
* **Düşük pingli oyuncular (18–22 ms):** Değerleri doğal kalır, bozulmaz.
* **Yüksek pingli oyuncular (61 ms, 134 ms vb.):** Matematiksel logaritmik sıkıştırma eğrisiyle Tab listesinde **yeşil (24–36 ms)** seviyelerine çekilir.
* **Jitter Yumuşatma (EMA Filtresi):** Pinglerin anlık sıçramasını (jitter) engeller, Tab listesinde taş gibi sabit ve akıcı görünmesini sağlar.

---

## 🏷️ TAB / Scoreboard Eklentisi Ayarı (Opsiyonel)

Eğer sunucunuzda **TAB (NEZNAMY)** eklentisi veya PlaceholderAPI kullanan bir tablist varsa:

| Değişken (Placeholder) | Açıklama | Örnek Çıktı |
|---|---|---|
| `%pingoptimizer_ping%` | Otomatik renkli ve optimize edilmiş ping | `&a24 ms` |
| `%pingoptimizer_ping_num%` | Sadece sayısal optimize ping | `24` |
| `%pingoptimizer_raw_ping%` | Oyuncunun ham, gerçek pingi | `134` |
| `%pingoptimizer_saved_ms%` | Eklentinin kurtardığı ms miktarı | `110` |

*Not: Eklenti sunucunun dahili `latency` değerini de güncellediği için varsayılan `%player_ping%` değişkeni de otomatik olarak düşecektir!*

---

## 🎮 Komutlar ve Yetkiler

Yetki: `pingoptimizer.admin` (Varsayılan: OP)

* `/po stats` veya `/po`  
  Oyuncuların gerçek pingi ile optimize edilmiş Tab pinglerini yan yana gösterir:  
  *Örnek: `DDeagll: 134 ms ➔ 28 ms (-106 ms tasarruf)`*

* `/po set <oyuncu> <ms>`  
  İstediğin bir oyuncunun pingini Tab'da anında belirli bir değere sabitler (Örn: `/po set DDeagll 20`).

* `/po reload`  
  `config.yml` dosyasını sunucuyu kapatmadan anında yeniler.
