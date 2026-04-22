# deepseek-ai-agent

## RU

Java-агент для автоматизации DeepSeek Chat через Selenium (Microsoft Edge).

### Возможности

- Интерактивный режим:
  - новый диалог,
  - продолжение сохраненного диалога,
  - удаление сохраненных диалогов.
- Выполнение операций из JSON-ответа модели:
  - `CMD`
  - `CMD_WAIT`
  - `TEXT`
  - `END`
- Подробные логи:
  - отправленный промпт,
  - сырой и очищенный ответ модели,
  - локальная команда и результат.
- Поддержка запуска браузера в `--headless`.
- Поддержка неинтерактивного запуска через `--exec`.

### Требования

- Windows
- JDK 21+
- Maven (или запуск из IDE)
- Microsoft Edge
- `resources/drivers/msedgedriver.exe`

### Где хранятся данные

- `%USERPROFILE%\\.vibecoder\\edge_profile_deepseek`
- `%USERPROFILE%\\.vibecoder\\sessions\\*.json`
- `%USERPROFILE%\\.vibecoder\\current-session.txt`

Старые данные из папки проекта мигрируются автоматически при старте (если возможно).

### Запуск

Обычный (интерактивный):

```powershell
java -cp target/classes ru.sibgatulinanton.App
```

Интерактивный + headless:

```powershell
java -cp target/classes ru.sibgatulinanton.App --headless
```

Одноразовый запуск (`--exec`):

```powershell
java -cp target/classes ru.sibgatulinanton.App --exec "Что за проект"
```

`--exec` в конкретном диалоге (`--thread`):

```powershell
java -cp target/classes ru.sibgatulinanton.App --exec "продолжай" --thread d77812e8-cd6f-471d-abdf-1d23c096e311
```

или:

```powershell
java -cp target/classes ru.sibgatulinanton.App --exec "проверь статус" --thread "https://chat.deepseek.com/a/chat/s/d77812e8-cd6f-471d-abdf-1d23c096e311"
```

Комбинация с `--headless`:

```powershell
java -cp target/classes ru.sibgatulinanton.App --headless --exec "сделай проверку" --thread d77812e8-cd6f-471d-abdf-1d23c096e311
```

Сборка:

```powershell
mvn clean package
```

### Важно про `--exec`

- Если `--thread` не указан:
  - открывается новый чат,
  - отправляется base prompt (`first_prompt`) с подстановками `{TASK}`, `{OS}`, `{WORKSPACE}`, `{CMD}`.
- Если `--thread` указан:
  - открывается указанный тред,
  - отправляется только переданный текст (без base prompt).
- После завершения сценария браузер корректно закрывается.

### Частые проблемы

- `User is NOT authenticated in DeepSeek`
  - авторизуйтесь в открытом окне Edge и нажмите Enter в консоли.
- Предупреждения CDP/SLF4J
  - обычно не блокируют работу агента.

---

## EN

Java agent for automating DeepSeek Chat via Selenium (Microsoft Edge).

### Features

- Interactive mode:
  - start a new dialog,
  - resume a saved dialog,
  - delete saved dialogs.
- Executes model JSON operations:
  - `CMD`
  - `CMD_WAIT`
  - `TEXT`
  - `END`
- Detailed logs:
  - outgoing prompt,
  - raw and cleaned model response,
  - local command and result.
- Supports browser launch in `--headless`.
- Supports non-interactive run via `--exec`.

### Requirements

- Windows
- JDK 21+
- Maven (or run from IDE)
- Microsoft Edge
- `resources/drivers/msedgedriver.exe`

### Data storage

- `%USERPROFILE%\\.vibecoder\\edge_profile_deepseek`
- `%USERPROFILE%\\.vibecoder\\sessions\\*.json`
- `%USERPROFILE%\\.vibecoder\\current-session.txt`

Legacy data from the project folder is migrated automatically on startup (when possible).

### Run

Regular interactive mode:

```powershell
java -cp target/classes ru.sibgatulinanton.App
```

Interactive + headless:

```powershell
java -cp target/classes ru.sibgatulinanton.App --headless
```

One-shot run (`--exec`):

```powershell
java -cp target/classes ru.sibgatulinanton.App --exec "What is this project?"
```

`--exec` in a specific dialog (`--thread`):

```powershell
java -cp target/classes ru.sibgatulinanton.App --exec "continue" --thread d77812e8-cd6f-471d-abdf-1d23c096e311
```

or:

```powershell
java -cp target/classes ru.sibgatulinanton.App --exec "check status" --thread "https://chat.deepseek.com/a/chat/s/d77812e8-cd6f-471d-abdf-1d23c096e311"
```

Combined with `--headless`:

```powershell
java -cp target/classes ru.sibgatulinanton.App --headless --exec "run check" --thread d77812e8-cd6f-471d-abdf-1d23c096e311
```

Build:

```powershell
mvn clean package
```

### `--exec` behavior

- If `--thread` is not provided:
  - opens a new chat,
  - sends base prompt (`first_prompt`) with `{TASK}`, `{OS}`, `{WORKSPACE}`, `{CMD}` substitutions.
- If `--thread` is provided:
  - opens the specified thread,
  - sends only the provided text (no base prompt).
- Browser is always closed on shutdown.

### Common issues

- `User is NOT authenticated in DeepSeek`
  - sign in within Edge window and press Enter in console.
- CDP/SLF4J warnings
  - usually do not block agent execution.

---

## Search Keywords

`deepseek ai agent`, `deepseek selenium`, `deepseek chat automation`, `java selenium edge`, `edge webdriver deepseek`, `json command executor`, `cmd_wait automation`, `headless deepseek bot`, `deepseek thread resume`, `deepseek --exec --thread`, `windows java ai agent`, `chat automation tool`
