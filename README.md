# Simian Tactical WiFi Sniffer 🐒📡

**Monitor-mode WiFi capture _with working channel control_ on old Broadcom fullMAC
(`bcmdhd`) Android phones — including the ones everyone says "can't do monitor mode."**

Built and tested on a **Samsung Epic 4G (SPH-D700)** — Broadcom **BCM4329**,
CyanogenMod 11 (**Android 4.4.4**) — but the core trick applies to a big pile of
2011–2015 BCM43xx devices running the `bcmdhd` driver.

---

## TL;DR

- Old Broadcom WiFi chips are **fullMAC**: the chip's _firmware_ owns the radio, so the
  standard Linux tools (`iw`, `iwconfig`) can flip the interface into **monitor mode** but
  **cannot set the channel** — you get `Operation not permitted`.
- This repo's headline piece is **[`setchan`](setchan/setchan.c)**, a ~60-line C tool that
  fires the raw Broadcom **`WLC_SET_CHANNEL` ioctl** straight at the driver (the same path the
  proprietary `wl channel` command uses). It **actually retunes the radio** where `iw` can't.
- On top of that: two tiny Android apps, **built with no Android Studio / no Gradle** (just
  `aapt`/`javac`/`d8`/`apksigner`):
  - **Simian Tactical WiFi Sniffer** — pick a channel _or_ sweep 1–11, dumps `.pcap` per channel.
  - **Simian WiFi Server** — one-tap HTTP file server for `/sdcard` (bonus tool).

If you've ever hit *"monitor mode works but I can't change channels"* on a Broadcom phone and
gave up — this is the missing piece.

---

## Why monitor mode "doesn't work" on these chips

WiFi chipsets come in two flavors:

| | **softMAC** (e.g. Atheros `ath9k`) | **fullMAC** (Broadcom `bcmdhd`) |
|---|---|---|
| MAC layer runs in | the host kernel (`mac80211`) | the **chip firmware** |
| Channel control | host sets it directly via nl80211 | firmware owns it; no host path |
| `iw set channel` in monitor | works | **`Operation not permitted`** |

On the BCM4329 (and most `bcmdhd` parts), `iw dev wlan0 set type monitor` succeeds — it's just
a flag — but `iw dev wlan0 set channel N` is rejected because nl80211 has no route to the
firmware that actually owns the radio. `iwconfig wlan0 channel N` silently no-ops. `dhdutil` is
a bus/SDIO-layer tool with no WLC commands. And `bcmon` — the classic old monitor-mode app —
just crashes (Java NPE) on KitKat.

So the radio sits on whatever channel the firmware last tuned to, and you can only ever capture
that one channel. That's where everybody stops.

## The fix: the `WLC_SET_CHANNEL` ioctl

Broadcom firmware _does_ accept a channel change — through its **private ioctl interface**
(`SIOCDEVPRIVATE` + a `wl_ioctl_t` struct), the same mechanism the proprietary `wl` binary
uses. We just don't have `wl`. So [`setchan.c`](setchan/setchan.c) reimplements the one call
we need:

```c
#define WLC_SET_CHANNEL 30
typedef struct wl_ioctl { unsigned int cmd; void *buf; unsigned int len;
                          unsigned char set; unsigned int used, needed; } wl_ioctl_t;
/* ifr.ifr_data -> &wl_ioctl{cmd=WLC_SET_CHANNEL, buf=&chan, len=4, set=1};
   ioctl(sock, SIOCDEVPRIVATE, &ifr);  */
```

```
$ setchan 6
SET_CHANNEL 6 -> ret=0 errno=0 (OK)
GET_CHANNEL -> ret=0 errno=0  hw=7 target=6 scan=7
```

**It works.** Verified not just by the `ret=0` but by capturing on different channels and
confirming the traffic differs (ch1 busy with the AP, ch6/ch11 progressively quieter) — which
only happens if the radio genuinely retuned.

## What's in here

```
setchan/setchan.c        # the channel-control ioctl tool (the important part)
sniffer/                 # "Simian Tactical WiFi Sniffer" Android app (channel pick + sweep)
server/                  # "Simian WiFi Server" Android app (HTTP file server for /sdcard)
build_apk.bat            # command-line APK build (no Android Studio / Gradle)
```

## Requirements

- A **rooted** Broadcom `bcmdhd` Android device whose driver accepts `iw ... set type monitor`.
- A small Linux userland on the device (this project uses a **Linux Deploy** chroot — Debian/Kali)
  for `iw` and a `gcc` to compile `setchan` once. The compiled binary lives at `/root/setchan`
  inside the chroot.
- A static **`tcpdump`** binary on the device (`/system/xbin/tcpdump`).
- The apps shell out via `su`, so SuperSU/Magisk root is required.

## Build (no Android Studio)

Both apps are plain-framework (no AndroidX), `minSdk`/`targetSdk` 19, and build straight from the
command line. `build_apk.bat` runs: `aapt package` → `javac -source 8 -target 8` →
`d8 --min-api 19` → `aapt add classes.dex` → `zipalign` → `apksigner` → `adb install`.
Edit the SDK/JDK paths at the top, then run it. (See the script for the exact pipeline — handy
reference for anyone who wants to build an APK without the whole IDE.)

## Usage

**Sniffer:** launch → type a channel + **CAPTURE THIS CHANNEL**, or **SWEEP ALL (1-11)** to dwell
on each channel and drop `sweep_<time>/chN.pcap`. **STOP & Restore WiFi** ends the capture and
puts the interface back to managed. Captures land in `/sdcard/recon_captures/`.

**Server:** launch → **START SERVER** → it prints the connect URLs (every IP the phone has, plus
the hotspot address). Browse to it from any device on the same WiFi (or joined to the phone's
hotspot) to download files off `/sdcard`.

## Known limitations / open TODOs

- **Frame format:** in monitor mode the `bcmdhd` driver delivers each 802.11 frame wrapped in a
  Broadcom event header (Ethernet `ethertype 0x886c`), not clean radiotap. So the `.pcap` is
  capturable and the 802.11 is *in* the bytes, but **airodump-ng / Wireshark won't natively
  parse it**. An auto-converter (strip the Broadcom wrapper, re-emit radiotap) is the obvious
  next step — PRs welcome.
- **Channel hopping is sequential dwell**, not RF-fast like a softMAC airodump sweep.
- Everything here is **firmware/driver-dependent** and "best-effort." Your BCM43xx mileage may vary.

## Ethical use

This is a **research / educational** tool for **networks you own or are authorized to test**.
Don't be a creep. Monitor mode captures whatever's in the air on the channel you tune to — keep
it to your own gear and your own lab.

## Credits

**Simian Tactical Unit** — Rev. J. Money.
Born from one long night of bringing a dead 2010 slider phone back to life as a pocket recon box.

## License

MIT — see [LICENSE](LICENSE).
