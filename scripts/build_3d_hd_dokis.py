import os
import json
import math
import subprocess
from pathlib import Path
import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, gaussian_filter

ROOT = str(Path(__file__).resolve().parents[1])
BLENDER_EXE = r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe"
BLENDER_SCRIPT = os.path.join(ROOT, "scripts", "blender_rig_and_render_hd.py")
SCRATCH_DIR = os.path.join(ROOT, "build_3d_hd_tmp")

HD_CHARACTERS = [
    {
        "char_id": "monika_school",
        "doki_id": "monika",
        "rel_path": "monika escuela/Mon1.webp",
        "center_x_px": 475.0,
        "color_hex": "#34D399",
        "skirt_half_w": 148.0,
        "sock_type": "thigh_high_monika",
        "shoe_type": "uwabaki",
        "shoe_accent": (228, 106, 136),
    },
    {
        "char_id": "sayori_school",
        "doki_id": "sayori",
        "rel_path": "sayori escuela/Say4.webp",
        "center_x_px": 496.0,
        "color_hex": "#FB7185",
        "skirt_half_w": 146.0,
        "sock_type": "knee_white",
        "shoe_type": "uwabaki",
        "shoe_accent": (82, 178, 232),
    },
    {
        "char_id": "sayori_casual",
        "doki_id": "sayori",
        "rel_path": "sayori casual/Yori1.webp",
        "center_x_px": 498.0,
        "color_hex": "#FB7185",
        "skirt_half_w": 118.0,
        "sock_type": "crew_coral",
        "shoe_type": "sneaker_coral",
        "shoe_accent": (236, 112, 138),
    },
    {
        "char_id": "natsuki_school",
        "doki_id": "natsuki",
        "rel_path": "natsuki escuela/Schoola.webp",
        "center_x_px": 494.0,
        "color_hex": "#F472B6",
        "skirt_half_w": 145.0,
        "sock_type": "knee_white",
        "shoe_type": "uwabaki",
        "shoe_accent": (242, 104, 156),
    },
    {
        "char_id": "natsuki_casual",
        "doki_id": "natsuki",
        "rel_path": "natsuki casual/Casual1.webp",
        "center_x_px": 494.0,
        "color_hex": "#F472B6",
        "skirt_half_w": 148.0,
        "sock_type": "crew_pink",
        "shoe_type": "sneaker_pink",
        "shoe_accent": (244, 118, 172),
    },
    {
        "char_id": "yuri_school",
        "doki_id": "yuri",
        "rel_path": "yuri escuela/Yuri_school_1.webp",
        "center_x_px": 505.0,
        "color_hex": "#A855F7",
        "skirt_half_w": 152.0,
        "sock_type": "knee_dark",
        "shoe_type": "uwabaki",
        "shoe_accent": (138, 106, 206),
    },
    {
        "char_id": "yuri_casual",
        "doki_id": "yuri",
        "rel_path": "yuri casual/Casualclothes1-0.webp",
        "center_x_px": 505.0,
        "color_hex": "#A855F7",
        "skirt_half_w": 140.0,
        "sock_type": "tights_plum",
        "shoe_type": "boots_plum",
        "shoe_accent": (88, 58, 78),
    },
]


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / max(1e-6, (edge1 - edge0)), 0.0, 1.0)
    return float(t * t * (3.0 - 2.0 * t))


