# Contributing to hier-set

You can contribute bug reports, fixes, and focused features to `hier-set`.

## Before you start

- For work beyond a small fix, **open an issue first**. We can agree on the
  approach before you spend time on the work.
- Check existing issues and pull requests to avoid duplicate work.

## Development

This is a Clojure library built with `deps.edn` and the
[Clojure CLI](https://clojure.org/guides/install_clojure); Leiningen is not
required. You need a JDK and the Clojure CLI. See the README for the full set
of aliases.

```bash
clojure -M:test    # run the test suite (compiled with *warn-on-reflection* on)
```

Make sure each change meets these requirements:

- **Tests first.** Add or update tests for the behavior you change. For a bug
  fix, add a regression test that fails before the fix and passes after it.
- **Green build.** The test suite must pass and the build must report **zero**
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
