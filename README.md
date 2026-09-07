# 🏷️ MollyAuction

Плагин аукциона для серверов Minecraft (**Paper / Purpur 1.21+**, Java 21).

## 📌 Зависимости
- **Vault** (и плагин экономики) или **PlayerPoints**

---

## 📋 Команды и права

| Команда | Описание | Право |
|---|---|---|
| `/ah` | Открыть аукцион | `mollyauction.use` |
| `/ah sell <цена>` | Выставить предмет из руки на продажу | `mollyauction.sell` |
| `/ah search <запрос>` | Поиск предметов по названию | `mollyauction.use` |
| `/ah my` | Управление своими лотами | `mollyauction.use` |
| `/ah storage` | Хранилище непроданных предметов | `mollyauction.use` |
| `/ah history` | История покупок и продаж | `mollyauction.use` |
| `/ah reload` | Перезагрузка конфигурации | `mollyauction.admin` |

### Дополнительные права:
- `mollyauction.limit.<кол-во>` — Лимит активных лотов у игрока (например, `mollyauction.limit.10`).
- `mollyauction.admin` — Модерация лотов в GUI (`Shift + ПКМ`) и перезагрузка плагина.

---

## 📥 Скачать
Готовый `.jar` файл доступен во вкладке [Releases](https://github.com/Nagivka/MollyAuction/releases).
