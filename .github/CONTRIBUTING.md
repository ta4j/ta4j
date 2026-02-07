# How to contribute to ta4j

Thanks for considering contributing to ta4j! Please take a moment to review this document in order to make the contribution process easy and effective for everyone involved.


## Reporting bugs and opening issues

Before you submit your issue [search the archive](https://github.com/ta4j/ta4j/issues?q=is%3Aissue), maybe your question was already answered.

If your issue appears to be a bug, and hasn't been reported, open a new issue. Providing the following information will increase the chances of your issue being dealt with quickly:

  * Overview of the Issue *(if an error is being thrown a non-minified stack trace helps)*
  * Motivation for or Use Case *(explain why this is a bug for you)*
  * Versions *(ta4j version, Java version, etc.)*
  * Reproduce the Error *(provide a [SSCCE](http://sscce.org/) or a unambiguous set of steps)*
  * Related Issues *(has a similar issue been reported before?)*
  * Suggest a Fix *(if you can't fix the bug yourself, perhaps you can point to what might be causing the problem (line of code or commit))*


## Submitting a patch

Each pull request is highly appreciated! Here are some tips to get it merged:

- It's generally best to start by [opening a new issue](https://github.com/ta4j/ta4j/issues) describing the bug or feature you're intending to fix. Even if you think it's relatively minor, it's helpful to know what people are working on. Mention in the initial issue that you are planning to work on that bug or feature so that it can be assigned to you.

- Follow the normal process of [forking](https://help.github.com/articles/fork-a-repo) the project, and set up a new branch to work in. It's important that each group of changes be done in separate branches in order to ensure that a pull request only includes the commits related to that bug or feature.

- Any significant changes should always be accompanied by tests. The project already has good test coverage, so look at some of the existing tests if you're unsure how to go about it. Pull requests of features without tests will not be accepted.

- Do your best to have [well-formed commit messages](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html) for each change. This provides consistency throughout the project, and ensures that commit messages are formatted properly by various git tools.

- **Make sure to format and test your changes with:** `mvn -B clean license:format formatter:format test install`. This will apply the project's license header, formatting rules, and run unit tests. If your changes are missing either the proper license header and/or formatting, **it will fail CI!**

- Finally, push the commits to your fork and submit a [pull request](https://help.github.com/articles/creating-a-pull-request).

### Optional: actionlint pre-push hook

To lint workflow changes before pushing, enable the repo hook once:

```
git config core.hooksPath .githooks
```

Then install `actionlint` (for example: `brew install actionlint`). The `.githooks/pre-push` hook will run actionlint for any modified files under `.github/workflows/`.

Looking for something to work on? Take a look at [the roadmap](https://github.com/ta4j/ta4j/wiki/Roadmap) or [our open issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue+is%3Aopen).