def synthesize_full_body_with_legs(orig_rgba: np.ndarray, cfg: dict) -> np.ndarray:
    """
    Takes the 960x960 waist/skirt-up DDLC HD sprite and synthesizes a seamless 960x1430
    full-body anime character with:
    1. Un-feathered original upper body & hands down to y=952
    2. Curved pleated skirt / shorts hem at y=948..966 with crisp DDLC outline
    3. Full cel-shaded anime legs (thighs, knees, calves, ankles) with a clean separation gap
       so left and right legs rig and articulate independently in 3D
    4. Character-accurate stockings/socks (Monika thigh-highs with pink trim, white/dark school
       knee-highs, casual striped crew socks, Yuri sheer tights) and uwabaki/casual shoes.
    """
    h0, w0, _ = orig_rgba.shape
    h_ext = 1430
    canvas = np.zeros((h_ext, w0, 4), dtype=np.uint8)

    center_x = float(cfg["center_x_px"])
    skirt_hw = float(cfg["skirt_half_w"])
    sock_type = cfg["sock_type"]
    shoe_type = cfg["shoe_type"]
    shoe_acc = cfg["shoe_accent"]

    # 0. Restore bottom 36 rows of orig_rgba where clean_all_doki_cutouts applied a cosine fade
    unfeathered = orig_rgba.copy()
    feather_rows = 36
    start_f = h0 - feather_rows
    for y in range(start_f, h0):
        t_f = (h0 - 1 - y) / float(feather_rows)
        factor = max(0.08, 0.5 * (1.0 - math.cos(math.pi * t_f)))
        row_a = unfeathered[y, :, 3].astype(np.float32)
        restored_a = np.where(row_a > 6, np.clip(row_a / factor, 0, 255), 0).astype(np.uint8)
        unfeathered[y, :, 3] = restored_a

    # 1. Draw Left Leg (side=-1) and Right Leg (side=+1) from y=922 to y=1418
    y_leg_start = 922
    y_ankle = 1350
    y_shoe_end = 1418

    outline_col = np.array([66, 38, 42], dtype=np.float32)
    skin_base = np.array([253, 225, 212], dtype=np.float32)
    skin_shadow = np.array([228, 186, 170], dtype=np.float32)
    skin_hi = np.array([255, 242, 235], dtype=np.float32)

    for y in range(y_leg_start, y_shoe_end + 1):
        is_shoe = y >= y_ankle
        t_leg = min(1.0, max(0.0, (y - y_leg_start) / float(y_ankle - y_leg_start)))
        t_shoe = min(1.0, max(0.0, (y - y_ankle) / float(y_shoe_end - y_ankle))) if is_shoe else 0.0

        if not is_shoe:
            thigh_w = 40.5 * (1.0 - 0.34 * smoothstep(0.0, 0.45, t_leg))
            calf_w = 3.8 * math.sin(((t_leg - 0.42) / 0.42) * math.pi) if 0.42 <= t_leg <= 0.84 else 0.0
            ankle_w = -7.8 * smoothstep(0.68, 0.98, t_leg)
            hw = thigh_w + calf_w + ankle_w
            if sock_type == "thigh_high_monika" and abs(y - 1044) < 6:
                hw -= 1.3 * (1.0 - abs(y - 1044) / 6.0)
        else:
            flare = math.sin(min(1.0, t_shoe * 1.12) * (math.pi * 0.66))
            hw = 20.5 + 11.5 * flare
            if t_shoe > 0.80:
                round_bottom = math.sqrt(max(0.0, 1.0 - ((t_shoe - 0.80) / 0.20) ** 2))
                hw *= (0.35 + 0.65 * round_bottom)

        for side in (-1.0, 1.0):
            leg_cx = center_x + side * (56.0 - 5.0 * smoothstep(0.0, 0.75, t_leg) + (3.5 * t_shoe if is_shoe else 0.0))
            x_min = max(0, int(math.floor(leg_cx - hw - 2)))
            x_max = min(w0 - 1, int(math.ceil(leg_cx + hw + 2)))

            for x in range(x_min, x_max + 1):
                dx = x - leg_cx
                u = dx / max(1.0, hw)
                edge_dist = (1.0 - abs(u)) * hw
                if edge_dist < 0.0:
                    continue

                alpha = min(1.0, edge_dist / 1.25)

                cuff_curve = 6.5 * (1.0 - min(1.0, u * u))
                if sock_type == "thigh_high_monika":
                    y_cuff = 1042.0 + cuff_curve
                elif sock_type in ("knee_white", "knee_dark"):
                    y_cuff = 1152.0 + cuff_curve
                elif sock_type in ("crew_coral", "crew_pink"):
                    y_cuff = 1272.0 + cuff_curve
                elif sock_type == "tights_plum":
                    y_cuff = 920.0
                else:
                    y_cuff = 1200.0

                if not is_shoe and y < y_cuff:
                    is_shadow = (u * side < -0.36) or (abs(u) > 0.74)
                    is_hi = (-0.18 <= u * side <= 0.16)
                    col = skin_shadow.copy() if is_shadow else (skin_hi.copy() if is_hi else skin_base.copy())
                    knee_d = abs(t_leg - 0.45)
                    if knee_d < 0.045 and abs(u) < 0.55:
                        blush_w = (1.0 - knee_d / 0.045) * (1.0 - abs(u) / 0.55)
                        col[0] = min(255.0, col[0] + 5.0 * blush_w)
                        col[1] = max(0.0, col[1] - 9.0 * blush_w)
                        col[2] = max(0.0, col[2] - 8.0 * blush_w)
                elif not is_shoe:
                    if sock_type == "thigh_high_monika":
                        if y < y_cuff + 8.5:
                            col = np.array([232, 106, 136], dtype=np.float32)
                        else:
                            col = np.array([40, 32, 38], dtype=np.float32)
                            if -0.22 <= u <= 0.18:
                                col = np.array([64, 52, 60], dtype=np.float32)
                            elif abs(u) > 0.70:
                                col = np.array([26, 20, 25], dtype=np.float32)
                    elif sock_type == "knee_white":
                        col = np.array([245, 247, 252], dtype=np.float32)
                        if (u * side < -0.35) or (abs(u) > 0.72) or (y < y_cuff + 5.0):
                            col = np.array([210, 218, 232], dtype=np.float32)
                    elif sock_type == "knee_dark":
                        col = np.array([36, 38, 52], dtype=np.float32)
                        if -0.20 <= u <= 0.18:
                            col = np.array([56, 60, 80], dtype=np.float32)
                        elif abs(u) > 0.72:
                            col = np.array([24, 25, 36], dtype=np.float32)
                    elif sock_type in ("crew_coral", "crew_pink"):
                        col = np.array([246, 247, 252], dtype=np.float32)
                        stripe_col = np.array(shoe_acc, dtype=np.float32)
                        if (y_cuff + 4.0 <= y <= y_cuff + 10.0) or (y_cuff + 15.0 <= y <= y_cuff + 20.0):
                            col = stripe_col
                        elif (u * side < -0.35) or (abs(u) > 0.72):
                            col = np.array([214, 220, 232], dtype=np.float32)
                    else:
                        col = np.array([46, 35, 52], dtype=np.float32)
                        sheen = max(0.0, 1.0 - (u / 0.48) ** 2) * (1.0 - 0.3 * abs(t_leg - 0.35))
                        col += np.array([42.0, 30.0, 36.0], dtype=np.float32) * sheen
                else:
                    acc_arr = np.array(shoe_acc, dtype=np.float32)
                    if shoe_type == "uwabaki":
                        col = np.array([246, 248, 253], dtype=np.float32)
                        if (u * side < -0.40) or (abs(u) > 0.74):
                            col = np.array([214, 222, 235], dtype=np.float32)
                        if 0.24 <= t_shoe <= 0.34 and abs(u) < 0.72:
                            col = np.array([218, 224, 236], dtype=np.float32)
                        toe_curve = 0.64 + 0.14 * (u * u)
                        if t_shoe >= toe_curve:
                            col = acc_arr * (0.88 if abs(u) > 0.65 else 1.0)
                    elif shoe_type in ("sneaker_coral", "sneaker_pink"):
                        col = acc_arr.copy()
                        if t_shoe >= 0.76 or (0.18 <= t_shoe <= 0.42 and abs(u) < 0.45):
                            col = np.array([248, 249, 253], dtype=np.float32)
                        elif abs(u) > 0.70:
                            col = acc_arr * 0.82
                    else:
                        col = np.array([58, 38, 48], dtype=np.float32)
                        if -0.22 <= u <= 0.18 and t_shoe < 0.80:
                            col = np.array([86, 58, 72], dtype=np.float32)
                        elif t_shoe >= 0.82:
                            col = np.array([32, 22, 28], dtype=np.float32)

                if y < 992:
                    skirt_shade = 0.76 + 0.24 * smoothstep(960.0, 992.0, float(y))
                    col *= skirt_shade

                is_cuff_line = (not is_shoe) and (abs(y - y_cuff) < 1.8) and (sock_type != "tights_plum")
                is_shoe_line = is_shoe and (t_shoe < 0.04 or abs(t_shoe - 0.82) < 0.025)
                if edge_dist < 2.3:
                    line_w = 1.0 - max(0.0, (edge_dist - 0.8) / 1.5)
                    col = col * (1.0 - line_w) + outline_col * line_w
                elif is_cuff_line or is_shoe_line:
                    col = col * 0.35 + outline_col * 0.65

                canvas[y, x, 0] = int(np.clip(col[0], 0, 255))
                canvas[y, x, 1] = int(np.clip(col[1], 0, 255))
                canvas[y, x, 2] = int(np.clip(col[2], 0, 255))
                canvas[y, x, 3] = int(np.clip(alpha * 255.0, 0, 255))

    # 2. Composite original un-feathered sprite down to y=954, and smoothly curve the skirt hem between y=946..966
    canvas[:946, :, :] = unfeathered[:946, :, :]
    lx_skirt = int(center_x - skirt_hw)
    rx_skirt = int(center_x + skirt_hw)

    for y in range(946, 968):
        for x in range(w0):
            is_outer_hand = abs(x - center_x) > (skirt_hw + 6.0)
            if is_outer_hand and y < h0:
                src = unfeathered[y, x]
                if src[3] > 15:
                    canvas[y, x] = src
                continue

            if x < lx_skirt - 6 or x > rx_skirt + 6:
                continue

            pleat_wave = 3.8 * math.cos((x - center_x) * 0.13)
            y_hem = 958.0 + pleat_wave - 5.0 * (((x - center_x) / max(1.0, skirt_hw)) ** 2)
            if y > y_hem + 1.5:
                continue

            src_y = min(y, 948)
            sample_rgba = unfeathered[src_y, x].astype(np.float32)
            if sample_rgba[3] < 35:
                continue

            hem_dist = y_hem - y
            rgb = sample_rgba[:3].copy()
            if hem_dist < 2.3:
                lw = 1.0 - max(0.0, hem_dist / 2.3)
                rgb = rgb * (1.0 - lw) + outline_col * lw

            a_sk = 1.0 if hem_dist >= 0.0 else max(0.0, 1.0 + hem_dist / 1.5)
            dst = canvas[y, x].astype(np.float32)
            out_a = a_sk + (dst[3] / 255.0) * (1.0 - a_sk)
            if out_a > 1e-4:
                out_rgb = (rgb * a_sk + dst[:3] * (dst[3] / 255.0) * (1.0 - a_sk)) / out_a
                canvas[y, x, :3] = np.clip(out_rgb, 0, 255).astype(np.uint8)
                canvas[y, x, 3] = int(np.clip(out_a * 255.0, 0, 255))

    return canvas


