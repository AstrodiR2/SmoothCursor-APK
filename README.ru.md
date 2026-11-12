# Termux:Monet + SmoothCursor (Standalone)

Сборка Termux, в которую **SmoothCursor встроен прямо в APK**: плавный курсор как в Neovide, плавная прокрутка и текстовые эффекты.

> ## ✅ Без root. Без LSPosed. Без Xposed.
> 👉 Мой прошлый проект: **[github.com/AstrodiR2/SmoothCursor](https://github.com/AstrodiR2/SmoothCursor)** работал **только с root** (модуль Xposed/LSPosed).
> **Root больше не нужен.** SmoothCursor теперь встроен прямо в APK Termux: установил APK и всё работает. Тот репозиторий устарел, пользуйся этим.

<sub>Неофициальный форк [Termux](https://github.com/termux/termux-app).</sub>

Автор: **[AstrodiR2](https://github.com/AstrodiR2)**.

---

## Возможности

- Плавный анимированный курсор (как в Neovide): цвет, обводка и шлейф настраиваются
- Плавная прокрутка с регулировкой скорости
- Отступы, свечение и плавное появление текста
- Все настройки внутри приложения, ничего внешнего настраивать не нужно
- Monet / Material You (`Termux:Monet`)

**Намеренно не перенесено:** настройки изображений, фона и заставки, графические протоколы iTerm2/kitty.

## Установка

1. Открой вкладку **[Actions](../../actions)** этого репозитория, выбери последний успешный запуск **Build APK** и скачай артефакт `termux-monet-universal` (или загляни в [Releases](../../releases), если там есть релиз).
2. Распакуй артефакт и установи `.apk` (разреши «Установку из неизвестных источников» для браузера/файлового менеджера).
3. Запусти приложение. Никаких модулей и перезагрузки.

> ⚠️ **Прочитай перед установкой поверх обычного Termux**
> Эта сборка подписана другим ключом, чем Termux из F-Droid / GitHub / Play Store, а имя пакета то же (`com.termux`). Android **не даст обновить** поверх, поэтому старый Termux придётся удалить, а **все его данные при этом сотрутся**.
> Сначала сделай бэкап в старом Termux:
> ```bash
> termux-setup-storage
> tar -czf /sdcard/termux-backup.tar.gz -C /data/data/com.termux/files ./home ./usr
> ```
> После установки этой сборки восстанови:
> ```bash
> tar -xzf /sdcard/termux-backup.tar.gz -C /data/data/com.termux/files --recursive-unlink --preserve-permissions
> ```
> Плагины Termux (Termux:API, Boot, Widget и т.д.) должны быть подписаны тем же ключом, что и основное приложение, так что ставь подходящие сборки.

## Как пользоваться

1. **Удерживай** палец на терминале, откроется контекстное меню.
2. Нажми **Настройки SmoothCursor**.
3. Настрой курсор (цвет, обводка, шлейф), скорость прокрутки, отступы, свечение и появление текста.
4. Изменения применяются сразу.

## Сборка самому

Нужны JDK 17 и Android SDK:

```bash
./gradlew :app:assembleDebug
```

APK (универсальный и под каждую ABI) появятся в `app/build/outputs/apk/debug/`.

Или пусть соберёт GitHub: запушь в свой репозиторий и запусти workflow **Build APK** (`.github/workflows/build.yml`), затем скачай APK из *Artifacts* запуска.

## Форки и авторство

Форки и изменения приветствуются, но **пожалуйста, указывай автора**: оставь ник **AstrodiR2** и/или ссылку на оригинальный репозиторий
(<https://github.com/AstrodiR2/SmoothCursor>) в своём README. Спасибо!

## На чём основано

- [Termux](https://github.com/termux/termux-app), приложение-основа (оригинальный README лежит в [`docs/upstream/`](docs/upstream/TERMUX_UPSTREAM_README.md))
- Оригинальный [Xposed-модуль SmoothCursor](https://github.com/AstrodiR2/SmoothCursor)

## Лицензия

Termux и его компоненты распространяются под **GPLv3** (и другими лицензиями из файлов `LICENSE*`). Производные работы должны оставаться на тех же условиях.
