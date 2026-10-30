"""
Arm rig description + skin-weight fields for the HD 3D Dokis.

Every coordinate here is in *sprite pixel space* of the 960x960 Satchely cutouts
(the same space the 'upper' texture layer lives in).  For each visible arm we
describe:

  pivot     shoulder joint
  elbow     elbow joint
  wrist     wrist / hand centre
  boundary  polyline [(y, x), ...] = the torso's side silhouette; vertices that
            are farther "outward" than this line (screen-left for the left arm,
            screen-right for the right arm) belong to the arm.

Yuri has both hands behind her back in her sprites, so she has no arm rig.
"""
import numpy as np


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / max(1e-6, (edge1 - edge0)), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


ARM_CFG = {
    "natsuki_casual": {
        "left": {
            "pivot": (376, 528), "elbow": (350, 712), "wrist": (292, 890),
            "boundary": [(440, 396), (520, 400), (600, 403), (650, 402), (700, 401),
                         (760, 398), (790, 384), (820, 366), (860, 350), (900, 338), (960, 330)],
        },
        "right": {
            "pivot": (622, 528), "elbow": (650, 712), "wrist": (704, 890),
            "boundary": [(440, 594), (520, 598), (600, 597), (650, 598), (700, 600),
                         (760, 602), (790, 612), (820, 630), (860, 652), (900, 668), (960, 676)],
        },
    },
    "natsuki_school": {
        "left": {
            "pivot": (372, 520), "elbow": (336, 700), "wrist": (290, 885),
            "boundary": [(440, 392), (500, 398), (600, 402), (680, 410), (740, 414),
                         (790, 392), (830, 362), (870, 332), (910, 316), (960, 306)],
        },
        "right": {
            "pivot": (618, 520), "elbow": (652, 700), "wrist": (704, 885),
            "boundary": [(440, 598), (500, 596), (600, 592), (680, 588), (740, 592),
                         (790, 618), (830, 650), (870, 676), (910, 692), (960, 700)],
        },
    },
    "sayori_casual": {
        "left": {
            "pivot": (384, 462), "elbow": (336, 690), "wrist": (255, 850),
            "boundary": [(430, 396), (500, 400), (600, 400), (660, 398), (720, 396),
                         (760, 392), (800, 360), (850, 336), (900, 330), (960, 330)],
        },
        "right": {
            "pivot": (606, 462), "elbow": (664, 690), "wrist": (742, 850),
            "boundary": [(430, 598), (500, 602), (600, 600), (640, 606), (700, 622),
                         (760, 640), (800, 662), (850, 672), (900, 672), (960, 672)],
        },
    },
    "sayori_school": {
        "left": {
            "pivot": (384, 452), "elbow": (330, 660), "wrist": (262, 842),
            "boundary": [(430, 392), (500, 398), (600, 404), (660, 408), (720, 410),
                         (780, 392), (820, 360), (860, 330), (900, 322), (960, 318)],
        },
        "right": {
            "pivot": (606, 452), "elbow": (664, 660), "wrist": (736, 842),
            "boundary": [(430, 600), (500, 596), (600, 592), (660, 592), (720, 600),
                         (780, 624), (820, 654), (860, 690), (900, 706), (960, 712)],
        },
    },
    "monika_school": {
        "left": {
            "pivot": (356, 455), "elbow": (316, 650), "wrist": (276, 880),
            "boundary": [(430, 376), (520, 386), (590, 390), (650, 382), (710, 364),
                         (770, 346), (810, 330), (860, 314), (910, 308), (960, 304)],
        },
        "right": {
            "pivot": (612, 455), "elbow": (652, 650), "wrist": (678, 880),
            "boundary": [(430, 590), (520, 586), (590, 590), (650, 598), (710, 614),
                         (770, 632), (810, 640), (860, 642), (910, 646), (960, 650)],
        },
        # Ponytail overlaps the right arm region: keep the hair out of the arm weights.
        "hair_excl_right": True,
    },
}


def has_arms(char_id: str) -> bool:
    return char_id in ARM_CFG


def hair_soft_mask(rgba: np.ndarray) -> np.ndarray:
    """Soft 0..1 mask of orange/brown hair pixels (Monika ponytail)."""
    from scipy.ndimage import gaussian_filter
    rgb = rgba[:, :, :3].astype(np.int32)
    a = rgba[:, :, 3] > 40
    r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    hair = a & ((r - b) > 52) & ((r - g) > 34) & (r < 232)
    hair[860:, :] = False  # fists / hands live below here
    return np.clip(gaussian_filter(hair.astype(np.float32), 4.0) * 1.6, 0.0, 1.0)


def _arm_axis(side_cfg):
    px, py = side_cfg["pivot"]
    wx, wy = side_cfg["wrist"]
    ex, ey = side_cfg["elbow"]
    ax, ay = wx - px, wy - py
    len2 = max(1e-6, ax * ax + ay * ay)
    t_elbow = ((ex - px) * ax + (ey - py) * ay) / len2
    return ax, ay, len2, t_elbow


def arm_weights(side_cfg, direction: int, x: float, y: float, hair_soft=None):
    """
    Returns (w_upper_arm, w_forearm) for one mesh vertex at sprite pixel (x, y).
    direction = -1 for the screen-left arm, +1 for the screen-right arm.
    """
    bnd = side_cfg["boundary"]
    ys = [p[0] for p in bnd]
    xs = [p[1] for p in bnd]
    xb = float(np.interp(y, ys, xs))
    d = direction * (x - xb)
    w_lat = float(smoothstep(-6.0, 18.0, d))
    piv_x, piv_y = side_cfg["pivot"]
    w_y = float(smoothstep(piv_y - 40.0, piv_y + 34.0, y))
    w = w_lat * w_y
    if hair_soft is not None:
        iy = int(np.clip(round(y), 0, hair_soft.shape[0] - 1))
        ix = int(np.clip(round(x), 0, hair_soft.shape[1] - 1))
        w *= (1.0 - float(hair_soft[iy, ix]))
    if w <= 1e-4:
        return 0.0, 0.0
    ax, ay, len2, t_e = _arm_axis(side_cfg)
    t = ((x - piv_x) * ax + (y - piv_y) * ay) / len2
    w_lo = float(smoothstep(t_e - 0.11, t_e + 0.11, t))
    return w * (1.0 - w_lo), w * w_lo


def arm_joints_world(char_id: str, bbox, world_h=1.90, z_bottom=0.04):
    """Joint positions in Blender world space (x, z; y is the sprite plane)."""
    if char_id not in ARM_CFG:
        return None
    x0, y0, x1, y1, cx = bbox
    crop_h = float(y1 - y0)
    scale = world_h / crop_h

    def to_world(p):
        wx = (p[0] - cx) * scale
        wz = z_bottom + (1.0 - (p[1] - y0) / crop_h) * world_h
        return [round(wx, 5), round(wz, 5)]

    out = {}
    for side in ("left", "right"):
        c = ARM_CFG[char_id][side]
        out[side] = {k: to_world(c[k]) for k in ("pivot", "elbow", "wrist")}
    return out
