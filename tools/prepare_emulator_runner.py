"""Adapt only API 37's checked-out pinned runner; never download SDK tools."""
from pathlib import Path


def prepare(path: Path) -> None:
    source = path.read_text()
    create = '        // create AVD\n'
    launch = '        // launch an emulator\n'
    if source.count(create) != 1 or source.count(launch) != 1:
        raise ValueError('Pinned emulator action boundaries changed')
    source = source.replace(create, '        core.addPath(process.env.COMMONPLACE_CMDLINE_TOOLS_BIN);\n' + create)
    # The action catches pre-launch-hook failures and boots anyway; gate outside it.
    source = source.replace(launch, "        await exec.exec('bash', ['-e', 'tools/assert_avd_target.sh']);\n" + launch)
    path.write_text(source)


if __name__ == '__main__':
    prepare(Path('.diagnostic-emulator-runner/lib/main.js'))