def compute_hd_bone_weights(x, z_norm, char_id):
    w = {
        "root": 0.0,
        "hips": 0.0,
        "spine": 0.0,
        "head": 0.0,
        "hair_l": 0.0,
        "hair_r": 0.0,
        "arm_l": 0.0,
        "arm_r": 0.0,
        "thigh_l": 0.0,
        "thigh_r": 0.0,
        "shin_l": 0.0,
        "shin_r": 0.0,
    }

    is_yuri = char_id.startswith("yuri")
    is_monika = char_id.startswith("monika")

    if z_norm >= 0.31:
        is_head = smoothstep(0.71, 0.76, z_norm)
        is_upper = smoothstep(0.45, 0.52, z_norm) * (1.0 - is_head)
        is_hips = max(0.0, 1.0 - is_head - is_upper)

        w["head"] += is_head

        if is_upper > 0.001:
            arm_lat = smoothstep(0.16, 0.28, abs(x))
            arm_w = (0.24 if (is_yuri or is_monika) else 0.34) * arm_lat
            w["spine"] += is_upper * (1.0 - arm_w)
            if x < 0:
                w["arm_l"] += is_upper * arm_w
            else:
                w["arm_r"] += is_upper * arm_w

        if is_hips > 0.001:
            skirt_thigh_blend = 1.0 - smoothstep(0.31, 0.36, z_norm)
            w["hips"] += is_hips * (1.0 - 0.50 * skirt_thigh_blend)
            if skirt_thigh_blend > 0.001:
                if x < 0:
                    w["thigh_l"] += is_hips * 0.50 * skirt_thigh_blend
                else:
                    w["thigh_r"] += is_hips * 0.50 * skirt_thigh_blend
    else:
        is_thigh = smoothstep(0.15, 0.21, z_norm)
        is_shin = 1.0 - is_thigh
        if x < 0:
            w["thigh_l"] += is_thigh
            w["shin_l"] += is_shin
        else:
            w["thigh_r"] += is_thigh
            w["shin_r"] += is_shin

    total = sum(w.values())
    if total > 1e-6:
        for k in w:
            w[k] /= total
    else:
        w["hips"] = 1.0
    return w


