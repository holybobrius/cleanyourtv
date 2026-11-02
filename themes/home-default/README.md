# Home default

The reference Home for the Xiaomi MiTV-MOEU1, saved as the version to return to (tag `home-v1`):

- a header with a one-line date, the greeting, the top-bar icons and a small clock, all on the same left line as the tiles;
- a quick-access panel of five fixed apps (`dock_apps`, `fill_dock: false`), tiles about 158×89dp with 14dp gaps;
- Down from the panel slides the header away and shows every app in a 5-column grid; Up from its first row or Back returns;
- a faint outline on every tile and the `halo` focus.

The wallpaper is not in the repository. Put your own picture in this folder as `wallpaper.jpg` before pushing,
otherwise `tools/theme.sh` removes the one on the TV:

```sh
TV_HOST=<tv ip> tools/theme.sh themes/home-default
```

The launcher code for this look is the `home-v1` tag: `git checkout home-v1 && tools/docker-build.sh`.
