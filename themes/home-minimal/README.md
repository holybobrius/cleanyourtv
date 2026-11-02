# Home minimal

The customised Home for the Xiaomi MiTV-MOEU1 (tag `home-v2`), next to the earlier reference look in `themes/home-default`
(tag `home-v1`):

- one header row: the date over a large time on the left; on the right a round menu button and the Google account's
  picture (`minimal_header`). A focused button turns white and opens out into its label, pushing its neighbour along;
- the menu is a side panel from the right with Inputs, Wi-Fi, Bluetooth, VPN, Background, All apps and Settings;
- focus as on Google TV (`glass_style: outline`): the tile grows by 2.5% and gets its own white outline; the other tiles dim;
- Down from the five-app panel opens all apps as a full-screen list;
- video wallpapers (`videos/`), picked under Menu → Background, with the theme's picture as the last choice; switches fade;
- after `idle_fade_seconds` (10) without a key, everything but the date and time fades out; the next key only wakes Home.

Nothing personal is in the repository: add your own files to this folder before pushing it.

- `wallpaper.jpg`: the picture.
- `videos/*.mp4` and `videos/captions.txt` (`file<TAB>name` per line): the video wallpapers. 1080p H.264 without sound
  plays best, e.g. `ffmpeg -i in.mp4 -an -vf scale=1920:1080,fps=30 -c:v libx264 -crf 20 -movflags +faststart out.mp4`.
- `avatars/*.png|jpg` and `"account_profiles": ["you@gmail.com|Your name|you.png"]` in `config.json`: the account
  button's name and picture. The account part may be the address or the part before the @. A launcher sees a Google
  account only after it was picked once in Android's chooser, which OK on the account button opens.

```sh
TV_HOST=<tv ip> tools/theme.sh themes/home-minimal
```

The launcher code for this look is the `home-v2` tag: `git checkout home-v2 && tools/docker-build.sh`.
