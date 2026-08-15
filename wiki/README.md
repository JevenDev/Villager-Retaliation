# Wiki Sources

Documentation and its static-site code live under this directory.

- `player/` builds the player wiki deployed at the GitHub Pages root.
- `player/content/` contains its hand-authored guide content.
- `dev/` builds the developer wiki deployed under `/developer-wiki/`.
- `dev/content/` contains the Markdown source pages for pack authors and runtime internals.
- `shared/` contains assets and browser code used by both wiki sites.

Generated browser artifacts are committed so the static tools work without a build step. Do not edit `site-data.js` or the Datapack Generator's `wiki-snapshot.js` directly. Regenerate them from their source content, then run the repository verifier:

```text
node wiki/player/build-data.mjs
node wiki/dev/build-data.mjs
node tools/datapack-builder/build-wiki-snapshot.mjs
node tools/verify.mjs
```
