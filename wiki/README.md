# Wiki Sources

Documentation and its static-site code live under this directory.

- `player/` builds the player wiki deployed at the GitHub Pages root.
- `player/content/` contains its hand-authored guide content.
- `dev/` builds the developer wiki deployed under `/developer-wiki/`.
- `dev/content/` contains the Markdown source pages for pack authors and runtime internals.

Regenerate checked-in site data after changing source content:

```text
node wiki/player/build-data.mjs
node wiki/dev/build-data.mjs
```
