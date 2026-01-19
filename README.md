<!--
SPDX-License-Identifier: MIT
Copyright: 2026 NiceBots.xyz
-->

# Licensor

Licensor adds copyright notices to files, including formats that need bespoke placement rules.

## Why this exists

There was no software that could coherently handle esoteric file formats. For example, `.astro` files require the
copyright to be added as a JSX comment, but **after** the front matter block, not before it.

## Technology

- Scala 3.8 on the JVM
- CLI parsing with `case-app`
- Filesystem walking and IO via `os-lib`
- YAML parsing via `SnakeYAML`
- Logging with SLF4J + Logback
- Built with sbt (assembly for a single runnable jar)

## Usage

Create a config file (defaults to `licensor-config.yaml`):

```yaml
holder: NiceBots.xyz
spdx: MIT
year: 2026
```

`year` can be a number or a two-item array (for a range). `spdx` is optional; when omitted, the notice uses "All rights
reserved".

Run against one or more input globs:

```bash
licensor add "src/**/*.py"
licensor check "src/**/*.py"
```

Use `-c` to point at a different config path, `--ignore` to exclude globs, and `--verbose` for debug logging.

## Supported extensions

- Hashtag comments: `py`, `sh`, `bash`, `zsh`, `rb`, `pl`, `ps1`, `psm1`, `psd1`, `yml`, `yaml`, `toml`, `ini`, `cfg`,
  `conf`
- Slash-slash comments: `js`, `jsx`, `ts`, `tsx`, `java`, `scala`, `kt`, `kts`, `rs`, `go`, `c`, `cpp`, `cc`, `cxx`,
  `h`, `hpp`, `cs`, `swift`, `dart`, `php`
- Astro (HTML comments after frontmatter): `astro`
- MDX (JSX comments after frontmatter): `mdx`
- HTML-like block comments: `html`, `htm`, `xhtml`, `xml`, `svg`, `svelte`, `md`, `vue`, `hbs`, `handlebars`, `mustache`
- CSS-like block comments: `css`, `scss`, `sass`, `less`
- CMD line comments: `cmd`

## Config reference

| Key      | Type                       | Required | Description                                                 |
|----------|----------------------------|----------|-------------------------------------------------------------|
| `holder` | string                     | yes      | Copyright holder name.                                      |
| `spdx`   | string                     | no       | SPDX license identifier; defaults to "All rights reserved". |
| `year`   | number or [number, number] | no       | Year or inclusive year range; defaults to current year.     |

## Exit codes

- `0`: Success (check passes; add did nothing).
- `1`: Failure (missing licenses in check, files updated in add, or no inputs matched).

## Inspiration

- HashiCorp copywrite: https://github.com/hashicorp/copywrite
- NWA: https://github.com/B1NARY-GR0UP/nwa
