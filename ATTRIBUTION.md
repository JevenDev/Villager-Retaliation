# Shader attribution

The map cloud effect adapts two shaders released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/):

- [Clouds in motion](https://godotshaders.com/shader/clouds-in-motion/) by gerardogc2378.
- [Pixelated, radial 2D-Clouds](https://godotshaders.com/shader/pixelated-radial-2d-clouds/) by Miaurin.

CC0-adapted code in `neoforge/src/main/resources/assets/villagerretaliation/shaders/core/map_fog.fsh`:

- Lines 95-97 (`cloudTexel`): pixel snapping from Pixelated, radial 2D-Clouds.
- Lines 105-110, 112-124, and 126-137 (`cloudCrest`, `cloudLayer`, `cloudBankCrest`): cloud shapes from Clouds in motion.
- Lines 148-200 (`parchmentClouds`): combined moving noise, boundary shaping, and cloud layering from both shaders.
