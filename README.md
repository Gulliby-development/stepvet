<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# stepvet

stepvet is an offline command-line tool for people who write behavior specs in the Given/When/Then text format: it lints spec files, selects scenarios with tag expressions, and checks every step against a list of step patterns.

[![CI](https://github.com/Gulliby-development/stepvet/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/stepvet/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Why

Specs written as `Feature:`, `Scenario:`, `Scenario Outline:` and `Examples:` tables are usually only checked by a full test runner. stepvet is a standalone check you can put in a pre-commit hook or a CI step: a misplaced tag, a placeholder with no column, a scenario without a `Then` step, a step with no code or with two matching code lines show up immediately, with a file, line and column. It reads only the files you name, makes no network call and starts no other program.

## Install

Install Java 21 or newer and Maven. From the project root, build and run the test suite:

```sh
mvn -B verify
```

Run the application with Java:

```sh
java -cp target/classes io.github.gulliby.stepvet.Main --help
```

The general invocation is `java -cp target/classes io.github.gulliby.stepvet.Main` followed by one of the commands below.

## Usage

The available commands are:

```text
usage: stepvet check FILE...
       stepvet select EXPR FILE...
       stepvet steps [--all] DEFS FILE...
```

`--help` prints the usage text when it is the only argument. Use `-` as a file name to read standard input (once). Files must be UTF-8; a leading byte order mark is ignored and CR LF line endings are accepted.

Exit code `0` means success, `1` means a negative result (lint problems, no selected scenarios, or undefined/ambiguous steps), and `2` means an input, argument or syntax error. In that case standard output is empty and standard error holds one line starting with `stepvet: `. Output is UTF-8 with LF line endings on Windows, macOS and Linux. Columns count UTF-16 characters.

### Example files

Use this content for `cart.txt`:

```gherkin
# shopping cart
@shop
Feature: Cart
  Customers fill a cart.

  Background:
    Given an empty cart

  @smoke
  Scenario: Add one item
    When I add 1 "apple"
    Then the cart holds 1 item

  @slow @smoke
  Scenario Outline: Add many
    When I add <n> "<name>"
    Then the cart holds <n> items

    @eu
    Examples:
      | n | name |
      | 3 | a\|b |

    Examples: more
      | name | n  |
      | fig  | 10 |
```

### check

`check` prints every problem of every file as `FILE:LINE:COL: CODE TEXT`, then the number of problems (exit 1), or `ok, N scenarios in M files` (exit 0).

```sh
java -cp target/classes io.github.gulliby.stepvet.Main check cart.txt
```

```text
ok, 2 scenarios in 1 file
```

For a file `broken.txt` with the lines `Feature: Broken`, `  Scenario: No assertion` and `    Given a thing`:

```text
broken.txt:2:3: SV104 scenario has no Then step
1 problem
```

The codes `SV001` to `SV008` are syntax problems (a tag in the wrong place, an unterminated table row, a row with the wrong number of cells and so on); `SV101` to `SV109` are lint problems (a scenario with no steps, a duplicate scenario name, a first step starting with `And`, no `Then` step, a step kind out of order, an outline without `Examples:`, a placeholder with no column, an unused column, an `Examples:` block without rows).

### select

`select` lists every scenario (an outline gives one entry per `Examples:` row) whose tags satisfy a tag expression, then `selected N of M`. It exits 0 when at least one scenario is selected, else 1.

```sh
java -cp target/classes io.github.gulliby.stepvet.Main select "@smoke and not @eu" cart.txt
```

```text
cart.txt:10: Add one item
cart.txt:26: Add many (row 2)
selected 2 of 3
```

Tag expressions combine tags with `and`, `or`, `not` and parentheses; `or` binds weaker than `and`, and `not` binds tightest. `@eu or @smoke and not @slow` and `not (@slow or @eu)` are valid expressions. A scenario has the tags of the `Feature:`, of the scenario and, for an outline row, of its `Examples:` block.

### steps

`steps` matches every step text of the files (outline cells substituted) against a definitions file with one step pattern per line. Use this content for `defs.txt`:

```text
# step definitions
an empty cart
I add {int} {string}
the cart holds {int} item(s)
the cart is/was empty
I remove {word}
{} cart
```

```sh
java -cp target/classes io.github.gulliby.stepvet.Main steps defs.txt cart.txt
```

```text
cart.txt:7: ambiguous: an empty cart matches defs.txt:2, defs.txt:7
defs.txt:5: unused: the cart is/was empty
defs.txt:6: unused: I remove {word}
steps: 7, undefined: 0, ambiguous: 1, unused patterns: 2
```

The command exits 1 when a step is undefined or ambiguous; unused patterns alone exit 0. Add `--all` after `steps` to also list the steps that have exactly one match with their captured arguments:

```text
cart.txt:11: ok: I add 1 "apple" -> defs.txt:3 ("1", "apple")
cart.txt:12: ok: the cart holds 1 item -> defs.txt:4 ("1")
cart.txt:16: ok: I add 3 "a|b" -> defs.txt:3 ("3", "a|b")
cart.txt:16: ok: I add 10 "fig" -> defs.txt:3 ("10", "fig")
cart.txt:17: ok: the cart holds 3 items -> defs.txt:4 ("3")
cart.txt:17: ok: the cart holds 10 items -> defs.txt:4 ("10")
```

### Options

| Option | Description |
|---|---|
| `--help` | Print the usage lines (only as the single argument). |
| `--all` | `steps` only: also list the steps with exactly one match. |
| `-` | As a file name: read standard input. |

## Step patterns

A definitions file contains one pattern per line; empty lines and lines starting with `#` are skipped. A pattern must match the entire step text. Captures are greedy first, then give characters back when needed to make the rest of the pattern match; when alternatives are present, they are tried from left to right.

- `{int}` captures an optional minus sign and ASCII digits: it accepts `-5` and `007`, but not `+5` or `5.0`.
- `{float}` captures an integer or a decimal with digits on both sides of the dot: it accepts `5` and `-0.5`, but not `.5` or `5.`.
- `{word}` captures a nonempty run without spaces or tabs: `a-b` matches, while `a b` does not.
- `{string}` captures text inside matching single or double quotes, without the quotes. It accepts `"a b"`, `'it'`, and `""`.
- `{}` captures any nonempty text.
- Parenthesized literal text is optional, so `item(s)` matches `item` or `items`.
- A slash separates ordered alternatives in a word, so `a/b c` matches `a c` or `b c`.
- Backslash escapes special characters. For example, `x\(y\) a\/b` matches the literal text `x(y) a/b`.

Literal spaces and tabs in a pattern are significant. Parameters may need to backtrack to satisfy later pattern text, while alternative order determines which matching form is tried first. An invalid pattern is reported as `DEFS:LINE:COL: invalid pattern: MESSAGE` with exit code 2.

## Development

Run the full build and tests from the project root:

```sh
mvn -B verify
```

The source targets Java 21 and uses only the Java standard library at runtime; JUnit 5 is the only (test) dependency. Keep examples and tests deterministic; do not rely on network access, environment-specific output, or the system's default text encoding. CI runs the build on Linux, Windows and macOS with Java 21 and 25.

## License

MIT, see [LICENSE](LICENSE).

---

Built with BAKED.
