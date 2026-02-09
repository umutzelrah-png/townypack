# Kese (Paper 1.21.11 + Vault)

## Özellikler
- /kese koy <para> : Envanter altınını Vault paraya çevirir
- /kese al <para>  : Vault parayı altına çevirir
- Kur:
  - 1 Gold Ingot = 1.0 para
  - 1 Gold Nugget = 0.125 para
  - 1 Gold Block = 9.0 para
- Nether Gold Ore smelt -> 2 nugget
- Ingot -> 8 nugget (shapeless)
- Totem craft:
  - Köşeler: Gold Block
  - Kenarlar: Blaze Rod
  - Orta: Heart of the Sea

## Gereksinimler
- Paper 1.21.11
- Vault
- Bir Economy provider (EssentialsX Economy / CMI vb.)

## Build
```bash
mvn clean package
