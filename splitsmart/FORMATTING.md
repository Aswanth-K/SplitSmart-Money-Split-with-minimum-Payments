# Formatting code in Kiro

The Java, HTML, CSS, JavaScript, and Python files have been formatted for readability. Formatter configuration is included so the style can be reproduced.

## In Kiro

1. Open the `splitsmart` project folder, not the parent `outputs` folder.
2. In Extensions, install **Language Support for Java by Red Hat** (or Extension Pack for Java) and **Prettier – Code formatter**. Workspace recommendations are in `.vscode/extensions.json`.
3. Open a source file. Use the Command Palette (`Cmd+Shift+P` on macOS, `Ctrl+Shift+P` on Windows/Linux) and choose **Format Document**. If multiple formatters are offered, select **Format Document With…** and choose your formatter.
4. Format on Save is enabled in `.vscode/settings.json`. Java uses the Java language extension; JavaScript, HTML, CSS, JSON, and Markdown use Prettier.

Kiro supports VS Code workspace settings and Open VSX extensions. No IDE extensions have been installed automatically by this change.

You can also run **Tasks: Run Task → Format Java (project style)** from the Command Palette to apply the exact Maven formatter inside Kiro. There are tasks for the frontend and both together in `.vscode/tasks.json`. Run `npm ci` first if using the frontend task.

The Java IDE formatter may differ slightly from the repository's Google Java Format AOSP style. Use the Maven commands below when you want the exact repository Java style. The `.editorconfig` file also records the common indentation and line-ending rules.

## Repeatable command-line formatting

Java (Spotless with Google Java Format 1.33.0, AOSP/four-space style):

```sh
./mvnw spotless:apply
./mvnw spotless:check
```

On Windows, replace `./mvnw` with `mvnw.cmd`. The pinned Java formatter needs JDK 21 or newer; the application itself still targets Java 17. The existing JDK 25 is compatible. Formatting tools are development-only dependencies.

Frontend, JSON, and Markdown (Prettier):

```sh
npm ci
npm run format
npm run format:check
```

Node.js/npm are only needed to run this formatter. Spring Boot still serves the frontend directly, with no npm build step required to run SplitSmart. Prettier is pinned in `package-lock.json`.

Python HTTP tests (Black):

```sh
python3 -m pip install -r requirements-dev.txt
python3 -m black tests
python3 -m black --check tests
```

After intentional code changes, run `./mvnw test` and build the app before running `python3 tests/api_test.py`.

## Official references

- [Kiro Java extensions and formatting tools](https://kiro.dev/docs/guides/languages-and-frameworks/java-guide/)
- [Kiro settings and VS Code compatibility](https://kiro.dev/docs/upgrade-guides/migrating-from-vscode/)
- [Prettier extension formatting settings](https://github.com/prettier/prettier-vscode)
