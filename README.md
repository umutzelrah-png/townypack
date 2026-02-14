# Kese (Paper 1.21.11)

Kese, Paper sunucuları için Vault tabanlı bir ekonomi eklentisidir. Oyuncular envanterlerindeki fiziksel altını (`GOLD_NUGGET`, `GOLD_INGOT`, `GOLD_BLOCK`) Vault parasına çevirebilir ya da tersini yapabilir.

## Özellikler

- `/kese koy <miktar>`
  - Oyuncunun envanterindeki altınlardan belirtilen miktar kadarını bozup Vault hesabına yatırır.
- `/kese al <miktar>`
  - Vault hesabından belirtilen miktar para çekip oyuncuya fiziksel altın verir.
- Güvenli bozuk para mekanizması
  - Gerekirse büyük birim tüketilip bozuk para (külçe/parça) iade edilir.
  - Envanterde iade/ödeme için yer yoksa işlem iptal edilir.
  - İşlem başarısız olursa para/eşya kaybı yaşanmaz.

## Kur

- `1 GOLD_INGOT  = 1.0`
- `1 GOLD_NUGGET = 0.125`
- `1 GOLD_BLOCK  = 9.0`

Tüm dönüşümler sadece `0.125` katları için geçerlidir.

## Tarifler ve Nerfler

1. Nether Gold Ore eritme nerfi
   - `NETHER_GOLD_ORE` fırında eritildiğinde sonuç zorla `GOLD_NUGGET x2` olur.

2. Özel tarif: 1 külçe -> 8 parça
   - Shapeless tarif ile `1 GOLD_INGOT` -> `8 GOLD_NUGGET`.

3. Vanilla korunur
   - `9 GOLD_NUGGET -> 1 GOLD_INGOT` vanilla tarifi aynen kalır.

4. Özel Totem of Undying tarifi (Shaped)

   ```
   G B G
   B H B
   G B G
   ```

   - `G = GOLD_BLOCK`
   - `B = BLAZE_ROD`
   - `H = HEART_OF_THE_SEA`
   - Sonuç: `TOTEM_OF_UNDYING x1`

## Gereksinimler

- Java 21
- Paper `1.21.11`
- Vault + bir Economy sağlayıcısı (ör. EssentialsX Economy)

## Kurulum

1. Bu projeyi derleyin:
   ```bash
   mvn -B clean package
   ```
2. Oluşan JAR dosyasını `target/` klasöründen alın.
3. Sunucunuzun `plugins/` klasörüne koyun.
4. Vault ve ekonomi eklentinizin yüklü olduğundan emin olun.

## Geliştirme

- API: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` (provided)
- Vault API: `com.github.MilkBowl:VaultAPI:1.7` (provided)
- Java release: 21

## GitHub Actions

Repo içinde `Build Jar` workflow'u bulunur:

- `main` branch'ine push edildiğinde ve manuel (`workflow_dispatch`) tetiklenebilir.
- Java 21 (Temurin) ile `mvn -B clean package` çalıştırır.
- Üretilen JAR dosyasını `kese-jar` adıyla artifact olarak yükler.
