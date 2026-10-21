# Synthetic reader fixture corpus

Generate the small corpus into the Android test assets directory:

```sh
python3 -m pip install -r tools/fixtures/requirements.txt
python3 tools/fixtures/generate_fixtures.py
python3 -m unittest tools/fixtures/test_generate_fixtures.py
```

`--large --output build/local-fixtures` also writes `large-100mib.local.pdf` in the ignored build output directory. Do not generate the large file inside Android assets: Git ignores do not prevent APK packaging. The local file is ignored by Git; its exact size, SHA-256 and page count are recorded in `app/src/androidTest/assets/fixtures/manifest.json`. Do not commit it. Generator dependencies are pinned in `requirements.txt`:

- ReportLab 5.0.1 — BSD License (package metadata: [PyPI release](https://pypi.org/project/reportlab/5.0.1/)).
- Pillow 12.3.0 — MIT-CMU (package metadata: [PyPI release](https://pypi.org/project/Pillow/12.3.0/)).
- pypdf 6.18.1 — BSD-3-Clause (package metadata: [PyPI release](https://pypi.org/project/pypdf/6.18.1/)).

The PDF font is a copy of the locally installed DejaVu Sans `DejaVuSans.ttf`, pinned by SHA-256 in the manifest. Its redistribution notice is in `assets/DejaVu-LICENSE.txt`; source license: [DejaVu Fonts](https://dejavu-fonts.github.io/License.html). No third-party books or image files are used. The scan image is original, generated deterministically by Pillow.

All document text and EPUB packages are original synthetic content. See the manifest for each fixture's SHA-256, expected text/page/labels/reference quads and the explicit limits on those expectations. Geometry is a generator-authored approximation, not evidence of any reader's selection accuracy. The hostile EPUB references loopback port 9 but does not start a server or test the reader's network policy.

Original fixture text, generated graphics and EPUB packages are licensed under the repository Apache-2.0 license. The bundled font retains its separate redistribution license.
