import os
import json
import math
import shutil
import time
import subprocess
from pathlib import Path
import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, gaussian_filter
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import doki_arms

ROOT = str(Path(__file__).resolve().parents[1])
BLENDER_EXE = r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe"
BLENDER_SCRIPT = os.path.join(ROOT, "scripts", "blender_rig_and_render_hd.py")
SCRATCH_DIR = os.path.join(ROOT, "build_3d_hd_tmp")

CANVAS_W = 960
CANVAS_H = 1400

HD_CHARACTERS = [
    {
        "char_id": "monika_school",
        "doki_id": "monika",
        "rel_path": "monika escuela/Mon1.webp",
        "center_x_px": 475.0,
        "color_hex": "#34D399",
        "sock_type": "thigh_high_monika",
        "shoe_type": "uwabaki",
        "shoe_accent": (228, 106, 136),
    },
    {
        "char_id": "sayori_school",
        "doki_id": "sayori",
        "rel_path": "sayori escuela/Say4.webp",
        "center_x_px": 500.0,
        "color_hex": "#FB7185",
        "sock_type": "knee_white",
        "shoe_type": "uwabaki",
        "shoe_accent": (82, 178, 232),
    },
    {
        "char_id": "sayori_casual",
        "doki_id": "sayori",
        "rel_path": "sayori casual/Yori1.webp",
        "center_x_px": 500.0,
        "color_hex": "#FB7185",
        "sock_type": "crew_coral",
        "shoe_type": "sneaker_coral",
        "shoe_accent": (238, 118, 142),
    },
    {
        "char_id": "natsuki_school",
        "doki_id": "natsuki",
        "rel_path": "natsuki escuela/Schoola.webp",
        "center_x_px": 494.0,
        "color_hex": "#F472B6",
        "sock_type": "knee_white",
        "shoe_type": "uwabaki",
        "shoe_accent": (242, 104, 156),
    },
    {
        "char_id": "natsuki_casual",
        "doki_id": "natsuki",
        "rel_path": "natsuki casual/Casual1.webp",
        "center_x_px": 498.0,
        "color_hex": "#F472B6",
        "sock_type": "crew_pink",
        "shoe_type": "sneaker_pink",
        "shoe_accent": (244, 134, 172),
    },
    {
        "char_id": "yuri_school",
        "doki_id": "yuri",
        "rel_path": "yuri escuela/Yuri_school_1.webp",
        "center_x_px": 495.0,
        "color_hex": "#A855F7",
        "sock_type": "knee_white",
        "shoe_type": "uwabaki",
        "shoe_accent": (132, 145, 225),
    },
    {
        "char_id": "yuri_casual",
        "doki_id": "yuri",
        "rel_path": "yuri casual/Casualclothes1-0.webp",
        "center_x_px": 502.0,
        "color_hex": "#A855F7",
        "sock_type": "leggings_dark",
        "shoe_type": "boots_dark",
        "shoe_accent": (52, 38, 48),
    },
]


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / np.maximum(1e-6, (edge1 - edge0)), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def smoothstep_scalar(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / max(1e-6, (edge1 - edge0))))
    return t * t * (3.0 - 2.0 * t)


def bleed_rgb_into_transparent(rgba: np.ndarray) -> np.ndarray:
    """
    Preserves the exact anti-aliased alpha channel while bleeding nearest opaque RGB
    into transparent pixels so bilinear texture sampling in Blender never fringes dark.
    """
    out = rgba.copy()
    alpha = out[:, :, 3]
    mask = alpha > 15
    if not np.any(mask):
        return out
    _, indices = distance_transform_edt(~mask, return_indices=True)
    for c in range(3):
        ch = out[:, :, c]
        nearest = ch[indices[0], indices[1]]
        out[:, :, c] = np.where(mask, ch, nearest)
    return out