def build_hd_character_3d_mesh_json(char_cfg):
    char_id = char_cfg["char_id"]
    src_path = os.path.join(
        ROOT, "android_app", "app", "src", "main", "assets", "ddsc",
        char_cfg["rel_path"]
    )
    im = Image.open(src_path).convert("RGBA")
    orig_arr = np.array(im)

    arr = synthesize_full_body_with_legs(orig_arr, char_cfg)

    alpha = arr[:, :, 3]
    ys, xs = np.where(alpha > 20)
    y0, y1 = max(0, int(ys.min()) - 4), min(arr.shape[0], int(ys.max()) + 4)
    x0, x1 = max(0, int(xs.min()) - 4), min(arr.shape[1], int(xs.max()) + 4)
    crop = arr[y0:y1, x0:x1].copy()
    ch, cw, _ = crop.shape

    center_u = (char_cfg["center_x_px"] - x0) / float(max(1, cw - 1))

    c_alpha = crop[:, :, 3]
    mask_hi = c_alpha > 42
    _, nearest_idx = distance_transform_edt(~mask_hi, return_indices=True)
    dilated_rgb = crop[:, :, :3].copy()
    for ch_idx in range(3):
        channel = dilated_rgb[:, :, ch_idx]
        dilated_rgb[:, :, ch_idx] = channel[tuple(nearest_idx)]
    tex_rgba = np.dstack([dilated_rgb, np.full_like(c_alpha, 255)])

    os.makedirs(SCRATCH_DIR, exist_ok=True)
    tex_out_path = os.path.join(SCRATCH_DIR, f"{char_id}_tex.png")
    Image.fromarray(tex_rgba, "RGBA").save(tex_out_path)
    Image.fromarray(crop, "RGBA").save(os.path.join(SCRATCH_DIR, f"{char_id}_fullbody.png"))

    grid_w, grid_h = 92, 164
    mask_grid_im = Image.fromarray((mask_hi.astype(np.uint8) * 255), "L").resize(
        (grid_w, grid_h), Image.Resampling.BILINEAR
    )
    grid_mask = np.array(mask_grid_im) > 135

    edt = distance_transform_edt(grid_mask)
    edt_smooth = gaussian_filter(edt.astype(np.float32), sigma=3.6)
    max_edt = max(1.0, float(edt_smooth.max()))
    norm_edt = np.clip(edt_smooth / max_edt, 0.0, 1.0)

    char_h_world = 1.88
    aspect = cw / float(ch)
    char_w_world = char_h_world * aspect

    verts = []
    uvs = []
    weights_map = {
        "root": [], "hips": [], "spine": [], "head": [],
        "hair_l": [], "hair_r": [], "arm_l": [], "arm_r": [],
        "thigh_l": [], "thigh_r": [], "shin_l": [], "shin_r": [],
    }

    front_idx = np.full((grid_h, grid_w), -1, dtype=np.int32)
    back_idx = np.full((grid_h, grid_w), -1, dtype=np.int32)
    boundary_vert_indices = set()

    for r in range(grid_h):
        for c in range(grid_w):
            if not grid_mask[r, c]:
                continue

            u = c / float(grid_w - 1)
            v_img = r / float(grid_h - 1)
            z_norm = 1.0 - v_img
            uv = [float(u), float(z_norm)]

            x = (u - center_u) * char_w_world
            z = 0.04 + z_norm * char_h_world

            is_boundary = False
            for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                nr, nc = r + dr, c + dc
                if nr < 0 or nr >= grid_h or nc < 0 or nc >= grid_w or not grid_mask[nr, nc]:
                    is_boundary = True
                    break

            if is_boundary:
                depth = 0.0
            else:
                base_dome = math.sin(norm_edt[r, c] * (math.pi * 0.5)) * 0.135
                bust_bump = math.exp(-((z_norm - 0.58) / 0.09) ** 2) * math.exp(-((x) / 0.15) ** 2) * 0.022
                depth = base_dome + bust_bump * norm_edt[r, c]

            bw = compute_hd_bone_weights(x, z_norm, char_id)

            f_i = len(verts)
            verts.append([float(x), float(-depth), float(z)])
            uvs.append(uv)
            for bname, wval in bw.items():
                if wval > 0.001:
                    weights_map[bname].append([f_i, round(float(wval), 4)])
            front_idx[r, c] = f_i

            if is_boundary or depth <= 1e-4:
                back_idx[r, c] = f_i
                boundary_vert_indices.add(f_i)
            else:
                b_i = len(verts)
                verts.append([float(x), float(depth * 0.82), float(z)])
                uvs.append(uv)
                for bname, wval in bw.items():
                    if wval > 0.001:
                        weights_map[bname].append([b_i, round(float(wval), 4)])
                back_idx[r, c] = b_i

    faces = []
    adj = {vi: set() for vi in boundary_vert_indices}

    def register_edge(u_i, v_i):
        if u_i in boundary_vert_indices and v_i in boundary_vert_indices and u_i != v_i:
            adj[u_i].add(v_i)
            adj[v_i].add(u_i)

    for r in range(grid_h - 1):
        for c in range(grid_w - 1):
            if (
                grid_mask[r, c]
                and grid_mask[r + 1, c]
                and grid_mask[r, c + 1]
                and grid_mask[r + 1, c + 1]
            ):
                f00 = int(front_idx[r, c])
                f10 = int(front_idx[r + 1, c])
                f11 = int(front_idx[r + 1, c + 1])
                f01 = int(front_idx[r, c + 1])
                if len({f00, f10, f11}) == 3:
                    faces.append([f00, f10, f11])
                    register_edge(f00, f10)
                    register_edge(f10, f11)
                    register_edge(f11, f00)
                if len({f00, f11, f01}) == 3:
                    faces.append([f00, f11, f01])
                    register_edge(f00, f11)
                    register_edge(f11, f01)
                    register_edge(f01, f00)

                b00 = int(back_idx[r, c])
                b10 = int(back_idx[r + 1, c])
                b11 = int(back_idx[r + 1, c + 1])
                b01 = int(back_idx[r, c + 1])
                if len({b00, b11, b10}) == 3:
                    faces.append([b00, b11, b10])
                if len({b00, b01, b11}) == 3:
                    faces.append([b00, b01, b11])

    for _ in range(4):
        new_pos = {}
        for vi in boundary_vert_indices:
            nbrs = adj[vi]
            if len(nbrs) >= 2:
                avg_x = sum(verts[n][0] for n in nbrs) / len(nbrs)
                avg_z = sum(verts[n][2] for n in nbrs) / len(nbrs)
                new_pos[vi] = (
                    0.45 * verts[vi][0] + 0.55 * avg_x,
                    0.45 * verts[vi][2] + 0.55 * avg_z,
                )
        for vi, (nx, nz) in new_pos.items():
            verts[vi][0] = round(nx, 5)
            verts[vi][2] = round(nz, 5)

    for v in verts:
        v[0] = round(v[0], 5)
        v[1] = round(v[1], 5)
        v[2] = round(v[2], 5)

    mesh_json_path = os.path.join(SCRATCH_DIR, f"{char_id}_mesh.json")
    data = {
        "char_id": char_id,
        "doki_id": char_cfg["doki_id"],
        "tex_path": tex_out_path,
        "color_hex": char_cfg["color_hex"],
        "verts": verts,
        "faces": faces,
        "uvs": uvs,
        "weights": weights_map,
    }
    with open(mesh_json_path, "w", encoding="utf-8") as f:
        json.dump(data, f)

    print(f"[HD FullBody MeshGen] {char_id}: {len(verts)} verts, {len(faces)} tris -> {mesh_json_path}")
    return mesh_json_path


