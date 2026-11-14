#!/usr/bin/env python3
"""Says how much of the app's code still comes from FOSS Browser.

    python3 tools/upstream_audit.py

For every source file of the app, git is asked where each line comes from
(`git blame`, following lines that were moved or copied). A line counts as
FOSS Browser's if it was last written in a commit of the upstream history,
which ends at 79654ad7. The result goes into LICENSE.md.

Own code of Foss Explorer, GPL-3.0. Needs the git history.
"""
import collections
import os
import subprocess

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
UPSTREAM_HEAD = '79654ad7'
CODE = 'app/src/main/java'
PREFIX = CODE + '/com/rpooi3/explorer/'


def git(*args):
    return subprocess.run(('git',) + args, cwd=ROOT, capture_output=True, text=True, errors='replace').stdout


def main():
    upstream = set(git('rev-list', UPSTREAM_HEAD).split())
    rows = []
    for name in git('ls-files', CODE).split('\n'):
        if not name or not os.path.exists(os.path.join(ROOT, name)):
            continue
        total = from_upstream = 0
        for line in git('blame', '-w', '-M', '-C', '--line-porcelain', 'HEAD', '--', name).split('\n'):
            first = line.split(' ')[0]
            if len(first) == 40 and len(line.split(' ')) >= 3 and all(c in '0123456789abcdef' for c in first):
                total += 1
                from_upstream += first in upstream
        rows.append((name, from_upstream, total))

    lines = sum(r[2] for r in rows)
    kept = sum(r[1] for r in rows)
    print('%d files, %d lines; %d lines (%d %%) in %d files are FOSS Browser\'s'
          % (len(rows), lines, kept, round(100.0 * kept / lines), sum(1 for r in rows if r[1])))
    by_package = collections.defaultdict(lambda: [0, 0])
    for name, kept, total in rows:
        package = os.path.basename(os.path.dirname(name))
        by_package[package][0] += kept
        by_package[package][1] += total
    print()
    for package, (kept, total) in sorted(by_package.items()):
        print('%-14s %5d of %5d lines (%3d %%)' % (package, kept, total, 100 * kept // max(total, 1)))
    print()
    for name, kept, total in sorted(rows, key=lambda r: -r[1]):
        if kept:
            print('%5d of %5d (%3d %%)  %s' % (kept, total, 100 * kept // total, name.replace(PREFIX, '')))


if __name__ == '__main__':
    main()
