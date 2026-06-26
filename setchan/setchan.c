/* setchan - set/get the WiFi channel on a Broadcom fullMAC (bcmdhd) chip by firing
 * the chip's private WLC_SET_CHANNEL / WLC_GET_CHANNEL ioctls directly -- the same
 * mechanism the proprietary `wl channel` command uses -- bypassing nl80211 (`iw`),
 * which can't reach the firmware that owns the radio on these chips.
 *
 * Build on the device in a chroot (Debian/Kali):   gcc -O2 setchan.c -o setchan
 * Usage:   setchan            (read current channel)
 *          setchan <channel>  (set channel, then read it back)
 *
 * Tested: Samsung Epic 4G (SPH-D700), BCM4329, CyanogenMod 11 (Android 4.4.4).
 * Run as root, with wlan0 already in monitor mode (iw dev wlan0 set type monitor).
 *
 * Simian Tactical Unit / Rev. J. Money - MIT License.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/sockios.h>   /* SIOCDEVPRIVATE = 0x89F0 */

#define WLC_GET_CHANNEL 29
#define WLC_SET_CHANNEL 30

/* Broadcom dongle-host-driver ioctl wrapper struct */
typedef struct wl_ioctl {
    unsigned int  cmd;     /* WLC_* command id            */
    void         *buf;     /* pointer to the value buffer */
    unsigned int  len;     /* length of that buffer       */
    unsigned char set;     /* 1 = set, 0 = query          */
    unsigned int  used;    /* (driver scratch)            */
    unsigned int  needed;  /* (driver scratch)            */
} wl_ioctl_t;

static int wlioc(int s, const char *ifname, int cmd, void *buf, int len, int set) {
    struct ifreq ifr;
    wl_ioctl_t   ioc;
    memset(&ioc, 0, sizeof(ioc));
    ioc.cmd = cmd; ioc.buf = buf; ioc.len = len; ioc.set = set;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);
    ifr.ifr_data = (char *)&ioc;
    return ioctl(s, SIOCDEVPRIVATE, &ifr);   /* the "wl" private ioctl */
}

int main(int argc, char **argv) {
    const char *ifn = "wlan0";
    int s = socket(AF_INET, SOCK_DGRAM, 0);
    if (s < 0) { perror("socket"); return 1; }

    if (argc >= 2) {
        int ch = atoi(argv[1]);
        errno = 0;
        int r = wlioc(s, ifn, WLC_SET_CHANNEL, &ch, sizeof(ch), 1);
        printf("SET_CHANNEL %d -> ret=%d errno=%d (%s)\n",
               ch, r, errno, r < 0 ? strerror(errno) : "OK");
    }

    /* WLC_GET_CHANNEL returns channel_info_t { hw_channel, target_channel, scan_channel } */
    int ci[3] = {0, 0, 0};
    errno = 0;
    int r = wlioc(s, ifn, WLC_GET_CHANNEL, ci, sizeof(ci), 0);
    printf("GET_CHANNEL -> ret=%d errno=%d  hw=%d target=%d scan=%d\n",
           r, errno, ci[0], ci[1], ci[2]);

    close(s);
    return 0;
}
