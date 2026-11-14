# Preparing SplitSmart for Git

Initialize and commit from the `splitsmart` folder containing `pom.xml`. The current workspace has no Git repository yet.

```sh
git init
git add .
git status --short
git diff --cached --stat
```

The ignore rules exclude the entire `data/` folder: the H2 database, account/password hashes, groups, expenses, repayments, and legacy files all stay on your machine. Local secrets, credential files, generated archives/JARs, logs, dependencies, and personal IDE state are excluded too. Source, tests, migrations, Maven wrapper, formatter settings, and documentation can be committed.

Ignoring a file does not delete it. Back up `data/` separately while the app is stopped. Git is not a backup for your private database. A fresh clone starts with a fresh database and accounts; your existing local database continues to work in this folder.

Before the first commit, these commands should print nothing:

```sh
git ls-files -- data .env secrets credentials backups dumps
git ls-files -- '*.db' '*.mv.db' '*.bin' '*.pem' '*.key' '*.jar' '*.zip' '*.log'
```

If private files were already tracked in a different copy of the repository, `.gitignore` alone will not untrack them. Remove only the tracked copy from Git's index with `git rm --cached` for each affected file, leaving local files intact. Removing a file from the latest commit does not remove it from existing Git history.

The parent workspace also has a guard `.gitignore` that excludes `work/` (including private backups) and generated outputs if a repository is accidentally initialized there. Do not copy private screenshots, database backups, or actual credentials into source files.
