#!/usr/bin/env python3
"""Writes the licence texts the app shows under Settings > About > Legal information.

    python3 tools/build_license_assets.py

Copies COPYING and COPYING.AGPL into app/src/main/assets/licenses/. Run it after
either of those files changed; LegalInformationTest fails if the assets are out of
date.

17.4.3: the app no longer carries a list of open source components — the row in
Legal information is gone with the text behind it (app/src/main/assets/licenses/
components.txt). The list of what was taken from where, with the licence of each
part, is in LICENSE.md of the repository.

Own code of Foss Explorer, GPL-3.0.
"""
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'licenses')


def read(*path):
    with open(os.path.join(ROOT, *path), encoding='utf-8') as f:
        return f.read()


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {
        'GPL-3.0.txt': read('COPYING'),
        'AGPL-3.0.txt': read('COPYING.AGPL'),
    }
    for name, text in files.items():
        with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
            f.write(text)
        print('%s: %d bytes' % (name, len(text.encode('utf-8'))))


if __name__ == '__main__':
    main()
