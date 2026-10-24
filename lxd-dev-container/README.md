# LXD dev container (`build-system-dev`)

Cloud-init provisioning for an LXD container used to develop this build-system
project. The container runs **Ubuntu 26.04 (resolute)** with `build-system-dev`
as its hostname and provides:

| Tool        | Version / source                                    |
|-------------|-----------------------------------------------------|
| JDK         | 21 (project default) + 25 (experiments), Ubuntu apt |
| Maven       | apt (`3.9.12` on 26.04, the project's pinned version) |
| Node.js     | 20 via NodeSource (needed by `spring-security/javascript`) |
| Shell       | zsh + oh-my-zsh                                     |
| opencode    | official installer, under the primary user          |
| Project     | repo bind-mounted at `/workspace/build-system`      |

User state that should survive container recreation is kept in **LXD custom
storage volumes** on the `default` pool (mounted, not bind-mounted):

| Volume | Mounted at | Holds |
|--------|-----------|-------|
| `build-system-m2` | `/home/noavarice/.m2` | Maven local cache (`~/.m2/repository`, `settings.xml`) |
| `build-system-opencode-config` | `/home/noavarice/.config/opencode` | opencode's per-user config/auth |

The volumes are created by `setup.sh` on first run and are **independent of the
container**: deleting or re-provisioning the container does not touch them, so
the downloaded Maven artifacts and opencode state persist across recreations.

## Files

- `cloud-init-build-system-dev.yaml` — the `#cloud-config` user-data.
- `setup.sh` — idempotent `lxc init`/mount/start sequence.

## Usage

```bash
bash lxd-dev-container/setup.sh
```

This:
1. ensures the persistent storage volumes (`build-system-m2`,
   `build-system-opencode-config`) exist,
2. deletes any existing `build-system-dev` container,
3. `lxc init` from `ubuntu:26.04` with the cloud-init user-data,
4. adds a disk device mounting this repo → `/workspace/build-system`, plus disk
   devices for the two persistent state volumes → `/home/noavarice/.m2` and
   `/home/noavarice/.config/opencode`,
5. starts it and waits for `cloud-init status --wait`.

Re-running `setup.sh` reuses the existing volumes, so Maven's `~/.m2` and
opencode's `~/.config/opencode` survive the container being deleted and
recreated.

Then work inside it:

```bash
lxc exec build-system-dev -- su - noavarice
cd /workspace/build-system && mvn clean install
```

Or run a single build without a shell:

```bash
lxc exec build-system-dev -- bash -lc \
  'cd /workspace/build-system && mvn -pl lib -am clean install'
```

## Notes

- **User & uid.** The container's primary user is `noavarice` (same name/uid as the
  host user) so the bind-mounted project tree is writable without uid shifting on
  LXD's default unprivileged containers. `setup.sh` applies `raw.idmap 'both 1000 1000'`
  to map the container uid/gid 1000 to host uid/gid 1000; without it the host files
  appear owned by `nobody` and Maven's `clean` cannot delete pre-existing `target/`.
- **Cloud-image home ownership quirk.** On the Ubuntu cloud image the `users:` home
  dir is left owned by `root`; the cloud-init `runcmd` chowns it to the user so
  per-user tools (oh-my-zsh, opencode, `~/.m2`) install cleanly.
- **No password / ssh.** `lock_passwd: true`; access is via `lxc exec` / `su -`.
  Root remains reachable with the bare `lxc exec build-system-dev -- bash`.
- **Maven.** The repo has no `mvnw`, so use the container's `mvn`. As in any
  fresh clone there is no repository proxy running inside the container; the
  network-dependent ITs in `mvn verify` bring their own in-process repository
  proxy (see `AGENTS.md`), so no extra service is needed for `mvn clean install`.
- **Re-provisioning.** Re-running `setup.sh` rebuilds the container but preserves
  the state volumes (`~/.m2`, `~/.config/opencode`). If you only want to bump
  packages, `lxc exec build-system-dev -- apt update && apt upgrade` and
  `lxc restart build-system-dev` instead.
- **Volumes vs. bind mounts.** The project tree (`.git`, `spring-security`
  submodule) must stay a host bind mount so the host shares it, but `~/.m2` and
  `~/.config/opencode` are filesystem storage volumes that persist across
  container recreations. To purge persistent state, delete them explicitly:
  `lxc storage volume list` then `lxc storage volume delete default <name>`.
- **Removing a container keeps the volumes.** `setup.sh` deletes and recreates the
  container each run; the LXD custom-volume state survives. Only explicit
  `lxc storage volume delete` removes it.