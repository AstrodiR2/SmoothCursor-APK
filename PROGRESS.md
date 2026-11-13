# Прогресс — этап 3 из 5

## Этап 1 — сделано
- Разделы настроек, формы курсора, дыхание, градиент/радуга и единая таблица несовместимостей.

## Этап 2 — сделано
- Добавлена картинка-курсор: системный выбор файла, собственный редактор обрезки по выбранной форме (перетаскивание, pinch и ползунок масштаба; кнопки отмены/применения сверху).
- Изображение копируется в приватное хранилище приложения. При импорте один раз вычисляется средний цвет видимых пикселей, который используется для цветного шлейфа.
- Изображение напрямую рисуется внутри геометрии курсора через Canvas clipping; fallback к картинке-маске не потребовался.
- Добавлена подсветка строки только в alternate screen buffer, с плавным следованием за курсором и настройками включения, цвета, прозрачности и скорости.
- Новые функции вынесены в отдельные разделы главного меню настроек; строки добавлены в английскую и русскую локализации.
- Пульс прозрачности заменяет дыхание при изменении видимого содержимого терминала и после короткой паузы дыхание возвращается. Это эвристика активности вывода, а не точное определение состояния процесса.
- Проверка: `./gradlew :app:assembleDebug` с JDK 17 завершилась успешно.

## Этап 3 — сделано
- Добавлен один общий shell-hook и протокол событий OSC для эффектов ошибок, командного пульса и правил цвета.
- Добавлены генерация и установка hook-строки в rc-файлы bash, zsh, fish, tcsh и nushell; при отключении функции строка удаляется, при пропаже включённой строки — восстанавливается с Toast. Для неподдерживаемого shell элементы управления отключены и показывается Toast.
- Добавлена плавная цветовая реакция на ненулевой exit code, отдельный цвет Ctrl+C (130), выбор покраски/вспышки, длительности и силы.
- Добавлены настройки командного пульса, короткой вибрации в активном приложении и тихого уведомления в фоне; уведомление не содержит текст команды.
- Добавлены упорядоченные редактируемые правила цвета с общим/индивидуальным цветом, буквальным регистрозависимым сопоставлением по отдельным словам и настройкой пульса для каждого правила. Исходный набор включает пакетные менеджеры, загрузчики, git, gradle и `./gradlew`.
- Совпавшее правило временно переопределяет цвет курсора и подавляет конфликтующие визуальные эффекты без изменения сохранённых настроек.
- Строки интерфейса и уведомлений добавлены в `values` и `values-ru`.
- Проверки: XML ресурсов корректен, дублирующихся строк нет; `bash -n` прошёл; `./gradlew :app:assembleDebug` с JDK 17 и Android SDK 34 завершился успешно.

## Осталось
- Этапы 4–5.

## Изменённые файлы этапа 3
- `PROGRESS.md`.
- `terminal-view/src/main/java/com/termux/view/SmoothCursorEffects.java`.
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java`.
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java`.
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalSessionClient.java`.
- `termux-shared/src/main/java/com/termux/shared/termux/terminal/TermuxTerminalSessionClientBase.java`.
- `app/src/main/java/com/termux/app/activities/SmoothCursorShellHook.java`.
- `app/src/main/java/com/termux/app/activities/SmoothCursorSettingsActivity.java`.
- `app/src/main/java/com/termux/app/terminal/SmoothCursorCommandHandler.java`.
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java`.
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionServiceClient.java`.
- `app/src/main/java/com/termux/app/TermuxActivity.java`.
- `app/src/main/java/com/termux/app/TermuxApplication.java`.
- `app/src/main/res/values/strings.xml`.
- `app/src/main/res/values-ru/strings.xml`.