def create_hd_showcase_gifs_and_posters():
    showcase_dir = os.path.join(ROOT, "docs", "characters", "3d_hd")
    os.makedirs(showcase_dir, exist_ok=True)

    for cfg in HD_CHARACTERS:
        cid = cfg["char_id"]
        render_dir = os.path.join(
            ROOT, "android_app", "app", "src", "main", "assets", "doki3d_hd", cid
        )
        dance_frames = []
        for i in range(12):
            fp = os.path.join(render_dir, f"dance_{i:02d}.webp")
            if os.path.exists(fp):
                im = Image.open(fp).convert("RGBA")
                dance_frames.append(im)

        if dance_frames:
            poster_frame = dance_frames[2] if len(dance_frames) > 2 else dance_frames[0]
            poster_path = os.path.join(showcase_dir, f"{cid}_3d.png")
            poster_frame.save(poster_path, "PNG")

            gif_path = os.path.join(showcase_dir, f"{cid}_3d_dance.gif")
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
        render_out_dir = os.path.join(
            ROOT, "android_app", "app", "src", "main", "assets", "doki3d_hd", cid
        )
        glb_out_path = os.path.join(ROOT, "docs", "models3d", f"{cid}_3d.glb")
        manifest_entries.append({
            "char_id": cid,
            "mesh_json": mesh_json,
            "render_out_dir": render_out_dir,
            "glb_out_path": glb_out_path,
        })

    manifest_path = os.path.join(SCRATCH_DIR, "blender_hd_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"characters": manifest_entries}, f, indent=2)

    print("[Blender 5.2 HD] Launching headless Blender 5.2 Workbench 3D HD Full-Body pipeline...")
    cmd = [
        BLENDER_EXE,
        "--background",
        "--python",
        BLENDER_SCRIPT,
        "--",
        manifest_path,
    ]
    res = subprocess.run(cmd, check=True)
    print(f"[Blender 5.2 HD] Completed with return code {res.returncode}")

    create_hd_showcase_gifs_and_posters()


if __name__ == "__main__":
    main()
