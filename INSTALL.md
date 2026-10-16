# CJ in Minecraft — install

You play Minecraft as CJ from GTA San Andreas: GTA runs beside Minecraft and gives CJ his animations, weapons, car and clothes, and Minecraft draws
him in your world. **Nothing of GTA is included**: the installer uses the files of your own copy of the game.

## What you need

- **GTA San Andreas version 1.0 US** (`gta_sa.exe` of 14,383,616 bytes). If your copy is the Steam version or another one, downgrade it to 1.0 US
  with a GTA San Andreas *downgrader*.
- **An ASI loader** in the GTA folder: the *Essentials Pack*, *Silent's ASI Loader* or *Ultimate ASI Loader*.
- **CLEO** (4 or 5) in the GTA folder, for the car: the mod makes it appear on a floor that a CLEO script builds. Without CLEO everything else works,
  but the G key brings no car.
- **Minecraft Java 1.21.1** with **Fabric**: the Fabric installer (https://fabricmc.net/use/installer/) and **Fabric API** for 1.21.1
  (https://modrinth.com/mod/fabric-api) in the `mods` folder. The official launcher or one like Prism will do.
- **Python 3.10 or newer** (https://www.python.org/downloads/). On Windows, tick **"Add python.exe to PATH"** when installing it.
- On **Linux**, **Wine** for GTA.

## Install

1. Unzip this package into a folder.
2. Open a terminal in that folder (on Windows: right click inside the folder → "Open in Terminal").
3. Run, with the path of **your** GTA folder (in quotes if it has spaces):

   **Windows**
   ```
   python setup\cj_setup.py install --gta "C:\path\to\GTA San Andreas" --mods
   ```
   **Linux**
   ```
   python3 setup/cj_setup.py install --gta "/path/to/GTA San Andreas" --mods
   ```

   - `--mods` copies the mod into Minecraft's `mods` folder (replacing an older version).
   - If you use a launcher **instance** (Prism, for example), add `--minecraft "<instance folder>"` (the one with `mods` and `config`). Without it
     the official launcher's folder is used: `%APPDATA%\.minecraft` on Windows, `~/.minecraft` on Linux.

   The installer checks your GTA, copies the bridge (`cj_bridge.asi`) and the script of the car's floor (`cj_platform.cs`, in `CLEO` or, if you use
   modloader, in `modloader/CjPlatform`) into the GTA folder, and converts CJ, the weapons and the car from your files.
   It ends with **"done"** and what is still missing, if anything (Fabric API, for example).

## Play

1. **GTA first**: double click `play.bat` (Windows) or run `./play.sh` (Linux). Load your save.
2. **Then Minecraft**, with the **Fabric** profile. Go into a world: your player is CJ.
3. The weapons and items of the mod: `/give @s cj_fabric:<item>` with `colt45`, `desert_eagle`, `chromegun`, `micro_uzi`, `ak47`, `m4`, `minigun`,
   `rocket_launcher`, `baseball_bat`, `jetpack`, `ammo1`, `ammo2`, `ammo3`, `skateboard` (they are also in the creative tabs, have recipes, and turn
   up in chests).
4. The car: **G** to get in and out.

If GTA is not open (or no save is loaded), Minecraft shows the normal player.

## Dress CJ as in your save

With GTA open and your save loaded (the bridge keeps CJ's clothes and body in `cj_outfit.json`), run:

**Windows** `python setup\cj_setup.py dress` — **Linux** `python3 setup/cj_setup.py dress`

and restart Minecraft.

## Weapon icons

The weapon icons made by the community are not included (the ammo ones are). The icons of the **SA Weapon Icons** mod
(https://gtaforums.com/topic/997951-sa-weapon-icons/) work: put them in a folder with these names and run `cj_setup.py icons <folder>`:

| item | file |
|---|---|
| colt45 | Pistol.png |
| desert_eagle | Desert Eagle.png |
| chromegun | Shotgun.png |
| micro_uzi | Micro SMG.png |
| ak47 | AK47.png |
| m4 | M4.png |
| minigun | Minigun.png |
| rocket_launcher | Rocket.png |
| baseball_bat | Bat.png |
| jetpack | Jetpack.png |
| skateboard | Skateboard.png |

Then turn it on in Minecraft: **Options → Resource Packs → cj-icons**.

## Troubleshooting

- **"not version 1.0 US"**: your `gta_sa.exe` is another version; downgrade it to 1.0 US.
- **"there is no ASI loader"**: install the *Essentials Pack* or an *ASI Loader* in the GTA folder.
- **"cj_bridge.asi could not be copied"**: GTA is open; close it and run `install` again.
- **G brings no car**: CLEO is missing, or it was installed after `install`; install it and run `install` again. The car shows up a few seconds after
  the save loads (the floor is built when the game starts).
- **CJ does not show in Minecraft**: open GTA first and load your save; then go into the Minecraft world.
- **The firewall asks about Java or GTA**: allow the local network. Minecraft and GTA talk to each other on your own computer (127.0.0.1).

Skating (optional): see `INSTALL-skate.md`.