def build_upper_body_layer(orig_rgba: np.ndarray, cfg: dict, canvas_h: int = CANVAS_H) -> np.ndarray:
    h_orig, w_orig, _ = orig_rgba.shape
    upper = np.zeros((canvas_h, w_orig, 4), dtype=np.uint8)
    cid = cfg["char_id"]

    if cid == "sayori_casual":
        upper[:h_orig, :, :] = orig_rgba
        for x in range(372, 628):
            col = orig_rgba[880:h_orig, x, :]
            is_shorts = (col[:, 3] > 100) & ((col[:, 0].astype(np.int32) - col[:, 2].astype(np.int32)) <= 22)
            shorts_ys = np.where(is_shorts)[0]
            if len(shorts_ys) > 0:
                bot_y = 880 + int(shorts_ys[-1]) + 2
                if bot_y < h_orig:
                    upper[bot_y:h_orig, x, :] = 0
            else:
                upper[915:h_orig, x, :] = 0
        return bleed_rgb_into_transparent(upper)

    if cid == "yuri_casual":
        upper[:h_orig, :, :] = orig_rgba
        for y in range(760, h_orig):
            for x in range(340, 670):
                if upper[y, x, 3] > 0:
                    rgb = upper[y, x, :3].astype(np.int32)
                    is_sweater = (rgb[0] > 100 and rgb[1] > 90 and rgb[2] > 70)
                    is_hair = (rgb[2] > rgb[1] + 10)
                    if not is_sweater and not is_hair:
                        upper[y, x, :] = 0
        return bleed_rgb_into_transparent(upper)

    # 5 Skirt characters: keep 0..958 untouched, add natural 9px pleated hem at 956..966
    upper[:h_orig, :, :] = orig_rgba
    ref_row = orig_rgba[956, :, :]
    opaque_xs = np.where(ref_row[:, 3] > 128)[0]
    if len(opaque_xs) > 10:
        x_min, x_max = int(opaque_xs[0]), int(opaque_xs[-1])
        if cid == "yuri_school":
            skirt_xs = opaque_xs[opaque_xs < 650]
            x_min, x_max = int(skirt_xs[0]), int(skirt_xs[-1])

        skirt_w = max(1, x_max - x_min)
        outline_rgb = np.array([46.0, 34.0, 44.0], dtype=np.float32)
        if cid == "natsuki_casual":
            outline_rgb = np.array([115.0, 52.0, 72.0], dtype=np.float32)

        for x in range(x_min, x_max + 1):
            u = (x - x_min) / float(skirt_w)
            arch = 4.0 * math.sin(u * math.pi)
            pleat = 2.0 * math.sin(u * math.pi * 10.0)
            hem_y = 959.0 + arch + pleat
            hem_end = int(math.ceil(hem_y)) + 2
            src_rgb = orig_rgba[956, x, :3].astype(np.float32)

            for y in range(957, min(canvas_h, hem_end + 1)):
                dist_to_hem = hem_y - float(y)
                if dist_to_hem < -1.2:
                    continue
                alpha_f = float(smoothstep_scalar(-1.2, 0.4, dist_to_hem))
                line_f = float(1.0 - smoothstep_scalar(0.5, 2.4, dist_to_hem))
                shade_f = float(1.0 - 0.12 * smoothstep_scalar(4.0, 0.0, dist_to_hem))
                rgb = src_rgb * shade_f * (1.0 - line_f) + outline_rgb * line_f
                upper[y, x, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
                upper[y, x, 3] = int(round(255.0 * alpha_f))

    return bleed_rgb_into_transparent(upper)


def render_single_leg_layer(
    orig_rgba: np.ndarray,
    cfg: dict,
    side: int,
    canvas_h: int = CANVAS_H,
    canvas_w: int = CANVAS_W,
) -> np.ndarray:
    out = np.zeros((canvas_h, canvas_w, 4), dtype=np.uint8)
    cid = cfg["char_id"]
    cx_pelvis = float(cfg["center_x_px"])

    y_top = 765 if cid == "yuri_casual" else 900
    y_hem = 924 if cid == "sayori_casual" else (795 if cid == "yuri_casual" else 962)
    y_knee = 1095
    y_ankle = 1310
    y_shoe_bot = 1380

    if cid == "sayori_casual":
        x_hip = 436.5 if side < 0 else 564.5
        x_knee = 446.0 if side < 0 else 555.0
        x_ankle = 450.0 if side < 0 else 551.0
        hw_top = 50.5
        hw_knee_target = 31.5
        hw_ankle_target = 20.0
    elif cid == "yuri_casual":
        x_hip = 445.0 if side < 0 else 558.0
        x_knee = 452.0 if side < 0 else 548.0
        x_ankle = 454.0 if side < 0 else 546.0
        hw_top = 56.0 if side < 0 else 64.0
        hw_knee_target = 32.0
        hw_ankle_target = 19.5
    else:
        x_hip = cx_pelvis + side * 46.5
        x_knee = cx_pelvis + side * 44.5
        x_ankle = cx_pelvis + side * 43.5
        hw_top = 45.5
        hw_knee_target = 31.0
        hw_ankle_target = 19.8

    sock_type = cfg["sock_type"]
    shoe_type = cfg["shoe_type"]
    shoe_accent = np.array(cfg["shoe_accent"], dtype=np.float32)

    skin_lit = np.array([255.0, 245.0, 220.0], dtype=np.float32)
    skin_mid = np.array([255.0, 236.0, 206.0], dtype=np.float32)
    skin_blush = np.array([252.0, 198.0, 162.0], dtype=np.float32)
    skin_shadow = np.array([235.0, 152.0, 130.0], dtype=np.float32)
    skin_outline = np.array([72.0, 43.0, 38.0], dtype=np.float32)

    for y in range(y_top, y_shoe_bot + 1):
        if y <= y_ankle:
            if y <= y_knee:
                tk = (y - y_top) / float(y_knee - y_top)
                cx = x_hip + (x_knee - x_hip) * float(smoothstep_scalar(0.0, 1.0, tk))
                hw = hw_top + (hw_knee_target - hw_top) * float(smoothstep_scalar(0.0, 1.0, tk))
            else:
                tk = (y - y_knee) / float(y_ankle - y_knee)
                cx = x_knee + (x_ankle - x_knee) * float(smoothstep_scalar(0.0, 1.0, tk))
                calf_swell = 2.0 * math.sin(min(1.0, tk * 1.45) * math.pi)
                ankle_taper = (hw_knee_target - hw_ankle_target) * float(smoothstep_scalar(0.16, 1.0, tk))
                hw = hw_knee_target + calf_swell - ankle_taper
        else:
            ts = (y - y_ankle) / float(y_shoe_bot - y_ankle)
            cx = x_ankle - side * 1.5 * float(smoothstep_scalar(0.0, 0.8, ts))
            hw = hw_ankle_target + 6.8 * math.sin(min(1.0, ts * 1.15) * math.pi * 0.85)
            if ts > 0.80:
                hw *= float(1.0 - 0.38 * ((ts - 0.80) / 0.20) ** 2)

        x0 = max(0, int(math.floor(cx - hw - 3)))
        x1 = min(canvas_w - 1, int(math.ceil(cx + hw + 3)))
        xs = np.arange(x0, x1 + 1, dtype=np.float32)
        u = (xs - cx) / max(1.0, hw)
        dist_edge = hw - np.abs(xs - cx)

        valid = dist_edge > -1.2
        if not np.any(valid):
            continue

        xs_v = xs[valid].astype(np.int32)
        u_v = u[valid]
        de_v = dist_edge[valid]

        alpha_f = smoothstep(-1.0, 0.6, de_v)
        outline_f = 1.0 - smoothstep(1.2, 2.7, de_v)

        is_shoe = y > y_ankle
        if not is_shoe:
            if sock_type == "leggings_dark":
                is_sock = np.ones_like(u_v, dtype=bool)
                sock_cuff_dist = np.full_like(u_v, 999.0)
            elif sock_type == "thigh_high_monika":
                cuff_y = 1015.0 + 3.5 * (u_v ** 2)
                is_sock = y >= cuff_y
                sock_cuff_dist = y - cuff_y
            elif sock_type == "knee_white":
                cuff_y = 1135.0 + 3.0 * (u_v ** 2)
                is_sock = y >= cuff_y
                sock_cuff_dist = y - cuff_y
            elif sock_type in ("crew_coral", "crew_pink"):
                cuff_y = 1225.0 + 2.5 * (u_v ** 2)
                is_sock = y >= cuff_y
                sock_cuff_dist = y - cuff_y
            else:
                is_sock = np.zeros_like(u_v, dtype=bool)
                sock_cuff_dist = np.full_like(u_v, -999.0)

        rgb = np.zeros((len(xs_v), 3), dtype=np.float32)

        if is_shoe:
            ts = (y - y_ankle) / float(y_shoe_bot - y_ankle)
            edge_sh = smoothstep(0.55, 0.95, np.abs(u_v))
            if shoe_type == "boots_dark":
                base = np.array([38.0, 28.0, 34.0], dtype=np.float32)
                hi = np.array([62.0, 48.0, 56.0], dtype=np.float32)
                hi_mask = np.exp(-((u_v + 0.15 * side) / 0.28) ** 2)[:, None]
                rgb = base * (1.0 - 0.22 * edge_sh[:, None]) + (hi - base) * hi_mask * 0.6
                if ts > 0.82:
                    rgb = np.array([22.0, 16.0, 20.0], dtype=np.float32)
                line_col = np.array([16.0, 10.0, 14.0], dtype=np.float32)
            elif shoe_type == "uwabaki":
                white_base = np.array([247.0, 246.0, 250.0], dtype=np.float32)
                white_sh = np.array([216.0, 218.0, 230.0], dtype=np.float32)
                rgb = white_base * (1.0 - edge_sh[:, None]) + white_sh * edge_sh[:, None]
                if 0.22 <= ts <= 0.31:
                    rgb = white_sh * 0.92
                toe_curve = 0.64 + 0.08 * (u_v ** 2)
                is_toe = (ts >= toe_curve) & (ts <= 0.87)
                if np.any(is_toe):
                    rgb[is_toe] = shoe_accent * (1.0 - 0.15 * edge_sh[is_toe, None])
                if ts > 0.87:
                    rgb[:] = shoe_accent * 0.82
                line_col = skin_outline
            else:
                body_col = shoe_accent
                rgb = body_col * (1.0 - 0.18 * edge_sh[:, None])
                if ts < 0.14:
                    rgb[:] = np.array([248.0, 246.0, 250.0], dtype=np.float32)
                if (0.25 <= ts <= 0.31) or (0.39 <= ts <= 0.45):
                    lace_m = np.abs(u_v) < 0.48
                    rgb[lace_m] = np.array([250.0, 248.0, 252.0], dtype=np.float32)
                toe_curve = 0.66 + 0.07 * (u_v ** 2)
                is_toe = ts >= toe_curve
                if np.any(is_toe):
                    rgb[is_toe] = np.array([246.0, 245.0, 249.0], dtype=np.float32) * (1.0 - 0.10 * edge_sh[is_toe, None])
                if ts > 0.88:
                    rgb[:] = np.array([222.0, 218.0, 226.0], dtype=np.float32)
                line_col = skin_outline
        else:
            abs_u = np.abs(u_v)
            mid_f = smoothstep(0.42, 0.72, abs_u)[:, None]
            blush_f = smoothstep(0.68, 0.94, abs_u)[:, None]
            skin_rgb = skin_lit * (1.0 - mid_f) + skin_mid * mid_f
            skin_rgb = skin_rgb * (1.0 - blush_f) + skin_blush * blush_f

            hem_curve = float(y_hem) + 20.0 - 6.0 * (u_v ** 2)
            skirt_sh = (1.0 - smoothstep(hem_curve - 4.0, hem_curve + 16.0, float(y)))[:, None]
            sh_strength = 0.55 if cid == "sayori_casual" else 0.75
            skin_rgb = skin_rgb * (1.0 - sh_strength * skirt_sh) + skin_shadow * (sh_strength * skirt_sh)

            knee_dy = abs(float(y - y_knee))
            if knee_dy < 18.0:
                k_blush = math.exp(-((knee_dy / 11.0) ** 2)) * np.exp(-((u_v / 0.55) ** 2))
                skin_rgb = skin_rgb * (1.0 - 0.35 * k_blush[:, None]) + skin_shadow * (0.35 * k_blush[:, None])
                if abs(float(y - (y_knee + 3))) < 1.6:
                    cap_m = np.abs(u_v) < 0.24
                    skin_rgb[cap_m] = skin_rgb[cap_m] * 0.65 + skin_shadow * 0.35

            if not np.any(is_sock):
                rgb = skin_rgb
                line_col = skin_outline
            else:
                edge_sh = smoothstep(0.62, 0.94, np.abs(u_v))[:, None]
                if sock_type == "leggings_dark":
                    t_leg = np.clip((y - 765.0) / float(y_ankle - 765.0), 0.0, 1.0)
                    top_col = np.array([42.0, 34.0, 22.0], dtype=np.float32)
                    bot_col = np.array([30.0, 21.0, 10.0], dtype=np.float32)
                    leg_base = top_col * (1.0 - t_leg) + bot_col * t_leg
                    leg_hi = leg_base + np.array([9.0, 7.0, 5.0], dtype=np.float32)
                    sheen = np.exp(-((u_v + 0.08 * side) / 0.36) ** 2)[:, None]
                    rgb = leg_base * (1.0 - 0.22 * edge_sh) + (leg_hi - leg_base) * sheen * 0.55
                    line_col = np.array([10.0, 2.0, 2.0], dtype=np.float32)
                elif sock_type == "thigh_high_monika":
                    st_base = np.array([36.0, 28.0, 33.0], dtype=np.float32)
                    st_hi = np.array([56.0, 44.0, 52.0], dtype=np.float32)
                    sheen = np.exp(-((u_v + 0.12 * side) / 0.34) ** 2)[:, None]
                    st_rgb = st_base * (1.0 - 0.22 * edge_sh) + (st_hi - st_base) * sheen
                    is_trim = (sock_cuff_dist >= 0.0) & (sock_cuff_dist <= 11.0)
                    is_border = (np.abs(sock_cuff_dist) < 1.8) | (np.abs(sock_cuff_dist - 11.0) < 1.6)
                    if np.any(is_trim):
                        st_rgb[is_trim] = shoe_accent * (1.0 - 0.16 * edge_sh[is_trim])
                    if np.any(is_border):
                        st_rgb[is_border] = skin_outline
                    rgb = np.where((sock_cuff_dist >= 0.0)[:, None], st_rgb, skin_rgb)
                    line_col = np.array([22.0, 15.0, 20.0], dtype=np.float32)
                else:
                    w_base = np.array([248.0, 247.0, 252.0], dtype=np.float32)
                    w_sh = np.array([218.0, 220.0, 232.0], dtype=np.float32)
                    s_rgb = w_base * (1.0 - edge_sh) + w_sh * edge_sh
                    if sock_type in ("crew_coral", "crew_pink") or cid == "natsuki_school":
                        st1 = (sock_cuff_dist >= 6.0) & (sock_cuff_dist <= 11.0)
                        st2 = (sock_cuff_dist >= 15.0) & (sock_cuff_dist <= 20.0)
                        str_m = st1 | st2
                        if np.any(str_m):
                            s_rgb[str_m] = shoe_accent * (1.0 - 0.15 * edge_sh[str_m])
                    is_cuff_line = np.abs(sock_cuff_dist) < 1.8
                    if np.any(is_cuff_line):
                        s_rgb[is_cuff_line] = skin_outline
                    rgb = np.where((sock_cuff_dist >= 0.0)[:, None], s_rgb, skin_rgb)
                    line_col = skin_outline

        final_rgb = rgb * (1.0 - outline_f[:, None]) + line_col * outline_f[:, None]
        out[y, xs_v, :3] = np.clip(final_rgb, 0, 255).astype(np.uint8)
        out[y, xs_v, 3] = np.clip(np.round(alpha_f * 255.0), 0, 255).astype(np.uint8)

    return bleed_rgb_into_transparent(out)


def compute_part_weights(part_name: str, x: float, z_norm: float, px: float = 0.0, py: float = 0.0, arm_ctx=None) -> dict:
    """
    Computes clean, non-distorting bone weights for each of the 3 separate mesh layers:
    - 'upper': weighted to head (z_norm >= 0.72), spine (0.48..0.72), and hips (< 0.48)
    - 'leg_l': weighted exclusively to thigh_l (above knee z_norm=0.22) and shin_l (below knee)
    - 'leg_r': weighted exclusively to thigh_r (above knee z_norm=0.22) and shin_r (below knee)
    """
    w = {
        "root": 0.0,
        "hips": 0.0,
        "spine": 0.0,
        "head": 0.0,
        "hair_l": 0.0,
        "hair_r": 0.0,
        "uarm_l": 0.0,
        "farm_l": 0.0,
        "uarm_r": 0.0,
        "farm_r": 0.0,
        "thigh_l": 0.0,
        "shin_l": 0.0,
        "thigh_r": 0.0,
        "shin_r": 0.0,
    }

    if part_name == "upper":
        is_head = smoothstep_scalar(0.71, 0.77, z_norm)
        is_spine = smoothstep_scalar(0.46, 0.53, z_norm) * (1.0 - is_head)
        is_hips = max(0.0, 1.0 - is_head - is_spine)
        w["head"] = is_head
        w["spine"] = is_spine
        w["hips"] = is_hips
        if arm_ctx is not None:
            cfg_arm = arm_ctx["cfg"]
            hair = arm_ctx.get("hair_soft")
            arm_total = 0.0
            for side, direction, ukey, fkey in (("left", -1, "uarm_l", "farm_l"), ("right", 1, "uarm_r", "farm_r")):
                hs = hair if (hair is not None and cfg_arm.get("hair_excl_" + side)) else None
                wu, wl = doki_arms.arm_weights(cfg_arm[side], direction, px, py, hs)
                w[ukey] = wu
                w[fkey] = wl
                arm_total += wu + wl
            arm_total = min(1.0, arm_total)
            for k in ("head", "spine", "hips"):
                w[k] *= (1.0 - arm_total)
    elif part_name == "leg_l":
        is_thigh = smoothstep_scalar(0.195, 0.245, z_norm)
        w["thigh_l"] = is_thigh
        w["shin_l"] = 1.0 - is_thigh
    elif part_name == "leg_r":
        is_thigh = smoothstep_scalar(0.195, 0.245, z_norm)
        w["thigh_r"] = is_thigh
        w["shin_r"] = 1.0 - is_thigh

    total = sum(w.values())
    if total > 1e-6:
        for k in w:
            w[k] = round(w[k] / total, 4)
    else:
        w["hips"] = 1.0
    return w


def build_single_part_mesh_data(
    part_name: str,
    rgba: np.ndarray,
    tex_path: str,
    bbox: tuple,
    y_plane_offset: float,
    grid_w: int = 96,
    grid_h: int = 140,
    arm_ctx=None,
) -> dict:
    """
    Builds a smooth 3D mesh for a single RGBA layer ('upper', 'leg_l', 'leg_r')
    mapping world coordinates cleanly from the shared full-body bounding box.
    """
    x0, y0, x1, y1, char_cx_px = bbox
    crop_w = float(x1 - x0)
    crop_h = float(y1 - y0)
    char_cx_px = float(char_cx_px)

    alpha_f = rgba[:, :, 3].astype(np.float32) / 255.0
    # Dilate alpha mask by 6px so all anti-aliased edge pixels are safely inside the mesh
    dilated = gaussian_filter(alpha_f, sigma=3.0)
    inside = alpha_f > 0.05
    dist = distance_transform_edt(inside)
    max_d = max(1.0, float(dist.max()))
    depth_map = gaussian_filter(np.power(np.clip(dist / max_d, 0.0, 1.0), 0.65), sigma=3.5)

    world_h = 1.90
    z_bottom = 0.04
    scale_px = world_h / crop_h

    xs = np.linspace(x0, x1 - 1, grid_w)
    ys = np.linspace(y0, y1 - 1, grid_h)

    vert_idx_grid = np.full((grid_h, grid_w), -1, dtype=np.int32)
    mask_grid = np.zeros((grid_h, grid_w), dtype=np.float32)

    for r in range(grid_h):
        py = int(np.clip(round(ys[r]), 0, rgba.shape[0] - 1))
        for c in range(grid_w):
            px = int(np.clip(round(xs[c]), 0, rgba.shape[1] - 1))
            # Keep any grid vertex within the dilated silhouette
            mask_grid[r, c] = max(float(alpha_f[py, px]), float(dilated[py, px]))

    vertices = []
    uvs = []
    weights_list = []

    def get_or_create_vertex(r: int, c: int, is_front: bool) -> int:
        if is_front and vert_idx_grid[r, c] != -1:
            return int(vert_idx_grid[r, c])

        px = float(xs[c])
        py = float(ys[r])
        ix = int(np.clip(round(px), 0, rgba.shape[1] - 1))
        iy = int(np.clip(round(py), 0, rgba.shape[0] - 1))

        d_val = float(depth_map[iy, ix])
        world_x = (px - char_cx_px) * scale_px
        z_norm = 1.0 - (py - y0) / max(1.0, crop_h)
        world_z = z_bottom + z_norm * world_h

        # Subtle, smooth 2.5D relief depth (0.024m max) so 3D rotation has gentle parallax
        thickness = 0.024 * d_val
        world_y = y_plane_offset + (-thickness if is_front else thickness)

        u = px / float(rgba.shape[1])
        v = 1.0 - (py / float(rgba.shape[0]))

        w = compute_part_weights(part_name, world_x, z_norm, px, py, arm_ctx)
        idx = len(vertices)
        vertices.append([round(world_x, 5), round(world_y, 5), round(world_z, 5)])
        uvs.append([round(u, 5), round(v, 5)])
        weights_list.append(w)

        if is_front:
            vert_idx_grid[r, c] = idx
        return idx

    faces = []
    thresh = 0.015
    for r in range(grid_h - 1):
        for c in range(grid_w - 1):
            m00 = mask_grid[r, c] > thresh
            m10 = mask_grid[r, c + 1] > thresh
            m01 = mask_grid[r + 1, c] > thresh
            m11 = mask_grid[r + 1, c + 1] > thresh
            if (int(m00) + int(m10) + int(m01) + int(m11)) >= 3:
                v00 = get_or_create_vertex(r, c, True)
                v10 = get_or_create_vertex(r, c + 1, True)
                v01 = get_or_create_vertex(r + 1, c, True)
                v11 = get_or_create_vertex(r + 1, c + 1, True)
                faces.append([v00, v01, v11])
                faces.append([v00, v11, v10])

    return {
        "part_name": part_name,
        "texture_path": tex_path,
        "vertices": vertices,
        "uvs": uvs,
        "faces": faces,
        "weights": weights_list,
    }


def split_arm_faces(part: dict, new_name: str, threshold: float = 0.5):
    """
    Moves every triangle whose mean arm-weight exceeds `threshold` out of `part`
    into a new part (same texture / same skin weights, re-indexed vertices) so the
    arms render as their own object in front of the torso.
    """
    keys = ("uarm_l", "farm_l", "uarm_r", "farm_r")
    arm_w = [sum(w.get(k, 0.0) for k in keys) for w in part["weights"]]
    torso_faces, arm_faces = [], []
    for f in part["faces"]:
        m = (arm_w[f[0]] + arm_w[f[1]] + arm_w[f[2]]) / 3.0
        (arm_faces if m > threshold else torso_faces).append(f)

    def pack(faces, name):
        used = sorted({i for f in faces for i in f})
        remap = {old: new for new, old in enumerate(used)}
        return {
            "part_name": name,
            "texture_path": part["texture_path"],
            "vertices": [part["vertices"][i] for i in used],
            "uvs": [part["uvs"][i] for i in used],
            "faces": [[remap[a], remap[b], remap[c]] for a, b, c in faces],
            "weights": [part["weights"][i] for i in used],
        }

    return pack(torso_faces, part["part_name"]), pack(arm_faces, new_name)


def build_hd_character_3d_mesh_json(char_cfg: dict) -> str:
    char_id = char_cfg["char_id"]
    src_path = os.path.join(
        ROOT, "android_app", "app", "src", "main", "assets", "ddsc", char_cfg["rel_path"]
    )
    orig_rgba = np.array(Image.open(src_path).convert("RGBA"))

    upper_rgba = build_upper_body_layer(orig_rgba, char_cfg, CANVAS_H)
    leg_l_rgba = render_single_leg_layer(orig_rgba, char_cfg, side=-1, canvas_h=CANVAS_H, canvas_w=CANVAS_W)
    leg_r_rgba = render_single_leg_layer(orig_rgba, char_cfg, side=+1, canvas_h=CANVAS_H, canvas_w=CANVAS_W)

    os.makedirs(SCRATCH_DIR, exist_ok=True)
    upper_tex = os.path.join(SCRATCH_DIR, f"{char_id}_upper.png")
    leg_l_tex = os.path.join(SCRATCH_DIR, f"{char_id}_leg_l.png")
    leg_r_tex = os.path.join(SCRATCH_DIR, f"{char_id}_leg_r.png")

    Image.fromarray(upper_rgba, "RGBA").save(upper_tex)
    Image.fromarray(leg_l_rgba, "RGBA").save(leg_l_tex)
    Image.fromarray(leg_r_rgba, "RGBA").save(leg_r_tex)

    # Compute unified bounding box across all 3 layers
    combined_alpha = np.maximum(upper_rgba[:, :, 3], np.maximum(leg_l_rgba[:, :, 3], leg_r_rgba[:, :, 3]))
    ys_nz, xs_nz = np.where(combined_alpha > 10)
    y0, y1 = max(0, int(ys_nz.min()) - 8), min(CANVAS_H, int(ys_nz.max()) + 8)
    x0, x1 = max(0, int(xs_nz.min()) - 8), min(CANVAS_W, int(xs_nz.max()) + 8)
    bbox = (x0, y0, x1, y1, float(char_cfg["center_x_px"]))

    # Build the 3 layered meshes: UpperBody at Y=0.00, LeftLeg at Y=+0.032, RightLeg at Y=+0.032
    arm_ctx = None
    if doki_arms.has_arms(char_id):
        arm_ctx = {
            "cfg": doki_arms.ARM_CFG[char_id],
            "hair_soft": doki_arms.hair_soft_mask(upper_rgba) if char_cfg["doki_id"] == "monika" else None,
        }
    part_upper = build_single_part_mesh_data("upper", upper_rgba, upper_tex, bbox, y_plane_offset=0.000, grid_w=96, grid_h=140, arm_ctx=arm_ctx)
    part_leg_l = build_single_part_mesh_data("leg_l", leg_l_rgba, leg_l_tex, bbox, y_plane_offset=0.032, grid_w=64, grid_h=110)
    part_leg_r = build_single_part_mesh_data("leg_r", leg_r_rgba, leg_r_tex, bbox, y_plane_offset=0.032, grid_w=64, grid_h=110)

    mesh_json_path = os.path.join(SCRATCH_DIR, f"{char_id}_mesh.json")
    payload = {
        "char_id": char_id,
        "doki_id": char_cfg["doki_id"],
        "color_hex": char_cfg["color_hex"],
        "parts": [part_leg_l, part_leg_r, part_upper],
    }
    if arm_ctx is not None:
        part_torso, part_arms = split_arm_faces(part_upper, "arms")
        payload["parts"] = [part_leg_l, part_leg_r, part_torso, part_arms]
        payload["arm_joints"] = doki_arms.arm_joints_world(char_id, bbox)
    with open(mesh_json_path, "w", encoding="utf-8") as f:
        json.dump(payload, f)

    total_v = sum(len(p["vertices"]) for p in payload["parts"])
    total_f = sum(len(p["faces"]) for p in payload["parts"])
    print(f"[HD 3-Layer MeshGen] {char_id}: {total_v} verts, {total_f} tris -> {mesh_json_path}")
    return mesh_json_path


def create_hd_showcase_gifs_and_posters():
    out_docs = os.path.join(ROOT, "docs", "characters", "3d_hd")
    os.makedirs(out_docs, exist_ok=True)
    assets_root = os.path.join(ROOT, "android_app", "app", "src", "main", "assets", "doki3d_hd")

    for cfg in HD_CHARACTERS:
        cid = cfg["char_id"]
        char_dir = os.path.join(assets_root, cid)
        dance_frames = []
        for i in range(48):
            fp = os.path.join(char_dir, f"dance_{i:02d}.webp")
            if os.path.exists(fp):
                dance_frames.append(Image.open(fp).convert("RGBA"))

        if dance_frames:
            poster_path = os.path.join(out_docs, f"{cid}_3d.png")
            dance_frames[2].save(poster_path)

            gif_path = os.path.join(out_docs, f"{cid}_3d_dance.gif")
            gif_frames = []
            for fr in dance_frames:
                rgb = Image.new("RGBA", fr.size, (18, 18, 26, 255))
                rgb.alpha_composite(fr)
                p_img = rgb.convert("RGB").quantize(colors=255, method=Image.Quantize.MEDIANCUT)
                gif_frames.append(p_img)
            gif_frames[0].save(
                gif_path,
                save_all=True,
                append_images=gif_frames[1:],
                duration=75,
                loop=0,
            )
            print(f"[HD Showcase] Saved {poster_path} & {gif_path}")


def main():
    os.makedirs(SCRATCH_DIR, exist_ok=True)
    manifest_entries = []
    for cfg in HD_CHARACTERS:
        cid = cfg["char_id"]
        mesh_json = build_hd_character_3d_mesh_json(cfg)
        render_scratch_dir = os.path.join(SCRATCH_DIR, "renders", cid)
        os.makedirs(render_scratch_dir, exist_ok=True)
        glb_out_path = os.path.join(ROOT, "docs", "models3d", f"{cid}_3d.glb")
        manifest_entries.append({
            "char_id": cid,
            "mesh_json": mesh_json,
            "render_out_dir": render_scratch_dir,
            "glb_out_path": glb_out_path,
        })

    manifest_path = os.path.join(SCRATCH_DIR, "blender_hd_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"characters": manifest_entries}, f, indent=2)

    print("[Blender 5.2 HD] Launching headless Blender 5.2 3-Layer Full-Body pipeline...")
    cmd = [
        BLENDER_EXE,
        "--background",
        "--python-exit-code",
        "1",
        "--python",
        BLENDER_SCRIPT,
        "--",
        manifest_path,
    ]
    res = subprocess.run(cmd, check=True)
    print(f"[Blender 5.2 HD] Completed with return code {res.returncode}")

    # Copy rendered frames to android assets with robust retry
    assets_root = os.path.join(ROOT, "android_app", "app", "src", "main", "assets", "doki3d_hd")
    for cfg in HD_CHARACTERS:
        cid = cfg["char_id"]
        src_dir = os.path.join(SCRATCH_DIR, "renders", cid)
        dst_dir = os.path.join(assets_root, cid)
        os.makedirs(dst_dir, exist_ok=True)
        for fname in os.listdir(src_dir):
            if fname.endswith(".webp"):
                sf = os.path.join(src_dir, fname)
                df = os.path.join(dst_dir, fname)
                copied = False
                for a in range(5):
                    try:
                        shutil.copy2(sf, df)
                        copied = True
                        break
                    except Exception:
                        time.sleep(0.2)
                if not copied:
                    shutil.copy2(sf, df)

    create_hd_showcase_gifs_and_posters()


if __name__ == "__main__":
    main()
