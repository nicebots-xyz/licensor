## licensor@0.2.1

### Release binaries are attached again

Releases are created as drafts, get their binaries, then are published, as immutable releases require. v0.2.0 has no binaries: use v0.2.1 or later.

## licensor@0.2.0

### setup-licensor installs its own release by default

The `version` input of setup-licensor is optional: without it, the action installs the licensor release it ships with, so updating the action pin updates licensor too. Passing `version` still installs any release.
