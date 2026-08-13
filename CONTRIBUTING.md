# Contributing to hier-set

You can contribute bug reports, fixes, and focused features to `hier-set`.

## Before you start

- For work beyond a small fix, **open an issue first**. We can agree on the
  approach before you spend time on the work.
- Check existing issues and pull requests to avoid duplicate work.

## Development

This is a Clojure library. You need a JDK and [Leiningen](https://leiningen.org/).
Projects that use `deps.edn` use the Clojure CLI instead. See the README.

```bash
lein test     # run the test suite
lein check    # AOT-compile; must be free of reflection warnings
```

Make sure each change meets these requirements:

- **Tests first.** Add or update tests for the behavior you change. For a bug
  fix, add a regression test that fails before the fix and passes after it.
- **Green build.** `lein test` must pass and `lein check` must report **zero**
  reflection warnings.
- **One scope.** Keep each pull request to one logical change.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …).
- Use the imperative mood for the subject. Keep it under about 72 characters.
- Update `CHANGELOG.md` when users can see your change.
- Rebase on the latest `main` before opening the pull request.

## License

By contributing, you agree that your contributions will be licensed under the
same license as this project (see `LICENSE` / the README).
